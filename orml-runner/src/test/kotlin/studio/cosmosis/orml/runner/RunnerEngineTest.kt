package studio.cosmosis.orml.runner

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class RunnerEngineTest {
    private fun image(path:java.nio.file.Path,w:Int=32,h:Int=24){
        val bi=BufferedImage(w,h,BufferedImage.TYPE_INT_RGB)
        for(y in 0 until h)for(x in 0 until w)bi.setRGB(x,y,Color((x*7)%255,(y*11)%255,80).rgb)
        ImageIO.write(bi,"png",path.toFile())
    }

    @Test fun parsesProtocolArguments(){
        val p=parseArgs(arrayOf("--capability","smart-subject-mask","--input","a.png","--output","b.png","--option","threshold=.5"))
        assertEquals("smart-subject-mask",p.capability)
        assertEquals(".5",p.options["threshold"])
    }

    @Test fun rejectsUnknownArguments(){
        assertFails{parseArgs(arrayOf("--mystery"))}
    }

    @Test fun rejectsAmbiguousCapabilityProviders(){
        val one=object:OrmlRunnerBackend{
            override val backendId="one"
            override val capabilities=setOf("smart-subject-mask")
            override fun run(request:RunnerRequest)=RunnerResult(false,"unused")
        }
        val two=object:OrmlRunnerBackend{
            override val backendId="two"
            override val capabilities=setOf("smart-subject-mask")
            override fun run(request:RunnerRequest)=RunnerResult(false,"unused")
        }
        val engine=RunnerEngine(listOf(one,two))
        assertFalse("smart-subject-mask" in engine.availableCapabilities())
        assertTrue(engine.errors.any{it.contains("ambiguous")})
    }

    @Test fun maskBackendMustProduceMatchingDecodableImage(){
        val dir=Files.createTempDirectory("runner-mask")
        val input=dir.resolve("in.png");image(input,32,24)
        val output=dir.resolve("mask.png")
        val good=object:OrmlRunnerBackend{
            override val backendId="fake-mask"
            override val capabilities=setOf("smart-subject-mask")
            override fun run(request:RunnerRequest):RunnerResult{
                val bi=BufferedImage(32,24,BufferedImage.TYPE_BYTE_GRAY)
                ImageIO.write(bi,"png",request.output.toFile())
                return RunnerResult(true,"done")
            }
        }
        val ok=RunnerEngine(listOf(good)).execute(RunnerRequest("smart-subject-mask",input,output))
        assertTrue(ok.ok);assertEquals("fake-mask",ok.metadata["backend"])

        val bad=object:OrmlRunnerBackend{
            override val backendId="bad-mask"
            override val capabilities=setOf("smart-subject-mask")
            override fun run(request:RunnerRequest):RunnerResult{
                val bi=BufferedImage(16,16,BufferedImage.TYPE_BYTE_GRAY)
                ImageIO.write(bi,"png",request.output.toFile())
                return RunnerResult(true,"wrong size")
            }
        }
        val rejected=RunnerEngine(listOf(bad)).execute(RunnerRequest("smart-subject-mask",input,output))
        assertFalse(rejected.ok);assertContains(rejected.message,"dimensions")
    }

    @Test fun rejectsSymlinkOutputArtifact(){
        if(System.getProperty("os.name").lowercase().contains("win"))return
        val dir=Files.createTempDirectory("runner-symlink")
        val input=dir.resolve("in.png");image(input)
        val output=dir.resolve("mask.png")
        val target=dir.resolve("elsewhere.png");image(target,32,24)
        val backend=object:OrmlRunnerBackend{
            override val backendId="symlink-mask"
            override val capabilities=setOf("smart-subject-mask")
            override fun run(request:RunnerRequest):RunnerResult{
                Files.createSymbolicLink(request.output,target)
                return RunnerResult(true,"claimed")
            }
        }
        val result=RunnerEngine(listOf(backend)).execute(RunnerRequest("smart-subject-mask",input,output))
        assertFalse(result.ok);assertContains(result.message,"regular output file")
    }

    @Test fun embeddingBackendMustProduceJson(){
        val dir=Files.createTempDirectory("runner-json")
        val input=dir.resolve("in.png");image(input)
        val output=dir.resolve("embedding.json")
        val backend=object:OrmlRunnerBackend{
            override val backendId="fake-embedding"
            override val capabilities=setOf("image-embedding")
            override fun run(request:RunnerRequest):RunnerResult{
                Files.writeString(request.output,"{\"labels\":[\"cat\"],\"embedding\":[0.1,0.2]}")
                return RunnerResult(true,"done")
            }
        }
        assertTrue(RunnerEngine(listOf(backend)).execute(RunnerRequest("image-embedding",input,output)).ok)

        val bad=object:OrmlRunnerBackend{
            override val backendId="bad-embedding"
            override val capabilities=setOf("image-embedding")
            override fun run(request:RunnerRequest):RunnerResult{
                Files.writeString(request.output,"not-json")
                return RunnerResult(true,"done")
            }
        }
        assertFalse(RunnerEngine(listOf(bad)).execute(RunnerRequest("image-embedding",input,output)).ok)
    }

    @Test fun describeIsMachineReadableShape(){
        val json=RunnerEngine(emptyList()).describeJson()
        assertContains(json,"\"protocolVersion\":\"1\"")
        assertContains(json,"\"smart-subject-mask\"")
        assertContains(json,"\"available\":false")
    }

    @Test fun neverOverwritesInput(){
        val input=Files.createTempFile("runner-in",".png");image(input)
        val result=RunnerEngine(emptyList()).execute(RunnerRequest("smart-subject-mask",input,input))
        assertFalse(result.ok);assertContains(result.message,"must not overwrite")
    }
}
