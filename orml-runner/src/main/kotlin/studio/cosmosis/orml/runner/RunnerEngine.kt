package studio.cosmosis.orml.runner

import java.nio.file.Files
import javax.imageio.ImageIO

class RunnerEngine(
    backends:Collection<OrmlRunnerBackend>,
    private val discoveryErrors:List<String> = emptyList()
) {
    private val known=CapabilityRegistry.all.map{it.id}.toSet()
    private val providers:Map<String,OrmlRunnerBackend>
    val errors:List<String>

    init {
        val validation=mutableListOf<String>()
        val candidates=mutableMapOf<String,MutableList<OrmlRunnerBackend>>()
        backends.forEach{backend->
            backend.capabilities.forEach{cap->
                if(cap !in known)validation+="backend "+backend.backendId+" declares unknown capability "+cap
                else candidates.getOrPut(cap){mutableListOf()}+=backend
            }
        }
        val accepted=mutableMapOf<String,OrmlRunnerBackend>()
        candidates.forEach{(cap,list)->
            if(list.size==1)accepted[cap]=list.single()
            else validation+="ambiguous backends for "+cap+": "+list.map{it.backendId}.distinct().sorted().joinToString()
        }
        providers=accepted
        errors=(discoveryErrors+validation).distinct()
    }

    fun availableCapabilities():Set<String> = providers.keys

    fun execute(request:RunnerRequest):RunnerResult {
        validateInput(request)?.let{return RunnerResult(false,it)}
        val backend=providers[request.capabilityId]
            ?:return RunnerResult(false,"no backend installed for "+request.capabilityId)
        request.output.parent?.let(Files::createDirectories)
        runCatching{Files.deleteIfExists(request.output)}
            .getOrElse{return RunnerResult(false,"could not prepare output path: "+sanitize(it.message?:it.javaClass.simpleName))}

        val result=try{backend.run(request)}
        catch(t:Throwable){return RunnerResult(false,"backend "+backend.backendId+" failed: "+sanitize(t.message?:t.javaClass.simpleName))}

        if(!result.ok)return result.copy(message=sanitize(result.message))
        val spec=CapabilityRegistry.find(request.capabilityId)!!
        if(!Files.isRegularFile(request.output))return RunnerResult(false,"backend reported success without output file")
        if(Files.size(request.output)<=0)return RunnerResult(false,"backend reported success with empty output file")
        when(spec.outputKind){
            OutputKind.JSON -> {
                val head=runCatching{Files.readString(request.output)}.getOrNull()?.trim().orEmpty()
                if(!(head.startsWith("{")||head.startsWith("[")))return RunnerResult(false,"embedding output is not JSON")
            }
            OutputKind.IMAGE,OutputKind.IMAGE_MASK -> {
                val outputImage=runCatching{ImageIO.read(request.output.toFile())}.getOrNull()
                    ?:return RunnerResult(false,"backend output is not a decodable image")
                if(outputImage.width<=0||outputImage.height<=0)return RunnerResult(false,"backend output image has invalid dimensions")
                if(spec.outputKind==OutputKind.IMAGE_MASK){
                    val inputImage=runCatching{ImageIO.read(request.input.toFile())}.getOrNull()
                        ?:return RunnerResult(false,"mask capability input is not a decodable image")
                    if(outputImage.width!=inputImage.width||outputImage.height!=inputImage.height)
                        return RunnerResult(false,"mask output dimensions must match input image")
                }
            }
        }
        return result.copy(
            message=sanitize(result.message),
            metadata=result.metadata+mapOf("backend" to backend.backendId,"protocolVersion" to RUNNER_PROTOCOL_VERSION)
        )
    }

    fun describeJson():String {
        val available=availableCapabilities()
        val caps=CapabilityRegistry.all.joinToString(","){spec->
            "{"id":""+escape(spec.id)+"","available":"+(spec.id in available)+","output":""+spec.outputKind.name.lowercase()+""}"
        }
        val errs=errors.joinToString(","){"""+escape(sanitize(it))+"""}
        return "{"protocolVersion":""+RUNNER_PROTOCOL_VERSION+"","capabilities":["+caps+"],"errors":["+errs+"]}"
    }

    private fun escape(s:String)=s.replace("\\","\\\\").replace(""","\\"")
}
