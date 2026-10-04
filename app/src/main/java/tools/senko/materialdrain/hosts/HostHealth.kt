package tools.senko.materialdrain.hosts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ProviderValidationResult

/** Whether a host answers, as far as the last check knows. [UNKNOWN] until a host has been checked. */
enum class Reachability { UNKNOWN, REACHABLE, UNREACHABLE }

/**
 * Keeps the reachability of each host, for the dot in the host switcher. A check asks the host's own
 * `validate()` and reads its `base_url` line, which is the address check every provider has. A sign-in problem
 * is not an unreachable host, so it doesn't turn the dot red.
 */
class HostHealth(
    private val registry: ProviderRegistry,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, Reachability>>(emptyMap())
    val states: StateFlow<Map<String, Reachability>> = _states.asStateFlow()

    /** Checks the given hosts in the background. A host keeps its last state until its check is done. */
    fun check(hostIds: Collection<String>) {
        hostIds.forEach { id ->
            scope.launch {
                val result = try {
                    registry.resolve(id).validate()
                } catch (_: Exception) {
                    null
                }
                _states.update { it + (id to reachabilityOf(result)) }
            }
        }
    }

    private fun reachabilityOf(result: ProviderValidationResult?): Reachability {
        if (result == null) return Reachability.UNREACHABLE
        val address = result.fields.firstOrNull { it.fieldId == "base_url" }
            ?: return if (result.allOk) Reachability.REACHABLE else Reachability.UNREACHABLE
        return when {
            address.ok -> Reachability.REACHABLE
            address.inconclusive -> Reachability.UNKNOWN
            else -> Reachability.UNREACHABLE
        }
    }
}
