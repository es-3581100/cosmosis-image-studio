package studio.cosmosis

import studio.cosmosis.ui.*
import java.nio.file.Path
import javax.swing.SwingUtilities

fun main(){
    val state=StudioState();val controller=StudioController(state)
    val legacyWorkspace=System.getenv("COSMOSIS_LEGACY_WORKSPACE")=="1"
    val legacyDock=legacyWorkspace&&System.getenv("COSMOSIS_LEGACY_CONTROL_DOCK")=="1"
    var dock:ControlDock?=null
    if(legacyDock)SwingUtilities.invokeAndWait{dock=ControlDock(controller,state)}
    val smoke=if(System.getenv("COSMOSIS_UI_SMOKE")=="1") WorkspaceSmokeConfig(
        frames=(System.getenv("COSMOSIS_UI_SMOKE_FRAMES")?:"20").toIntOrNull()?.coerceIn(3,240)?:20,
        reportPath=Path.of(System.getenv("COSMOSIS_UI_SMOKE_REPORT")?:"build/ui-smoke/report.txt"),
        screenshotPath=Path.of(System.getenv("COSMOSIS_UI_SMOKE_SCREENSHOT")?:"build/ui-smoke/workstation.png")
    ) else null
    Runtime.getRuntime().addShutdownHook(Thread{runCatching{controller.close()}})
    if(legacyWorkspace) launchWorkspace(state,controller,dock,smoke) else launchFriendlyWorkspace(state,controller,smoke)
    if(smoke!=null){
        runCatching{controller.close()}
        val report=runCatching{java.nio.file.Files.readString(smoke.reportPath)}.getOrElse{"UI_SMOKE_FAIL\nreport.read.error=${it.message}"}
        check(report.lineSequence().firstOrNull()=="UI_SMOKE_PASS"){report}
    }
}
