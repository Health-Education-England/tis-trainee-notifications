/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.event;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.DockerImageNames;
import uk.nhs.tis.trainee.notifications.model.UserAccount;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AccountEventListenerIntegrationTest {

  private static final String USER_DETAILS_QUEUE = UUID.randomUUID() + ".fifo";
  private static final String EVENT_TYPE_HEADER = "event_type";
  private static final String PRODUCER_HEADER = "producer";
  private static final String PRODUCER = "tis-trainee-user-management";

  private static final String SUB = "cognito-sub";
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TRAINEE_ID = "40";
  private static final String FORENAME = "Anthony";
  private static final String SURNAME = "Gilliam";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(
      DockerImageNames.LOCALSTACK)
      .withServices(SQS);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("application.queues.user-details", () -> USER_DETAILS_QUEUE);

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);
    registry.add("spring.flyway.enabled", () -> false);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    ExecResult result = localstack.execInContainer("awslocal", "sqs", "create-queue",
        "--queue-name", USER_DETAILS_QUEUE, "--attributes", "FifoQueue=true");

    if (result.getExitCode() != 0) {
      throw new IllegalStateException("Failed to create queue: " + result.getStderr());
    }
  }

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate mongoTemplate;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), UserAccount.class);
  }

  @Test
  void shouldInsertAccountWhenUpdateEventForNewAccount() throws JsonProcessingException {
    UUID id = UUID.randomUUID();
    final Instant start = Instant.now();

    sendEvent(id, "UPDATE", fullEventJson(id));

    UserAccount account = awaitAccount(id);
    assertThat("Unexpected sub.", account.sub(), is(SUB));
    assertThat("Unexpected email.", account.email(), is(EMAIL));
    assertThat("Unexpected trainee ID.", account.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected forename.", account.forename(), is(FORENAME));
    assertThat("Unexpected surname.", account.surname(), is(SURNAME));
    assertThat("Unexpected last modified.", account.lastModified(), notNullValue());
    assertThat("Unexpected last modified.", account.lastModified().isBefore(start), is(false));
  }

  @Test
  void shouldStoreIdAsStandardUuidWhenUpdateEvent() throws JsonProcessingException {
    UUID id = UUID.randomUUID();

    sendEvent(id, "UPDATE", fullEventJson(id));
    awaitAccount(id);

    BsonDocument document = mongoTemplate.getCollection("UserAccount")
        .withDocumentClass(BsonDocument.class)
        .find()
        .first();

    assertThat("Unexpected document.", document, notNullValue());
    assertThat("Unexpected ID binary subtype.", document.getBinary("_id").getType(),
        is(BsonBinarySubType.UUID_STANDARD.getValue()));
  }

  @Test
  void shouldReplaceAccountWhenUpdateEventForExistingAccount() throws JsonProcessingException {
    UUID id = UUID.randomUUID();
    UserAccount existing = insertAccount(new UserAccount(id, "old-sub", "old@example.com", "1",
        "Old", "Name", null));

    sendEvent(id, "UPDATE", fullEventJson(id));

    await()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          UserAccount account = mongoTemplate.findById(id, UserAccount.class);
          assertThat("Unexpected account.", account, notNullValue());
          assertThat("Unexpected email.", account.email(), is(EMAIL));
          assertThat("Unexpected sub.", account.sub(), is(SUB));
          assertThat("Unexpected trainee ID.", account.traineeId(), is(TRAINEE_ID));
          assertThat("Unexpected forename.", account.forename(), is(FORENAME));
          assertThat("Unexpected surname.", account.surname(), is(SURNAME));
          assertThat("Unexpected last modified.", account.lastModified(),
              not(existing.lastModified()));
        });
  }

  @Test
  void shouldClearFieldsWhenUpdateEventHasNullValues() throws JsonProcessingException {
    UUID id = UUID.randomUUID();
    insertAccount(new UserAccount(id, SUB, EMAIL, TRAINEE_ID, FORENAME, SURNAME, null));

    String eventJson = """
        {
          "id": "%s",
          "sub": "%s",
          "email": null,
          "traineeId": null,
          "forename": null,
          "surname": null
        }""".formatted(id, SUB);
    sendEvent(id, "UPDATE", eventJson);

    await()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          UserAccount account = mongoTemplate.findById(id, UserAccount.class);
          assertThat("Unexpected account.", account, notNullValue());
          assertThat("Unexpected email.", account.email(), nullValue());
        });

    UserAccount account = mongoTemplate.findById(id, UserAccount.class);
    assertThat("Unexpected account.", account, notNullValue());
    assertThat("Unexpected sub.", account.sub(), is(SUB));
    assertThat("Unexpected trainee ID.", account.traineeId(), nullValue());
    assertThat("Unexpected forename.", account.forename(), nullValue());
    assertThat("Unexpected surname.", account.surname(), nullValue());
  }

  @Test
  void shouldBeIdempotentWhenSameUpdateEventReceivedTwice() throws JsonProcessingException {
    UUID id = UUID.randomUUID();
    String eventJson = fullEventJson(id);

    sendEvent(id, "UPDATE", eventJson);
    UserAccount first = awaitAccount(id);

    sendEvent(id, "UPDATE", eventJson);

    await()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          UserAccount account = mongoTemplate.findById(id, UserAccount.class);
          assertThat("Unexpected account.", account, notNullValue());
          assertThat("Unexpected last modified.", account.lastModified(),
              not(first.lastModified()));
        });

    assertThat("Unexpected account count.", mongoTemplate.count(new Query(), UserAccount.class),
        is(1L));

    UserAccount second = mongoTemplate.findById(id, UserAccount.class);
    assertThat("Unexpected account.", second, notNullValue());
    assertThat("Unexpected sub.", second.sub(), is(first.sub()));
    assertThat("Unexpected email.", second.email(), is(first.email()));
    assertThat("Unexpected trainee ID.", second.traineeId(), is(first.traineeId()));
    assertThat("Unexpected forename.", second.forename(), is(first.forename()));
    assertThat("Unexpected surname.", second.surname(), is(first.surname()));
  }

  @Test
  void shouldDeleteAccountWhenDeleteEvent() throws JsonProcessingException {
    UUID id = UUID.randomUUID();
    insertAccount(new UserAccount(id, SUB, EMAIL, TRAINEE_ID, FORENAME, SURNAME, null));
    UUID otherId = UUID.randomUUID();
    insertAccount(new UserAccount(otherId, SUB, EMAIL, TRAINEE_ID, FORENAME, SURNAME, null));

    sendEvent(id, "DELETE", """
        {
          "id": "%s"
        }""".formatted(id));

    await()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat("Unexpected account.",
            mongoTemplate.findById(id, UserAccount.class), nullValue()));

    assertThat("Unexpected other account.", mongoTemplate.findById(otherId, UserAccount.class),
        notNullValue());
  }

  @Test
  void shouldNotFailWhenDeleteEventForMissingAccount() throws JsonProcessingException {
    UUID id = UUID.randomUUID();

    // A failing DELETE would block the FIFO message group, delaying the following UPDATE.
    sendEvent(id, "DELETE", """
        {
          "id": "%s"
        }""".formatted(id));
    sendEvent(id, "UPDATE", fullEventJson(id));

    UserAccount account = awaitAccount(id);
    assertThat("Unexpected email.", account.email(), is(EMAIL));
  }

  @Test
  void shouldIgnoreEventWhenEventTypeUnexpected() throws JsonProcessingException {
    UUID id = UUID.randomUUID();
    final UserAccount existing = insertAccount(new UserAccount(id, "old-sub", "old@example.com",
        "1", "Old", "Name", null));

    // Use the same message group so the unexpected event is processed before the marker event.
    sendEvent(id, "UNEXPECTED", fullEventJson(id));
    UUID markerId = UUID.randomUUID();
    sendEvent(id, "UPDATE", fullEventJson(markerId));

    awaitAccount(markerId);

    UserAccount account = mongoTemplate.findById(id, UserAccount.class);
    assertThat("Unexpected account.", account, is(existing));
  }

  /**
   * Insert a user account and read it back, so the stored audit timestamp is returned.
   *
   * @param account The account to insert.
   * @return The account as stored.
   */
  private UserAccount insertAccount(UserAccount account) {
    mongoTemplate.insert(account);
    return mongoTemplate.findById(account.id(), UserAccount.class);
  }

  /**
   * Build an UPDATE event body with all fields populated.
   *
   * @param id The account ID.
   * @return The event JSON string.
   */
  private String fullEventJson(UUID id) {
    return """
        {
          "id": "%s",
          "sub": "%s",
          "email": "%s",
          "traineeId": "%s",
          "forename": "%s",
          "surname": "%s"
        }""".formatted(id, SUB, EMAIL, TRAINEE_ID, FORENAME, SURNAME);
  }

  /**
   * Send an account event to the queue, using the account ID as the message group.
   *
   * @param groupId   The message group ID.
   * @param eventType The event type attribute.
   * @param eventJson The event body.
   */
  private void sendEvent(UUID groupId, String eventType, String eventJson)
      throws JsonProcessingException {
    JsonNode payload = JsonMapper.builder().build().readTree(eventJson);

    sqsTemplate.send(to -> to
        .queue(USER_DETAILS_QUEUE)
        .payload(payload)
        .header(EVENT_TYPE_HEADER, eventType)
        .header(PRODUCER_HEADER, PRODUCER)
        .messageGroupId(groupId.toString())
        .messageDeduplicationId(UUID.randomUUID().toString()));
  }

  /**
   * Wait for an account with the given ID to be stored.
   *
   * @param id The account ID.
   * @return The stored account.
   */
  private UserAccount awaitAccount(UUID id) {
    return await()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(10))
        .until(() -> mongoTemplate.findById(id, UserAccount.class), notNullValue());
  }
}









