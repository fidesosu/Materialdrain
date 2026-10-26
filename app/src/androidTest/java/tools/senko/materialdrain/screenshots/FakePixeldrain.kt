package tools.senko.materialdrain.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Interceptor
import okio.buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A made-up pixeldrain.com for the screenshots: answers every request of the app's clients (see HttpInterceptors) from
 * the account below, so nothing reaches the network. The files have contents too: pictures drawn here, the songs and the
 * video from the test's assets (fake-pixeldrain, made with ffmpeg: tagged, with covers), a PDF, text, Markdown and a zip
 * made here. A request it doesn't know gets a 404, and a line in the log (tag FakePixeldrain) to show what's missing.
 */
class FakePixeldrain : Interceptor {

    /** A file of the account: [days] ago, [thumb] whether the host makes it a thumbnail (pictures and videos). */
    private class File(val id: String, val name: String, val size: Long, val mime: String, val days: Long, val thumb: Boolean = false)

    private val files = listOf(
        File("sUnSet01", "Sunset over the bay.jpg", 4_200_000, "image/jpeg", 1, thumb = true),
        File("mTrail02", "Mountain trail.jpg", 3_100_000, "image/jpeg", 2, thumb = true),
        File("cItyLt03", "City lights.png", 6_800_000, "image/png", 3, thumb = true),
        File("vLog0004", "Weekend vlog.mp4", 248_000_000, "video/mp4", 4, thumb = true),
        File("nDrive05", "Night drive.mp3", 8_400_000, "audio/mpeg", 5),
        File("oWaves06", "Ocean waves.flac", 31_000_000, "audio/flac", 7),
        File("tRipIt07", "Trip itinerary.pdf", 412_000, "application/pdf", 9),
        File("bAckUp08", "Project backup.zip", 1_200_000_000, "application/zip", 12),
        File("nOtes009", "Notes.txt", 3_200, "text/plain", 15),
        File("rEadMe10", "Readme.md", 2_400, "text/markdown", 18),
        File("bUdget11", "Budget 2026.xlsx", 58_000, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 20),
        File("mCoffe12", "Morning coffee.mp3", 7_900_000, "audio/mpeg", 22)
    )
    private val byId = files.associateBy { it.id }
    private val byName = files.associateBy { it.name }

    /** The filesystem: each folder's path under "me", and what's in it ("/" at the end for a folder). */
    private val folders = mapOf(
        "me" to listOf("Photos/", "Music/", "Documents/", "Videos/", "Projects/", "Wallpaper.png", "Readme.md"),
        "me/Photos" to listOf("Holiday 2026/", "Sunset over the bay.jpg", "Mountain trail.jpg", "City lights.png"),
        "me/Photos/Holiday 2026" to listOf("Beach.jpg", "Harbour.jpg", "Old town.jpg", "Lighthouse.jpg", "Market.jpg", "Pier at dusk.jpg"),
        "me/Music" to listOf("Night drive.mp3", "Ocean waves.flac", "Morning coffee.mp3"),
        "me/Documents" to listOf("Trip itinerary.pdf", "Budget 2026.xlsx", "Notes.txt", "Readme.md"),
        "me/Videos" to listOf("Weekend vlog.mp4"),
        "me/Projects" to listOf("Project backup.zip", "Notes.txt")
    )

    private val lists = listOf(
        Triple("hOliday1", "Holiday 2026", listOf("sUnSet01", "mTrail02", "cItyLt03", "vLog0004")),
        Triple("wAllpap2", "Wallpapers", listOf("cItyLt03", "sUnSet01")),
        Triple("mIxtape3", "Mixtape", listOf("nDrive05", "oWaves06", "mCoffe12"))
    )

    /** The path of every request asked so far ("/api/user/files"), for tests of what the app fetches when (see AppLockSecurityTest). */
    val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** The Range header of each request which had one, for tests of downloads going on (see DownloadRetryTest). */
    val ranges: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** How long the lists of files (the account's, a folder's) take to come, as on a slow connection (see PlaceholderTest). */
    @Volatile
    var listDelayMs = 0L

    /** Whether the host answers a range with the whole file, as some hosts do (see DocumentsProviderTest). */
    @Volatile
    var ignoreRanges = false

    /** The file whose next download (of all of it) stops halfway, as a connection that drops; null for none. */
    @Volatile
    var breakNextDownloadOf: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        requests += url.encodedPath
        Log.v(TAG, "asked: ${request.method} $url ${request.header("Range").orEmpty()}")
        request.header("Range")?.let { ranges += it }
        if (listDelayMs > 0 && (url.encodedPath == "/api/user/files" || (url.encodedPath.startsWith("/api/filesystem") && url.query == "stat"))) {
            Thread.sleep(listDelayMs)
        }
        val path = url.encodedPathSegments.map { java.net.URLDecoder.decode(it, "UTF-8") }
        val api = if (path.firstOrNull() == "api") path.drop(1) else null
        val answer: Answer? = when {
            api == null -> null
            api == listOf("user") -> json(user())
            // Signing in (with any password) and out
            api == listOf("user", "login") -> json("""{"auth_key": "screenshots-session"}""")
            api == listOf("user", "session") -> json("""{"success": true}""")
            api == listOf("user", "files") -> json("""{"files": [${files.joinToString { fileJson(it) }}]}""")
            api == listOf("user", "lists") -> json(
                """{"lists": [${lists.joinToString { (id, title, ids) -> """{"id": "$id", "title": ${q(title)}, "file_count": ${ids.size}, "can_edit": true, "date_created": "${daysAgo(30)}"}""" }}]}"""
            )
            api.size == 2 && api[0] == "list" -> lists.firstOrNull { it.first == api[1] }?.let { (id, title, ids) ->
                json("""{"id": "$id", "title": ${q(title)}, "can_edit": true, "files": [${ids.mapNotNull(byId::get).joinToString { fileJson(it) }}]}""")
            }
            api.size == 3 && api[0] == "file" && api[2] == "info" -> byId[api[1]]?.let { json(fileJson(it)) }
            api.size == 3 && api[0] == "file" && api[2] == "thumbnail" -> byId[api[1]]?.takeIf { it.thumb }?.let { picture(it.name, 256) }
            api.size == 2 && api[0] == "file" -> byId[api[1]]?.let { content(it.name) }
            api.firstOrNull() == "filesystem" -> filesystem(api.drop(1).joinToString("/"), url.queryParameterNames)
            else -> null
        }
        if (answer == null) Log.w(TAG, "not answered: ${request.method} $url")
        else Log.d(TAG, "${answer.code} ${request.method} $url ${request.header("Range").orEmpty()}")
        val name = api?.takeIf { it.size == 2 && it[0] == "file" }?.let { byId[it[1]]?.name }
        if (answer != null && name != null && name == breakNextDownloadOf && request.header("Range") == null) {
            breakNextDownloadOf = null
            return answer.brokenHalfway(request)
        }
        return (answer ?: Answer(404, "application/json", """{"success": false, "value": "not_found"}""".toByteArray()))
            .toResponse(if (ignoreRanges) request.newBuilder().removeHeader("Range").build() else request)
    }

    private fun filesystem(path: String, query: Set<String>): Answer? {
        val name = path.substringAfterLast('/')
        return when {
            "thumbnail" in query -> if (isPicture(name)) picture(name, 256) else null
            // Inside a zip: its entries, or one entry's text
            "zip_info" in query -> json(ZIP_INFO)
            "zip_file" in query -> Answer(200, "text/plain", NOTES.toByteArray())
            path in folders -> json(listing(path))
            "stat" in query -> null
            else -> content(name)
        }
    }

    private fun listing(path: String): String {
        val crumbs = path.split('/').runningReduce { a, b -> "$a/$b" }
        // The folders down to this one, with the fields Pixeldrain sends for every entry
        val breadcrumb = crumbs.joinToString {
            """{"type": "dir", "path": "/$it", "name": ${q(it.substringAfterLast('/'))}, "created": "${daysAgo(60)}",
                "modified": "${daysAgo(3)}", "mode_string": "drwxr-xr-x", "mode_octal": "755", "created_by": "sam",
                "file_size": 0, "file_type": "", "sha256_sum": ""}"""
        }
        val children = folders.getValue(path).mapIndexed { i, entry ->
            val dir = entry.endsWith("/")
            val name = entry.removeSuffix("/")
            val known = byName[name]
            val mime = if (dir) "" else known?.mime ?: mimeOf(name)
            val size = if (dir) 0 else known?.size ?: (1_800_000L + name.length * 97_000L)
            """{"type": "${if (dir) "dir" else "file"}", "path": ${q("/$path/$name")}, "name": ${q(name)}, "file_size": $size,
                "file_type": "$mime", "mime_type": "$mime", "created": "${daysAgo(40L + i)}", "modified": "${daysAgo(2L + i)}",
                "mode_string": "${if (dir) "drwxr-xr-x" else "-rw-r--r--"}", "mode_octal": "${if (dir) "755" else "644"}",
                "created_by": "sam", "sha256_sum": "", "id": ${known?.let { q(it.id) } ?: "null"}}"""
        }
        return """{"path": [$breadcrumb], "base_index": ${crumbs.size - 1}, "children": [${children.joinToString()}],
            "permissions": {"owner": true, "read": true, "write": true, "delete": true}, "context": {"premium_transfer": true}}"""
    }

    private fun user(): String {
        val used = files.sumOf { it.size }
        return """{"id": "screenshots", "username": "sam", "email_addresses": ["sam@example.com"], "otp_enabled": true,
            "subscription": {"id": "prepaid", "name": "Prepaid", "type": "prepaid", "file_size_limit": 100000000000,
              "storage_space": 2000000000000, "filesystem_access": true, "filesystem_storage_limit": 2000000000000,
              "monthly_transfer_cap": 0},
            "storage_space_used": $used, "filesystem_storage_used": 412000000000, "file_count": ${files.size},
            "filesystem_node_count": 1834, "balance_micro_eur": 12340000, "monthly_transfer_used": 86000000000}"""
    }

    private fun fileJson(f: File) =
        """{"id": "${f.id}", "name": ${q(f.name)}, "size": ${f.size}, "mime_type": "${f.mime}", "date_upload": "${daysAgo(f.days)}",
            "date_last_view": "${daysAgo(0)}", "views": ${(f.size % 900) + 12}, "downloads": ${(f.size % 300) + 3},
            "bandwidth_used": ${f.size * 3}, "can_edit": true, "thumbnail_href": "/file/${f.id}/thumbnail"}"""

    // ---- Contents ----

    /** The bytes of the file called [name], by its kind; null for kinds without any (the app then shows no preview). */
    private fun content(name: String): Answer? {
        val mime = byName[name]?.mime ?: mimeOf(name)
        return when {
            mime.startsWith("image/") -> picture(name, 1600)
            mime.startsWith("audio/") || mime.startsWith("video/") -> asset(
                when {
                    mime.startsWith("video/") -> "video.mp4"
                    name.startsWith("Ocean") -> "ocean-waves.mp3"
                    name.startsWith("Morning") -> "morning-coffee.mp3"
                    else -> "night-drive.mp3"
                },
                mime
            )
            mime == "application/pdf" -> Answer(200, mime, pdf())
            mime == "application/zip" -> Answer(200, mime, zip())
            mime == "text/markdown" -> Answer(200, mime, README.toByteArray())
            mime.startsWith("text/") -> Answer(200, mime, NOTES.toByteArray())
            else -> null
        }
    }

    private fun asset(file: String, mime: String): Answer {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("fake-pixeldrain/$file").use { it.readBytes() }
        return Answer(200, mime, bytes)
    }

    /** A picture for [name], [size] wide: a gradient of two colours made from its name, with a sun, so each has its own. */
    private fun picture(name: String, size: Int): Answer {
        val hue = (name.hashCode() and 0x7fffffff) % 360f
        val width = size
        val height = if (size > 256) size * 2 / 3 else size
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.95f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 50) % 360, 0.75f, 0.45f))
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        val unit = width / 256f
        canvas.drawCircle((80f + hue % 100) * unit, 96f * unit * height / width, 36f * unit, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 244, 214) })
        // Hills along the bottom
        val hills = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.HSVToColor(160, floatArrayOf((hue + 90) % 360, 0.5f, 0.3f)) }
        canvas.drawOval(-0.2f * width, 0.72f * height, 0.7f * width, 1.4f * height, hills)
        canvas.drawOval(0.4f * width, 0.78f * height, 1.3f * width, 1.5f * height, hills)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Answer(200, "image/png", out.toByteArray())
    }

    private fun pdf(): ByteArray {
        val document = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; isFakeBoldText = true }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = Color.DKGRAY }
        val days = listOf(
            "Day 1" to listOf("Arrive in the afternoon", "Check in at the harbour hotel", "Dinner by the pier"),
            "Day 2" to listOf("Mountain trail, 12 km", "Picnic at the lookout", "Sunset over the bay"),
            "Day 3" to listOf("Old town walk", "Market in the morning", "Train home at 18:40")
        )
        days.chunked(2).forEachIndexed { pageIndex, pageDays ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create())
            var y = 80f
            page.canvas.drawText(if (pageIndex == 0) "Trip itinerary" else "Trip itinerary (continued)", 60f, y, title)
            y += 50
            pageDays.forEach { (day, items) ->
                page.canvas.drawText(day, 60f, y, title.apply { textSize = 18f })
                y += 30
                items.forEach { page.canvas.drawText("•  $it", 76f, y, text); y += 24 }
                y += 24
            }
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also { document.writeTo(it); document.close() }.toByteArray()
    }

    private fun zip(): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            listOf("src/main.kt" to "fun main() = println(\"Hello\")\n", "README.md" to README, "notes/todo.txt" to NOTES)
                .forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }.toByteArray()

    private class Answer(val code: Int, val type: String, val body: ByteArray) {
        /** All of it promised, but the connection drops after half. */
        fun brokenHalfway(request: Request): Response {
            val half = okio.Buffer().write(body, 0, body.size / 2)
            val source = object : okio.ForwardingSource(half) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val read = super.read(sink, byteCount)
                    if (read < 0) throw java.io.IOException("The connection dropped (FakePixeldrain.breakNextDownloadOf)")
                    return read
                }
            }
            val responseBody = object : okhttp3.ResponseBody() {
                override fun contentType() = type.toMediaType()
                override fun contentLength() = body.size.toLong()
                override fun source() = source.buffer()
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", type).header("Accept-Ranges", "bytes").body(responseBody).build()
        }

        fun toResponse(request: Request): Response {
            // A range (the players read files in parts) gets just that part
            val range = request.header("Range")?.let { Regex("""bytes=(\d+)-(\d*)""").find(it) }
            if (code == 200 && range != null) {
                val start = range.groupValues[1].toInt().coerceAtMost(body.size)
                val end = range.groupValues[2].toIntOrNull()?.coerceAtMost(body.size - 1) ?: (body.size - 1)
                return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(206).message("Partial Content")
                    .header("Content-Type", type)
                    .header("Content-Range", "bytes $start-$end/${body.size}")
                    .header("Accept-Ranges", "bytes")
                    .body(body.copyOfRange(start, end + 1).toResponseBody(type.toMediaType()))
                    .build()
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Not Found")
                .header("Content-Type", type)
                .header("Accept-Ranges", "bytes")
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }
    }

    private fun json(text: String) = Answer(200, "application/json", text.toByteArray())

    private fun isPicture(name: String) = mimeOf(name).startsWith("image/") || mimeOf(name).startsWith("video/")

    private fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "md" -> "text/markdown"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun daysAgo(days: Long) = Instant.now().minus(days, ChronoUnit.DAYS).minus(days * 37, ChronoUnit.MINUTES).toString()

    private fun q(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val TAG = "FakePixeldrain"

        val README = """
            # Holiday photos

            Everything from the **2026** trip, sorted by day.

            ## What's where

            - `Photos/Holiday 2026`: the best ones
            - `Videos`: the weekend vlog
            - `Documents`: the itinerary and the budget

            > Remember to back up the originals before deleting anything.

            1. Pick the favourites
            2. Make a list of them
            3. Share the list
        """.trimIndent()

        /** What's in Project backup.zip, as Pixeldrain's ?zip_info lists it (see ZipInfo). */
        val ZIP_INFO = """
            {"children": {
              "app": {"children": {
                "src": {"children": {"Main.kt": {"size": 4210}, "Player.kt": {"size": 12873}, "Settings.kt": {"size": 6532}}},
                "build.gradle.kts": {"size": 3120}
              }},
              "docs": {"children": {"guide.md": {"size": 8410}, "screenshot.png": {"size": 245000}}},
              "assets": {"children": {"icon.svg": {"size": 2310}, "theme.json": {"size": 980}}},
              "README.md": {"size": 2400},
              "LICENSE": {"size": 1069},
              "settings.gradle.kts": {"size": 412}
            }}
        """.trimIndent()

        val NOTES = """
            Things to do this week

            - Renew the storage plan
            - Upload the rest of the trip photos
            - Make a list for the wallpapers
            - Send the itinerary to Alex

            Shopping: coffee, bread, film for the old camera
        """.trimIndent()
    }
}
