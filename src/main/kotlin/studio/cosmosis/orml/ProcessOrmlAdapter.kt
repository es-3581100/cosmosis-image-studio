package studio.cosmosis.orml

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Executes an explicitly configured local ORML runner without a shell.
 *
 * Runner contract:
 *   runner --capability <id> --input <absolute-file> --output <absolute-file>
 *          [--option key=value]...
 *
 * The runner owns its OPENRNDR/TensorFlow/GL lifecycle. Cosmosis owns admission,
 * timeout, output existence checks, and lineage.
 */
class ProcessOrmlAdapter(
    override val capabilityId:String,
    private val executable:Path,
    private val defaultTimeoutSeconds:Long=180
):OrmlAdapter {
    fun available():Boolean = Files.isRegularFile(executable) && Files.isExecutable(executable)

    override fun invoke(request:OrmlInvocation):OrmlResult {
        if(!available())return OrmlResult(capabilityId,false,message="Configured ORML runner is not executable")
        val input=runCatching{Path.of(request.inputPath).toAbsolutePath()}.getOrNull()
            ?:return OrmlResult(capabilityId,false,message="Invalid ORML input path")
        if(!Files.isRegularFile(input))return OrmlResult(capabilityId,false,message="ORML input file does not exist")

        val descriptor=OrmlRegistry.capabilities.firstOrNull{it.id==capabilityId}
            ?:return OrmlResult(capabilityId,false,message="Unknown ORML capability")
        val outputDir=request.options["outputDir"]?.let(Path::of)?.toAbsolutePath()
            ?: input.parent.resolve(".cosmosis-orml")
        Files.createDirectories(outputDir)
        val ext=when {
            descriptor.outputs.any{it=="image"||it.contains("mask",true)} -> request.options["outputFormat"]?.lowercase()?.let{
                when(it){"jpeg","jpg"->"jpg";"webp"->"webp";else->"png"}
            }?:"png"
            else -> "json"
        }
        val output=outputDir.resolve("orml-"+capabilityId+"-"+System.nanoTime()+"."+ext)
        val timeout=request.options["timeoutSeconds"]?.toLongOrNull()?.coerceIn(1,3600)?:defaultTimeoutSeconds

        val args=mutableListOf(
            executable.toAbsolutePath().toString(),
            "--capability",capabilityId,
            "--input",input.toString(),
            "--output",output.toString()
        )
        request.options
            .filterKeys{it !in INTERNAL_OPTIONS}
            .toSortedMap()
            .forEach{(k,v)->args+=listOf("--option",k+"="+v)}

        return runCatching {
            val process=ProcessBuilder(args).redirectErrorStream(true).start()
            val finished=process.waitFor(timeout,TimeUnit.SECONDS)
            if(!finished){
                process.destroyForcibly()
                return OrmlResult(capabilityId,false,message="ORML runner timed out after "+timeout+"s")
            }
            val log=process.inputStream.bufferedReader().use{it.readText()}.replace(Regex("[\\r\\n]+")," ").take(500)
            if(process.exitValue()!=0)return OrmlResult(capabilityId,false,message="ORML runner exited "+process.exitValue()+if(log.isBlank())"" else ": "+log)
            if(descriptor.outputs.any{it=="image"||it.contains("mask",true)} && !Files.isRegularFile(output))
                return OrmlResult(capabilityId,false,message="ORML runner completed without required output file")
            OrmlResult(
                capabilityId,true,
                outputPaths=if(Files.isRegularFile(output))listOf(output.toString())else emptyList(),
                metadata=mapOf("runner" to executable.fileName.toString(),"mode" to "external-process"),
                message=if(log.isBlank())"ORML runner complete" else "ORML runner complete / "+log
            )
        }.getOrElse{OrmlResult(capabilityId,false,message="ORML runner failed: "+(it.message?:it.javaClass.simpleName))}
    }

    companion object {
        private val INTERNAL_OPTIONS=setOf("outputDir","outputFormat","timeoutSeconds")
    }
}

data class ProcessOrmlDiscovery(val adapters:List<OrmlAdapter>,val errors:List<String>)

object ProcessOrmlAdapters {
    private val ENV_BY_CAPABILITY=linkedMapOf(
        "smart-subject-mask" to "COSMOSIS_ORML_U2NET_RUNNER",
        "person-body-mask" to "COSMOSIS_ORML_BODYPIX_RUNNER",
        "image-embedding" to "COSMOSIS_ORML_CLASSIFIER_RUNNER",
        "super-resolution" to "COSMOSIS_ORML_SUPER_RESOLUTION_RUNNER"
    )

    fun discover(environment:Map<String,String> = System.getenv()):ProcessOrmlDiscovery {
        val adapters=mutableListOf<OrmlAdapter>();val errors=mutableListOf<String>()
        ENV_BY_CAPABILITY.forEach{(capability,env)->
            val raw=environment[env]?.trim().orEmpty()
            if(raw.isBlank())return@forEach
            val path=runCatching{Path.of(raw).toAbsolutePath()}.getOrNull()
            if(path==null||!Files.isRegularFile(path)||!Files.isExecutable(path)){
                errors+="Rejected $env: configured runner is not an executable file"
            }else adapters+=ProcessOrmlAdapter(capability,path)
        }
        return ProcessOrmlDiscovery(adapters,errors)
    }

    fun environmentNames():Map<String,String> = ENV_BY_CAPABILITY.toMap()
}
