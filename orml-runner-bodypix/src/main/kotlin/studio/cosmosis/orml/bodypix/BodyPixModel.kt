package studio.cosmosis.orml.bodypix

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object BodyPixModelPin {
    const val upstreamRepository = "https://github.com/openrndr/orml"
    const val upstreamCommit = "bb6333e62b17a9a0fc12ef889bc3642428e6f4f4"
    const val modelName = "bodypix-mobilenet-1.0"
    const val modelSha256 = "c64d6f3252217f9bd0ba790ac2a6ac8b45fc002767c97379cb2e8a3ce7317b56"
    const val modelUrl = "https://mlmodels.openrndr.org/bodypix-mobilenet-1.0.pb"
    const val inputTensor = "sub_2"
    const val outputTensor = "float_segments"
    const val modelEnv = "COSMOSIS_ORML_BODYPIX_MODEL"
    const val outputStride = 16
    const val defaultThreshold = 0.7f
    const val defaultInternalResolution = 0.5
    const val defaultMaxInputSide = 1025

    fun requirePinnedModel(environment:Map<String,String> = System.getenv()):Path {
        val raw=environment[modelEnv]?.trim().orEmpty()
        require(raw.isNotBlank()){"$modelEnv must point to the pinned BodyPix .pb model"}
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
