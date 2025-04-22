/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.DockerImageNames;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class LtftListenerIntegrationTest {

  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Dr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";

  private static final String DEFAULT_GREETING = "Dear Doctor,";
  private static final String DEFAULT_DETAIL = "The status of your LTFT application has changed.";
  private static final String DEFAULT_APP_REF = "Application Reference: (not set)";
  private static final String DEFAULT_DISCLAIMER = "This email is intended only for use by the "
      + "named addressee. It may contain confidential and/or privileged information. If you are "
      + "not the intended recipient, you should contact us immediately and should not disclose, "
      + "use, or rely on this email. We do not accept any liability arising from a third-party "
      + "taking action, or refraining from taking action, on the basis of information contained "
      + "in this email. Thank you.";

  private static final String LTFT_NAME = "ltft_name";
  private static final String STATUS = "Approved";
  private static final Instant TIMESTAMP = Instant.parse("2025-03-15T10:00:00Z");
  private static final String FORM_REF = "ltft_47165_001";

  private static final String LTFT_UPDATED_QUEUE = UUID.randomUUID().toString();

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
    registry.add("application.queues.ltft-updated", () -> LTFT_UPDATED_QUEUE);

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal sqs create-queue --queue-name", LTFT_UPDATED_QUEUE);
  }

  @MockBean
  private JavaMailSender mailSender;

  @MockBean
  private UserAccountService userAccountService;

  @MockBean
  private S3Template s3Template;

  @Autowired
  private EmailService emailService;

  @Autowired
  private LtftListener listener;

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Value("${application.template-versions.ltft-updated.email}")
  private String templateVersion;

  private String traineeId;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    traineeId = UUID.randomUUID().toString();
    when(userAccountService.getUserAccountIds(traineeId)).thenReturn(Set.of(USER_ID));
  }

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), History.class);
  }

  @Test
  void shouldSendDefaultLtftNotificationWhenTemplateVariablesNull()
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": null,
          "formName": null,
          "personalDetails": {
            "surname": null
          },
          "status": {
            "current" : {
              "state": null,
              "timestamp": null
            }
          }
        }
        """.formatted(traineeId);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender).send(messageCaptor.capture()));

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(8));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element disclaimer = bodyChildren.get(7);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element detail = bodyChildren.get(2);
    assertThat("Unexpected detail.", detail.text(), is(DEFAULT_DETAIL));

    Element applicationRef = bodyChildren.get(5);
    assertThat("Unexpected application reference.", applicationRef.text(),
        is(DEFAULT_APP_REF));
  }

  @Test
  void shouldSendDefaultLtftNotificationWhenTemplateVariablesEmpty()
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, "", "", GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "",
          "formName": "",
          "personalDetails": {
            "surname": ""
          },
          "status": {
            "current" : {
              "state": "",
              "timestamp": ""
            }
          }
        }
        """.formatted(traineeId);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender).send(messageCaptor.capture()));

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(8));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element disclaimer = bodyChildren.get(7);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element detail = bodyChildren.get(2);
    assertThat("Unexpected detail.", detail.text(), is(DEFAULT_DETAIL));

    Element applicationRef = bodyChildren.get(5);
    assertThat("Unexpected application reference.", applicationRef.text(),
        is(DEFAULT_APP_REF));
  }

  @Test
  void shouldSendFullyTailoredLtftUpdatedNotificationWhenAllTemplateVariablesAvailable()
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "%s",
          "formName": "%s",
          "personalDetails": {
            "surname": "%s"
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": "%s"
            }
          }
        }
        """.formatted(traineeId, FORM_REF, LTFT_NAME, FAMILY_NAME, STATUS, TIMESTAMP);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender).send(messageCaptor.capture()));

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(8));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element disclaimer = bodyChildren.get(7);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element detail = bodyChildren.get(2);
    assertThat("Unexpected detail.", detail.text(),
        is("The status of your LTFT application (ref: ltft_47165_001) has changed "
            + "to Approved on 15 March 2025."));

    Element applicationRef = bodyChildren.get(5);
    assertThat("Unexpected application reference.", applicationRef.text(),
        is("Application Reference: ltft_name"));
  }

  @Test
  void shouldStoreLtftUpdatedNotificationHistoryWhenMessageSent() throws JsonProcessingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "%s",
          "formName": "%s",
          "personalDetails": {
            "surname": "%s"
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": "%s"
            }
          }
        }
        """.formatted(traineeId, FORM_REF, LTFT_NAME, FAMILY_NAME, STATUS, TIMESTAMP);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    Criteria criteria = Criteria.where("recipient.id").is(traineeId);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(), is(1));
          histories.addAll(found);
        });

    History history = histories.get(0);
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(LTFT_UPDATED));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(traineeId));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(LTFT_UPDATED.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(),
        is(5));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"),
        is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("givenName"),
        is(GIVEN_NAME));

    LtftUpdateEvent event = (LtftUpdateEvent) storedVariables.get("var");
    assertThat("Unexpected trainee ID.", event.getTraineeId(), is(traineeId));
    assertThat("Unexpected form ref.", event.getFormRef(), is(FORM_REF));
    assertThat("Unexpected form name.", event.getFormName(), is(LTFT_NAME));
    assertThat("Unexpected state.", event.getState(), is(STATUS));
    assertThat("Unexpected timestamp.", event.getTimestamp(), is(TIMESTAMP));
  }
}
