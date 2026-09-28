package studio.cosmosis.ui

import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.math.Vector2
import studio.cosmosis.JobState
import studio.cosmosis.lineage.VersionGraphLayout
import studio.cosmosis.theme.OffworldTheme
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.math.min

private fun java.awt.Color.toOrColor()=ColorRGBa(red/255.0,green/255.0,blue/255.0,alpha/255.0)
private val BG=OffworldTheme.background.toOrColor()
private val FG=OffworldTheme.foreground.toOrColor()
private val MUTED=OffworldTheme.muted.toOrColor()
private val SECOND=OffworldTheme.secondary.toOrColor()
private val GREEN=OffworldTheme.positive.toOrColor()
private val RED=OffworldTheme.destructive.toOrColor()
private val SURFACE=ColorRGBa(21/255.0,21/255.0,17/255.0,1.0)

fun launchWorkspace(state:StudioState,controller:StudioController,dock:ControlDock)=application {
    configure { width=1480;height=900;title="COSMOSIS / IMAGE STUDIO" }
    program {
        val monoPath=listOf("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf").firstOrNull{Files.exists(Path.of(it))}
        val serifPath=listOf("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf","/usr/share/fonts/truetype/liberation2/LiberationSerif-Regular.ttf").firstOrNull{Files.exists(Path.of(it))}
        val mono=monoPath?.let{loadFont(it,13.0)};val monoSmall=monoPath?.let{loadFont(it,10.0)};val serif=serifPath?.let{loadFont(it,24.0)}
        var loadedPath:String?=null;var image:ColorBuffer?=null;var compareLoaded:String?=null;var compareImage:ColorBuffer?=null;var maskLoaded:String?=null;var mask:ColorBuffer?=null
        var pan=Vector2.ZERO;var zoom=1.0;var lastDrag:Vector2?=null
        window.drop.listen { dropped -> dropped.files.firstOrNull{File(it).extension.lowercase() in listOf("png","jpg","jpeg","webp") }?.let{runCatching{controller.importImage(Path.of(it))}} }
        mouse.scrolled.listen { zoom=(zoom*(if(it.rotation.y<0)1.1 else .9)).coerceIn(.1,8.0);state.update{s->s.copy(zoom=zoom)} }
        mouse.buttonDown.listen { lastDrag=it.position }
        mouse.buttonUp.listen { lastDrag=null }
        mouse.dragged.listen { val prev=lastDrag?:it.position;pan += it.position-prev;lastDrag=it.position;state.update{s->s.copy(panX=pan.x,panY=pan.y)} }
        keyboard.keyDown.listen { ev -> val ctrl=ev.modifiers.any { it.name=="CTRL" || it.name=="META" || it.name=="SUPER" };when {
            ctrl && ev.name=="k" -> SwingUtilities.invokeLater{dock.openCommandPalette()}
            ctrl && ev.name=="enter" -> { val s=state.get();if(s.promptBody.isNotBlank())runCatching{controller.generate(s.promptBody,s.provider.lowercase(),s.model)} }
            ev.name=="g" -> state.update{it.copy(message="MODE / GENERATE")}
            ev.name=="e" -> state.update{it.copy(message="MODE / EDIT")}
            ev.name=="m" -> SwingUtilities.invokeLater{dock.toFront()}
            ev.name=="c" -> state.update{it.copy(compareMode=if(it.compareMode=="OFF")"SPLIT" else "OFF",message="COMPARE / ${if(it.compareMode=="OFF")"SPLIT" else "OFF"}")}
            ev.name=="0" -> {pan=Vector2.ZERO;zoom=1.0;controller.resetView()}
        } }
        extend {
            val s=state.get();drawer.clear(BG)
            // quiet technical grid
            drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.035);drawer.strokeWeight=1.0
            for(x in 82 until width-300 step 34)drawer.lineSegment(x.toDouble(),72.0,x.toDouble(),height-170.0)
            for(y in 72 until height-170 step 34)drawer.lineSegment(82.0,y.toDouble(),(width-300).toDouble(),y.toDouble())
            // workstation planes
            drawer.fill=SURFACE;drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.12);drawer.rectangle(0.0,0.0,width.toDouble(),72.0);drawer.rectangle(0.0,72.0,82.0,(height-72).toDouble());drawer.rectangle((width-300).toDouble(),72.0,300.0,(height-72).toDouble());drawer.rectangle(82.0,(height-170).toDouble(),(width-382).toDouble(),120.0);drawer.rectangle(82.0,(height-50).toDouble(),(width-82).toDouble(),50.0)
            if(mono!=null){drawer.fontMap=mono;drawer.fill=FG;drawer.text("COSMOSIS / IMAGE STUDIO",21.0,30.0);drawer.fontMap=monoSmall!!;drawer.fill=MUTED;drawer.text("${s.projectName}  /  ${s.currentVersion}",21.0,52.0);drawer.text("${s.provider} / ${s.model}",(width-560).toDouble(),30.0);drawer.fill=if(s.jobState=="FAILED")RED else if(s.jobState=="COMPLETE")GREEN else FG;drawer.text("JOB ${s.jobState}",(width-180).toDouble(),30.0)}
            val tools=listOf("SEL","MASK","CROP","AI","FIT","CMP");if(monoSmall!=null){drawer.fontMap=monoSmall;tools.forEachIndexed{i,t->drawer.fill=if(i==0)FG else MUTED;drawer.text(t,23.0,118.0+i*54)}}
            // image field / split compare instrument
            s.imagePath?.let { p ->
                if(p!=loadedPath){runCatching{image?.destroy();image=loadImage(p);loadedPath=p}}
                image?.let{img->
                    val fieldX=103.0;val fieldY=93.0;val fieldW=width-424.0;val fieldH=height-290.0
                    val comparePath=s.comparePath.takeIf{s.compareMode=="SPLIT"}
                    if(comparePath!=null){
                        if(comparePath!=compareLoaded){runCatching{compareImage?.destroy();compareImage=loadImage(comparePath);compareLoaded=comparePath}}
                        val gap=13.0;val half=(fieldW-gap)/2.0
                        val fitA=min(half/img.width,fieldH/img.height);val aw=img.width*fitA*zoom;val ah=img.height*fitA*zoom;val ax=fieldX+(half-aw)/2+pan.x;val ay=fieldY+(fieldH-ah)/2+pan.y
                        drawer.image(img,ax,ay,aw,ah)
                        compareImage?.let{cmp->val fitB=min(half/cmp.width,fieldH/cmp.height);val bw=cmp.width*fitB*zoom;val bh=cmp.height*fitB*zoom;val bx=fieldX+half+gap+(half-bw)/2+pan.x;val by=fieldY+(fieldH-bh)/2+pan.y;drawer.image(cmp,bx,by,bw,bh)}
                        drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.18);drawer.lineSegment(fieldX+half+gap/2,fieldY,fieldX+half+gap/2,fieldY+fieldH)
                        if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("CURRENT",fieldX,fieldY+14);drawer.text("COMPARE",fieldX+half+gap,fieldY+14)}
                        Unit
                    } else {
                        val fit=min(fieldW/img.width,fieldH/img.height);val dw=img.width*fit*zoom;val dh=img.height*fit*zoom;val x=fieldX+(fieldW-dw)/2+pan.x;val y=fieldY+(fieldH-dh)/2+pan.y
                        drawer.image(img,x,y,dw,dh);drawer.fill=null;drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.55);val m=12.0;drawer.lineSegment(x,y,x+m,y);drawer.lineSegment(x,y,x,y+m);drawer.lineSegment(x+dw,y,x+dw-m,y);drawer.lineSegment(x+dw,y,x+dw,y+m);drawer.lineSegment(x,y+dh,x+m,y+dh);drawer.lineSegment(x,y+dh,x,y+dh-m);drawer.lineSegment(x+dw,y+dh,x+dw-m,y+dh);drawer.lineSegment(x+dw,y+dh,x+dw,y+dh-m)
                        s.maskOverlayPath?.takeIf{s.maskVisible}?.let{mp->if(mp!=maskLoaded){runCatching{mask?.destroy();mask=loadImage(mp);maskLoaded=mp}};mask?.let{drawer.image(it,x,y,dw,dh)}}
                        Unit
                    }
                }
            } ?: run {
                if(serif!=null){drawer.fontMap=serif;drawer.fill=FG;drawer.text("No image loaded",width/2.0-100,height/2.0-15)}
                if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("DROP IMAGE HERE  /  or use Control → Import Image",width/2.0-150,height/2.0+15)}
            }
            // inspector
            if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("// INSPECTOR",(width-278).toDouble(),105.0);drawer.fill=FG;drawer.text("PROMPT",(width-278).toDouble(),140.0);drawer.fill=MUTED;val preview=s.promptBody.replace('\n',' ').take(220);preview.chunked(34).take(6).forEachIndexed{i,line->drawer.text(line,(width-278).toDouble(),164.0+i*16)};drawer.fill=FG;drawer.text("REFERENCES",(width-278).toDouble(),285.0);drawer.fill=MUTED;drawer.text(if(s.imagePath==null)"none" else "1 current image",(width-278).toDouble(),307.0);drawer.fill=FG;drawer.text("MASK",(width-278).toDouble(),350.0);drawer.fill=if(s.maskPath==null)MUTED else GREEN;drawer.text(if(s.maskPath==null)"○ OFF" else "● ACTIVE",(width-278).toDouble(),372.0);drawer.fill=FG;drawer.text("WORKERS",(width-278).toDouble(),420.0);s.jobs.takeLast(8).forEachIndexed{i,j->drawer.fill=if(j.state==JobState.COMPLETE)GREEN else if(j.state==JobState.FAILED)RED else MUTED;drawer.text("${j.type.take(16)}  ${j.state}",(width-278).toDouble(),444.0+i*18)} }
            // branching lineage instrument: geometry communicates branches instead of color.
            if(monoSmall!=null){
                drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("// VERSION GRAPH",103.0,(height-145).toDouble())
                val nodes=s.versions.takeLast(48)
                val layout=VersionGraphLayout.layout(nodes)
                val byId=layout.associateBy{it.id}
                val gx0=110.0;val gx1=(width-338).toDouble();val gy0=(height-128).toDouble();val gy1=(height-67).toDouble()
                drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.22);drawer.strokeWeight=1.0
                layout.forEach{pt->pt.parentId?.let{pid->byId[pid]?.let{parent->
                    val px=gx0+(gx1-gx0)*parent.x;val py=gy0+(gy1-gy0)*parent.y
                    val cx=gx0+(gx1-gx0)*pt.x;val cy=gy0+(gy1-gy0)*pt.y
                    val elbow=(px+cx)/2.0;drawer.lineSegment(px,py,elbow,py);drawer.lineSegment(elbow,py,elbow,cy);drawer.lineSegment(elbow,cy,cx,cy)
                }}}
                layout.forEach{pt->val x=gx0+(gx1-gx0)*pt.x;val y=gy0+(gy1-gy0)*pt.y;val active=pt.id==s.currentVersion;drawer.stroke=if(active)FG else ColorRGBa(1.0,1.0,227/255.0,.28);drawer.fill=if(active)FG else SURFACE;drawer.rectangle(x-4,y-4,8.0,8.0);if(active){drawer.fill=FG;drawer.text(pt.id.takeLast(6),x+8,y+4)}}
            }
            if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("${s.imageWidth}×${s.imageHeight}  │  ${"%.0f".format(zoom*100)}%  │  MASK ${if(s.maskPath==null)"off" else "on"}  │  ${s.message}",103.0,(height-20).toDouble())}
        }
    }
}
