package studio.cosmosis.orml.superresolution

import studio.cosmosis.orml.runner.OrmlRunnerBackend
import studio.cosmosis.orml.runner.RunnerRequest
import studio.cosmosis.orml.runner.RunnerResult
import java.awt.image.BufferedImage
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class SuperResolutionBackend @JvmOverloads constructor(
    private val runtimeFactory:()->SuperResolutionRuntime = { ReflectiveTensorFlowSuperResolutionRuntime.fromEnvironment() },
    readinessProbe:()->Unit = { ReflectiveTensorFlowSuperResolutionRuntime.verifyEnvironment() }
):OrmlRunnerBackend {
    init { readinessProbe() }

    override val backendId:String="falsr-a-tensorflow-pinned"
    override val capabilities:Set<String> = setOf("super-resolution")

    override fun run(request:RunnerRequest):RunnerResult {
        if(request.capabilityId!="super-resolution")
            return RunnerResult(false,"unsupported capability "+request.capabilityId)

        val extension=request.output.fileName.toString().substringAfterLast('.',missingDelimiterValue="").lowercase()
        if(extension!="png")return RunnerResult(false,"super-resolution requires PNG output")

        val octaves=request.options["octaves"]?.toIntOrNull()?:1
        if(octaves !in 1..3)return RunnerResult(false,"octaves must be between 1 and 3")

        val source=runCatching{ImageIO.read(request.input.toFile())}.getOrNull()
            ?:return RunnerResult(false,"input is not a decodable image")
        val scale=1 shl octaves
        val outputWidth=source.width.toLong()*scale
        val outputHeight=source.height.toLong()*scale
        if(outputWidth>SuperResolutionModelPin.maxOutputSide || outputHeight>SuperResolutionModelPin.maxOutputSide)
            return RunnerResult(false,"requested super-resolution output exceeds "+SuperResolutionModelPin.maxOutputSide+" px side bound")
        if(outputWidth*outputHeight>SuperResolutionModelPin.maxOutputPixels)
            return RunnerResult(false,"requested super-resolution output exceeds "+SuperResolutionModelPin.maxOutputPixels+" pixel neural budget")

        return runtimeFactory().use{runtime->
            runCatching{
                var current=source
                repeat(octaves){
                    current=runtime.upscale2x(current)
                }
                request.output.parent?.let(Files::createDirectories)
                require(ImageIO.write(current,"png",request.output.toFile())){"PNG writer unavailable"}
                RunnerResult(
                    true,
                    "pinned FALSR-A super-resolution generated",
                    mapOf(
                        "backend" to backendId,
                        "upstreamCommit" to SuperResolutionModelPin.upstreamCommit,
                        "model" to SuperResolutionModelPin.modelName,
                        "modelSha256" to SuperResolutionModelPin.modelSha256,
                        "scale" to scale.toString(),
                        "width" to current.width.toString(),
                        "height" to current.height.toString()
                    )
                )
            }.getOrElse{
                RunnerResult(false,"super-resolution backend failed: "+(it.message?:it.javaClass.simpleName))
            }
        }
    }
}

interface SuperResolutionRuntime:Closeable {
    fun upscale2x(input:BufferedImage):BufferedImage
}
