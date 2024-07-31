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
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

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
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class HistoryServiceIntegrationTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private static final ObjectId NOTIFICATION_ID = ObjectId.get();

  private static final String TEMPLATE_NAME = "email/test-template";
  private static final String TEMPLATE_VERSION = "v1.2.3";
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
        templateInfo, SENT_AT, READ_AT, SENT, null, null);
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
  void shouldNotFindNotificationsWhenTraineeIdNotMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, SENT_AT, READ_AT, SENT, null, null);
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
        templateInfo, SENT_AT, READ_AT, SENT, null, null);
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
        now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        after, before, SENT, null, null));

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
        templateInfo, SENT_AT, READ_AT, SENT, null, null);
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
        templateInfo, SENT_AT, READ_AT, SENT, null, null);
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
        now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        after, before, SENT, null, null));

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
        now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        after, before, SENT, null, null));

    List<HistoryDto> foundHistory = service.findAllSentForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(2));

    HistoryDto history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(now));

    HistoryDto history2 = foundHistory.get(1);
    assertThat("Unexpected history sent at.", history2.sentAt(), is(before));
  }

  @Test
  void shouldSortFoundScheduledInAppNotificationsBySentAt() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        after, before, SENT, null, null));

    List<History> foundHistory = service.findAllScheduledInAppForTrainee(
        TRAINEE_ID, TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(1));

    History history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(after));
  }

  @Test
  void shouldNotSortEmailNotificationsBySentAt() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        now, now, SENT, null, null));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        before, after, SENT, null, null));

    service.save(new History(null, tisReferenceInfo, FORM_UPDATED, recipientInfo, templateInfo,
        after, before, SENT, null, null));

    List<History> foundHistory = service.findAllScheduledInAppForTrainee(
        TRAINEE_ID, TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(0));
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
        after, after, SENT, null, null));

    service.save(new History(null, tisRefInfoPlacement, FORM_UPDATED, recipientInfo, templateInfo,
        after, after, SENT, null, null));

    service.save(new History(null, tisRefInfoPlacement2, FORM_UPDATED, recipientInfo, templateInfo,
        after, after, SENT, null, null));

    List<History> foundHistory = service.findAllScheduledInAppForTrainee(
        TRAINEE_ID, TisReferenceType.PLACEMENT, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(1));

    History history = foundHistory.get(0);
    assertThat("Unexpected tis reference type at.",
        history.tisReference().type(), is(TisReferenceType.PLACEMENT));
    assertThat("Unexpected tis reference id at.",
        history.tisReference().id(), is(TIS_REFERENCE_ID));
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
        recipientInfo, templateInfo, Instant.now(), Instant.now(), SENT, null, null);
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
        recipientInfo, templateInfo, Instant.now(), Instant.now(), SENT, null, null);
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
        recipientInfo, templateInfo, Instant.now(), Instant.now(), SENT, null, null);
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
        recipientInfo, templateInfo, Instant.now(), Instant.now(), UNREAD, null, null);
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
}
