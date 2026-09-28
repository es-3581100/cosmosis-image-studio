package studio.cosmosis

import studio.cosmosis.ui.StudioController
import studio.cosmosis.ui.StudioState
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Dependency-resolved acceptance smoke for CI. It never calls a paid provider.
 * The local deterministic preview provider exercises the same worker, SQLite,
 * mask, lineage and export paths used by remote providers.
 */
fun main() {
    val temp=Files.createTempDirectory("cosmosis-acceptance-")
    val projectRoot=temp.resolve("project")
    val source=temp.resolve("source.png")
    fixture(source)
    val state=StudioState()
    StudioController(state).use { studio ->
        studio.createProject(projectRoot,"Cosmosis Acceptance")
        studio.importImage(source)
        require(state.get().imagePath!=null && state.get().imageWidth==192 && state.get().imageHeight==128)
        val rootVersion=state.get().currentVersion

        val extracted=studio.analyzeCurrent()
        require(extracted.contains("STYLE DNA"))
        val prompt=studio.savePrompt("Acceptance prompt",extracted,"MY PROMPTS/Acceptance")
        val mask=studio.smartSaliencyMask()
        require(Files.size(mask)>0)

        studio.generate(
            prompt="Preserve the central subject and alter only the selected region.",
            providerId="local",model="local-preview-v1",variants=2,edit=true,
            quality="preview",promptId=prompt.id
        )
        awaitJob(state,"first edit")
        val editVersion=state.get().currentVersion
        require(editVersion!=rootVersion)
        require(studio.versionNodes().count{it.parentId==rootVersion}>=2)

        // Branch from the immutable root rather than flattening history.
        studio.setCurrentVersion(rootVersion)
        studio.generate(
            prompt="A second concept branch from the original.",
            providerId="local",model="local-preview-v1",variants=1,edit=false,
            quality="preview",promptId=prompt.id
        )
        awaitJob(state,"branch generation")
        val branchVersion=state.get().currentVersion
        require(branchVersion!=editVersion)
        require(studio.versionNodes().count{it.parentId==rootVersion}>=3)

        studio.setCurrentVersion(editVersion)
        studio.rotateCurrent(true)
        require(state.get().imageWidth==128 && state.get().imageHeight==192)

        // Agent Build executes the visible Director plan through the same bounded worker path.
        studio.setCurrentVersion(rootVersion)
        val agentPlan=studio.runAgentBuild(
            intent="Preserve the central subject, replace the background with a warm editorial field, and make two concepts.",
            providerId="local",model="local-preview-v1",variants=2,
            budget=JobBudget(maxGenerations=2,maxRetries=1,maxParallelWorkers=2,timeoutSeconds=30)
        )
        awaitAgentBuild(state,agentPlan)
        require(studio.agentBuildReports().any{Files.readString(it).contains("critic:")})
        require(studio.allPrompts().any{it.origin==PromptOrigin.AGENT && it.source=="Director/$agentPlan"})

        val report=studio.exportReport()
        val diagnostics=studio.exportDiagnostics()
        require(Files.size(report)>500)
        require(Files.readString(report).contains("Version graph"))
        require(Files.readString(report).contains("Generation ledger"))
        require(Files.readString(diagnostics).contains("COSMOSIS IMAGE STUDIO"))

        // Restart/reopen proves the durable local model and does not rerun jobs.
        studio.openProject(projectRoot)
        require(studio.versionNodes().size>=5)
        require(state.get().imagePath!=null)
        println("ACCEPTANCE_SMOKE_PASS project=$projectRoot versions=${studio.versionNodes().size}")
    }
}

private fun awaitAgentBuild(state:StudioState,planId:String,timeoutMs:Long=12_000) {
    val deadline=System.currentTimeMillis()+timeoutMs
    while(System.currentTimeMillis()<deadline) {
        val message=state.get().message
        if(message.contains("AGENT BUILD $planId / COMPLETE")) return
        if(message.contains("AGENT BUILD $planId / FAILED")) error(message)
        Thread.sleep(25)
    }
    error("Agent Build timed out: ${state.get().message}")
}

private fun awaitJob(state:StudioState,label:String,timeoutMs:Long=12_000) {
    val deadline=System.currentTimeMillis()+timeoutMs
    while(System.currentTimeMillis()<deadline) {
        when(state.get().jobState) {
            "COMPLETE" -> return
            "FAILED" -> error("$label failed: ${state.get().message}")
        }
        Thread.sleep(25)
    }
    error("$label timed out: ${state.get().message}")
}

private fun fixture(path:Path) {
    val image=BufferedImage(192,128,BufferedImage.TYPE_INT_RGB)
    for(y in 0 until image.height) for(x in 0 until image.width) {
        val subject=x in 50..145 && y in 20..108
        image.setRGB(x,y,if(subject) Color(226,202,145).rgb else Color(18,18,15).rgb)
    }
    ImageIO.write(image,"png",path.toFile())
}
