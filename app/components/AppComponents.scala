package components

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import controllers.OpenForm
import com.gu.googleauth.GoogleAuthConfig
import controllers.{ Application, Auth }
import kong.{ Kong, KongClient }
import org.joda.time.Duration
import play.api.ApplicationLoader.Context
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi, MessagesApi }
import play.api.libs.ws.ning.NingWSComponents
import play.api.routing.Router
import play.api.{ BuiltInComponents, BuiltInComponentsFromContext }
import store.Dynamo
import util.AWSConstants._
import router.Routes

trait GoogleAuthComponent { self: BuiltInComponents =>

  val googleAuthConfig = {
    def missingKey(description: String) =
      sys.error(s"$description missing. You can create an OAuth 2 client from the Credentials section of the Google dev console.")
    GoogleAuthConfig(
      clientId = configuration.getString("google.clientId") getOrElse missingKey("OAuth 2 client ID"),
      clientSecret = configuration.getString("google.clientSecret") getOrElse missingKey("OAuth 2 client secret"),
      redirectUrl = configuration.getString("google.redirectUrl") getOrElse missingKey("OAuth 2 callback URL"),
      domain = Some("guardian.co.uk"),
      maxAuthAge = Some(Duration.standardDays(90)),
      enforceValidity = true
    )
  }
}

trait DynamoComponent {
  def dynamo: Dynamo
}

trait DynamoComponentImpl extends DynamoComponent { self: BuiltInComponents =>
  val dynamo = {
    val awsRegion = Regions.fromName(configuration.getString("aws.region") getOrElse "eu-west-1")
    val usersTable = configuration.getString("aws.dynamo.usersTableName") getOrElse "Bonobo-Users"
    val keysTable = configuration.getString("aws.dynamo.keysTableName") getOrElse "Bonobo-Keys"
    val client: AmazonDynamoDBClient = new AmazonDynamoDBClient(CredentialsProvider).withRegion(awsRegion)
    new Dynamo(new DynamoDB(client), usersTable, keysTable)
  }
}

trait KongComponent {
  def kong: Kong
}

trait KongComponentImpl extends KongComponent { self: BuiltInComponents with NingWSComponents =>
  val kong = {
    def confString(key: String) = configuration.getString(key) getOrElse sys.error(s"Missing configuration key: $key")
    val apiAddress = confString("kong.apiAddress")
    val apiName = confString("kong.apiName")
    new KongClient(wsClient, apiAddress, apiName)
  }
}

trait ControllersComponent { self: BuiltInComponents with NingWSComponents with GoogleAuthComponent with DynamoComponent with KongComponent =>
  def enableAuth: Boolean
  def messagesApi: MessagesApi = new DefaultMessagesApi(environment, configuration, new DefaultLangs(configuration))
  def appController = new Application(dynamo, kong, messagesApi, googleAuthConfig, enableAuth)
  def authController = new Auth(googleAuthConfig, wsApi)

  val openFormController = new OpenForm(dynamo, kong, messagesApi)
  val assets = new controllers.Assets(httpErrorHandler)
  val router: Router = new Routes(httpErrorHandler, appController, openFormController, authController, assets)
}

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with NingWSComponents
    with GoogleAuthComponent
    with DynamoComponentImpl
    with KongComponentImpl
    with ControllersComponent {
  val enableAuth = true
}