package studio.cosmosis.orml

import java.nio.file.Files
import java.nio.file.Path

/**
 * Fail-closed bridge boundary for optional ORML modules.
 * The application never claims a local-ML action succeeded unless an actual
 * adapter is installed and returns a concrete, valid result.
 */
data class OrmlInvocation(
    val capabilityId: String,
    val inputPath: String,
    val options: Map<String, String> = emptyMap()
)

data class OrmlResult(
    val capabilityId: String,
    val ok: Boolean,
    val outputPaths: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val message: String
)

data class OrmlRuntimeDiagnostics(
    val statuses: Map<String, String>,
    val adapters: Map<String, String>,
    val errors: List<String>
)

interface OrmlAdapter {
    val capabilityId: String
    fun invoke(request: OrmlInvocation): OrmlResult
}

class OrmlExecutor(
    adapters: Collection<OrmlAdapter> = emptyList(),
    discoveryErrors: List<String> = emptyList()
) {
    private val grouped = adapters.groupBy { it.capabilityId }
    private val duplicateErrors = grouped
        .filterValues { it.size > 1 }
        .map { (id, providers) ->
            "Rejected ambiguous adapters for '$id': ${providers.joinToString { it.javaClass.name }}"
        }
    private val installed = grouped
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    private val errors = (discoveryErrors + duplicateErrors).distinct()

    fun status(capabilityId: String): String {
        val descriptor = OrmlRegistry.capabilities.firstOrNull { it.id == capabilityId } ?: return "UNKNOWN"
        return when {
            installed.containsKey(capabilityId) -> "READY"
            descriptor.runtimeAvailable() -> "RUNTIME_DETECTED / ADAPTER_REQUIRED"
            else -> "UNAVAILABLE"
        }
    }

    fun diagnostics(): OrmlRuntimeDiagnostics = OrmlRuntimeDiagnostics(
        statuses = OrmlRegistry.capabilities.associate { it.id to status(it.id) },
        adapters = installed.mapValues { it.value.javaClass.name },
        errors = errors
    )

    fun invoke(request: OrmlInvocation): OrmlResult {
        val descriptor = OrmlRegistry.capabilities.firstOrNull { it.id == request.capabilityId }
            ?: return failed(request, "Unknown ORML capability")

        val input = runCatching { Path.of(request.inputPath) }.getOrNull()
            ?: return failed(request, "Invalid ORML input path")
        if (!Files.isRegularFile(input)) {
            return failed(request, "ORML input does not exist or is not a regular file")
        }

        val adapter = installed[request.capabilityId]
            ?: return failed(request, "${descriptor.module} is optional and no verified runtime adapter is installed")

        val result = try {
            adapter.invoke(request)
        } catch (e: RuntimeException) {
            return failed(request, "ORML adapter failed: ${safeMessage(e)}")
        } catch (e: LinkageError) {
            return failed(request, "ORML adapter linkage failed: ${safeMessage(e)}")
        }

        if (result.capabilityId != request.capabilityId) {
            return failed(request, "ORML adapter returned capability '${result.capabilityId}' for '${request.capabilityId}'")
        }
        if (!result.ok) return result

        val requiresFileOutput = descriptor.outputs.any {
            it == "image" || it.contains("mask", ignoreCase = true)
        }
        if (requiresFileOutput && result.outputPaths.isEmpty()) {
            return failed(request, "ORML adapter reported success without a required output file")
        }
        val missing = result.outputPaths.filterNot { output ->
            runCatching { Files.isRegularFile(Path.of(output)) }.getOrDefault(false)
        }
        if (missing.isNotEmpty()) {
            return failed(request, "ORML adapter reported missing output file(s): ${missing.joinToString()}")
        }
        return result
    }

    companion object {
        fun discovered(classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            ?: OrmlAdapter::class.java.classLoader): OrmlExecutor {
            val service = OrmlAdapterDiscovery.discover(classLoader)
            val process = ProcessOrmlAdapters.discover()
            return OrmlExecutor(service.adapters+process.adapters,service.errors+process.errors)
        }
    }

    private fun failed(request: OrmlInvocation, message: String) =
        OrmlResult(request.capabilityId, false, message = message)

    private fun safeMessage(t: Throwable): String =
        (t.message ?: t.javaClass.simpleName).replace(Regex("[\\r\\n]+"), " ").take(300)
}
