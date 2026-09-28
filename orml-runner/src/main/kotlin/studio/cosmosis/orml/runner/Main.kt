package studio.cosmosis.orml.runner

import java.nio.file.Path
import kotlin.system.exitProcess

data class CliArgs(
    val capability:String?=null,
    val input:Path?=null,
    val output:Path?=null,
    val options:Map<String,String> = emptyMap(),
    val describe:Boolean=false,
    val selfTest:Boolean=false
)

fun parseArgs(args:Array<String>):CliArgs {
    var i=0
    var capability:String?=null
    var input:Path?=null
    var output:Path?=null
    var describe=false
    var selfTest=false
    val options=linkedMapOf<String,String>()
    while(i<args.size){
        when(val a=args[i]){
            "--capability"->{require(i+1<args.size){"--capability requires a value"};capability=args[++i]}
            "--input"->{require(i+1<args.size){"--input requires a value"};input=Path.of(args[++i])}
            "--output"->{require(i+1<args.size){"--output requires a value"};output=Path.of(args[++i])}
            "--option"->{
                require(i+1<args.size){"--option requires key=value"}
                val raw=args[++i];val eq=raw.indexOf('=')
                require(eq>0){"--option requires key=value"}
                options[raw.substring(0,eq)]=raw.substring(eq+1)
            }
            "--describe"->describe=true
            "--self-test"->selfTest=true
            "--help","-h"->throw HelpRequested
            else->error("unknown argument: "+a)
        }
        i++
    }
    return CliArgs(capability,input,output,options,describe,selfTest)
}

object HelpRequested:RuntimeException()

fun usage():String = """
Cosmosis ORML runner protocol v$RUNNER_PROTOCOL_VERSION

Usage:
  orml-runner --describe
  orml-runner --self-test
  orml-runner --capability <id> --input <file> --output <file> [--option key=value]...

Capabilities:
  smart-subject-mask
  person-body-mask
  image-embedding
  super-resolution
""".trimIndent()

fun main(args:Array<String>) {
    val discovery=BackendLoader.discover()
    val engine=RunnerEngine(discovery.backends,discovery.errors)
    val cli=try{parseArgs(args)}catch(_:HelpRequested){println(usage());return}catch(t:Throwable){
        System.err.println("ERROR "+sanitize(t.message?:t.javaClass.simpleName));System.err.println(usage());exitProcess(2)
    }

    if(cli.describe){println(engine.describeJson());return}
    if(cli.selfTest){
        println(engine.describeJson())
        if(engine.errors.isNotEmpty())exitProcess(3)
        return
    }

    val capability=cli.capability
    val input=cli.input
    val output=cli.output
    if(capability==null||input==null||output==null){
        System.err.println("ERROR --capability, --input, and --output are required")
        System.err.println(usage())
        exitProcess(2)
    }

    val result=engine.execute(RunnerRequest(capability,input.toAbsolutePath(),output.toAbsolutePath(),cli.options))
    if(result.ok){
        println("OK "+sanitize(result.message))
        result.metadata.toSortedMap().forEach{(k,v)->println("META "+sanitize(k)+"="+sanitize(v))}
        exitProcess(0)
    }
    System.err.println("ERROR "+sanitize(result.message))
    exitProcess(if(result.message.startsWith("no backend installed"))3 else 4)
}
