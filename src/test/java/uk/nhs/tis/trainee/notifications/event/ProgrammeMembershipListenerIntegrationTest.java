package uk.nhs.tis.trainee.notifications.event;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
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
public class ProgrammeMembershipListenerIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final UUID PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID();
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Mr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";

  private static final String PM_UPDATED_QUEUE = UUID.randomUUID().toString();
  private static final LocalDate START_DATE_FAR_FUTURE = LocalDate.now().plusYears(1);

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

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
  }

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), History.class);
  }

  @Test
  void shouldStoreFutureReminderNotificationsWhenProgrammeMembershipReceived()
      throws JsonProcessingException {
    when(userAccountService.getUserDetailsById(PERSON_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    LocalDate startDate12Weeks = LocalDate.now().plusWeeks(12);

    String eventString =  """
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
          "curricula": "[{\\"curriculumSubType\\":\\"MEDICAL_CURRICULUM\\",\\"curriculumSpecialty\\":\\"specialty\\",\\"curriculumSpecialtyBlockIndemnity\\":false}]"
        },
        "metadata": {
          "operation": "LOAD"
        }
      }
    }
    """.formatted(PROGRAMME_MEMBERSHIP_ID, PROGRAMME_MEMBERSHIP_ID, PERSON_ID, startDate12Weeks);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PM_UPDATED_QUEUE, eventJson);

    Criteria criteria = Criteria.where("recipient.id").is(PERSON_ID);
    Query query = Query.query(criteria);
    List<History> histories = new ArrayList<>();

    int expectedNotificationCount
        = NotificationType.getReminderProgrammeUpdateNotificationTypes().size();

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

    History history = histories.get(0);
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.",
        NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(history.type()),
        is(true));
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
    assertThat("Unexpected template variable count.", storedVariables.size(), is(6));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"), is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("givenName"), is(GIVEN_NAME));

  }

}
