/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.PENDING;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_SUBMITTED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.FORMR_PARTA;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.DockerImageNames;
import uk.nhs.tis.trainee.notifications.model.History;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProfileMoveListenerIntegrationTest {

  private static final String FROM_TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TO_TRAINEE_ID = UUID.randomUUID().toString();

  private static final String PROFILE_MOVE_QUEUE = UUID.randomUUID().toString();

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
    registry.add("application.queues.profile-move", () -> PROFILE_MOVE_QUEUE);

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
    localstack.execInContainer("awslocal sqs create-queue --queue-name",
        PROFILE_MOVE_QUEUE);
  }

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate mongoTemplate;

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), History.class);
  }

  @Test
  void shouldMoveAllNotificationHistoriesWhenProfileMove() throws JsonProcessingException {
    ObjectId id1 = ObjectId.get();
    History.RecipientInfo recipient1 = new History.RecipientInfo(FROM_TRAINEE_ID, EMAIL,
        "from@email.com");
    History historyToMove1 = History.builder()
        .id(id1)
        .recipient(recipient1)
        .status(SENT)
        .type(PROGRAMME_DAY_ONE)
        .tisReference(new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, "123"))
        .build();
    mongoTemplate.insert(historyToMove1);

    ObjectId id2 = ObjectId.get();
    History.RecipientInfo recipient2 = new History.RecipientInfo(FROM_TRAINEE_ID, EMAIL,
        "from@email.com.x");
    History historyToMove2 = History.builder()
        .id(id2)
        .recipient(recipient2)
        .status(PENDING)
        .type(FORM_SUBMITTED)
        .tisReference(new History.TisReferenceInfo(FORMR_PARTA, "1234"))
        .build();
    mongoTemplate.insert(historyToMove2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("recipient.id").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected moved history count.", found.size(),
              is(2));
          histories.addAll(found);
        });

    History movedHistory1 = histories.stream()
        .filter(a -> a.id().equals(id1))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected moved history trainee.", movedHistory1.recipient().id(),
        is(TO_TRAINEE_ID));
    History.RecipientInfo expectedRecipient1 = new History.RecipientInfo(TO_TRAINEE_ID,
        recipient1.type(), recipient1.contact());
    assertThat("Unexpected moved history data.", historyToMove1.withRecipient(expectedRecipient1),
        is(movedHistory1));

    History movedHistory2 = histories.stream()
        .filter(a -> a.id().equals(id2))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected moved history trainee.", movedHistory2.recipient().id(),
        is(TO_TRAINEE_ID));
    History.RecipientInfo expectedRecipient2 = new History.RecipientInfo(TO_TRAINEE_ID,
        recipient2.type(), recipient2.contact());
    assertThat("Unexpected moved history data.", historyToMove2.withRecipient(expectedRecipient2),
        is(movedHistory2));
  }

  @Test
  void shouldNotMoveUnexpectedHistoriesWhenProfileMove() throws JsonProcessingException {
    ObjectId id1 = ObjectId.get();
    History.RecipientInfo recipient1 = new History.RecipientInfo(TO_TRAINEE_ID, EMAIL,
        "to@email.com");
    History historyToMove1 = History.builder()
        .id(id1)
        .recipient(recipient1)
        .status(SENT)
        .type(PROGRAMME_DAY_ONE)
        .tisReference(new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, "123"))
        .build();
    mongoTemplate.insert(historyToMove1);

    ObjectId id2 = ObjectId.get();
    History.RecipientInfo recipient2 = new History.RecipientInfo("another-id", EMAIL,
        "abc@email.com");
    History historyToMove2 = History.builder()
        .id(id2)
        .recipient(recipient2)
        .status(PENDING)
        .type(FORM_SUBMITTED)
        .tisReference(new History.TisReferenceInfo(FORMR_PARTA, "1234"))
        .build();
    mongoTemplate.insert(historyToMove2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("recipient.id").ne(FROM_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected unchanged history count.", found.size(),
              is(2));
          histories.addAll(found);
        });

    History unchangedHistory1 = histories.stream()
        .filter(a -> a.id().equals(id1))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected unchanged history.", unchangedHistory1, is(historyToMove1));

    History unchangedHistory2 = histories.stream()
        .filter(a -> a.id().equals(id2))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected unchanged history.", unchangedHistory2, is(historyToMove2));
  }
}
