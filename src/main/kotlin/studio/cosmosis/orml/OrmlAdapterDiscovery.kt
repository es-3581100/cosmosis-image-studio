package studio.cosmosis.orml

import java.util.ServiceConfigurationError
import java.util.ServiceLoader

data class OrmlDiscoveryResult(
    val adapters: List<OrmlAdapter>,
    val errors: List<String> = emptyList()
)

/**
 * Discovers optional ORML adapters from the runtime classpath using Java's
 * ServiceLoader SPI. The core application does not dynamically download or
 * execute model code.
 *
 * Adapter JARs opt in by providing:
 * META-INF/services/studio.cosmosis.orml.OrmlAdapter
 *
 * Unknown and duplicate capability providers are rejected so a plugin cannot
 * silently shadow another implementation.
 */
object OrmlAdapterDiscovery {
    fun discover(
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            ?: OrmlAdapter::class.java.classLoader
    ): OrmlDiscoveryResult {
        val loaded = mutableListOf<OrmlAdapter>()
        val errors = mutableListOf<String>()
        val iterator = try {
            ServiceLoader.load(OrmlAdapter::class.java, classLoader).iterator()
        } catch (e: ServiceConfigurationError) {
            return OrmlDiscoveryResult(emptyList(), listOf("ServiceLoader setup failed: ${safeMessage(e)}"))
        } catch (e: LinkageError) {
            return OrmlDiscoveryResult(emptyList(), listOf("Adapter linkage failed: ${safeMessage(e)}"))
        }

        while (true) {
            val hasNext = try {
                iterator.hasNext()
            } catch (e: ServiceConfigurationError) {
                errors += "Adapter discovery failed: ${safeMessage(e)}"
                break
            } catch (e: LinkageError) {
                errors += "Adapter linkage failed: ${safeMessage(e)}"
                break
            }
            if (!hasNext) break

            try {
                loaded += iterator.next()
            } catch (e: ServiceConfigurationError) {
                errors += "Adapter initialization failed: ${safeMessage(e)}"
            } catch (e: LinkageError) {
                errors += "Adapter linkage failed: ${safeMessage(e)}"
            } catch (e: RuntimeException) {
                errors += "Adapter initialization failed: ${safeMessage(e)}"
            }
        }

        val known = OrmlRegistry.capabilities.map { it.id }.toSet()
        val accepted = mutableListOf<OrmlAdapter>()
        loaded.groupBy { it.capabilityId }.toSortedMap().forEach { (capabilityId, providers) ->
            when {
                capabilityId !in known ->
                    errors += "Rejected adapter for unknown capability '$capabilityId'"
                providers.size != 1 ->
                    errors += "Rejected ambiguous adapters for '$capabilityId': " +
                        providers.joinToString { it.javaClass.name }
                else -> accepted += providers.single()
            }
        }
        return OrmlDiscoveryResult(accepted, errors)
    }

    private fun safeMessage(t: Throwable): String =
        (t.message ?: t.javaClass.simpleName).replace(Regex("[\\r\\n]+"), " ").take(300)
}
