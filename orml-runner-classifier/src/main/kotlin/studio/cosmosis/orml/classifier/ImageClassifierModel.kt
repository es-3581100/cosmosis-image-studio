package studio.cosmosis.orml.classifier

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object ImageClassifierModelPin {
    const val upstreamRepository = "https://github.com/openrndr/orml"
    const val upstreamCommit = "bb6333e62b17a9a0fc12ef889bc3642428e6f4f4"
    const val sourcePath = "orml-image-classifier/src/main/kotlin/ImageClassifier.kt"
    const val modelPath = "orml-image-classifier/src/main/resources/tfmodels/v3-large-minimalistic_224_1.0_float.pb"
    const val modelName = "v3-large-minimalistic_224_1.0_float"
    const val modelGitBlobSha1 = "2e03a7f49b2bf46dc04349d8af9b669d2fca484e"
    const val modelSizeBytes = 15_923_156L
    const val rawModelUrl = "https://raw.githubusercontent.com/openrndr/orml/bb6333e62b17a9a0fc12ef889bc3642428e6f4f4/orml-image-classifier/src/main/resources/tfmodels/v3-large-minimalistic_224_1.0_float.pb"
    const val inputTensor = "input"
    const val classificationTensor = "MobilenetV3/Predictions/Softmax"
    const val embeddingTensor = "MobilenetV3/Logits/Conv2d_1c_1x1/BiasAdd"
    const val width = 224
    const val height = 224
    const val modelEnv = "COSMOSIS_ORML_CLASSIFIER_MODEL"
    const val defaultTopK = 5

    fun requirePinnedModel(environment:Map<String,String> = System.getenv()):Path {
        val raw=environment[modelEnv]?.trim().orEmpty()
        require(raw.isNotBlank()){"$modelEnv must point to the pinned MobileNetV3 classifier .pb model"}
        val path=Path.of(raw).toAbsolutePath().normalize()
        require(Files.isRegularFile(path)){"$modelEnv is not a regular file: $path"}
        val size=Files.size(path)
        require(size==modelSizeBytes){"$modelEnv size is $size bytes but expected $modelSizeBytes"}
        val found=gitBlobSha1(path)
        require(found.equals(modelGitBlobSha1,true)){
            "$modelEnv Git blob SHA-1 is $found but expected $modelGitBlobSha1"
        }
        return path
    }

    fun gitBlobSha1(path:Path):String {
        val size=Files.size(path)
        val digest=MessageDigest.getInstance("SHA-1")
        digest.update("blob $size\u0000".toByteArray(StandardCharsets.UTF_8))
        Files.newInputStream(path).use{input->
            val buffer=ByteArray(64*1024)
            while(true){
                val n=input.read(buffer)
                if(n<0)break
                digest.update(buffer,0,n)
            }
        }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
}
