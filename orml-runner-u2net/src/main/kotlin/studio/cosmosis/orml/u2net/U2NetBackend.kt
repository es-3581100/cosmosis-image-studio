package studio.cosmosis.orml.u2net

import studio.cosmosis.orml.runner.OrmlRunnerBackend
import studio.cosmosis.orml.runner.RunnerRequest
import studio.cosmosis.orml.runner.RunnerResult
import java.io.Closeable
import java.nio.file.Path

class U2NetBackend @JvmOverloads constructor(
    private val runtimeFactory:()->U2NetRuntime = { ReflectiveTensorFlowU2NetRuntime.fromEnvironment() },
    readinessProbe:()->Unit = { ReflectiveTensorFlowU2NetRuntime.verifyEnvironment() }
):OrmlRunnerBackend {
    init { readinessProbe() }

    override val backendId:String="u2net-tensorflow-pinned"
    override val capabilities:Set<String> = setOf("smart-subject-mask")

    override fun run(request:RunnerRequest):RunnerResult {
        if(request.capabilityId!="smart-subject-mask")
            return RunnerResult(false,"unsupported capability "+request.capabilityId)

        val extension=request.output.fileName.toString().substringAfterLast('.',missingDelimiterValue="").lowercase()
        if(extension!="png")return RunnerResult(false,"smart-subject-mask requires PNG output")

        return runtimeFactory().use{runtime->
            runCatching{
                runtime.generateMask(request.input,request.output)
                RunnerResult(
                    true,
                    "pinned U2Net subject mask generated",
                    mapOf(
                        "backend" to backendId,
                        "upstreamCommit" to U2NetModelPin.upstreamCommit,
                        "model" to U2NetModelPin.modelName,
                        "modelSha256" to U2NetModelPin.modelSha256
                    )
                )
            }.getOrElse{
                RunnerResult(false,"U2Net backend failed: "+(it.message?:it.javaClass.simpleName))
            }
        }
    }
}

interface U2NetRuntime:Closeable {
    fun generateMask(input:Path,output:Path)
}
