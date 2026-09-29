package studio.cosmosis

import studio.cosmosis.ui.*
import java.nio.file.Path
import javax.swing.SwingUtilities

fun main(){
    val state=StudioState();val controller=StudioController(state);lateinit var dock:ControlDock
    val smoke=if(System.getenv("COSMOSIS_UI_SMOKE")=="1") WorkspaceSmokeConfig(
        frames=(System.getenv("COSMOSIS_UI_SMOKE_FRAMES")?:"20").toIntOrNull()?.coerceIn(3,240)?:20,
        reportPath=Path.of(System.getenv("COSMOSIS_UI_SMOKE_REPORT")?:"build/ui-smoke/report.txt"),
        screenshotPath=Path.of(System.getenv("COSMOSIS_UI_SMOKE_SCREENSHOT")?:"build/ui-smoke/workstation.png")
    ) else null
    SwingUtilities.invokeAndWait{dock=ControlDock(controller,state)}
    Runtime.getRuntime().addShutdownHook(Thread{runCatching{controller.close()}})
    launchWorkspace(state,controller,dock,smoke)
    if(smoke!=null)runCatching{controller.close()}
}
