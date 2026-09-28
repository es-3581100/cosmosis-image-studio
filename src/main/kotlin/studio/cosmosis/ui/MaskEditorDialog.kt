package studio.cosmosis.ui

import studio.cosmosis.mask.MaskDocument
import studio.cosmosis.mask.MaskStroke
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.min

class MaskEditorDialog(owner:Window?, imagePath:Path):JDialog(owner,"MASK / MANUAL",ModalityType.APPLICATION_MODAL) {
    private val source=requireNotNull(ImageIO.read(imagePath.toFile())){"Unsupported image"}
    private val doc=MaskDocument(source.width,source.height)
    private var brush=42; private var erase=false; var accepted=false; private set
    private val canvas=object:JPanel(){
        init{preferredSize=Dimension(900,650);background=Color(0x10,0x10,0x0E)}
        override fun paintComponent(g0:Graphics){super.paintComponent(g0);val g=g0 as Graphics2D;g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);val scale=min(width.toDouble()/source.width,height.toDouble()/source.height);val dw=(source.width*scale).toInt();val dh=(source.height*scale).toInt();val ox=(width-dw)/2;val oy=(height-dh)/2;g.drawImage(source,ox,oy,dw,dh,null);val mask=doc.toBufferedImage();val tint=BufferedImage(mask.width,mask.height,BufferedImage.TYPE_INT_ARGB);for(y in 0 until mask.height step 2)for(x in 0 until mask.width step 2){val a=(mask.raster.getSample(x,y,0)*0.45).toInt().coerceIn(0,115);val rgb=(a shl 24) or (0xEF shl 16) or (0x44 shl 8) or 0x44;tint.setRGB(x,y,rgb);if(x+1<tint.width)tint.setRGB(x+1,y,rgb);if(y+1<tint.height){tint.setRGB(x,y+1,rgb);if(x+1<tint.width)tint.setRGB(x+1,y+1,rgb)}};g.drawImage(tint,ox,oy,dw,dh,null);g.color=Color(255,255,227,180);g.drawRect(ox,oy,dw-1,dh-1)}
        fun map(e:MouseEvent):Pair<Int,Int>{val scale=min(width.toDouble()/source.width,height.toDouble()/source.height);val dw=(source.width*scale).toInt();val dh=(source.height*scale).toInt();val ox=(width-dw)/2;val oy=(height-dh)/2;return (((e.x-ox)/scale).toInt().coerceIn(0,source.width-1)) to (((e.y-oy)/scale).toInt().coerceIn(0,source.height-1))}
    }
    private var last:Pair<Int,Int>?=null
    init{
        contentPane.background=Color(0x10,0x10,0x0E);layout=BorderLayout();add(canvas,BorderLayout.CENTER)
        val tools=JPanel(FlowLayout(FlowLayout.LEFT,8,8)).apply{background=Color(0x15,0x15,0x11)}
        fun btn(t:String,run:()->Unit)=JButton(t).apply{isFocusPainted=false;background=Color(0x1C,0x1C,0x18);foreground=Color(0xFF,0xFF,0xE3);border=BorderFactory.createLineBorder(Color(0x60,0x60,0x55));addActionListener{run()};tools.add(this)}
        btn("BRUSH"){erase=false};btn("ERASE"){erase=true};btn("[ smaller"){brush=(brush-8).coerceAtLeast(2)};btn("] larger"){brush=(brush+8).coerceAtMost(180)};btn("UNDO"){doc.undo();canvas.repaint()};btn("REDO"){doc.redo();canvas.repaint()};btn("INVERT"){doc.invert();canvas.repaint()};btn("FEATHER 4"){doc.feather(4);canvas.repaint()};btn("CLEAR"){doc.clear();canvas.repaint()};btn("CANCEL"){dispose()};btn("USE MASK"){accepted=true;dispose()};add(tools,BorderLayout.NORTH)
        val mouse=object:MouseAdapter(){override fun mousePressed(e:MouseEvent){last=canvas.map(e)};override fun mouseReleased(e:MouseEvent){last=null};override fun mouseDragged(e:MouseEvent){val cur=canvas.map(e);val prev=last?:cur;doc.apply(MaskStroke(prev.first,prev.second,cur.first,cur.second,brush,erase));last=cur;canvas.repaint()}}

        val input=canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);val actions=canvas.actionMap
        fun key(stroke:String,name:String,run:()->Unit){input.put(KeyStroke.getKeyStroke(stroke),name);actions.put(name,object:AbstractAction(){override fun actionPerformed(e:java.awt.event.ActionEvent?){run();canvas.repaint()}})}
        key("OPEN_BRACKET","brush-smaller"){brush=(brush-8).coerceAtLeast(2)};key("CLOSE_BRACKET","brush-larger"){brush=(brush+8).coerceAtMost(180)};key("X","toggle-erase"){erase=!erase};key("control Z","undo"){doc.undo()};key("control Y","redo"){doc.redo()}
        canvas.addMouseListener(mouse);canvas.addMouseMotionListener(mouse);pack();setLocationRelativeTo(owner)
    }
    fun export(path:Path){doc.save(path)}
    fun maskDocument()=doc
}
