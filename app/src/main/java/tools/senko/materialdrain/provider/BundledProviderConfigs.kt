package tools.senko.materialdrain.provider

import android.content.Context
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.provider.api.ProviderLog

/** The example configs in docs/provider-configs are bundled as assets (see app/build.gradle.kts). */
private const val BUNDLED_PIXELDRAIN_FILE = "pixeldrain.json"
private const val PIXELDRAIN_CONFIG_ID = "tools.senko.materialdrain.examples.pixeldrain"
private const val BUNDLED_PREFS = "bundled_configs"
private const val KEY_PIXELDRAIN_IMPORTED = "pixeldrain_imported_once"

/**
 * Imports the bundled Pixeldrain config on the first start, and makes it the host the Files, Lists and Filesystem
 * screens use, so the config can be tested right away. It's only ever offered once: a config the user removed
 * stays removed, the Pixeldrain account in Settings keeps working without it.
 */
fun importBundledPixeldrainConfig(context: Context, store: ProviderConfigStore) {
    val prefs = context.getSharedPreferences(BUNDLED_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_PIXELDRAIN_IMPORTED, false)) return
    if (store.providers.value.any { it.config.meta?.id == PIXELDRAIN_CONFIG_ID }) {
        prefs.edit().putBoolean(KEY_PIXELDRAIN_IMPORTED, true).apply()
        return
    }
    val text = try {
        context.assets.open(BUNDLED_PIXELDRAIN_FILE).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ProviderLog.e("Config", "could not read the bundled $BUNDLED_PIXELDRAIN_FILE", e)
        return
    }
    val config = ProviderConfigCodec.decode(text)
    if (config == null) {
        ProviderLog.e("Config", "the bundled $BUNDLED_PIXELDRAIN_FILE is not a valid provider config")
        return
    }
    val id = store.import(config)
    store.setActive(id)
    prefs.edit().putBoolean(KEY_PIXELDRAIN_IMPORTED, true).apply()
    ProviderLog.i("Config", "imported the bundled '${config.name}' config and made it the active host")
}
