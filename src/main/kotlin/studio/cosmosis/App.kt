package studio.cosmosis

import studio.cosmosis.ui.*
import javax.swing.SwingUtilities

fun main(){
    val state=StudioState();val controller=StudioController(state);lateinit var dock:ControlDock
    SwingUtilities.invokeAndWait{dock=ControlDock(controller,state)}
    Runtime.getRuntime().addShutdownHook(Thread{runCatching{controller.close()}})
    launchWorkspace(state,controller,dock)
}
