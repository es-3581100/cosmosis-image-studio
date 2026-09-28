package studio.cosmosis.orml.u2net

import studio.cosmosis.orml.runner.RunnerEngine
import studio.cosmosis.orml.runner.RunnerRequest
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class U2NetBackendTest {
    @Test fun rejectsUnsupportedCapability(){
        val backend=U2NetBackend(runtimeFactory={FakeRuntime()},readinessProbe={})
        val input=tempImage(24,18)
        val out=Files.createTempFile("mask",".png")
        val result=backend.run(RunnerRequest("person-body-mask",input,out))
        assertFalse(result.ok)
        assertContains(result.message,"unsupported")
    }

    @Test fun requiresPngOutput(){
        val backend=U2NetBackend(runtimeFactory={FakeRuntime()},readinessProbe={})
        val input=tempImage(24,18)
        val out=Files.createTempFile("mask",".jpg")
        val result=backend.run(RunnerRequest("smart-subject-mask",input,out))
        assertFalse(result.ok)
        assertContains(result.message,"PNG")
    }

    @Test fun runnerAdmitsSameSizeMaskFromBackend(){
        val backend=U2NetBackend(runtimeFactory={FakeRuntime()},readinessProbe={})
        val input=tempImage(24,18)
        val out=Files.createTempDirectory("u2net-test").resolve("mask.png")
        val result=RunnerEngine(listOf(backend)).execute(RunnerRequest("smart-subject-mask",input,out))
        assertTrue(result.ok,result.message)
        val image=ImageIO.read(out.toFile())
        assertEquals(24,image.width)
        assertEquals(18,image.height)
        assertEquals(U2NetModelPin.modelSha256,result.metadata["modelSha256"])
    }

    @Test fun pinnedModelHashIsEnforced(){
        val model=Files.createTempFile("fake-u2net",".pb")
        Files.writeString(model,"not-the-pinned-model")
        val error=assertFailsWith<IllegalArgumentException>{
            U2NetModelPin.requirePinnedModel(mapOf(U2NetModelPin.modelEnv to model.toString()))
        }
        assertContains(error.message.orEmpty(),"SHA-256")
    }

    @Test fun missingModelEnvironmentFailsReadiness(){
        val error=assertFailsWith<IllegalArgumentException>{
            U2NetModelPin.requirePinnedModel(emptyMap())
        }
        assertContains(error.message.orEmpty(),U2NetModelPin.modelEnv)
    }

    private class FakeRuntime:U2NetRuntime {
        override fun generateMask(input:Path,output:Path){
            val source=ImageIO.read(input.toFile())
            val mask=BufferedImage(source.width,source.height,BufferedImage.TYPE_BYTE_GRAY)
            for(y in 0 until mask.height)for(x in 0 until mask.width){
                mask.raster.setSample(x,y,0,if(x<mask.width/2)255 else 32)
            }
            output.parent?.let(Files::createDirectories)
            ImageIO.write(mask,"png",output.toFile())
        }
        override fun close(){}
    }

    private fun tempImage(width:Int,height:Int):Path {
        val path=Files.createTempFile("u2net-input",".png")
        val image=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image,"png",path.toFile())
        return path
    }
}
