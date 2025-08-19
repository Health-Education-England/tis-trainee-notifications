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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.API_GET_OWNER_CONTACT;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.API_TRAINEE_DETAILS;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.OWNER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipActionsService.TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.DockerImageNames;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeActionType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.MessageSendingService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

/**
 * Integration test for the ProgrammeMembershipListener.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ProgrammeMembershipListenerIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final UUID PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Mr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";

  private static final String PM_UPDATED_QUEUE = UUID.randomUUID().toString();
  private static final LocalDate START_DATE_FUTURE = LocalDate.now().plusYears(1);
  private static final UserDetails USER_DETAILS = new UserDetails(true, EMAIL, TITLE,
      FAMILY_NAME, GIVEN_NAME, GMC);

  @Value("${service.trainee.url}")
  private String serviceUrl;
  @Value("${service.reference.url}")
  private String referenceUrl;
  @Value("${service.actions.url}")
  private String actionsUrl;

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
    registry.add("application.queues.programme-membership-updated", () -> PM_UPDATED_QUEUE);
    registry.add("application.email.enabled", () -> true);
    registry.add("application.domain", () -> URI.create("https://test.test.test"));

    //new-starter and pilot logic is tested elsewhere
    registry.add("application.notifications-whitelist", () -> List.of(PERSON_ID));

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal sqs create-queue --queue-name", PM_UPDATED_QUEUE);
  }

  @MockBean
  private JavaMailSender mailSender;

  @MockBean
  private MessageSendingService messageService;

  @MockBean
  private UserAccountService userAccountService;

  @MockBean
  private S3Template s3Template;

  @MockBean
  Scheduler scheduler;

  @MockBean
  RestTemplate restTemplate;

  @Autowired
  private EmailService emailService;

  @Autowired
  private LtftListener listener;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserDetailsById(PERSON_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    when(restTemplate.getForObject(serviceUrl + API_TRAINEE_DETAILS,
        UserDetails.class, Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(USER_DETAILS);

    when(restTemplate.getForObject(referenceUrl + API_GET_OWNER_CONTACT,
        List.class, Map.of(OWNER_FIELD, "deaneryTest"))).thenReturn(List.of());

    //default empty actions set returned
    ResponseEntity<Set<ActionDto>> responseEntity = new ResponseEntity<>(Set.of(), HttpStatus.OK);
    when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class),
        anyMap())).thenReturn(responseEntity);
  }

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), History.class);
  }

  @Test
  void shouldStoreFutureReminderNotificationsWithoutActionStatusesWhenProgrammeMembershipReceived()
      throws JsonProcessingException {

    sqsTemplate.send(PM_UPDATED_QUEUE, buildStandardProgrammeMembershipEvent(START_DATE_FUTURE));

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getActiveProgrammeUpdateNotificationTypes().size();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationCount));
          histories.addAll(found);
        });

    for (NotificationType reminderNotification
        : NotificationType.getReminderProgrammeUpdateNotificationTypes()) {
      History history = histories.stream()
          .filter(h -> h.type().equals(reminderNotification))
          .findFirst()
          .orElseThrow(() -> new AssertionError(
              "Expected notification type not found: " + reminderNotification));
      assertThat("Unexpected notification id.", history.id(), notNullValue());
      assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

      History.RecipientInfo recipient = history.recipient();
      assertThat("Unexpected recipient id.", recipient.id(), is(PERSON_ID));
      assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
      assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

      History.TemplateInfo templateInfo = history.template();
      assertThat("Unexpected template name.", templateInfo.name(),
          is(history.type().getTemplateName()));
      assertThat("Unexpected template version.", templateInfo.version(), is("v1.0.0"));

      Map<String, Object> storedVariables = templateInfo.variables();
      assertThat("Unexpected template variable count.", storedVariables.size(), is(19));
      for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
        assertThat("Unexpected template variable for action type: " + actionType,
            storedVariables.get(actionType.toString()), nullValue());
      }
    }
  }

  @Test
  void shouldSkipReminderNotificationIfAllActionsAssumedComplete()
      throws JsonProcessingException {

    LocalDate week12ReminderDate = LocalDate.now().plusWeeks(12);
    sqsTemplate.send(PM_UPDATED_QUEUE, buildStandardProgrammeMembershipEvent(week12ReminderDate));

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getActiveProgrammeUpdateNotificationTypes().size()
        - 1; // Exclude week 12 reminder

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationCount));
          histories.addAll(found);
        });

    Optional<History> history = histories.stream()
        .filter(h -> h.type().equals(PROGRAMME_UPDATED_WEEK_12))
        .findAny();
    assertThat("Unexpected 12-week reminder found.", history, is(Optional.empty()));
  }

  @Test
  void shouldSkipReminderNotificationIfAllActionsComplete()
      throws JsonProcessingException {

    Set<ActionDto> completeActions = new HashSet<>();
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      completeActions.add(new ActionDto(
          "id", actionType.toString(), PERSON_ID,
          new ActionDto.TisReferenceInfo(PROGRAMME_MEMBERSHIP_ID.toString(), PROGRAMME_MEMBERSHIP),
          LocalDate.MIN, LocalDate.MIN.plusDays(1), Instant.now()));
    }
    ResponseEntity<Set<ActionDto>> responseEntity
        = new ResponseEntity<>(completeActions, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class),
        anyMap())).thenReturn(responseEntity);

    when(userAccountService.getUserDetailsById(PERSON_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    LocalDate week12ReminderDate = LocalDate.now().plusWeeks(12);
    sqsTemplate.send(PM_UPDATED_QUEUE, buildStandardProgrammeMembershipEvent(week12ReminderDate));

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getActiveProgrammeUpdateNotificationTypes().size()
        - 1; // Exclude week 12 reminder

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationCount));
          histories.addAll(found);
        });

    Optional<History> history = histories.stream()
        .filter(h -> h.type().equals(PROGRAMME_UPDATED_WEEK_12))
        .findAny();
    assertThat("Unexpected 12-week reminder found.", history, is(Optional.empty()));
  }

  @Test
  void shouldSkipReminderNotificationIfOverdue()
      throws JsonProcessingException {

    Set<ActionDto> completeActions = new HashSet<>();
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      completeActions.add(new ActionDto(
          "id", actionType.toString(), PERSON_ID,
          new ActionDto.TisReferenceInfo(PROGRAMME_MEMBERSHIP_ID.toString(), PROGRAMME_MEMBERSHIP),
          LocalDate.MIN, LocalDate.MIN.plusDays(1), null));
    }
    ResponseEntity<Set<ActionDto>> responseEntity
        = new ResponseEntity<>(completeActions, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class),
        anyMap())).thenReturn(responseEntity);

    when(userAccountService.getUserDetailsById(PERSON_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    LocalDate week12ReminderDateOverdue = LocalDate.now().plusWeeks(12).minusDays(1);
    sqsTemplate.send(PM_UPDATED_QUEUE,
        buildStandardProgrammeMembershipEvent(week12ReminderDateOverdue));

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getActiveProgrammeUpdateNotificationTypes().size()
        - 1; // Exclude week 12 reminder

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationCount));
          histories.addAll(found);
        });

    Optional<History> history = histories.stream()
        .filter(h -> h.type().equals(PROGRAMME_UPDATED_WEEK_12))
        .findAny();
    assertThat("Unexpected 12-week reminder found.", history, is(Optional.empty()));
  }

  @Test
  void shouldSendReminderNotificationIfActionsIncomplete()
      throws JsonProcessingException {

    Set<ActionDto> completeActions = new HashSet<>();
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      completeActions.add(new ActionDto(
          "id", actionType.toString(), PERSON_ID,
          new ActionDto.TisReferenceInfo(PROGRAMME_MEMBERSHIP_ID.toString(), PROGRAMME_MEMBERSHIP),
          LocalDate.MIN, LocalDate.MIN.plusDays(1), null));
    }
    ResponseEntity<Set<ActionDto>> responseEntity
        = new ResponseEntity<>(completeActions, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class),
        anyMap())).thenReturn(responseEntity);

    when(userAccountService.getUserDetailsById(PERSON_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    LocalDate week12ReminderDate = LocalDate.now().plusWeeks(12);
    sqsTemplate.send(PM_UPDATED_QUEUE,
        buildStandardProgrammeMembershipEvent(week12ReminderDate));

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getActiveProgrammeUpdateNotificationTypes().size();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationCount));
          histories.addAll(found);
        });

    Optional<History> historyOptional = histories.stream()
        .filter(h -> h.type().equals(PROGRAMME_UPDATED_WEEK_12))
        .findAny();
    assertThat("Unexpected missing 12-week reminder.", historyOptional.isPresent(), is(true));

    History history = historyOptional.get();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    History.RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(PERSON_ID));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    History.TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(history.type().getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is("v1.0.0"));

    Map<String, Object> storedVariables = templateInfo.variables();
    int expectedVariableCount = 19 //basic set
        + ProgrammeActionType.values().length
        + 2; //domain and hashedEmail added when sending email
    assertThat("Unexpected template variable count.", storedVariables.size(),
        is(expectedVariableCount));
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      assertThat("Unexpected template variable for action type: " + actionType,
          storedVariables.get(actionType.toString()), is(false));
    }
    assertThat("Unexpected welcome notification date.",
        storedVariables.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @Test
  void shouldSetWelcomeDateInReminderTemplateDataIfAvailable()
      throws JsonProcessingException {

    //insert a welcome notification for the trainee
    ObjectId id = ObjectId.get();
    History welcomeNotification = new History(
        id, new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, PROGRAMME_MEMBERSHIP_ID.toString()),
        PROGRAMME_CREATED, new History.RecipientInfo(PERSON_ID, MessageType.EMAIL, EMAIL), null,
        null, Instant.now(), null, NotificationStatus.SENT, null, null);
    mongoTemplate.insert(welcomeNotification);

    Set<ActionDto> completeActions = new HashSet<>();
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      completeActions.add(new ActionDto(
          "id", actionType.toString(), PERSON_ID,
          new ActionDto.TisReferenceInfo(PROGRAMME_MEMBERSHIP_ID.toString(), PROGRAMME_MEMBERSHIP),
          LocalDate.MIN, LocalDate.MIN.plusDays(1), null));
    }
    ResponseEntity<Set<ActionDto>> responseEntity
        = new ResponseEntity<>(completeActions, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), any(), isNull(), any(ParameterizedTypeReference.class),
        anyMap())).thenReturn(responseEntity);

    when(userAccountService.getUserDetailsById(PERSON_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    LocalDate week12ReminderDate = LocalDate.now().plusWeeks(12);
    sqsTemplate.send(PM_UPDATED_QUEUE,
        buildStandardProgrammeMembershipEvent(week12ReminderDate));

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getActiveProgrammeUpdateNotificationTypes().size();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<History> found = mongoTemplate.find(query, History.class);
          assertThat("Unexpected history count.", found.size(),
              is(expectedNotificationCount));
          histories.addAll(found);
        });

    Optional<History> historyOptional = histories.stream()
        .filter(h -> h.type().equals(PROGRAMME_UPDATED_WEEK_12))
        .findAny();
    assertThat("Unexpected missing 12-week reminder.", historyOptional.isPresent(), is(true));

    History history = historyOptional.get();
    Map<String, Object> storedVariables = history.template().variables();
    assertThat("Unexpected welcome notification date.",
        storedVariables.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD),
        is(LocalDate.from(Instant.now().atZone(ZoneId.of("UTC")))));
  }

  //TODO: template content tests

  /**
   * Helper method to build a standard programme membership event JSON.
   *
   * @param startDate The start date of the programme membership
   * @return A JsonNode representing the programme membership event.
   * @throws JsonProcessingException If there is an error processing the JSON.
   */
  JsonNode buildStandardProgrammeMembershipEvent(LocalDate startDate)
      throws JsonProcessingException {
    String eventString = """
        {
          "tisId": "%s",
          "record": {
            "data": {
              "tisId": "%s",
              "personId": "%s",
              "startDate": "%s",
              "programmeName": "test",
              "managingDeanery": "deaneryTest",
              "programmeNumber": "123456",
              "designatedBody": "desBody",
              "curricula": "[{\\"curriculumSubType\\":\\"MEDICAL_CURRICULUM\\",
              \\"curriculumSpecialty\\":\\"specialty\\",
              \\"curriculumSpecialtyBlockIndemnity\\":false}]"
            },
            "metadata": {
              "operation": "LOAD"
            }
          }
        }
        """.formatted(PROGRAMME_MEMBERSHIP_ID, PROGRAMME_MEMBERSHIP_ID, PERSON_ID, startDate);

    return JsonMapper.builder()
        .build()
        .readTree(eventString);
  }
}