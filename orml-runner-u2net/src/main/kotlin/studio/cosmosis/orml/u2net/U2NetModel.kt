package studio.cosmosis.orml.u2net

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object U2NetModelPin {
    const val upstreamRepository = "https://github.com/openrndr/orml"
    const val upstreamCommit = "bb6333e62b17a9a0fc12ef889bc3642428e6f4f4"
    const val modelName = "u2netp-320x320-float32-1.0"
    const val modelSha256 = "79e8757f7c5342c4f2b0dab4c66c4bc128b077c9541db5c6a5b363ff75c8b54a"
    const val modelUrl = "https://mlmodels.openrndr.org/u2netp-320x320-float32-1.0.pb"
    const val inputTensor = "inputs"
    const val outputTensor = "functional_1/tf_op_layer_Sigmoid_6/Sigmoid_6"
    const val width = 320
    const val height = 320
    const val modelEnv = "COSMOSIS_ORML_U2NET_MODEL"

    fun requirePinnedModel(environment:Map<String,String> = System.getenv()):Path {
        val raw=environment[modelEnv]?.trim().orEmpty()
        require(raw.isNotBlank()){"$modelEnv must point to the pinned U2Net .pb model"}
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
