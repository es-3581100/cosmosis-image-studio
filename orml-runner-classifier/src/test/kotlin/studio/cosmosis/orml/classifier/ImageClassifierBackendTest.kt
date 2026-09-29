package studio.cosmosis.orml.classifier

import studio.cosmosis.orml.runner.RunnerEngine
import studio.cosmosis.orml.runner.RunnerRequest
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.*

class ImageClassifierBackendTest {
    @Test fun preprocessingUsesZeroToOneRgbAndVerticalFlip(){
        val image=BufferedImage(224,224,BufferedImage.TYPE_INT_RGB)
        for(y in 0 until 224)for(x in 0 until 224){
            image.setRGB(x,y,if(y<112)Color.RED.rgb else Color.BLUE.rgb)
        }
        val data=normalizeClassifierInput(image)
        assertEquals(224*224*3,data.size)
        // First tensor row reads the bottom source row because upstream ORML flips vertically.
        assertTrue(abs(data[0]-0.0f)<1e-6)
        assertTrue(abs(data[1]-0.0f)<1e-6)
        assertTrue(abs(data[2]-1.0f)<1e-6)
        val last=(224*224-1)*3
        assertTrue(abs(data[last]-1.0f)<1e-6)
        assertTrue(abs(data[last+1]-0.0f)<1e-6)
        assertTrue(abs(data[last+2]-0.0f)<1e-6)
        assertTrue(data.all{it in 0.0f..1.0f})
    }

    @Test fun gitBlobHashMatchesCanonicalGitObjectHash(){
        val file=Files.createTempFile("classifier-blob",".txt")
        Files.writeString(file,"hello\n")
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a",ImageClassifierModelPin.gitBlobSha1(file))
    }

    @Test fun missingOrWrongModelFailsReadiness(){
        assertFailsWith<IllegalArgumentException>{
            ImageClassifierModelPin.requirePinnedModel(emptyMap())
        }
        val file=Files.createTempFile("fake-classifier",".pb")
        Files.writeString(file,"wrong")
        val error=assertFailsWith<IllegalArgumentException>{
            ImageClassifierModelPin.requirePinnedModel(mapOf(ImageClassifierModelPin.modelEnv to file.toString()))
        }
        assertTrue(error.message.orEmpty().contains("size")||error.message.orEmpty().contains("Git blob"))
    }

    @Test fun topClassesAreScoreOrdered(){
        val scores=floatArrayOf(.1f,.9f,.2f,.8f)
        assertEquals(listOf(1,3),topClassIndices(scores,2))
    }

    @Test fun backendProducesDerivedEmbeddingJson(){
        val fake=object:ImageClassifierRuntime{
            override fun infer(input:Path)=ImageClassifierInference(
                classScores=floatArrayOf(.05f,.7f,.1f,.15f),
                embedding=floatArrayOf(.1f,-.2f,.3f,.4f)
            )
            override fun close(){}
        }
        val backend=ImageClassifierBackend(runtimeFactory={fake},readinessProbe={})
        val input=Files.createTempFile("classifier-input",".png")
        Files.write(input,byteArrayOf(1))
        val out=Files.createTempDirectory("classifier-test").resolve("embedding.json")
        val result=RunnerEngine(listOf(backend)).execute(
            RunnerRequest("image-embedding",input,out,mapOf("topK" to "2"))
        )
        assertTrue(result.ok,result.message)
        val json=Files.readString(out)
        assertContains(json,"\"labelsResolved\":false")
        assertContains(json,"\"generatedTag\":\"imagenet-index:1\"")
        assertContains(json,"\"embedding\":{\"dimension\":4")
        assertFalse(json.contains("userTag"))
    }

    @Test fun invalidTopKAndNonFiniteOutputFailClosed(){
        val fake=object:ImageClassifierRuntime{
            override fun infer(input:Path)=ImageClassifierInference(
                classScores=floatArrayOf(Float.NaN),embedding=floatArrayOf(1.0f)
            )
            override fun close(){}
        }
        val backend=ImageClassifierBackend(runtimeFactory={fake},readinessProbe={})
        val input=Files.createTempFile("classifier-input",".png")
        val out=Files.createTempDirectory("classifier-test").resolve("embedding.json")
        assertFalse(backend.run(RunnerRequest("image-embedding",input,out,mapOf("topK" to "0"))).ok)
        assertFalse(backend.run(RunnerRequest("image-embedding",input,out)).ok)
    }
}
