package studio.cosmosis.orml.superresolution

import studio.cosmosis.orml.runner.RunnerEngine
import studio.cosmosis.orml.runner.RunnerRequest
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.*

class SuperResolutionBackendTest {
    @Test fun rgbToYpbprMatchesPinnedShaderMath(){
        val white=BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).apply{setRGB(0,0,Color.WHITE.rgb)}
        val wi=falsrInputs(white)
        assertTrue(abs(wi.y[0]-1.0f)<1e-6)
        assertTrue(wi.pbpr.all{abs(it)<1e-6})
        assertEquals(8,wi.pbpr.size)

        val red=BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).apply{setRGB(0,0,Color.RED.rgb)}
        val ri=falsrInputs(red)
        assertTrue(abs(ri.y[0]-0.2126f)<1e-5)
        val expectedPb=0.5f*(0.0f-0.2126f)/(1.0f-0.0722f)
        assertTrue(abs(ri.pbpr[0]-expectedPb)<1e-5)
        assertTrue(abs(ri.pbpr[1]-0.5f)<1e-5)
        for(pixel in 0 until 4){
            assertTrue(abs(ri.pbpr[pixel*2]-expectedPb)<1e-5)
            assertTrue(abs(ri.pbpr[pixel*2+1]-0.5f)<1e-5)
        }
    }

    @Test fun rgbOutputClampsAndPreservesDimensions(){
        val rgb=floatArrayOf(-1.0f,.5f,2.0f, 1.0f,0.0f,.25f)
        val image=falsrRgbImage(rgb,2,1)
        assertEquals(2,image.width);assertEquals(1,image.height)
        val a=image.getRGB(0,0)
        assertEquals(0,(a ushr 16) and 0xff)
        assertTrue(((a ushr 8) and 0xff) in 127..128)
        assertEquals(255,a and 0xff)
    }

    @Test fun backendSupportsBoundedRecursiveOctaves(){
        val fake=object:SuperResolutionRuntime{
            override fun upscale2x(input:BufferedImage):BufferedImage =
                BufferedImage(input.width*2,input.height*2,BufferedImage.TYPE_INT_RGB)
            override fun close(){}
        }
        val backend=SuperResolutionBackend(runtimeFactory={fake},readinessProbe={})
        val input=Files.createTempFile("falsr-in",".png")
        javax.imageio.ImageIO.write(BufferedImage(12,8,BufferedImage.TYPE_INT_RGB),"png",input.toFile())
        val output=Files.createTempDirectory("falsr-out").resolve("upscaled.png")
        val result=RunnerEngine(listOf(backend)).execute(
            RunnerRequest("super-resolution",input,output,mapOf("octaves" to "2"))
        )
        assertTrue(result.ok,result.message)
        val image=javax.imageio.ImageIO.read(output.toFile())
        assertEquals(48,image.width);assertEquals(32,image.height)
        assertEquals("4",result.metadata["scale"])
    }

    @Test fun invalidOctavesFailClosed(){
        val fake=object:SuperResolutionRuntime{
            override fun upscale2x(input:BufferedImage)=input
            override fun close(){}
        }
        val backend=SuperResolutionBackend(runtimeFactory={fake},readinessProbe={})
        val input=Files.createTempFile("falsr-in",".png")
        javax.imageio.ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",input.toFile())
        val output=Files.createTempFile("falsr-out",".png")
        assertFalse(backend.run(RunnerRequest("super-resolution",input,output,mapOf("octaves" to "0"))).ok)
        assertFalse(backend.run(RunnerRequest("super-resolution",input,output,mapOf("octaves" to "4"))).ok)
    }

    @Test fun pinnedModelHashIsEnforced(){
        val model=Files.createTempFile("fake-falsr",".pb")
        Files.writeString(model,"not-the-pinned-model")
        val error=assertFailsWith<IllegalArgumentException>{
            SuperResolutionModelPin.requirePinnedModel(mapOf(SuperResolutionModelPin.modelEnv to model.toString()))
        }
        assertContains(error.message.orEmpty(),"SHA-256")
    }

    @Test fun missingModelEnvironmentFailsReadiness(){
        val error=assertFailsWith<IllegalArgumentException>{
            SuperResolutionModelPin.requirePinnedModel(emptyMap())
        }
        assertContains(error.message.orEmpty(),SuperResolutionModelPin.modelEnv)
    }
}
