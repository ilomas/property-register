package org.techdelivery.property.batch

import java.security.MessageDigest
import org.techdelivery.property.mongo.propertyMongoDB._
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONBinary
import reactivemongo.bson.buffer.ReadableBuffer
import reactivemongo.bson.buffer.ArrayReadableBuffer
import reactivemongo.bson.Subtype
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Success
import scala.util.Failure
import com.typesafe.scalalogging.slf4j.Logging

case class Checksum(id:Option[String], checksum: Array[Byte]) {
  def toHex(b: Array[Byte]): String = b.map{b => String.format("%02X", java.lang.Byte.valueOf(b))} mkString("")
  
  override def toString(): String = "Checksum(" + id + "," + toHex(checksum) + ")"
}

object Checksum extends Logging {
  def apply(checksum: Array[Byte]): Checksum = {
    logger.debug(checksum.toString)
    new Checksum(None, checksum)
  }
  def apply(id: String, checksum: Array[Byte]): Checksum = new Checksum(Some(id),checksum)
  def apply(list: List[String]): Checksum = {
    val md = MessageDigest.getInstance("md5")
    list.foreach { item => md.update(item.toCharArray().map(_.toByte)); md.update(Array(','.toByte)) }
    Checksum(md.digest)
  }
}

object ChecksumChecker extends Logging {
  private def col = checksumCollection
  
  implicit def toBinary(d: Array[Byte]): BSONBinary = new BSONBinary(ArrayReadableBuffer(d), Subtype.Md5Subtype)
  
  def runAfterCheck(parsedLine: List[String], f: List[String] => Unit) {
    val sum = Checksum(parsedLine)
    val cursor = col.find(BSONDocument("checksum" -> toBinary(sum.checksum))).cursor[BSONDocument].toList
    cursor onComplete {
      case Success(list) => {
        list match {
          case Nil => insertChecksum(sum, parsedLine)(f)
          case _ => logger.error("Line " + parsedLine + " already imported, ignoring")
        }
      }
      case Failure(t) => { logger.error(t.getMessage); logger.debug(t.getMessage, t)}
    }
  }
  
  private def insertChecksum(sum: Checksum, l: List[String])(f: List[String] => Unit): Unit = {
    val result = col.insert(BSONDocument("checksum" -> toBinary(sum.checksum)))
    result onComplete {
      case Success(op) => { 
        logger.info("Added checksum " + sum.checksum.map("%02X" format _).mkString)
        f(l)
      }
      case Failure(t) => { logger.error(t.getMessage); logger.debug(t.getMessage, t)}
    }
  }
}