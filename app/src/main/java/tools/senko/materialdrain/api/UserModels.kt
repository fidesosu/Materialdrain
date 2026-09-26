package tools.senko.materialdrain.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserFilesListResponse(
    val files: List<FileInfoResponse>
)

@Serializable
data class UserList(
    val id: String,
    val title: String,
    @SerialName("date_created") val dateCreated: String,
    @SerialName("file_count") val fileCount: Int,
    val files: List<FileInfoResponse>? = null,
    @SerialName("can_edit") val canEdit: Boolean
)

@Serializable
data class UserListsResponse(
    val lists: List<UserList>
)


// Response of POST /user/login when a session was created (HTTP 201)
@Serializable
data class LoginSession(
    @SerialName("auth_key") val authKey: String,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("creation_time") val creationTime: String? = null
)

sealed class LoginResult {
    data class LoggedIn(val session: LoginSession) : LoginResult()
    // HTTP 202 login_link_sent: a login link was e-mailed to the account
    data object LoginLinkSent : LoginResult()
}
