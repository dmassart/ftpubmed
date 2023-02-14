#!/usr/bin/env amm

import $ivy.`commons-net:commons-net:3.6`
import $ivy.`commons-io:commons-io:2.11.0`

import java.io.File
import java.util.Date
import org.apache.commons.net.ftp.{FTPClient,FTPClientConfig}
import org.apache.commons.io.FileUtils
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps
import scala.util.Try

val ocolor: Option[String] = try{
  sun.management.ManagementFactoryHelper.getRuntimeMXBean.getInputArguments.isEmpty
  // runs in intellij
  Option.empty[String]
} catch{
  // runs out of intellij
  case _: java.lang.IllegalAccessError => sys.env.get("TERM").find( _ == "xterm-color" )
}

val tmpFolder: File = File("./tmp")
val localFiles: Option[List[(String,Date)]] = Try(
  FileUtils
    .listFiles( tmpFolder, null, false )
    .asScala
    .toList
    .map( file => file.getName -> Date(file.lastModified) )
    .filter( _._1.endsWith(".xml") )
    .sorted
  ).toOption
val lastLocal: Option[(String, Date)] = localFiles.flatMap( _.lastOption.map(last => s"${last._1}.gz" -> last._2 ) )

val client = new FTPClient
val config = new FTPClientConfig(FTPClientConfig.SYST_UNIX)
config.setServerTimeZoneId("UTC")
client.configure(config)

client.connect("ftp.ncbi.nlm.nih.gov")
client.login("anonymous", "")
client.enterLocalPassiveMode()

val remoteFiles =
  client
    .listFiles("/pubmed/updatefiles")
    .map( file => file.getName -> file.getTimestamp )
    .filter( _._1.endsWith(".gz") )
    .toList
    .sorted

if (remoteFiles.isEmpty)
  println( colored(Console.RED) + "No remote file available!" )
else {
  val lastRemote = remoteFiles.last
  val remoteName = lastRemote._1
  val remoteDate = lastRemote._2.getTime

  print( colored(Console.GREEN) + "\nLast remote: ")
  println( s"${colored(Console.MAGENTA)}$remoteName${colored(Console.WHITE)} ($remoteDate)" )
  lastLocal match {
    case None =>
      println( colored(Console.RED) + "No local file available!" )
    case Some((localName,localDate)) =>
      println( s"${colored(Console.GREEN)}Last local : ${colored(Console.MAGENTA)}$localName${colored(Console.WHITE)} ($localDate)" )
      val newFiles = remoteFiles.map(_._1).filter( remoteName => remoteName > localName )
      if (newFiles.isEmpty)
        println( s"${colored(Console.YELLOW)}No new remote file found!" )
      else {
        val size = newFiles.size
        if (size == 1)
          println( s"${colored(Console.YELLOW)}Found 1 new remote file: " )
        else
          println( s"${colored(Console.YELLOW)}Found $size new remote files: " )
        print(colored(Console.GREEN))
        newFiles.foreach( newFile => println( s"wget ftp://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/$newFile" ) )
        Thread.sleep(2500)
        println(colored(Console.WHITE))
        newFiles.foreach( newFile => {
          //import scala.sys.process.*
          //s"wget ftp://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/$newFile"!!
          os.proc("wget", s"ftp://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/$newFile").spawn(stderr = os.Inherit)
        })
      }
    }
  }
println(colored(Console.WHITE))

def colored(color: String) = {
  ocolor match {
    case None    => ""
    case Some(_) => color
  }
}
