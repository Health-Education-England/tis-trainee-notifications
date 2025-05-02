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
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.tis.trainee.notifications.event.LtftListener.LTFT_UPDATE_EXPLICIT_NOTIFICATION_TYPES;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.LTFT_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.SUPPORTED_RETURN_TO_TRAINING;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.TSS_SUPPORT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import uk.nhs.tis.trainee.notifications.event.LtftListener.Contact;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;
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

  private static final String LTFT_NAME = "ltft_name";
  private static final Instant TIMESTAMP = Instant.parse("2025-03-15T10:00:00Z");
  private static final String FORM_REF = "ltft_47165_001";
  private static final String MANAGING_DEANERY = "North West";
  private static final String TPD_NAME = "TPD name";
  private static final String PM_NAME = "General Practice";
  private static final String START_DATE = "2025-05-01";
  private static final String WTE_CURRENT = "1.0";

  private static final String LTFT_UPDATED_QUEUE = UUID.randomUUID().toString();
  private static final Set<LocalOfficeContactType> EXPECTED_CONTACTS = Set.of(LTFT, LTFT_SUPPORT,
      SUPPORTED_RETURN_TO_TRAINING, TSS_SUPPORT);

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
  private NotificationService notificationService;

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

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED     | LTFT_APPROVED
      SUBMITTED    | LTFT_SUBMITTED_TPD
      SUBMITTED    | LTFT_SUBMITTED_TRAINEE
      Other-Status | LTFT_UPDATED
      """)
  void shouldSendDefaultNotificationsWhenTemplateVariablesNull(String state, NotificationType type)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "status": {
            "current" : {
              "state": "%s"
            }
          }
        }
        """.formatted(traineeId, state);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender, times(expectedNotificationEmailCount(state)))
            .send(messageCaptor.capture()));

    URL resource = getClass().getResource(
        "/email/" + type.getTemplateName() + "-minimal.html");
    assert resource != null;
    Document expectedContent = Jsoup.parse(Paths.get(resource.toURI()).toFile());
    int matchedContentCount = 0;
    List<MimeMessage> messages = messageCaptor.getAllValues();
    for (MimeMessage message : messages) {
      Document content = Jsoup.parse((String) message.getContent());
      if (expectedContent.html().equals(content.html())) {
        matchedContentCount++;
      }
    }
    assertThat("Unexpected content.", matchedContentCount, is(1));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED     | LTFT_APPROVED
      SUBMITTED    | LTFT_SUBMITTED_TPD
      SUBMITTED    | LTFT_SUBMITTED_TRAINEE
      Other-Status | LTFT_UPDATED
      """)
  void shouldSendDefaultNotificationsWhenTemplateVariablesEmpty(String state, NotificationType type)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, "", "", GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "",
          "formName": "",
          "personalDetails": {
            "gmcNumber": ""
          },
          "programmeMembership": {
            "name": "",
            "managingDeanery": "",
            "startDate": "",
            "wte": ""
          },
          "change": {
            "endDate": "",
            "wte": ""
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": ""
            }
          }
        }
        """.formatted(traineeId, state);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender, times(expectedNotificationEmailCount(state)))
            .send(messageCaptor.capture()));

    URL resource = getClass().getResource(
        "/email/" + type.getTemplateName() + "-minimal.html");
    assert resource != null;
    Document expectedContent = Jsoup.parse(Paths.get(resource.toURI()).toFile());
    int matchedContentCount = 0;
    List<MimeMessage> messages = messageCaptor.getAllValues();
    for (MimeMessage message : messages) {
      Document content = Jsoup.parse((String) message.getContent());
      if (expectedContent.html().equals(content.html())) {
        matchedContentCount++;
      }
    }
    assertThat("Unexpected content.", matchedContentCount, is(1));
  }

  /*
  APPROVED  | LTFT_APPROVED
      SUBMITTED | LTFT_SUBMITTED_TPD
      SUBMITTED | LTFT_SUBMITTED_TRAINEE
   */
  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED  | LTFT_APPROVED
      SUBMITTED | LTFT_SUBMITTED_TPD
      SUBMITTED | LTFT_SUBMITTED_TRAINEE
      """)
  void shouldSendFullyTailoredNotificationsWhenAllTemplateVariablesAvailableAndUrlContacts(
      String state, NotificationType type) throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    when(notificationService.getOwnerContactList(MANAGING_DEANERY)).thenReturn(
        EXPECTED_CONTACTS.stream()
            .map(ct -> Map.of(
                "contact", "https://test/" + ct,
                "contactTypeName", ct.getContactTypeName()
            ))
            .toList());
    when(notificationService.getOwnerContact(any(), any(), eq(TSS_SUPPORT),
        eq(""))).thenCallRealMethod();
    when(notificationService.getHrefTypeForContact(any())).thenCallRealMethod();

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "ltft_47165_001",
          "formName": "form_name",
          "personalDetails": {
            "gmcNumber": "1234567"
          },
          "programmeMembership": {
            "name": "General Practice",
            "startDate": "2025-01-03",
            "managingDeanery": "%s",
            "wte": 1.0
          },
          "change": {
            "startDate": "2025-04-03",
            "wte": 0.5,
            "cctDate": "2027-06-05"
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": "2026-05-04T01:02:03.004Z"
            }
          },
          "discussions": {
            "tpdName": "Mr TPD"
          }
        }
        """.formatted(traineeId, MANAGING_DEANERY, state);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender, times(expectedNotificationEmailCount(state)))
            .send(messageCaptor.capture()));

    URL resource = getClass().getResource(
        "/email/" + type.getTemplateName() + "-full-url-contacts.html");
    assert resource != null;
    Document expectedContent = Jsoup.parse(Paths.get(resource.toURI()).toFile());
    int matchedContentCount = 0;
    List<MimeMessage> messages = messageCaptor.getAllValues();
    for (MimeMessage message : messages) {
      Document content = Jsoup.parse((String) message.getContent());
      if (expectedContent.html().equals(content.html())) {
        matchedContentCount++;
      }
    }
    assertThat("Unexpected content.", matchedContentCount, is(1));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED  | LTFT_APPROVED
      SUBMITTED | LTFT_SUBMITTED_TPD
      SUBMITTED | LTFT_SUBMITTED_TRAINEE
      """)
  void shouldSendFullyTailoredNotificationsWhenAllTemplateVariablesAvailableAndEmailContacts(
      String state, NotificationType type) throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    when(notificationService.getOwnerContactList(MANAGING_DEANERY)).thenReturn(
        EXPECTED_CONTACTS.stream()
            .map(ct -> Map.of(
                "contact", ct + "@example.com",
                "contactTypeName", ct.getContactTypeName()
            ))
            .toList());
    when(notificationService.getOwnerContact(any(), any(), eq(TSS_SUPPORT),
        eq(""))).thenCallRealMethod();
    when(notificationService.getHrefTypeForContact(any())).thenCallRealMethod();

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "ltft_47165_001",
          "formName": "form_name",
          "personalDetails": {
            "gmcNumber": "1234567"
          },
          "programmeMembership": {
            "name": "General Practice",
            "managingDeanery": "%s",
            "startDate": "2025-01-03",
            "wte": 1.0
          },
          "change": {
            "startDate": "2025-04-03",
            "wte": 0.5,
            "cctDate": "2027-06-05"
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": "2026-05-04T01:02:03.004Z"
            }
          },
          "discussions": {
            "tpdName": "Mr TPD"
          }
        }
        """.formatted(traineeId, MANAGING_DEANERY, state);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(LTFT_UPDATED_QUEUE, eventJson);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> verify(mailSender, times(expectedNotificationEmailCount(state)))
            .send(messageCaptor.capture()));

    URL resource = getClass().getResource(
        "/email/" + type.getTemplateName() + "-full-email-contacts.html");
    assert resource != null;
    Document expectedContent = Jsoup.parse(Paths.get(resource.toURI()).toFile());
    int matchedContentCount = 0;
    List<MimeMessage> messages = messageCaptor.getAllValues();
    for (MimeMessage message : messages) {
      Document content = Jsoup.parse((String) message.getContent());
      if (expectedContent.html().equals(content.html())) {
        matchedContentCount++;
      }
    }
    assertThat("Unexpected content.", matchedContentCount, is(1));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      Other-Status | LTFT_UPDATED
      """)
  void shouldSendFullyTailoredNotificationsWhenAllTemplateVariablesAvailableAndNoContacts(
      String state, NotificationType type) throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "ltft_47165_001",
          "formName": "form_name",
          "personalDetails": {
            "gmcNumber": "1234567"
          },
          "programmeMembership": {
            "name": "General Practice",
            "managingDeanery": "%s",
            "startDate": "2025-04-03",
            "wte": 1.0
          },
          "change": {
            "endDate": "2027-06-05",
            "wte": 0.5
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": "2026-05-04T01:02:03.004Z"
            }
          }
        }
        """.formatted(traineeId, MANAGING_DEANERY, state);

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

    URL resource = getClass().getResource("/email/" + type.getTemplateName() + "-full.html");
    assert resource != null;
    Document expectedContent = Jsoup.parse(Paths.get(resource.toURI()).toFile());
    assertThat("Unexpected content.", content.html(), is(expectedContent.html()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED     | LTFT_APPROVED
      SUBMITTED    | LTFT_SUBMITTED_TPD
      SUBMITTED    | LTFT_SUBMITTED_TRAINEE
      Other-Status | LTFT_UPDATED
      """)
  void shouldStoreNotificationHistoryWhenMessageSent(String state, NotificationType type)
      throws JsonProcessingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    when(notificationService.getOwnerContactList(MANAGING_DEANERY)).thenReturn(
        EXPECTED_CONTACTS.stream()
            .map(ct -> Map.of(
                "contact", "https://test/" + ct,
                "contactTypeName", ct.getContactTypeName()
            ))
            .toList());
    when(notificationService.getOwnerContact(any(), any(), eq(TSS_SUPPORT),
        eq(""))).thenCallRealMethod();
    when(notificationService.getHrefTypeForContact(any())).thenCallRealMethod();

    String eventString = """
        {
          "traineeTisId": "%s",
          "formRef": "%s",
          "formName": "%s",
          "programmeMembership": {
            "managingDeanery": "%s",
            "name": "%s",
            "startDate": "%s",
            "wte": "%s"
          },
          "status": {
            "current" : {
              "state": "%s",
              "timestamp": "%s"
            }
          }
        }
        """.formatted(traineeId, FORM_REF, LTFT_NAME, MANAGING_DEANERY, PM_NAME,
        START_DATE, WTE_CURRENT, state, TIMESTAMP, TPD_NAME);

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
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationEmailCount(state)));
          histories.addAll(found);
        });

    //check that histories contain all the expected types
    if (LTFT_UPDATE_EXPLICIT_NOTIFICATION_TYPES.containsKey(state)) {
      assertThat("Unexpected history type found.", histories.stream()
              .filter(h -> !LTFT_UPDATE_EXPLICIT_NOTIFICATION_TYPES.get(state).contains(h.type()))
              .count(),
          is(0L));
    }

    // Find the history entry matching the expected type
    History history = histories.stream()
        .filter(h -> h.type() == type)
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "No history entry found with type " + type));

    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(traineeId));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(), is(type.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(6));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"),
        is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("givenName"), is(GIVEN_NAME));

    LtftUpdateEvent event = (LtftUpdateEvent) storedVariables.get("var");
    assertThat("Unexpected trainee ID.", event.getTraineeId(), is(traineeId));
    assertThat("Unexpected form ref.", event.getFormRef(), is(FORM_REF));
    assertThat("Unexpected form name.", event.getFormName(), is(LTFT_NAME));
    assertThat("Unexpected state.", event.getState(), is(state));
    assertThat("Unexpected timestamp.", event.getTimestamp(), is(TIMESTAMP));
    assertThat("Unexpected programme name.", event.getProgrammeMembership().name(),
        is(PM_NAME));
    assertThat("Unexpected programme start date.",
        event.getProgrammeMembership().startDate().toString(), is(START_DATE));
    assertThat("Unexpected current WTE.", event.getProgrammeMembership().wte().toString(),
        is(WTE_CURRENT));

    Map<String, Contact> contacts = (Map<String, Contact>) storedVariables.get("contacts");
    assertThat("Unexpected contact count.", contacts.keySet(), hasSize(4));
    EXPECTED_CONTACTS.forEach(ct -> {
      Contact contact = contacts.get(ct.name());
      assertThat("Unexpected contact link.", contact.contact(), is("https://test/" + ct));
      assertThat("Unexpected contact HREF type.", contact.type(), is("url"));
    });
  }

  /**
   * Get the expected number of notification emails to be sent for a given LTFT update state.
   *
   * @param state The LTFT update state.
   * @return The expected number of notification emails.
   */
  int expectedNotificationEmailCount(String state) {
    if (LTFT_UPDATE_EXPLICIT_NOTIFICATION_TYPES.containsKey(state)) {
      return LTFT_UPDATE_EXPLICIT_NOTIFICATION_TYPES.get(state).size();
    } else {
      return 1;
    }
  }
}
