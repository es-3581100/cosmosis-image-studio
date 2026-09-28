package studio.cosmosis.ui

import studio.cosmosis.StructuredPrompt
import studio.cosmosis.analysis.VisualRegion
import studio.cosmosis.image.ImageToPrompt
import studio.cosmosis.image.ImagePromptResult
import studio.cosmosis.image.PromptOutputMode
import studio.cosmosis.theme.OffworldTheme
import java.awt.*
import java.nio.file.Path
import javax.swing.*

/** Human-editable IMAGE → STRUCTURE → PROMPT workstation. */
class ImageToPromptDialog(owner:Window,private val source:Path):JDialog(owner,"COSMOSIS / IMAGE → PROMPT",ModalityType.APPLICATION_MODAL) {
    private val mode=JComboBox(PromptOutputMode.entries.toTypedArray()).apply{selectedItem=PromptOutputMode.SUBJECT_REPLACEABLE}
    private val fields=linkedMapOf<String,JTextArea>()
    private val regionModel=DefaultListModel<String>()
    private val regionList=JList(regionModel)
    private val status=JLabel("LOCAL ANALYSIS NOT RUN")
    private var latest:ImagePromptResult?=null
    var accepted=false;private set
    var saveToLibrary=false;private set
    val titleField=JTextField("Image → Prompt / ${source.fileName}")
    val pathField=JTextField("MY PROMPTS/Image to Prompt")

    init {
        background=OffworldTheme.background;layout=BorderLayout(0,0);preferredSize=Dimension(980,760)
        val top=JPanel(BorderLayout(8,8)).apply{background=OffworldTheme.background;border=BorderFactory.createEmptyBorder(13,13,13,13);add(JLabel("MODE").themed(),BorderLayout.WEST);add(mode,BorderLayout.CENTER);add(JButton("ANALYZE LOCAL").apply{addActionListener{analyze()}},BorderLayout.EAST)};add(top,BorderLayout.NORTH)
        val tabs=JTabbedPane();tabs.addTab("STRUCTURE",structurePanel());tabs.addTab("REGIONS / OCR",regionsPanel());add(tabs,BorderLayout.CENTER)
        val bottom=JPanel(BorderLayout(8,8)).apply{background=OffworldTheme.background;border=BorderFactory.createEmptyBorder(13,13,13,13);add(status,BorderLayout.WEST);val buttons=JPanel(FlowLayout(FlowLayout.RIGHT,8,0)).apply{background=OffworldTheme.background;add(JButton("CANCEL").apply{addActionListener{dispose()}});add(JButton("USE IN EDITOR").apply{addActionListener{accepted=true;dispose()}});add(JButton("SAVE + USE").apply{addActionListener{accepted=true;saveToLibrary=true;dispose()}})};add(buttons,BorderLayout.EAST)};add(bottom,BorderLayout.SOUTH)
        defaultCloseOperation=DISPOSE_ON_CLOSE;pack();setLocationRelativeTo(owner);analyze()
    }
    private fun structurePanel():JComponent {
        val content=JPanel();content.layout=BoxLayout(content,BoxLayout.Y_AXIS);content.background=OffworldTheme.background;content.border=BorderFactory.createEmptyBorder(13,13,13,13)
        content.add(row("PROMPT TITLE",titleField));content.add(row("SAVE PATH",pathField))
        listOf("SUBJECT","STYLE DNA","COMPOSITION","CAMERA","LIGHTING","COLOR","MATERIALS","TYPOGRAPHY","QUALITY","AVOID").forEach{key->val area=JTextArea(3,60).apply{lineWrap=true;wrapStyleWord=true};fields[key]=area;content.add(areaRow(key,area))}
        return JScrollPane(content)
    }
    private fun regionsPanel():JComponent {
        val p=JPanel(BorderLayout(8,8)).apply{background=OffworldTheme.background;border=BorderFactory.createEmptyBorder(13,13,13,13)}
        p.add(JLabel("Derived regions. OCR is optional and remains metadata; edit prompt fields in STRUCTURE.").themed(),BorderLayout.NORTH);regionList.font=Font(Font.MONOSPACED,Font.PLAIN,11);p.add(JScrollPane(regionList),BorderLayout.CENTER);return p
    }
    private fun analyze(){
        status.text="ANALYZING / LOCAL";runCatching{ImageToPrompt.extract(source,mode.selectedItem as PromptOutputMode)}.onSuccess{r->latest=r;load(r);status.text="LOCAL / ${r.analysis?.width}×${r.analysis?.height} / OCR ${if(r.ocrAvailable)"AVAILABLE" else "OFF"} / ${r.regions.size} REGIONS"}.onFailure{status.text="FAILED / ${it.message}"}
    }
    private fun load(r:ImagePromptResult){val s=r.structure;mapOf("SUBJECT" to s.subject,"STYLE DNA" to s.styleDna,"COMPOSITION" to s.composition,"CAMERA" to s.camera,"LIGHTING" to s.lighting,"COLOR" to s.color,"MATERIALS" to s.materials,"TYPOGRAPHY" to s.typography,"QUALITY" to s.qualityConstraints,"AVOID" to s.avoid).forEach{(k,v)->fields[k]?.text=v};regionModel.clear();r.regions.forEach{regionModel.addElement(regionLabel(it))}}
    fun structured():StructuredPrompt=StructuredPrompt(subject=t("SUBJECT"),styleDna=t("STYLE DNA"),composition=t("COMPOSITION"),camera=t("CAMERA"),lighting=t("LIGHTING"),color=t("COLOR"),materials=t("MATERIALS"),typography=t("TYPOGRAPHY"),qualityConstraints=t("QUALITY"),avoid=t("AVOID"))
    fun promptText():String=when(mode.selectedItem as PromptOutputMode){PromptOutputMode.STRUCTURED_JSON->ImageToPrompt.toJson(structured(),latest?.regions.orEmpty());else->structured().toPlainText()}
    private fun t(k:String)=fields[k]?.text.orEmpty().trim()
    private fun row(label:String,field:JComponent)=JPanel(BorderLayout(8,4)).apply{background=OffworldTheme.background;maximumSize=Dimension(Int.MAX_VALUE,58);border=BorderFactory.createEmptyBorder(0,0,8,0);add(JLabel(label).themed(),BorderLayout.NORTH);add(field,BorderLayout.CENTER)}
    private fun areaRow(label:String,area:JTextArea)=JPanel(BorderLayout(8,4)).apply{background=OffworldTheme.background;maximumSize=Dimension(Int.MAX_VALUE,105);border=BorderFactory.createEmptyBorder(0,0,13,0);add(JLabel(label).themed(),BorderLayout.NORTH);add(JScrollPane(area),BorderLayout.CENTER)}
    private fun JLabel.themed()=apply{foreground=OffworldTheme.muted;font=Font(Font.MONOSPACED,Font.PLAIN,10)}
    private fun regionLabel(r:VisualRegion)="${r.kind.uppercase()}  [${r.x},${r.y} ${r.width}×${r.height}]  ${r.confidence?.let{"${"%.0f".format(it*100)}%"}?:""}  ${r.label}"
}
