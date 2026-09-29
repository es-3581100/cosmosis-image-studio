package studio.cosmosis.orml.bodypix

import studio.cosmosis.orml.runner.OrmlRunnerBackend
import studio.cosmosis.orml.runner.RunnerRequest
import studio.cosmosis.orml.runner.RunnerResult
import java.io.Closeable
import java.nio.file.Path

data class BodyPixOptions(
    val threshold:Float=BodyPixModelPin.defaultThreshold,
    val internalResolution:Double=BodyPixModelPin.defaultInternalResolution,
    val maxInputSide:Int=BodyPixModelPin.defaultMaxInputSide
)

class BodyPixBackend @JvmOverloads constructor(
    private val runtimeFactory:()->BodyPixRuntime = { ReflectiveTensorFlowBodyPixRuntime.fromEnvironment() },
    readinessProbe:()->Unit = { ReflectiveTensorFlowBodyPixRuntime.verifyEnvironment() }
):OrmlRunnerBackend {
    init { readinessProbe() }

    override val backendId:String="bodypix-mobilenet-tensorflow-pinned"
    override val capabilities:Set<String> = setOf("person-body-mask")

    override fun run(request:RunnerRequest):RunnerResult {
        if(request.capabilityId!="person-body-mask")
            return RunnerResult(false,"unsupported capability "+request.capabilityId)

        val extension=request.output.fileName.toString().substringAfterLast('.',missingDelimiterValue="").lowercase()
        if(extension!="png")return RunnerResult(false,"person-body-mask requires PNG output")

        val threshold=request.options["threshold"]?.toFloatOrNull()?:BodyPixModelPin.defaultThreshold
        if(threshold !in 0.0f..1.0f)return RunnerResult(false,"threshold must be between 0 and 1")
        val internal=request.options["internalResolution"]?.toDoubleOrNull()?:BodyPixModelPin.defaultInternalResolution
        if(internal !in 0.1..1.0)return RunnerResult(false,"internalResolution must be between 0.1 and 1.0")
        val maxSide=request.options["maxInputSide"]?.toIntOrNull()?:BodyPixModelPin.defaultMaxInputSide
        if(maxSide !in 257..2049)return RunnerResult(false,"maxInputSide must be between 257 and 2049")

        val options=BodyPixOptions(threshold,internal,maxSide)
        return runtimeFactory().use{runtime->
            runCatching{
                runtime.generateMask(request.input,request.output,options)
                RunnerResult(
                    true,
                    "pinned BodyPix person mask generated",
                    mapOf(
                        "backend" to backendId,
                        "upstreamCommit" to BodyPixModelPin.upstreamCommit,
                        "model" to BodyPixModelPin.modelName,
                        "modelSha256" to BodyPixModelPin.modelSha256,
                        "threshold" to threshold.toString(),
                        "internalResolution" to internal.toString()
                    )
                )
            }.getOrElse{
                RunnerResult(false,"BodyPix backend failed: "+(it.message?:it.javaClass.simpleName))
            }
        }
    }
}

interface BodyPixRuntime:Closeable {
    fun generateMask(input:Path,output:Path,options:BodyPixOptions)
}
