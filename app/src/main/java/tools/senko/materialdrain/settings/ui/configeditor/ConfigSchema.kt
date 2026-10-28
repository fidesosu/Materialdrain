package tools.senko.materialdrain.settings.ui.configeditor

import android.content.res.Resources
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import tools.senko.materialdrain.R

/*
 * What the config editor knows about each kind of config: its fields, how each one is edited, and what it means. The
 * meanings are the ones in the documentation's provider configuration page (docs/provider-configuration.md in the
 * website branch, https://materialdrain.senko.tools/docs/provider-configuration/), kept short: one line under the
 * field, with the default when there is one. The editor works on the config's JSON itself, so a field that isn't
 * described here is still kept, and shown as it is (see ConfigEditor's "Other fields").
 */

/** How a field is edited. */
sealed interface FieldKind {
    /** A line of text; [multiline] for longer text, e.g. a JSON body. */
    data class Text(val multiline: Boolean = false) : FieldKind
    data object Number : FieldKind
    /** On or off. */
    data object Toggle : FieldKind
    /** One of [options]: buttons side by side when there are a few, a dropdown when there are more. */
    data class Choice(val options: List<ChoiceOption>) : FieldKind
    /** An object with fields of its own, e.g. the sign-in. */
    data class Group(val fields: List<FieldSpec>) : FieldKind
    /** Names with a text value each, e.g. a response map; [suggestions] are the names the app reads. */
    data class KeyValues(val suggestions: List<Suggestion> = emptyList(), val valueHint: String = "") : FieldKind
    /** HTTP status codes, written "200, 201". */
    data object StatusCodes : FieldKind
    /** The tabs the host shows, see ScreensEditor. */
    data object Screens : FieldKind
    /** A generic REST config's requests, see EndpointsEditor. */
    data object Endpoints : FieldKind
}

data class ChoiceOption(val value: String, val label: String, val help: String? = null)

/** A name a key-value field can take, with what it's for; [value] is filled in when it's added. */
data class Suggestion(val key: String, val help: String, val value: String = "")

/**
 * One field of a config.
 *
 * @param default what the app uses while the field is left out, shown with it; null when there is none
 * @param required the config can't be used without it
 * @param common shown first; the others are folded under "More options" (unfolded when one of them is set), so a page
 *   shows little at first but every field is a tap away
 * @param canTurnOff for an object ([FieldKind.Group]) whose being there at all means something, e.g. signing in with a
 *   password: its page starts with a switch, rather than it being there as soon as one of its fields is filled in
 * @param shownWhen only for the fields that mean something given the others in the same object, e.g. the header name of
 *   a sign-in sent in a header
 */
data class FieldSpec(
    val key: String,
    val label: String,
    val help: String,
    val kind: FieldKind = FieldKind.Text(),
    val default: JsonElement? = null,
    val required: Boolean = false,
    val common: Boolean = required,
    val placeholder: String? = null,
    val canTurnOff: Boolean = false,
    val shownWhen: (JsonObject) -> Boolean = { true }
)

/** A part of the editor's form: on its first page when [startOpen], otherwise a page of its own. */
data class Section(val title: String, val summary: String, val fields: List<FieldSpec>, val startOpen: Boolean = false)

/** The text of a field in [obj], e.g. a choice; null when it's left out or isn't text. */
fun JsonObject.textOf(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * A REST endpoint the app knows: what it unlocks ([area]), what it's for, the placeholders it fills in, and its [fields]
 * (see ConfigSchema.KnownEndpoints).
 */
data class EndpointInfo(
    val name: String,
    val area: String,
    val help: String,
    val placeholders: String? = null,
    val fields: List<FieldSpec> = emptyList()
)

/**
 * What the config editor knows about each kind of config (see the top of this file), with its texts in the app's
 * language, from [resources]. Made once per configuration: see configSchema().
 */
class ConfigSchema(private val resources: Resources) {
    private fun choice(vararg options: ChoiceOption) = FieldKind.Choice(options.toList())
    private fun str(value: String) = JsonPrimitive(value)

    // ---- Shared parts ----

    private val nameField = FieldSpec(
        "name", resources.getString(R.string.settings_name), resources.getString(R.string.settings_shown_in_the_host_switcher),
        required = true, placeholder = resources.getString(R.string.settings_my_nas)
    )

    private val screensField = FieldSpec(
        "screens", resources.getString(R.string.settings_screens),
        resources.getString(R.string.settings_which_tabs_show_in_order),
        kind = FieldKind.Screens, common = true
    )

    private val metaSection = Section(
        resources.getString(R.string.settings_sharing_and_updates),
        resources.getString(R.string.settings_author_and_update_source),
        listOf(
            FieldSpec(
                "meta", resources.getString(R.string.files_details), "", common = true,
                kind = FieldKind.Group(
                    listOf(
                        FieldSpec(
                            "id", resources.getString(R.string.settings_config_id), resources.getString(R.string.settings_same_id_and_update_url),
                            common = true, placeholder = "com.example.my-host"
                        ),
                        FieldSpec("version", resources.getString(R.string.upload_version), resources.getString(R.string.settings_raise_it_with_each_change), kind = FieldKind.Number, default = JsonPrimitive(0), common = true),
                        FieldSpec("author", resources.getString(R.string.settings_author), resources.getString(R.string.settings_shown_in_the_app_next), common = true),
                        FieldSpec(
                            "update_url", resources.getString(R.string.settings_update_address), resources.getString(R.string.settings_https_url_of_the_raw),
                            placeholder = "https://example.com/my-host.json"
                        ),
                        FieldSpec("min_app_version", resources.getString(R.string.settings_lowest_app_version), resources.getString(R.string.settings_older_apps_say_so_instead), kind = FieldKind.Number)
                    )
                )
            )
        )
    )

    private val methodChoice = choice(
        ChoiceOption("GET", "GET"), ChoiceOption("POST", "POST"), ChoiceOption("PUT", "PUT"),
        ChoiceOption("PATCH", "PATCH"), ChoiceOption("DELETE", "DELETE"), ChoiceOption("HEAD", "HEAD")
    )

    private val bodyChoice = choice(
        ChoiceOption("RAW", resources.getString(R.string.settings_raw), resources.getString(R.string.settings_the_file_itself_or_nothing)),
        ChoiceOption("MULTIPART", resources.getString(R.string.settings_multipart), resources.getString(R.string.settings_a_file_upload_form)),
        ChoiceOption("FORM", resources.getString(R.string.settings_form), resources.getString(R.string.settings_the_form_fields_below)),
        ChoiceOption("JSON", "JSON", resources.getString(R.string.settings_the_json_text_below))
    )

    private val responseFieldSuggestions = listOf(
        Suggestion("id", resources.getString(R.string.settings_the_id_of_a_file), "$.id"),
        Suggestion("name", resources.getString(R.string.settings_the_file_name_or_a), "$.name"),
        Suggestion("path", resources.getString(R.string.settings_the_path_of_a_file), "$.path"),
        Suggestion("is_directory", resources.getString(R.string.settings_whether_it_s_a_folder), "$.type"),
        Suggestion("size", resources.getString(R.string.settings_the_size_in_bytes), "$.size"),
        Suggestion("mime_type", resources.getString(R.string.settings_the_file_s_type_for), "$.mime_type"),
        Suggestion("created", resources.getString(R.string.settings_when_it_was_made), "$.created"),
        Suggestion("modified", resources.getString(R.string.settings_when_it_last_changed), "$.modified"),
        Suggestion("username", resources.getString(R.string.settings_the_account_s_name_user), "$.username"),
        Suggestion("quota_used", resources.getString(R.string.settings_storage_used_user_info), "$.storage_used"),
        Suggestion("quota_total", resources.getString(R.string.settings_storage_there_is_user_info), "$.storage_total"),
        Suggestion("title", resources.getString(R.string.settings_a_list_s_title), "$.title"),
        Suggestion("file_count", resources.getString(R.string.settings_how_many_files_a_list), "$.file_count"),
        Suggestion("can_edit", resources.getString(R.string.settings_whether_a_list_can_be), "$.can_edit")
    )

    /** The fields of one request: an endpoint, or the sign-out. */
    private fun requestFields(placeholders: String?): List<FieldSpec> = listOf(
        FieldSpec("method", resources.getString(R.string.settings_method), resources.getString(R.string.settings_the_http_method_of_the), kind = methodChoice, required = true, default = str("GET")),
        FieldSpec(
            "path", resources.getString(R.string.settings_path),
            resources.getString(R.string.settings_path_added_to_base) + (placeholders?.let { resources.getString(R.string.settings_can_use, it) } ?: ""),
            required = true, placeholder = "/files/{id}"
        ),
        FieldSpec("body", resources.getString(R.string.settings_body), resources.getString(R.string.settings_what_s_sent_with_the), kind = bodyChoice, default = str("RAW")),
        FieldSpec(
            "query", resources.getString(R.string.settings_query_parameters), resources.getString(R.string.settings_added_to_the_url_empty),
            kind = FieldKind.KeyValues(valueHint = resources.getString(R.string.settings_placeholder_or_a_fixed_value))
        ),
        FieldSpec(
            "form", resources.getString(R.string.settings_form_fields), resources.getString(R.string.settings_sent_as_the_body_when), kind = FieldKind.KeyValues(valueHint = resources.getString(R.string.settings_placeholder_or_a_fixed_value)),
            shownWhen = { it.textOf("body") == "FORM" || it.containsKey("form") }
        ),
        FieldSpec(
            "json", resources.getString(R.string.settings_json_body), resources.getString(R.string.settings_json_body_title_json_and),
            kind = FieldKind.Text(multiline = true), shownWhen = { it.textOf("body") == "JSON" || it.containsKey("json") },
            common = true
        ),
        FieldSpec(
            "success_status", resources.getString(R.string.settings_success_codes), resources.getString(R.string.settings_the_http_status_codes_that), kind = FieldKind.StatusCodes,
            default = JsonArray(listOf(JsonPrimitive(200), JsonPrimitive(201)))
        ),
        FieldSpec(
            "list_path", resources.getString(R.string.settings_list_of_items), resources.getString(R.string.settings_path_to_the_items_array),
            placeholder = "$.files"
        ),
        FieldSpec(
            "response_map", resources.getString(R.string.settings_answer_fields), resources.getString(R.string.settings_dot_path_to_each_value),
            kind = FieldKind.KeyValues(responseFieldSuggestions, valueHint = "$.field")
        ),
        FieldSpec("breadcrumb_path", resources.getString(R.string.settings_folder_path), resources.getString(R.string.settings_path_to_the_parent_folders), placeholder = "$.path"),
        FieldSpec(
            "listing_map", resources.getString(R.string.settings_listing_fields), resources.getString(R.string.settings_values_of_the_listing_as),
            kind = FieldKind.KeyValues(
                listOf(
                    Suggestion("can_write", resources.getString(R.string.settings_a_folder_can_be_changed), resources.getString(R.string.settings_permissions_write)),
                    Suggestion("can_delete", resources.getString(R.string.settings_a_folder_s_files_can), resources.getString(R.string.settings_permissions_delete)),
                    Suggestion("title", resources.getString(R.string.settings_a_list_s_title), "$.title"),
                    Suggestion("can_edit", resources.getString(R.string.settings_a_list_can_be_changed), "$.can_edit")
                ),
                valueHint = "$.field"
            )
        )
    )

    /** The fields of an endpoint the app doesn't know by name. */
    fun endpointFields(): List<FieldSpec> = requestFields(null)


    /** Every endpoint name the app uses, by what it unlocks, in the order they're offered. */
    val KnownEndpoints: List<EndpointInfo> = listOf(
        EndpointInfo("list", resources.getString(R.string.settings_files_tab), resources.getString(R.string.settings_the_flat_list_of_every)),
        EndpointInfo("file_info", resources.getString(R.string.settings_files_tab), resources.getString(R.string.settings_the_details_of_one_file), "{id}"),
        EndpointInfo("download", resources.getString(R.string.settings_files_tab), resources.getString(R.string.settings_downloading_a_file_by_its), "{id}"),
        EndpointInfo("download_archive", resources.getString(R.string.settings_files_tab), resources.getString(R.string.settings_several_files_at_once_as), "{ids}"),
        EndpointInfo("delete", resources.getString(R.string.settings_files_tab), resources.getString(R.string.settings_deleting_a_file_by_its), "{id}"),
        EndpointInfo("upload", resources.getString(R.string.settings_upload_tab), resources.getString(R.string.settings_uploading_a_file_with_no), "{filename}"),
        EndpointInfo("browse_list", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_listing_one_folder), "{path}"),
        EndpointInfo("browse_download", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_downloading_a_file_by_its_2), "{path}"),
        EndpointInfo("browse_download_archive", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_a_folder_and_everything_in), "{path}"),
        EndpointInfo("browse_upload", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_uploading_into_a_folder), "{path} and {make_parents}"),
        EndpointInfo("browse_thumbnail", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_the_thumbnail_of_a_file), "{path}"),
        EndpointInfo("browse_mkdir", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_making_a_folder_action_is), "{path} and {action}"),
        EndpointInfo("browse_rename", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_renaming_or_moving_a_file), "{path}, {target} and {make_parents}"),
        EndpointInfo("browse_delete", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_deleting_a_file_or_folder), "{path} and {recursive}"),
        EndpointInfo("browse_import", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_copying_files_into_a_folder), "{path} and {files_json}"),
        EndpointInfo(
            "archive_info", resources.getString(R.string.settings_filesystem_tab),
            resources.getString(R.string.settings_archive_contents_without_downloading_path),
            "{path} or {id}"
        ),
        EndpointInfo("archive_file", resources.getString(R.string.settings_filesystem_tab), resources.getString(R.string.settings_one_file_out_of_an), "{path} or {id}, and {entry}"),
        EndpointInfo("user_lists", resources.getString(R.string.settings_lists_tab), resources.getString(R.string.settings_the_lists_of_the_account)),
        EndpointInfo("list_info", resources.getString(R.string.settings_lists_tab), resources.getString(R.string.settings_the_files_of_one_list), "{list_id}"),
        EndpointInfo("list_create", resources.getString(R.string.settings_lists_tab), resources.getString(R.string.settings_making_a_list), "{title_json} and {files_json}"),
        EndpointInfo("list_update", resources.getString(R.string.settings_lists_tab), resources.getString(R.string.settings_changing_a_list_s_title), "{list_id}, {title_json} and {files_json}"),
        EndpointInfo("list_delete", resources.getString(R.string.settings_lists_tab), resources.getString(R.string.settings_deleting_a_list), "{list_id}"),
        EndpointInfo("user_info", resources.getString(R.string.settings_account), resources.getString(R.string.settings_the_account_s_name_and)),
        EndpointInfo("thumbnail_id", resources.getString(R.string.settings_previews_and_links), resources.getString(R.string.settings_the_thumbnail_of_a_file_2), "{id}"),
        EndpointInfo("raw_id", resources.getString(R.string.settings_previews_and_links), resources.getString(R.string.settings_a_file_s_own_address), "{id}"),
        EndpointInfo("share_id", resources.getString(R.string.settings_previews_and_links), resources.getString(R.string.settings_the_public_page_of_a), "{id}"),
        EndpointInfo("share_path", resources.getString(R.string.settings_previews_and_links), resources.getString(R.string.settings_the_public_page_of_a_2), "{path}"),
        EndpointInfo("share_list", resources.getString(R.string.settings_previews_and_links), resources.getString(R.string.settings_the_public_page_of_a_3), "{list_id}")
    ).map { it.copy(fields = requestFields(it.placeholders)) }

    /** The sign-in fields, for WebDAV ([webdav]: sent as HTTP Basic unless set otherwise) and generic REST configs. */
    private fun authField(webdav: Boolean) = FieldSpec(
        "auth", resources.getString(R.string.settings_sign_in_2), "", common = true,
        kind = FieldKind.Group(
            listOf(
                FieldSpec(
                    "type", resources.getString(R.string.settings_api_key_sent_as),
                    resources.getString(R.string.settings_how_the_api_key_or),
                    kind = choice(
                        ChoiceOption("NONE", resources.getString(R.string.settings_nothing), resources.getString(R.string.settings_no_api_key)),
                        ChoiceOption("BASIC", resources.getString(R.string.settings_basic), resources.getString(R.string.settings_http_basic_the_key_is)),
                        ChoiceOption("BEARER", resources.getString(R.string.settings_bearer), resources.getString(R.string.settings_authorization_bearer_key)),
                        ChoiceOption("HEADER", resources.getString(R.string.settings_header), resources.getString(R.string.settings_the_key_in_a_header))
                    ),
                    default = str(if (webdav) "BASIC" else "NONE"), common = true
                ),
                FieldSpec(
                    "header_name", resources.getString(R.string.settings_header_name), resources.getString(R.string.settings_the_header_the_key_goes), default = str("Authorization"), common = true,
                    shownWhen = { it.textOf("type") == "HEADER" }
                ),
                FieldSpec(
                    "key_username", resources.getString(R.string.settings_username_sent_with_the_key), resources.getString(R.string.settings_for_basic_often_empty),
                    default = str(""), shownWhen = { (it.textOf("type") ?: if (webdav) "BASIC" else "NONE") == "BASIC" }
                ),
                FieldSpec(
                    "password_auth", resources.getString(R.string.settings_username_and_password), resources.getString(R.string.settings_for_hosts_with_a_username),
                    common = true, canTurnOff = true,
                    kind = FieldKind.Group(
                        listOf(
                            FieldSpec(
                                "mode", resources.getString(R.string.settings_used_as),
                                "",
                                kind = choice(
                                    ChoiceOption("BASIC", resources.getString(R.string.settings_every_request), resources.getString(R.string.settings_http_basic_the_password_is)),
                                    ChoiceOption("LOGIN", resources.getString(R.string.settings_sign_in_request), resources.getString(R.string.settings_exchanged_for_a_session_the))
                                ),
                                required = true, default = str("BASIC")
                            ),
                            FieldSpec(
                                "login", resources.getString(R.string.settings_sign_in_request), resources.getString(R.string.settings_the_request_that_exchanges_the),
                                required = true, shownWhen = { it.textOf("mode") == "LOGIN" },
                                kind = FieldKind.Group(
                                    listOf(
                                        FieldSpec("method", resources.getString(R.string.settings_method), resources.getString(R.string.settings_the_http_method_of_the_2), kind = methodChoice, default = str("POST"), common = true),
                                        FieldSpec("path", resources.getString(R.string.settings_path), resources.getString(R.string.settings_added_to_the_base_address), required = true, placeholder = "/user/login"),
                                        FieldSpec(
                                            "body", resources.getString(R.string.settings_sent_as), resources.getString(R.string.settings_how_the_fields_below_are),
                                            kind = choice(ChoiceOption("FORM", resources.getString(R.string.settings_form)), ChoiceOption("JSON", "JSON")), default = str("FORM"), common = true
                                        ),
                                        FieldSpec(
                                            "fields", resources.getString(R.string.settings_fields),
                                            resources.getString(R.string.settings_username_password_and_otp_are),
                                            kind = FieldKind.KeyValues(
                                                listOf(
                                                    Suggestion("username", resources.getString(R.string.settings_the_username), "{username}"),
                                                    Suggestion("password", resources.getString(R.string.settings_the_password), "{password}"),
                                                    Suggestion("otp", resources.getString(R.string.settings_a_two_factor_code), "{otp}")
                                                ),
                                                valueHint = resources.getString(R.string.settings_username_password_otp_or_a)
                                            ),
                                            common = true
                                        ),
                                        FieldSpec("token", resources.getString(R.string.settings_session_in_the_answer), resources.getString(R.string.settings_where_the_session_token_is), required = true, placeholder = "$.auth_key"),
                                        FieldSpec(
                                            "otp", resources.getString(R.string.settings_two_factor_codes), resources.getString(R.string.settings_if_the_host_asks_for),
                                            common = true, canTurnOff = true,
                                            kind = FieldKind.Group(
                                                listOf(
                                                    FieldSpec("required_when", resources.getString(R.string.settings_a_code_is_asked_for), "", required = true, kind = conditionGroup()),
                                                    FieldSpec("rejected_when", resources.getString(R.string.settings_the_code_was_wrong_when), "", kind = conditionGroup(), common = true, canTurnOff = true),
                                                    FieldSpec("digits", resources.getString(R.string.settings_digits), resources.getString(R.string.settings_how_long_a_code_is), kind = FieldKind.Number, default = JsonPrimitive(6))
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                            FieldSpec(
                                "logout", resources.getString(R.string.settings_sign_out_request), resources.getString(R.string.settings_ends_the_session_on_the),
                                kind = FieldKind.Group(requestFields(null)), common = true, canTurnOff = true,
                                shownWhen = { it.textOf("mode") == "LOGIN" }
                            )
                        )
                    )
                )
            )
        )
    )

    private fun conditionGroup() = FieldKind.Group(
        listOf(
            FieldSpec("path", resources.getString(R.string.settings_field_in_the_answer), "", required = true, placeholder = "$.value"),
            FieldSpec("equals", resources.getString(R.string.settings_has_the_value), "", required = true, placeholder = "otp_required")
        )
    )

    // ---- The kinds ----

    private val genericRestSections = listOf(
        Section(
            resources.getString(R.string.settings_basics), resources.getString(R.string.settings_the_name_and_where_the),
            listOf(
                nameField,
                FieldSpec("base_url", resources.getString(R.string.settings_base_address), resources.getString(R.string.settings_the_api_s_base_url), required = true, placeholder = "https://files.example.com/api"),
                FieldSpec("browse_root", resources.getString(R.string.settings_start_folder), resources.getString(R.string.settings_the_folder_the_filesystem_tab), default = str("")),
                FieldSpec(
                    "send_credentials", resources.getString(R.string.settings_send_the_sign_in), resources.getString(R.string.settings_off_for_a_public_host),
                    kind = FieldKind.Toggle, default = JsonPrimitive(true)
                ),
                FieldSpec(
                    "account_fallback", resources.getString(R.string.settings_use_the_pixeldrain_account), resources.getString(R.string.settings_uses_the_account_sign_in),
                    kind = FieldKind.Toggle, default = JsonPrimitive(false)
                )
            ),
            startOpen = true
        ),
        Section(resources.getString(R.string.settings_sign_in_2), resources.getString(R.string.settings_how_sign_in_is_sent), listOf(authField(webdav = false))),
        Section(
            resources.getString(R.string.settings_endpoints), resources.getString(R.string.settings_each_request_unlocks_a_feature),
            listOf(FieldSpec("endpoints", resources.getString(R.string.settings_endpoints), "", kind = FieldKind.Endpoints, required = true))
        ),
        Section(resources.getString(R.string.settings_screens), resources.getString(R.string.settings_the_tabs_this_host_shows), listOf(screensField)),
        metaSection
    )

    private val webDavSections = listOf(
        Section(
            resources.getString(R.string.settings_basics), resources.getString(R.string.settings_the_name_and_where_the_2),
            listOf(
                nameField,
                FieldSpec(
                    "base_url", resources.getString(R.string.settings_webdav_address), resources.getString(R.string.settings_username_is_replaced_with_your),
                    required = true, placeholder = "https://cloud.example.com/remote.php/dav/files/{username}"
                ),
                FieldSpec("root_path", resources.getString(R.string.settings_start_folder), resources.getString(R.string.settings_the_folder_the_app_starts), default = str("/"), common = true)
            ),
            startOpen = true
        ),
        Section(resources.getString(R.string.settings_sign_in_2), resources.getString(R.string.settings_username_and_password_go_on), listOf(authField(webdav = true))),
        Section(resources.getString(R.string.settings_screens), resources.getString(R.string.settings_the_tabs_this_host_shows), listOf(screensField)),
        metaSection
    )

    private val s3Sections = listOf(
        Section(
            resources.getString(R.string.settings_basics), resources.getString(R.string.settings_the_name_and_which_bucket),
            listOf(
                nameField,
                FieldSpec("endpoint", resources.getString(R.string.settings_server_address), resources.getString(R.string.settings_the_s3_server_with_its), required = true, placeholder = "https://nas.example.com:9000"),
                FieldSpec("bucket", resources.getString(R.string.settings_bucket), resources.getString(R.string.settings_the_bucket_to_browse), required = true, placeholder = "my-bucket"),
                FieldSpec("region", resources.getString(R.string.settings_region), resources.getString(R.string.settings_most_self_hosted_servers_accept), default = str("us-east-1"), common = true),
                FieldSpec(
                    "path_style", resources.getString(R.string.settings_bucket_in_the_path), resources.getString(R.string.settings_server_bucket_file_minio_most),
                    kind = FieldKind.Toggle, default = JsonPrimitive(true)
                ),
                FieldSpec("prefix", resources.getString(R.string.settings_only_under), resources.getString(R.string.settings_only_show_files_under_this), default = str(""), placeholder = "photos/")
            ),
            startOpen = true
        ),
        Section(resources.getString(R.string.settings_screens), resources.getString(R.string.settings_the_tabs_this_host_shows), listOf(screensField)),
        metaSection
    )

    private val smbSections = listOf(
        Section(
            resources.getString(R.string.settings_basics), resources.getString(R.string.settings_the_name_and_which_share),
            listOf(
                nameField,
                FieldSpec("host", resources.getString(R.string.settings_server), resources.getString(R.string.settings_the_server_s_name_or), required = true, placeholder = resources.getString(R.string.settings_nas_local_or_192_168)),
                FieldSpec("share", resources.getString(R.string.settings_share), resources.getString(R.string.settings_the_share_to_browse_by), required = true, placeholder = resources.getString(R.string.settings_documents)),
                FieldSpec(
                    "auth", resources.getString(R.string.settings_sign_in), resources.getString(R.string.settings_guest_and_anonymous_shares_need),
                    kind = choice(
                        ChoiceOption("CREDENTIALS", resources.getString(R.string.settings_account), resources.getString(R.string.settings_a_username_and_password_entered)),
                        ChoiceOption("GUEST", resources.getString(R.string.settings_guest)),
                        ChoiceOption("ANONYMOUS", resources.getString(R.string.settings_anonymous))
                    ),
                    default = str("CREDENTIALS"), common = true
                )
            ),
            startOpen = true
        ),
        Section(
            resources.getString(R.string.settings_connection), resources.getString(R.string.settings_port_domain_and_encryption),
            listOf(
                FieldSpec("root_path", resources.getString(R.string.settings_start_folder), resources.getString(R.string.settings_the_folder_inside_the_share), default = str(""), placeholder = "Photos/2024"),
                FieldSpec("port", resources.getString(R.string.settings_port), resources.getString(R.string.settings_the_smb_port), kind = FieldKind.Number, default = JsonPrimitive(445)),
                FieldSpec("domain", resources.getString(R.string.settings_domain), resources.getString(R.string.settings_empty_for_a_local_account), default = str("")),
                FieldSpec(
                    "min_version", resources.getString(R.string.settings_oldest_version), resources.getString(R.string.settings_oldest_smb_version_allowed),
                    kind = choice(ChoiceOption("SMB2", "SMB 2"), ChoiceOption("SMB3", "SMB 3")), default = str("SMB2")
                ),
                FieldSpec(
                    "encrypt", resources.getString(R.string.settings_encrypt), resources.getString(R.string.settings_needs_smb_3_fails_instead),
                    kind = FieldKind.Toggle, default = JsonPrimitive(false)
                )
            )
        ),
        Section(resources.getString(R.string.settings_screens), resources.getString(R.string.settings_the_tabs_this_host_shows), listOf(screensField)),
        metaSection
    )

    /** The form of a config of [kind] (its "kind" field), null for a kind the app doesn't know. */
    fun sectionsFor(kind: String?): List<Section>? = when (kind) {
        "generic_rest" -> genericRestSections
        "webdav" -> webDavSections
        "s3" -> s3Sections
        "smb" -> smbSections
        else -> null
    }

    /** What a config of [kind] is, for the editor's title. */
    fun kindLabel(kind: String?): String = when (kind) {
        "generic_rest" -> resources.getString(R.string.settings_rest_api)
        "webdav" -> "WebDAV"
        "s3" -> "S3"
        "smb" -> resources.getString(R.string.settings_smb_share)
        else -> kind ?: resources.getString(R.string.settings_unknown_kind)
    }
}

/** The keys a list of sections describes at their top level, the rest of a config is "Other fields". */
fun describedKeys(sections: List<Section>): Set<String> = sections.flatMap { s -> s.fields.map { it.key } }.toSet() + "kind"

/** How a value reads back in a field's "Default:" line. */
fun JsonElement.displayText(): String = when (this) {
    is JsonPrimitive -> if (isString) content.ifEmpty { "empty" } else content
    is JsonArray -> joinToString(", ") { runCatching { it.jsonPrimitive.content }.getOrDefault(it.toString()) }
    else -> toString()
}

/** The config editor's schema in the app's language: made once, and again when the configuration changes. */
@Composable
fun configSchema(): ConfigSchema {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { ConfigSchema(resources) }
}
