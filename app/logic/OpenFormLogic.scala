package logic

import controllers.Forms.{ OpenCreateKeyFormData, CreateKeyFormData, CreateUserFormData, EditKeyFormData }
import kong.Kong
import kong.Kong.{ ConflictFailure, Happy }
import models._
import store.DB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OpenFormLogic(dynamo: DB, kong: Kong) {

  /**
   * Creates a consumer and key on Kong and a Bonobo user,
   * and saves the user and key in Dynamo.
   * The key will be randomly generated, the tier is Developer
   * and the default rate limits are being used.
   *
   * @return a Future of the newly created Kong consumer's ID
   */
  def createUser(form: OpenCreateKeyFormData): Future[String] = {
    def saveUserAndKeyOnDB(consumer: ConsumerCreationResult, formData: OpenCreateKeyFormData): Unit = {
      val newBonoboUser = BonoboUser(consumer.id, formData)
      dynamo.saveBonoboUser(newBonoboUser)

      val newKongKey = KongKey(consumer.id, consumer, Developer.rateLimit, Developer)
      dynamo.saveKongKey(newKongKey)
    }

    if (dynamo.getKeyForEmail(form.email).isDefined)
      Future.failed(ConflictFailure("Email already taken. You cannot have more than one key associated with an email."))
    else {
      kong.createConsumerAndKey(Developer, Developer.rateLimit, key = None) map {
        consumer =>
          saveUserAndKeyOnDB(consumer, form)
          consumer.id
      }
    }
  }
}