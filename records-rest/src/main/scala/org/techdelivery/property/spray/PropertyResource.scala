package org.techdelivery.property.spray

import akka.actor.{ActorLogging, Actor}
import spray.http.{HttpBody, HttpResponse, HttpRequest}
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import scala.concurrent.ExecutionContext.Implicits.global
import reactivemongo.bson.{BSONObjectID, BSONDocument}
import reactivemongo.api.collections.default.BSONCollection
import spray.json._
import scala.util.{Failure, Success}
import org.techdelivery.property.entity._
import RecordMapper._
import RegistryRecordProtocol._

class PropertyResource(collection: BSONCollection) extends Actor with ActorLogging {
  val get_rx = "^\\/property\\/(\\w*)$".r

  def receive= {
    case HttpRequest(GET, path, _, _, _) => {
      val origin = sender
      path match {
        case get_rx(id) => {
          try {
            val filter = BSONDocument( "_id" -> BSONObjectID(id))
            val result = collection.find(filter).cursor[MongoRegistryRecord]
            val response = result.toList
            response onComplete {
              case Success(list) => {
                list match {
                  case record :: Nil => origin ! HttpResponse( status = 200, entity = HttpBody(`application/json`,record.toJson.toString) )
                  case record :: xs => log.error("Get " + collection.name + "[" + id + "] returned " + list.size + " matches instead of 1")
                  case Nil => origin ! HttpResponse( status = 404 )
                }
              }
              case Failure(f) => origin ! HttpResponse( status = 503, entity = f.getMessage)
            }
          } catch {
            case iae: IllegalArgumentException => origin ! HttpResponse(status = 404)
            case e: Exception => { log.warning(e.getMessage) ; log.debug(e.getMessage, e) ; origin ! HttpResponse(status = 500) }
          }
        }
        case _ => origin ! HttpResponse(status = 404)
      }
    }
    case _ => sender ! HttpResponse(status = 404)

  }
}
