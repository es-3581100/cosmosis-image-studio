package studio.cosmosis.ui

import studio.cosmosis.*
import studio.cosmosis.workers.JobPlan
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

data class UiSnapshot(
    val projectName:String="NO PROJECT",
    val projectRoot:String="",
    val currentVersion:String="V---",
    val imagePath:String?=null,
    val imageWidth:Int=0,
    val imageHeight:Int=0,
    val comparePath:String?=null,
    val maskPath:String?=null,
    val maskOverlayPath:String?=null,
    val provider:String="OPENAI",
    val model:String="gpt-image-2.5-flare",
    val jobState:String="IDLE",
    val promptTitle:String="NEW / UNSAVED",
    val promptBody:String="",
    val versions:List<VersionNode> = emptyList(),
    val jobs:List<WorkerJob> = emptyList(),
    val plan:JobPlan?=null,
    val zoom:Double=1.0,
    val panX:Double=0.0,
    val panY:Double=0.0,
    val compareMode:String="OFF",
    val maskVisible:Boolean=true,
    val reducedMotion:Boolean=false,
    val message:String="READY / LOCAL"
)

class StudioState {
    private val ref=AtomicReference(UiSnapshot())
    private val listeners=CopyOnWriteArrayList<(UiSnapshot)->Unit>()
    fun get():UiSnapshot=ref.get()
    fun update(block:(UiSnapshot)->UiSnapshot){val next=block(ref.get());ref.set(next);listeners.forEach{it(next)}}
    fun listen(listener:(UiSnapshot)->Unit){listeners+=listener;listener(ref.get())}
}
