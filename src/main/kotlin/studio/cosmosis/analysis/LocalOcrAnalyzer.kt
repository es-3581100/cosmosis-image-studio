package studio.cosmosis.analysis

import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class VisualRegion(
    val kind:String,
    val label:String,
    val x:Int,
    val y:Int,
    val width:Int,
    val height:Int,
    val confidence:Double?=null
)

data class OcrAnalysis(val available:Boolean,val regions:List<VisualRegion>,val text:String,val message:String)

/** Optional local OCR bridge. No OCR dependency is required at runtime. */
object LocalOcrAnalyzer {
    fun analyze(path:Path,timeoutSeconds:Long=20):OcrAnalysis {
        val command=findTesseract() ?: return OcrAnalysis(false,emptyList(),"","tesseract not found")
        return runCatching {
            val p=ProcessBuilder(command,path.toAbsolutePath().toString(),"stdout","--psm","11","tsv").redirectErrorStream(true).start()
            if(!p.waitFor(timeoutSeconds,TimeUnit.SECONDS)){p.destroyForcibly();return OcrAnalysis(true,emptyList(),"","OCR timed out")}
            val raw=p.inputStream.bufferedReader().use{it.readText()}
            if(p.exitValue()!=0) return OcrAnalysis(true,emptyList(),"","OCR failed: ${raw.take(200)}")
            val regions=raw.lineSequence().drop(1).mapNotNull{line->
                val c=line.split('\t',limit=12);if(c.size<12)return@mapNotNull null
                val level=c[0].toIntOrNull()?:return@mapNotNull null;if(level!=5)return@mapNotNull null
                val conf=c[10].toDoubleOrNull()?:-1.0;val text=c[11].trim();if(text.isBlank()||conf<20)return@mapNotNull null
                VisualRegion("ocr",text,c[6].toIntOrNull()?:0,c[7].toIntOrNull()?:0,c[8].toIntOrNull()?:0,c[9].toIntOrNull()?:0,conf/100.0)
            }.toList()
            OcrAnalysis(true,regions,regions.joinToString(" "){it.label},"OCR complete / ${regions.size} word regions")
        }.getOrElse{OcrAnalysis(true,emptyList(),"","OCR unavailable: ${it.message}")}
    }
    private fun findTesseract():String? {
        val path=System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator).map{Path.of(it,"tesseract")}.firstOrNull{java.nio.file.Files.isExecutable(it)}
        return path?.toString()
    }
}
