package tools.senko.materialdrain.provider

import tools.senko.materialdrain.provider.api.ProviderError

/** The message of a failed request as the screens show it: a refused login says what to do about it. */
fun ProviderError.forDisplay(): String =
    if (httpStatus == 401 || code == "authentication_required") {
        "${message.ifBlank { "Not signed in" }}. Sign in, or add an API key for this host under Settings → Hosts."
    } else {
        message
    }
