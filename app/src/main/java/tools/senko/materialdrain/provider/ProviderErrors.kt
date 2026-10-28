package tools.senko.materialdrain.provider

import tools.senko.materialdrain.provider.api.ErrorCodes
import tools.senko.materialdrain.provider.api.ProviderError
import android.content.res.Resources
import tools.senko.materialdrain.R

/** The message of a failed request as the screens show it: a refused login says what to do about it. */
fun ProviderError.forDisplay(resources: Resources): String =
    if (httpStatus == 401 || code == ErrorCodes.AUTHENTICATION_REQUIRED) {
        resources.getString(R.string.app_sign_in_or_add_an_api, message.ifBlank { resources.getString(R.string.app_not_signed_in) })
    } else {
        message
    }
