package studio.cosmosis.orml.classifier

import studio.cosmosis.orml.runner.OrmlRunnerBackend
import studio.cosmosis.orml.runner.RunnerRequest
import studio.cosmosis.orml.runner.RunnerResult
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

data class ImageClassifierInference(
    val classScores:FloatArray,
    val embedding:FloatArray
)

class ImageClassifierBackend @JvmOverloads constructor(
    private val runtimeFactory:()->ImageClassifierRuntime = { ReflectiveTensorFlowImageClassifierRuntime.fromEnvironment() },
    readinessProbe:()->Unit = { ReflectiveTensorFlowImageClassifierRuntime.verifyEnvironment() }
):OrmlRunnerBackend {
    init { readinessProbe() }

    override val backendId:String="mobilenetv3-classifier-tensorflow-pinned"
    override val capabilities:Set<String> = setOf("image-embedding")

    override fun run(request:RunnerRequest):RunnerResult {
        if(request.capabilityId!="image-embedding")
            return RunnerResult(false,"unsupported capability "+request.capabilityId)

        val extension=request.output.fileName.toString().substringAfterLast('.',missingDelimiterValue="").lowercase()
        if(extension!="json")return RunnerResult(false,"image-embedding requires JSON output")

        val topK=request.options["topK"]?.toIntOrNull()?:ImageClassifierModelPin.defaultTopK
        if(topK !in 1..25)return RunnerResult(false,"topK must be between 1 and 25")

        return runtimeFactory().use{runtime->
            runCatching{
                val inference=runtime.infer(request.input)
                validateInference(inference)
                val json=classifierJson(inference,topK)
                request.output.parent?.let(Files::createDirectories)
                Files.writeString(request.output,json)
                RunnerResult(
                    true,
                    "pinned MobileNetV3 embedding generated",
                    mapOf(
                        "backend" to backendId,
                        "upstreamCommit" to ImageClassifierModelPin.upstreamCommit,
                        "model" to ImageClassifierModelPin.modelName,
                        "modelGitBlobSha1" to ImageClassifierModelPin.modelGitBlobSha1,
                        "classificationDimension" to inference.classScores.size.toString(),
                        "embeddingDimension" to inference.embedding.size.toString(),
                        "labelsResolved" to "false"
                    )
                )
            }.getOrElse{
                RunnerResult(false,"image classifier backend failed: "+(it.message?:it.javaClass.simpleName))
            }
        }
    }
}

interface ImageClassifierRuntime:Closeable {
    fun infer(input:Path):ImageClassifierInference
}

internal fun validateInference(inference:ImageClassifierInference){
    require(inference.classScores.isNotEmpty()){"classification scores are empty"}
    require(inference.embedding.isNotEmpty()){"embedding is empty"}
    require(inference.classScores.all{it.isFinite()}){"classification scores contain non-finite values"}
    require(inference.embedding.all{it.isFinite()}){"embedding contains non-finite values"}
}

internal fun topClassIndices(scores:FloatArray,topK:Int):List<Int> =
    scores.indices.sortedByDescending{scores[it]}.take(topK)

internal fun classifierJson(inference:ImageClassifierInference,topK:Int):String {
    val top=topClassIndices(inference.classScores,topK)
    val topJson=top.joinToString(","){i->
        """{"index":$i,"score":${inference.classScores[i]},"generatedTag":"imagenet-index:$i"}"""
    }
    val embedding=inference.embedding.joinToString(","){it.toString()}
    return """{"schemaVersion":1,"backend":"mobilenetv3-classifier-tensorflow-pinned","model":"${ImageClassifierModelPin.modelName}","modelGitBlobSha1":"${ImageClassifierModelPin.modelGitBlobSha1}","labelsResolved":false,"classification":{"dimension":${inference.classScores.size},"top":[$topJson]},"embedding":{"dimension":${inference.embedding.size},"values":[$embedding]}}"""
}
