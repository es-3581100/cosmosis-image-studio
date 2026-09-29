package studio.cosmosis.orml.bodypix

import studio.cosmosis.orml.runner.RunnerEngine
import studio.cosmosis.orml.runner.RunnerRequest
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class BodyPixBackendTest {
    @Test fun validResolutionMatchesBodyPixStrideRule(){
        assertEquals(321,validBodyPixResolution(320.0,16))
        assertEquals(321,validBodyPixResolution(321.0,16))
        assertEquals(33,validBodyPixResolution(10.0,16))
    }

    @Test fun inputSizeIsBoundedAndStrideValid(){
        val (w,h)=bodyPixInputSize(4000,3000,0.5,1025)
        assertEquals(1025,w)
        assertTrue(h<=1025)
        assertEquals(0,(w-1)%16)
        assertEquals(0,(h-1)%16)
    }

    @Test fun mobileNetNormalizationUsesMinusOneToOne(){
        val image=BufferedImage(2,1,BufferedImage.TYPE_INT_RGB)
        image.setRGB(0,0,Color.BLACK.rgb)
        image.setRGB(1,0,Color.WHITE.rgb)
        val values=normalizeBodyPixMobileNetInput(image,2,1)
        assertEquals(6,values.size)
        assertEquals(-1.0f,values[0],0.0001f)
        assertEquals(-1.0f,values[1],0.0001f)
        assertEquals(-1.0f,values[2],0.0001f)
        assertEquals(1.0f,values[3],0.0001f)
        assertEquals(1.0f,values[4],0.0001f)
        assertEquals(1.0f,values[5],0.0001f)
    }

    @Test fun segmentationScoresBecomeBinaryMask(){
        val mask=bodyPixMaskImage(floatArrayOf(0.69f,0.71f),2,1,2,1,0.7f)
        assertEquals(0,mask.raster.getSample(0,0,0))
        assertEquals(255,mask.raster.getSample(1,0,0))
    }

    @Test fun rejectsUnsupportedCapabilityAndInvalidOptions(){
        val backend=BodyPixBackend(runtimeFactory={FakeRuntime()},readinessProbe={})
        val input=tempImage(64,48)
        val out=Files.createTempDirectory("bodypix-out").resolve("mask.png")
        assertFalse(backend.run(RunnerRequest("smart-subject-mask",input,out)).ok)
        val bad=backend.run(RunnerRequest("person-body-mask",input,out,mapOf("threshold" to "1.5")))
        assertFalse(bad.ok)
        assertContains(bad.message,"threshold")
    }

    @Test fun runnerAdmitsSameSizePersonMask(){
        val backend=BodyPixBackend(runtimeFactory={FakeRuntime()},readinessProbe={})
        val input=tempImage(64,48)
        val out=Files.createTempDirectory("bodypix-runner").resolve("mask.png")
        val result=RunnerEngine(listOf(backend)).execute(RunnerRequest("person-body-mask",input,out))
        assertTrue(result.ok,result.message)
        val image=ImageIO.read(out.toFile())
        assertEquals(64,image.width)
        assertEquals(48,image.height)
        assertEquals(BodyPixModelPin.modelSha256,result.metadata["modelSha256"])
    }

    @Test fun pinnedModelHashIsEnforced(){
        val model=Files.createTempFile("fake-bodypix",".pb")
        Files.writeString(model,"not-the-pinned-model")
        val error=assertFailsWith<IllegalArgumentException>{
            BodyPixModelPin.requirePinnedModel(mapOf(BodyPixModelPin.modelEnv to model.toString()))
        }
        assertContains(error.message.orEmpty(),"SHA-256")
    }

    @Test fun missingModelEnvironmentFailsReadiness(){
        val error=assertFailsWith<IllegalArgumentException>{
            BodyPixModelPin.requirePinnedModel(emptyMap())
        }
        assertContains(error.message.orEmpty(),BodyPixModelPin.modelEnv)
    }

    private class FakeRuntime:BodyPixRuntime {
        override fun generateMask(input:Path,output:Path,options:BodyPixOptions){
            val source=ImageIO.read(input.toFile())
            val mask=BufferedImage(source.width,source.height,BufferedImage.TYPE_BYTE_GRAY)
            for(y in 0 until mask.height)for(x in 0 until mask.width){
                mask.raster.setSample(x,y,0,if(x<mask.width/2)255 else 0)
            }
            output.parent?.let(Files::createDirectories)
            ImageIO.write(mask,"png",output.toFile())
        }
        override fun close(){}
    }

    private fun tempImage(width:Int,height:Int):Path {
        val path=Files.createTempFile("bodypix-input",".png")
        val image=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image,"png",path.toFile())
        return path
    }
}
