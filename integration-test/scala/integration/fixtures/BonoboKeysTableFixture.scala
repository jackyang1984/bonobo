package integration.fixtures

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.{ BeforeAndAfterAll, Suite }
import scala.collection.JavaConverters._

trait BonoboKeysTableFixture extends DynamoDbClientFixture with BeforeAndAfterAll { this: Suite =>

  val keysTableName = randomTableName("integration-test-bonobo-keys")

  override def beforeAll() {
    val attributeDefinitions: List[AttributeDefinition] = List(
      new AttributeDefinition().withAttributeName("hashkey").withAttributeType("S"),
      new AttributeDefinition().withAttributeName("rangekey").withAttributeType("S"),
      new AttributeDefinition().withAttributeName("keyValue").withAttributeType("S")
    )

    val keySchema: List[KeySchemaElement] = List(
      new KeySchemaElement().withAttributeName("hashkey").withKeyType(KeyType.HASH),
      new KeySchemaElement().withAttributeName("rangekey").withKeyType(KeyType.RANGE)
    )
    val indexKeySchema: List[KeySchemaElement] = new KeySchemaElement().withAttributeName("keyValue").withKeyType(KeyType.HASH) :: Nil

    val createTableRequest: CreateTableRequest = new CreateTableRequest()
      .withTableName(keysTableName)
      .withKeySchema(keySchema.asJava)
      .withAttributeDefinitions(attributeDefinitions.asJava)
      .withProvisionedThroughput(new ProvisionedThroughput()
        .withReadCapacityUnits(100L)
        .withWriteCapacityUnits(100L))
      .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
        .withIndexName("keyValue-index")
        .withKeySchema(indexKeySchema.asJava)
        .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY))
        .withProvisionedThroughput(new ProvisionedThroughput()
          .withReadCapacityUnits(5L)
          .withWriteCapacityUnits(5L)))

    println(s"Creating keys table $keysTableName")
    dynamoClient.createTable(createTableRequest)
    waitForTableToBecomeActive(keysTableName)

    super.beforeAll()
  }

  override def afterAll() {
    try super.afterAll()
    finally {
      println(s"Deleting keys table $keysTableName")
      dynamoClient.deleteTable(keysTableName)
    }
  }
}
