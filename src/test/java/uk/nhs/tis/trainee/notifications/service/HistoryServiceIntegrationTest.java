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

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.PENDING;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class HistoryServiceIntegrationTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private static final ObjectId NOTIFICATION_ID = ObjectId.get();

  private static final String TEMPLATE_NAME = "ltft-updated";
  private static final String TEMPLATE_VERSION = "v1.0.0";
  private static final Map<String, Object> TEMPLATE_VARIABLES = Map.of(
      "key1", "value1",
      "key2", "value2",
      "isValidGmc", true);
  private static final TisReferenceType TIS_REFERENCE_TYPE = TisReferenceType.PLACEMENT;
  private static final String TIS_REFERENCE_ID = UUID.randomUUID().toString();
  private static final String TIS_REFERENCE_ID_2 = UUID.randomUUID().toString();

  private static final Instant SENT_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);
  private static final Instant READ_AT = Instant.now().plus(Duration.ofDays(1))
      .truncatedTo(ChronoUnit.MILLIS);

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @MockBean
  EventBroadcastService eventBroadcastService;

  @MockBean
  private SqsTemplate sqsTemplate;

  @Autowired
  private HistoryService service;

  @BeforeEach
  void setUp(@Autowired HistoryRepository repository) {
    repository.deleteAll();
  }

  @Test
  void shouldSaveHistory() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    History savedHistory = service.save(history);

    assertThat("Unexpected ID.", savedHistory.id(), instanceOf(ObjectId.class));
    assertThat("Unexpected type.", savedHistory.type(), is(FORM_UPDATED));
    assertThat("Unexpected sent at.", savedHistory.sentAt(), is(SENT_AT));
    assertThat("Unexpected read at.", savedHistory.readAt(), is(READ_AT));

    RecipientInfo savedRecipientInfo = savedHistory.recipient();
    assertThat("Unexpected recipient id.", savedRecipientInfo.id(), is(TRAINEE_ID));
    assertThat("Unexpected recipient type.", savedRecipientInfo.type(), is(EMAIL));
    assertThat("Unexpected recipient contact.", savedRecipientInfo.contact(), is(TRAINEE_CONTACT));

    TemplateInfo savedTemplateInfo = savedHistory.template();
    assertThat("Unexpected template name.", savedTemplateInfo.name(), is(TEMPLATE_NAME));
    assertThat("Unexpected template version.", savedTemplateInfo.version(), is(TEMPLATE_VERSION));
    assertThat("Unexpected template variables.", savedTemplateInfo.variables(),
        is(TEMPLATE_VARIABLES));

    TisReferenceInfo savedReferenceInfo = savedHistory.tisReference();
    assertThat("Unexpected TIS reference type.", savedReferenceInfo.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected TIS reference id.", savedReferenceInfo.id(),
        is(TIS_REFERENCE_ID));
  }

  @Test
  void shouldFindOverdueNotifications() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History past = service.save(
        new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo, null,
            SENT_AT.minus(Duration.ofDays(1)), null, SCHEDULED, null, null));
    History current = service.save(
        new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo, null,
            SENT_AT, null, SCHEDULED, null, null));
    service.save(
        new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo, null,
            SENT_AT.plus(Duration.ofDays(1)), null, SCHEDULED, null, null));

    List<ObjectIdWrapper> wrappedOverdue = service.findAllOverdue();

    assertThat("Unexpected overdue count.", wrappedOverdue, hasSize(2));

    List<ObjectId> overdue = wrappedOverdue.stream()
        .map(ObjectIdWrapper::id)
        .toList();
    assertThat("Unexpected overdue IDs.", overdue, hasItems(past.id(), current.id()));
  }

  @Test
  void shouldNotFindNotificationsWhenTraineeIdNotMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history);

    List<HistoryDto> foundHistory = service.findAllForTrainee("notFound");

    assertThat("Unexpected history count.", foundHistory.size(), is(0));
  }

  @Test
  void shouldFindNotificationsWhenTraineeIdMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    History savedHistory = service.save(history);

    List<HistoryDto> foundHistory = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(1));

    HistoryDto historyDto = foundHistory.get(0);
    assertThat("Unexpected history id.", historyDto.id(), is(savedHistory.id().toString()));
    assertThat("Unexpected history type.", historyDto.type(), is(EMAIL));
    assertThat("Unexpected history subject.", historyDto.subject(), is(FORM_UPDATED));
    assertThat("Unexpected history contact.", historyDto.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto.sentAt(), is(SENT_AT));
    assertThat("Unexpected history read at.", historyDto.readAt(), is(READ_AT));
  }

  @Test
  void shouldSortFoundNotificationsBySentAtWhenMultipleFound() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, before, SENT, null, null));

    List<HistoryDto> foundHistory = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(3));

    HistoryDto history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(after));

    HistoryDto history2 = foundHistory.get(1);
    assertThat("Unexpected history sent at.", history2.sentAt(), is(now));

    HistoryDto history3 = foundHistory.get(2);
    assertThat("Unexpected history sent at.", history3.sentAt(), is(before));
  }

  @Test
  void shouldNotFindNotificationsHistoryWhenTraineeIdNotMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history);

    List<History> foundHistory = service.findAllHistoryForTrainee("notFound");

    assertThat("Unexpected history count.", foundHistory.size(), is(0));
  }

  @Test
  void shouldFindNotificationsHistoryWhenTraineeIdMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    History savedHistory = service.save(history);

    List<History> foundHistory = service.findAllHistoryForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(1));

    History historyReceived = foundHistory.get(0);
    assertThat("Unexpected history id.", historyReceived.id(), is(savedHistory.id()));
    assertThat("Unexpected history type.", historyReceived.type(), is(FORM_UPDATED));
    RecipientInfo recipientInfoReceived = historyReceived.recipient();
    assertThat("Unexpected history recipient type.", recipientInfoReceived.type(),
        is(EMAIL));
    assertThat("Unexpected history recipient contact.", recipientInfoReceived.contact(),
        is(TRAINEE_CONTACT));
    assertThat("Unexpected history recipient id.", recipientInfoReceived.id(),
        is(TRAINEE_ID));
    assertThat("Unexpected history sent at.", historyReceived.sentAt(), is(SENT_AT));
    assertThat("Unexpected history read at.", historyReceived.readAt(), is(READ_AT));
  }

  @Test
  void shouldSortFoundNotificationsHistoryBySentAtWhenMultipleFound() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, before, SENT, null, null));

    List<History> foundHistory = service.findAllHistoryForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(3));

    History history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(after));

    History history2 = foundHistory.get(1);
    assertThat("Unexpected history sent at.", history2.sentAt(), is(now));

    History history3 = foundHistory.get(2);
    assertThat("Unexpected history sent at.", history3.sentAt(), is(before));
  }

  @Test
  void shouldSortFoundSentNotificationsBySentAtWhenMultipleFound() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, before, SENT, null, null));

    List<HistoryDto> foundHistory = service.findAllSentForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(2));

    HistoryDto history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(now));

    HistoryDto history2 = foundHistory.get(1);
    assertThat("Unexpected history sent at.", history2.sentAt(), is(before));
  }

  @Test
  void shouldSortFoundEmailAndInAppScheduledNotificationsBySentAt() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, now, any(), SENT, null, null));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, before, any(), SENT, null, null));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, before, any(), SCHEDULED, null, null));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, any(), UNREAD, null, null));

    List<History> foundHistory = service.findAllScheduledForTrainee(
        TRAINEE_ID, TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(2));

    History history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(after));
    History history2 = foundHistory.get(1);
    assertThat("Unexpected history sent at.", history2.sentAt(), is(before));
    assertThat("Unexpected history status.", history2.status(), is(SCHEDULED));
  }

  @Test
  void shouldSortScheduledEmailNotificationsByRefAndType() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo1 =
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);
    TisReferenceInfo tisReferenceInfo2 =
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID);
    TisReferenceInfo tisReferenceInfo3 =
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID_2);
    TisReferenceInfo tisReferenceInfo4 =
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);
    TisReferenceInfo tisReferenceInfo5 =
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_REFERENCE_ID_2);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo1, FORM_UPDATED, recipientInfo, templateInfo,
        null, now, now, SENT, null, null));
    service.save(new History(null, tisReferenceInfo2, PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo, null, now, now, SCHEDULED, null, null));
    service.save(new History(null, tisReferenceInfo3, PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo, null, now, now, SCHEDULED, null, null));
    service.save(new History(null, tisReferenceInfo4, PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo, null, now, now, SCHEDULED, null, null));
    service.save(new History(null, tisReferenceInfo5, PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo, null, now, now, SCHEDULED, null, null));

    History foundHistory = service.findScheduledEmailForTraineeByRefAndType(
        TRAINEE_ID, TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID, PROGRAMME_DAY_ONE);

    assertThat("Unexpected history message type.", foundHistory.recipient().type(), is(EMAIL));
    assertThat("Unexpected history reference type.", foundHistory.tisReference().type(),
        is(TisReferenceType.PROGRAMME_MEMBERSHIP));
    assertThat("Unexpected history reference id.", foundHistory.tisReference().id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history notification type.", foundHistory.type(),
        is(PROGRAMME_DAY_ONE));
    assertThat("Unexpected history status.", foundHistory.status(), is(SCHEDULED));
  }

  @Test
  void shouldNotReturnInAppNotificationsBySentAt() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo1 =
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);
    TisReferenceInfo tisReferenceInfo2 =
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo1, FORM_UPDATED, recipientInfo, templateInfo,
        null, now, now, SENT, null, null));
    service.save(new History(null, tisReferenceInfo2, PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo, null, now, now, SCHEDULED, null, null));

    History foundHistory = service.findScheduledEmailForTraineeByRefAndType(
        TRAINEE_ID, TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID, PROGRAMME_DAY_ONE);

    assertThat("Unexpected history count.", foundHistory, is(nullValue()));
  }

  @Test
  void shouldFilterInAppNotificationsBySentAtRefType() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisRefInfoPm = new TisReferenceInfo(
        TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID);
    TisReferenceInfo tisRefInfoPlacement = new TisReferenceInfo(
        TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);
    TisReferenceInfo tisRefInfoPlacement2 = new TisReferenceInfo(
        TisReferenceType.PLACEMENT, TIS_REFERENCE_ID_2);

    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisRefInfoPm, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, after, SENT, null, null));

    service.save(new History(null, tisRefInfoPlacement, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, after, SENT, null, null));

    service.save(new History(null, tisRefInfoPlacement2, FORM_UPDATED, recipientInfo, templateInfo,
        null, after, after, SENT, null, null));

    List<History> foundHistory = service.findAllScheduledForTrainee(
        TRAINEE_ID, TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(1));

    History history = foundHistory.get(0);
    assertThat("Unexpected tis reference type at.",
        history.tisReference().type(), is(TisReferenceType.PLACEMENT));
    assertThat("Unexpected tis reference id at.",
        history.tisReference().id(), is(TIS_REFERENCE_ID));
  }

  @Test
  void shouldFindSentHistoryForTraineeRefAndTypeWhenSentNotificationsExist() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history1);

    //not sent
    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, null, null, SCHEDULED, null, null);
    service.save(history2);

    //not same ref type
    ObjectId id3 = ObjectId.get();
    TisReferenceInfo tisReferenceInfo2
        = new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID);
    History history3 = new History(id3, tisReferenceInfo2, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history3);

    //not same ref
    ObjectId id4 = ObjectId.get();
    TisReferenceInfo tisReferenceInfo3
        = new TisReferenceInfo(TIS_REFERENCE_TYPE, "other id");
    History history4 = new History(id4, tisReferenceInfo3, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history4);

    List<History> history = service.findAllSentEmailForTraineeByRefAndType(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID, PLACEMENT_UPDATED_WEEK_12);

    assertThat("Unexpected history count.", history.size(), is(1));

    History returnedHistory1 = history.get(0);
    assertThat("Unexpected history id.", returnedHistory1.id(), is(id1));
    TisReferenceInfo referenceInfo2 = history.get(0).tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo2.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo2.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history sent at.", returnedHistory1.sentAt(), is(SENT_AT));
    assertThat("Unexpected history read at.", returnedHistory1.readAt(), is(READ_AT));
  }

  @Test
  void shouldSortSentHistoryForTraineeRefAndTypeWhenSentNotificationsExist() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant after = SENT_AT.plus(Duration.ofDays(1));
    ObjectId id0 = ObjectId.get();
    History history0 = new History(id0, tisReferenceInfo, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history0);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, after, READ_AT, SENT, null, null);
    service.save(history1);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, PLACEMENT_UPDATED_WEEK_12, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null);
    service.save(history2);

    List<History> history = service.findAllSentEmailForTraineeByRefAndType(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID, PLACEMENT_UPDATED_WEEK_12);

    assertThat("Unexpected history count.", history.size(), is(3));

    History returnedHistory1 = history.get(0);
    assertThat("Unexpected history id.", returnedHistory1.id(), is(id1));
    assertThat("Unexpected history sent at.", returnedHistory1.sentAt(), is(after));
  }

  @Test
  void shouldNotRebuildMessageWhenNotificationNotFound() {
    Optional<String> message = service.rebuildMessage(ObjectId.get().toString());

    assertThat("Unexpected message.", message, is(Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldRebuildEmailMessageWhenNotificationFound(NotificationType notificationType,
      String version) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(), version,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, notificationType,
        recipientInfo, templateInfo, null, Instant.now(), Instant.now(), SENT, null, null);
    service.save(history);

    Optional<String> message = service.rebuildMessage(NOTIFICATION_ID.toString());

    assertThat("Unexpected message presence.", message.isPresent(), is(true));

    Document content = Jsoup.parse(message.get());
    Element body = content.body();

    Element emailHeader = body.children().get(0);
    assertThat("Unexpected element tag.", emailHeader.tagName(), is("h1"));
    assertThat("Unexpected email header.", emailHeader.text(), is("Email Message"));

    Element subjectHeader = body.children().get(1);
    assertThat("Unexpected element tag.", subjectHeader.tagName(), is("h2"));
    assertThat("Unexpected subject header.", subjectHeader.text(), is("Subject"));

    Element bodyHeader = body.children().get(2);
    assertThat("Unexpected element tag.", bodyHeader.tagName(), is("h2"));
    assertThat("Unexpected body header.", bodyHeader.text(), is("Content"));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getInAppTemplateTypeAndVersions")
  void shouldRebuildInAppMessageWhenNotificationFound(NotificationType notificationType,
      String version) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(), version,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, notificationType,
        recipientInfo, templateInfo, null, Instant.now(), Instant.now(), SENT, null, null);
    service.save(history);

    Optional<String> message = service.rebuildMessage(NOTIFICATION_ID.toString());

    assertThat("Unexpected message presence.", message.isPresent(), is(true));

    Document content = Jsoup.parse(message.get());
    Element body = content.body();

    Element emailHeader = body.children().get(0);
    assertThat("Unexpected element tag.", emailHeader.tagName(), is("h1"));
    assertThat("Unexpected email header.", emailHeader.text(), is("In-App Notification"));

    Element subjectHeader = body.children().get(1);
    assertThat("Unexpected element tag.", subjectHeader.tagName(), is("h2"));
    assertThat("Unexpected subject header.", subjectHeader.text(), is("Subject"));

    Element bodyHeader = body.children().get(2);
    assertThat("Unexpected element tag.", bodyHeader.tagName(), is("h2"));
    assertThat("Unexpected body header.", bodyHeader.text(), is("Content"));
  }

  @Test
  void shouldNotRebuildMessageForTraineeWhenNotificationNotFound() {
    Optional<String> message = service.rebuildMessage(TRAINEE_ID, ObjectId.get().toString());

    assertThat("Unexpected message.", message, is(Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldRebuildEmailMessageForTraineeWhenNotificationFound(NotificationType notificationType,
      String version) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(), version,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, notificationType,
        recipientInfo, templateInfo, null, Instant.now(), Instant.now(), SENT, null, null);
    service.save(history);

    Optional<String> message = service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID.toString());

    assertThat("Unexpected message presence.", message.isPresent(), is(true));

    Document content = Jsoup.parse(message.get());
    Element body = content.body();

    Element emailHeader = body.children().get(0);
    assertThat("Unexpected element tag.", emailHeader.tagName(), is("h1"));
    assertThat("Unexpected email header.", emailHeader.text(), is("Email Message"));

    Element subjectHeader = body.children().get(1);
    assertThat("Unexpected element tag.", subjectHeader.tagName(), is("h2"));
    assertThat("Unexpected subject header.", subjectHeader.text(), is("Subject"));

    Element bodyHeader = body.children().get(2);
    assertThat("Unexpected element tag.", bodyHeader.tagName(), is("h2"));
    assertThat("Unexpected body header.", bodyHeader.text(), is("Content"));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getInAppTemplateTypeAndVersions")
  void shouldRebuildInAppMessageForTraineeWhenNotificationFound(NotificationType notificationType,
      String version) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(), version,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, notificationType,
        recipientInfo, templateInfo, null, Instant.now(), Instant.now(), UNREAD, null, null);
    service.save(history);

    Optional<String> message = service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID.toString());

    assertThat("Unexpected message presence.", message.isPresent(), is(true));

    Document content = Jsoup.parse(message.get());
    Element body = content.body();

    assertThat("Unexpected child count.", body.childNodeSize(), greaterThanOrEqualTo(1));

    body.children().forEach(
        contentNode -> assertThat("Unexpected node type.", contentNode.tagName(),
            either(is("p")).or(is("ul"))));
  }

  @Test
  void shouldUpdateStatusWhenNewStatusTimestampIsNewer() {
    // Given a history record with an older status timestamp
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo
        = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION, TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    Instant olderTimestamp = Instant.now().minus(Duration.ofDays(1));

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, PROGRAMME_DAY_ONE,
        recipientInfo, templateInfo, null, SENT_AT, READ_AT, PENDING, null, null, olderTimestamp);
    service.save(history);

    // When attempting to update with a newer timestamp
    Instant newerTimestamp = Instant.now();
    service.updateStatus(NOTIFICATION_ID.toString(), FAILED, "Update with newer timestamp",
        newerTimestamp);

    // Then the status should be updated
    Optional<History> updatedHistory = service.findAllHistoryForTrainee(TRAINEE_ID).stream()
        .filter(h -> h.id().equals(NOTIFICATION_ID))
        .findFirst();
    assertThat("History should be present", updatedHistory.isPresent(), is(true));
    assertThat("Unexpected status", updatedHistory.get().status(), is(FAILED));
    assertThat("Unexpected status detail", updatedHistory.get().statusDetail(),
        is("Update with newer timestamp"));
    assertThat("Unexpected status timestamp", updatedHistory.get().latestStatusEventAt(),
        is(newerTimestamp.truncatedTo(ChronoUnit.MILLIS)));
  }

  @Test
  void shouldNotUpdateStatusWhenNewStatusTimestampIsOlder() {
    // Given a history record with a newer status timestamp
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo
        = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION, TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    Instant newerTimestamp = Instant.now();

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null, newerTimestamp);
    service.save(history);

    // When attempting to update with an older timestamp
    Instant olderTimestamp = Instant.now().minus(Duration.ofDays(1));
    service.updateStatus(NOTIFICATION_ID.toString(), FAILED, "Update with older timestamp",
        olderTimestamp);

    // Then the status should not be updated
    Optional<History> updatedHistory = service.findAllHistoryForTrainee(TRAINEE_ID).stream()
        .filter(h -> h.id().equals(NOTIFICATION_ID))
        .findFirst();
    assertThat("History should be present", updatedHistory.isPresent(), is(true));
    assertThat("Status should not change", updatedHistory.get().status(), is(SENT));
    assertThat("Status timestamp should not change",
        updatedHistory.get().latestStatusEventAt(),
        is(newerTimestamp.truncatedTo(ChronoUnit.MILLIS)));
  }

  @Test
  void shouldUpdateStatusWhenCurrentTimestampIsNull() {
    // Given a history record with no status timestamp
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo
        = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION, TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(NOTIFICATION_ID, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, SENT_AT, READ_AT, SENT, null, null, null);
    service.save(history);

    // When updating with any timestamp
    Instant updateTimestamp = Instant.now();
    service.updateStatus(NOTIFICATION_ID.toString(), FAILED, "Update with new timestamp",
        updateTimestamp);

    // Then the status should be updated
    Optional<History> updatedHistory = service.findAllHistoryForTrainee(TRAINEE_ID).stream()
        .filter(h -> h.id().equals(NOTIFICATION_ID))
        .findFirst();
    assertThat("History should be present", updatedHistory.isPresent(), is(true));
    assertThat("Unexpected status", updatedHistory.get().status(), is(FAILED));
    assertThat("Unexpected status detail", updatedHistory.get().statusDetail(),
        is("Update with new timestamp"));
    assertThat("Unexpected status timestamp", updatedHistory.get().latestStatusEventAt(),
        is(updateTimestamp.truncatedTo(ChronoUnit.MILLIS)));
  }

  @Test
  void shouldMoveNotificationsFromOneTraineeToAnother() {
    String fromTraineeId = "40";
    History history1 = getBasicHistory(fromTraineeId, "old@test.com", EMAIL,
        NotificationType.PROGRAMME_CREATED, Instant.now());
    History history2 = getBasicHistory(fromTraineeId, "old@test.com", IN_APP,
        NotificationType.PROGRAMME_DAY_ONE, Instant.now());

    // Save initial notifications for fromTrainee
    service.save(history1);
    service.save(history2);

    // Move notifications
    List<History> beforeMove = service.findAllHistoryForTrainee(fromTraineeId);
    assertThat("Unexpected notifications before move.", beforeMove.size(), is(2));

    String toTraineeId = "50";
    service.moveNotifications(fromTraineeId, toTraineeId);

    // Verify notifications moved correctly
    List<History> afterMoveFrom = service.findAllHistoryForTrainee(fromTraineeId);
    List<History> afterMoveTo = service.findAllHistoryForTrainee(toTraineeId);

    assertThat("Unexpected notifications for source trainee.",
        afterMoveFrom.size(), is(0));
    assertThat("Unexpected notifications for target trainee.",
        afterMoveTo.size(), is(2));

    for (History h : afterMoveTo) {
      assertThat("Unexpected recipient ID.", h.recipient().id(), is(toTraineeId));
      assertThat("Unexpected recipient email.", h.recipient().contact(), is("old@test.com"));
      assertThat("Unexpected recipient type.", h.recipient().type(), is(oneOf(EMAIL, IN_APP)));
    }

    ArgumentCaptor<History> eventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService, times(2 + 2)) // +2 for the initial saves
        .publishNotificationsEvent(eventCaptor.capture());
    List<History> capturedEvents = eventCaptor.getAllValues();
    assertThat("Unexpected number of events published.", capturedEvents.size(), is(4));
    int withOldId = 0;
    int withNewId = 0;
    for (History event : capturedEvents) {
      if (event.recipient().id().equals(fromTraineeId)) {
        withOldId++;
      } else if (event.recipient().id().equals(toTraineeId)) {
        withNewId++;
      }
    }
    assertThat("Unexpected number of events with old trainee ID.", withOldId, is(2));
    assertThat("Unexpected number of events with new trainee ID.", withNewId, is(2));
  }

  @Test
  void shouldHandleEmptyNotificationsWhenMoving() {
    String fromTraineeId = "empty";
    String toTraineeId = "target";

    service.moveNotifications(fromTraineeId, toTraineeId);

    List<History> afterMoveFrom = service.findAllHistoryForTrainee(fromTraineeId);
    List<History> afterMoveTo = service.findAllHistoryForTrainee(toTraineeId);

    assertThat("Unexpected notifications for source trainee.",
        afterMoveFrom.size(), is(0));
    assertThat("Unexpected notifications for target trainee.",
        afterMoveTo.size(), is(0));
  }

  /**
   * Helper method to create a basic History object for testing.
   */
  private History getBasicHistory(String traineeId, String contact, MessageType type,
      NotificationType notificationType, Instant sentAt) {
    return History.builder()
        .id(new ObjectId())
        .recipient(new History.RecipientInfo(traineeId, type, contact))
        .type(notificationType)
        .template(new History.TemplateInfo("template", "1.0", Map.of()))
        .sentAt(sentAt)
        .status(SENT)
        .build();
  }
}
