package studio.cosmosis.export

import studio.cosmosis.*
import studio.cosmosis.lineage.VersionGraph
import studio.cosmosis.security.Redaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

object HtmlReportExporter {
    fun projectReport(project:ProjectRecord,versions:List<VersionNode>,generations:List<GenerationRecord>,output:Path,embedImages:Map<String,Pair<String,ByteArray>> = emptyMap()) {
        val graph=VersionGraph(versions); val body=buildString {
            append("<section id='project'><div class='k'>PROJECT</div><h1>${h(project.name)}</h1><p>${h(project.id)} · ${h(project.updatedAt)}</p></section>")
            append("<section id='lineage'><div class='k'>LINEAGE</div><h2>Version graph</h2><pre>")
            graph.roots().forEach { root -> appendTree(this,graph,root,0) }; append("</pre></section>")
            append("<section id='generations'><div class='k'>GENERATIONS</div><h2>Generation ledger</h2>")
            generations.forEach { g -> append("<article><h3>${h(g.id)} / ${h(g.providerId)} / ${h(g.model)}</h3><p class='serif'>${h(g.userPrompt)}</p><details><summary>compiled prompt</summary><pre>${h(g.compiledPrompt)}</pre></details><dl><dt>duration</dt><dd>${g.durationMs}ms</dd><dt>retries</dt><dd>${g.retryCount}</dd><dt>endpoint</dt><dd>${h(g.endpoint)}</dd><dt>mask</dt><dd>${h(g.maskId?:"none")}</dd><dt>outputs</dt><dd>${g.outputImageIds.size}</dd><dt>provider context</dt><dd>${h(g.providerContextId?:"none")}</dd></dl></article>") }
            append("</section>")
            if(embedImages.isNotEmpty()){append("<section id='contact-sheet'><div class='k'>CONTACT SHEET</div><h2>Embedded results</h2><div class='sheet'>");embedImages.forEach{(name,pair)->append("<figure><img alt='${h(name)}' src='data:${pair.first};base64,${Base64.getEncoder().encodeToString(pair.second)}'><figcaption>${h(name)}</figcaption></figure>")};append("</div></section>")}
        }
        Files.createDirectories(output.parent); Files.writeString(output,template("${project.name} / project report",Redaction.sanitize(body)))
    }
    fun generationComparison(title:String,items:List<Triple<String,String,ByteArray>>,output:Path){val body=buildString{append("<section><div class='k'>COMPARISON</div><h1>${h(title)}</h1><div class='sheet'>");items.forEach{(name,mime,bytes)->append("<figure><img src='data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}' alt='${h(name)}'><figcaption>${h(name)}</figcaption></figure>")};append("</div></section>")};Files.writeString(output,template(title,body))}
    private fun appendTree(sb:StringBuilder,g:VersionGraph,n:VersionNode,depth:Int){sb.append("  ".repeat(depth)).append(if(depth==0)"" else "└── ").append(h(n.id)).append("  ").append(h(n.name)).append("\n");g.children(n.id).forEach{appendTree(sb,g,it,depth+1)}}
    fun sanitizeHtmlText(s:String)=Redaction.sanitize(h(s))
    private fun h(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun template(title:String,body:String)="""<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\"><title>${h(title)}</title><style>:root{color-scheme:dark;--bg:#10100E;--fg:#FFFFE3;--muted:#8C8C7D;--line:rgba(255,255,227,.18);--good:#22C55E;--bad:#EF4444}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:14px ui-monospace,'Cascadia Code','JetBrains Mono',monospace}main{max-width:1180px;margin:auto;padding:34px}section{padding:34px 0;border-bottom:1px solid var(--line)}h1,h2,p.serif{font-family:Georgia,'Times New Roman',serif;font-weight:400}h1{font-size:42px}.k{color:var(--muted);letter-spacing:.18em;font-size:11px}.sheet{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:21px}figure{margin:0;border-top:1px solid var(--line);padding-top:13px}img{max-width:100%;display:block}figcaption{padding:8px 0;color:var(--muted)}article{padding:21px 0;border-top:1px solid var(--line)}dl{display:grid;grid-template-columns:120px 1fr;gap:5px 13px}dt{color:var(--muted)}pre{white-space:pre-wrap;line-height:1.6}</style></head><body><main>$body</main></body></html>"""
}
