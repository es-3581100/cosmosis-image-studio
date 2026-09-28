package studio.cosmosis.provider

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.UUID

class HttpSupport(private val client:HttpClient=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
    fun json(method:String,url:String,headers:Map<String,String>,body:String?=null,timeoutSeconds:Long=180):Pair<Int,String> {
        val b=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(timeoutSeconds)).header("Content-Type","application/json")
        headers.forEach(b::header)
        if(method=="GET") b.GET() else b.method(method,HttpRequest.BodyPublishers.ofString(body?:"",StandardCharsets.UTF_8))
        val r=client.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); return r.statusCode() to r.body()
    }
    fun multipart(url:String,headers:Map<String,String>,fields:Map<String,String>,files:List<Triple<String,ReferenceImage,String>>,timeoutSeconds:Long=180):Pair<Int,String> {
        val boundary="offworld-${UUID.randomUUID()}"; val chunks=mutableListOf<ByteArray>()
        fun text(s:String){chunks += s.toByteArray(StandardCharsets.UTF_8)}
        fields.forEach { (k,v)-> text("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n") }
        files.forEach { (field,ref,name) -> text("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$name\"\r\nContent-Type: ${ref.mime}\r\n\r\n"); chunks += Files.readAllBytes(ref.path); text("\r\n") }
        text("--$boundary--\r\n")
        val total=chunks.sumOf{it.size}; val bytes=ByteArray(total); var off=0; chunks.forEach{System.arraycopy(it,0,bytes,off,it.size);off+=it.size}
        val b=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(timeoutSeconds)).header("Content-Type","multipart/form-data; boundary=$boundary")
        headers.forEach(b::header); b.POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
        val r=client.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); return r.statusCode() to r.body()
    }
    fun dataUri(ref:ReferenceImage):String="data:${ref.mime};base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(ref.path))
}
