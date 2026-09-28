package studio.cosmosis.orml.runner

import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

const val RUNNER_PROTOCOL_VERSION = "1"

enum class OutputKind { IMAGE_MASK, IMAGE, JSON }

data class CapabilitySpec(
    val id:String,
    val outputKind:OutputKind,
    val defaultExtension:String
)

object CapabilityRegistry {
    val all:List<CapabilitySpec> = listOf(
        CapabilitySpec("smart-subject-mask",OutputKind.IMAGE_MASK,"png"),
        CapabilitySpec("person-body-mask",OutputKind.IMAGE_MASK,"png"),
        CapabilitySpec("image-embedding",OutputKind.JSON,"json"),
        CapabilitySpec("super-resolution",OutputKind.IMAGE,"png")
    )
    fun find(id:String)=all.firstOrNull{it.id==id}
}

data class RunnerRequest(
    val capabilityId:String,
    val input:Path,
    val output:Path,
    val options:Map<String,String> = emptyMap()
)

data class RunnerResult(
    val ok:Boolean,
    val message:String,
    val metadata:Map<String,String> = emptyMap()
)

interface OrmlRunnerBackend {
    val backendId:String
    val capabilities:Set<String>
    fun run(request:RunnerRequest):RunnerResult
}

data class BackendDiscovery(
    val backends:List<OrmlRunnerBackend>,
    val errors:List<String>
)

object BackendLoader {
    fun discover(
        classLoader:ClassLoader=Thread.currentThread().contextClassLoader
            ?:OrmlRunnerBackend::class.java.classLoader
    ):BackendDiscovery {
        val loaded=mutableListOf<OrmlRunnerBackend>()
        val errors=mutableListOf<String>()
        val iterator=try {
            ServiceLoader.load(OrmlRunnerBackend::class.java,classLoader).iterator()
        } catch(e:ServiceConfigurationError) {
            return BackendDiscovery(emptyList(),listOf("backend ServiceLoader setup failed: "+safe(e)))
        } catch(e:LinkageError) {
            return BackendDiscovery(emptyList(),listOf("backend linkage failed: "+safe(e)))
        }
        while(true){
            val hasNext=try{iterator.hasNext()}catch(e:ServiceConfigurationError){
                errors+="backend discovery failed: "+safe(e);break
            }catch(e:LinkageError){
                errors+="backend linkage failed: "+safe(e);break
            }
            if(!hasNext)break
            try{loaded+=iterator.next()}
            catch(e:ServiceConfigurationError){errors+="backend initialization failed: "+safe(e)}
            catch(e:LinkageError){errors+="backend linkage failed: "+safe(e)}
            catch(e:RuntimeException){errors+="backend initialization failed: "+safe(e)}
        }

        val known=CapabilityRegistry.all.map{it.id}.toSet()
        loaded.forEach { backend ->
            val unknown=backend.capabilities-known
            if(unknown.isNotEmpty())errors+="backend "+backend.backendId+" declares unknown capabilities: "+unknown.sorted().joinToString()
        }
        val duplicates=loaded
            .flatMap{backend->backend.capabilities.filter{it in known}.map{it to backend.backendId}}
            .groupBy({it.first},{it.second})
            .filterValues{it.distinct().size>1}
        duplicates.forEach{(cap,providers)->errors+="ambiguous backends for "+cap+": "+providers.distinct().sorted().joinToString()}

        return BackendDiscovery(loaded,errors.distinct())
    }

    private fun safe(t:Throwable)=sanitize(t.message?:t.javaClass.simpleName).take(300)
}

fun sanitize(text:String):String = text
    .replace(Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s]+"),"$1[REDACTED]")
    .replace(Regex("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s]+"),"$1[REDACTED]")
    .replace(Regex("sk-[A-Za-z0-9_-]{12,}"),"[REDACTED]")
    .replace(Regex("[\\r\\n]+")," ")
    .take(1000)

fun validateInput(request:RunnerRequest):String? {
    if(CapabilityRegistry.find(request.capabilityId)==null)return "unknown capability '"+request.capabilityId+"'"
    if(!Files.isRegularFile(request.input))return "input is not a regular file: "+request.input
    if(request.input.toAbsolutePath()==request.output.toAbsolutePath())return "output must not overwrite input"
    request.output.parent?.let{
        if(Files.exists(it)&&!Files.isDirectory(it))return "output parent is not a directory: "+it
    }
    return null
}
