package org.techdelivery.property.batch

import scala.io.Source
import scala.io.Codec
import org.techdelivery.property.batch.parser.csvParser
import akka.actor.{PoisonPill,Props}
import org.techdelivery.property.entity.{RegistryRecordLineParser, RegistryRecord}
import org.techdelivery.property.mongo.MongoImporter
import org.techdelivery.property.mongo.propertyMongoDB._

object ImporterApp extends App {
  
  val system = actorSystem
  
  val importer = system.actorOf(Props(new MongoImporter(propertyCollection)))
  
  args.foreach { importRegistries(_) }

  system.shutdown()
  
  //val registerLine: List[String] => Unit = (line) => { importer !  RegistryRecordLineParser(line) }
  
  def registerLine(line: List[String]): Unit = { importer !  RegistryRecordLineParser(line) }
  
  def importRegistries(file: String) = {
    val lines = Source.fromFile(file)(Codec.ISO8859).getLines().drop(1)
    val records = lines.map(csvParser.parse(_)(0))
    records.foreach { line =>
      val csum = Checksum(line)
      ChecksumChecker.runAfterCheck(line, registerLine )
    }
  }
  
}