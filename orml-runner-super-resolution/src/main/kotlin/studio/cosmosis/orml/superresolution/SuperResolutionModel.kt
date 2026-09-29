package studio.cosmosis.orml.superresolution

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object SuperResolutionModelPin {
    const val upstreamRepository = "https://github.com/openrndr/orml"
    const val upstreamCommit = "bb6333e62b17a9a0fc12ef889bc3642428e6f4f4"
    const val sourcePath = "orml-super-resolution/src/main/kotlin/ImageUpscaler.kt"
    const val modelName = "FALSR-A-1.0"
    const val modelUrl = "https://mlmodels.openrndr.org/FALSR-A-1.0.pb"
    const val modelSha256 = "639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1"
    const val inputYTensor = "input_image_evaluate_y"
    const val inputPbPrTensor = "input_image_evaluate_pbpr"
    const val outputTensor = "test_sr_evaluator_i1_b0_g/target"
    const val modelEnv = "COSMOSIS_ORML_SUPER_RESOLUTION_MODEL"
    const val scaleFactor = 2
    const val maxOutputSide = 8192
    const val maxOutputPixels = 16_777_216L

    fun requirePinnedModel(environment:Map<String,String> = System.getenv()):Path {
        val raw=environment[modelEnv]?.trim().orEmpty()
        require(raw.isNotBlank()){"$modelEnv must point to the pinned FALSR-A .pb model"}
        val path=Path.of(raw).toAbsolutePath().normalize()
        require(Files.isRegularFile(path)){"$modelEnv is not a regular file: $path"}
        val found=sha256(path)
        require(found.equals(modelSha256,true)){
            "$modelEnv SHA-256 is $found but expected $modelSha256"
        }
        return path
    }

    fun sha256(path:Path):String {
        val digest=MessageDigest.getInstance("SHA-256")
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
