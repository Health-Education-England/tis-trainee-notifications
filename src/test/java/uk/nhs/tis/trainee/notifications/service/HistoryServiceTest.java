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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.matcher.InstantCloseTo.closeTo;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.mapper.HistoryMapper;
import uk.nhs.tis.trainee.notifications.mapper.HistoryMapperImpl;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

class HistoryServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private static final String NOTIFICATION_ID = ObjectId.get().toString();
  private static final ObjectId HISTORY_ID = ObjectId.get();

  private static final String TEMPLATE_NAME = "test/template";
  private static final String TEMPLATE_VERSION = "v1.2.3";
  private static final Map<String, Object> TEMPLATE_VARIABLES = Map.of("key1", "value1");

  private static final TisReferenceType TIS_REFERENCE_TYPE = PLACEMENT;
  private static final String TIS_REFERENCE_ID = UUID.randomUUID().toString();

  private HistoryService service;
  private HistoryRepository repository;
  private TemplateService templateService;
  private EventBroadcastService eventBroadcastService;
  private final HistoryMapper mapper = new HistoryMapperImpl();
  private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp() {
    repository = mock(HistoryRepository.class);
    templateService = mock(TemplateService.class);
    eventBroadcastService = mock(EventBroadcastService.class);
    mongoTemplate = mock(MongoTemplate.class);
    service = new HistoryService(repository, templateService, eventBroadcastService,
        mapper, mongoTemplate);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSaveHistory(NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(null, null);
    Instant sent = Instant.now();
    Instant read = Instant.now().plus(Duration.ofDays(1));
    History history = new History(null, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, sent, read, SENT, null, null);

    ObjectId id = new ObjectId();
    when(repository.save(history)).then(inv -> {
      History saving = inv.getArgument(0);
      assertThat("Unexpected ID.", saving.id(), nullValue());
      return new History(id, saving.tisReference(), saving.type(), saving.recipient(),
          saving.template(), null, saving.sentAt(), saving.readAt(), SENT, null, null);
    });

    History savedHistory = service.save(history);

    assertThat("Unexpected ID.", savedHistory.id(), is(id));
    assertThat("Unexpected TIS reference.", savedHistory.tisReference(),
        is(tisReferenceInfo));
    assertThat("Unexpected type.", savedHistory.type(), is(notificationType));
    assertThat("Unexpected recipient.", savedHistory.recipient(), sameInstance(recipientInfo));
    assertThat("Unexpected template.", savedHistory.template(), sameInstance(templateInfo));
    assertThat("Unexpected sent at.", savedHistory.sentAt(), is(sent));
    assertThat("Unexpected read at.", savedHistory.readAt(), is(read));
    assertThat("Unexpected status.", savedHistory.status(), is(SENT));
    assertThat("Unexpected status detail.", savedHistory.statusDetail(), nullValue());

    //some Histories are saved with null id; the saved object will have a proper id assigned to it.
    verify(eventBroadcastService).publishNotificationsEvent(savedHistory);
  }

  @ParameterizedTest
  @EnumSource(NotificationStatus.class)
  void shouldNotUpdateStatusWhenHistoryNotFound(NotificationStatus status) {
    when(repository.findById(any())).thenReturn(Optional.empty());

    Optional<HistoryDto> updatedHistory = service.updateStatus(NOTIFICATION_ID, status,
        "Status: update", null);

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "PENDING", "SENT", "DELETED"})
  void shouldThrowExceptionWhenUpdatingEmailHistoryWithInvalidStatus(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        null, Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(NOTIFICATION_ID, status, "", null));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"ARCHIVED", "READ", "UNREAD", "SCHEDULED", "DELETED"})
  void shouldUpdateValidStatusWhenEmailHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, null, Instant.MIN, Instant.MAX, null, null, null);

    String templatePath = "email/test/template/v1.2.3";
    when(templateService.getTemplatePath(EMAIL, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<HistoryDto> updatedHistory = service.updateStatus(NOTIFICATION_ID, status,
        "Status: update", null);

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(true));
    HistoryDto history = updatedHistory.get();

    assertThat("Unexpected status.", history.status(), is(status));
    assertThat("Unexpected status detail.", history.statusDetail(), is("Status: update"));

    // Check other fields are unchanged.
    assertThat("Unexpected ID.", history.id(), is(notificationId.toString()));
    assertThat("Unexpected TIS reference.", history.tisReference(), is(tisReferenceInfo));
    assertThat("Unexpected type.", history.type(), is(EMAIL));
    assertThat("Unexpected subject.", history.subject(), is(COJ_CONFIRMATION));
    assertThat("Unexpected subject.", history.subjectText(), is("Test Subject"));
    assertThat("Unexpected contact.", history.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected sent at.", history.sentAt(), is(Instant.MIN));
    assertThat("Unexpected read at.", history.readAt(), is(Instant.MAX));

    ArgumentCaptor<History> historyPublished = ArgumentCaptor.forClass(History.class);
    verify(eventBroadcastService).publishNotificationsEvent(historyPublished.capture());

    History historyPublishedValue = historyPublished.getValue();
    HistoryDto publishedDto = mapper.toDto(historyPublishedValue, "Test Subject");
    assertThat("Unexpected history published.", publishedDto,
        is(history));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE, names = {"ARCHIVED", "READ",
      "SCHEDULED", "UNREAD", "DELETED"})
  void shouldThrowExceptionWhenUpdatingInAppHistoryWithInvalidStatus(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        null, Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(NOTIFICATION_ID, status, "", null));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "PENDING", "SENT", "DELETED"})
  void shouldUpdateValidStatusWhenInAppHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, null, Instant.MIN, Instant.MAX, null, null, null);

    String templatePath = "in-app/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<HistoryDto> updatedHistory = service.updateStatus(NOTIFICATION_ID, status,
        "Status: update", null);

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(true));
    HistoryDto history = updatedHistory.get();

    assertThat("Unexpected status.", history.status(), is(status));
    assertThat("Unexpected status detail.", history.statusDetail(), is("Status: update"));

    // Check other fields are unchanged.
    assertThat("Unexpected ID.", history.id(), is(notificationId.toString()));
    assertThat("Unexpected TIS reference.", history.tisReference(), is(tisReferenceInfo));
    assertThat("Unexpected type.", history.type(), is(IN_APP));
    assertThat("Unexpected subject.", history.subject(), is(COJ_CONFIRMATION));
    assertThat("Unexpected subject text.", history.subjectText(), is("Test Subject"));
    assertThat("Unexpected contact.", history.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected sent at.", history.sentAt(), is(Instant.MIN));
    assertThat("Unexpected read at.", history.readAt(), is(Instant.MAX));

    ArgumentCaptor<History> historyPublished = ArgumentCaptor.forClass(History.class);
    verify(eventBroadcastService).publishNotificationsEvent(historyPublished.capture());

    History historyPublishedValue = historyPublished.getValue();
    HistoryDto publishedDto = mapper.toDto(historyPublishedValue, "Test Subject");
    assertThat("Unexpected history published.", publishedDto, is(history));
  }

  @ParameterizedTest
  @EnumSource(NotificationStatus.class)
  void shouldNotUpdateStatusForTraineeWhenHistoryNotFound(NotificationStatus status) {
    when(repository.findByIdAndRecipient_Id(any(), any())).thenReturn(Optional.empty());

    Optional<HistoryDto> updatedHistory = service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status);

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "PENDING", "SENT"})
  void shouldThrowExceptionWhenUpdatingEmailHistoryForTraineeWithInvalidStatus(
      NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        null, Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findByIdAndRecipient_Id(notificationId, TRAINEE_ID)).thenReturn(
        Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE, names = {"ARCHIVED", "READ",
      "SCHEDULED", "UNREAD", "DELETED"})
  void shouldUpdateValidStatusForTraineeWhenEmailHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, null, Instant.MIN, Instant.MAX, null, null, null);

    String templatePath = "email/test/template/v1.2.3";
    when(templateService.getTemplatePath(EMAIL, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findByIdAndRecipient_Id(notificationId, TRAINEE_ID)).thenReturn(
        Optional.of(foundHistory));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<HistoryDto> updatedHistory = service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status);

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(true));
    HistoryDto history = updatedHistory.get();

    assertThat("Unexpected status.", history.status(), is(status));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());

    // Check other fields are unchanged.
    assertThat("Unexpected ID.", history.id(), is(notificationId.toString()));
    assertThat("Unexpected TIS reference.", history.tisReference(), is(tisReferenceInfo));
    assertThat("Unexpected type.", history.type(), is(EMAIL));
    assertThat("Unexpected subject.", history.subject(), is(COJ_CONFIRMATION));
    assertThat("Unexpected subject text.", history.subjectText(), is("Test Subject"));
    assertThat("Unexpected contact.", history.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected sent at.", history.sentAt(), is(Instant.MIN));
    assertThat("Unexpected read at.", history.readAt(), is(Instant.MAX));

    ArgumentCaptor<History> historyPublished = ArgumentCaptor.forClass(History.class);
    verify(eventBroadcastService).publishNotificationsEvent(historyPublished.capture());

    History historyPublishedValue = historyPublished.getValue();
    HistoryDto publishedDto = mapper.toDto(historyPublishedValue, "Test Subject");
    assertThat("Unexpected history published.", publishedDto, is(history));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE, names = {"ARCHIVED", "READ",
      "SCHEDULED", "UNREAD", "DELETED"})
  void shouldThrowExceptionWhenUpdatingInAppHistoryForTraineeWithInvalidStatus(
      NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        null, Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findByIdAndRecipient_Id(notificationId, TRAINEE_ID)).thenReturn(
        Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "PENDING", "SENT", "DELETED"})
  void shouldUpdateValidStatusForTraineeWhenInAppHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, null, Instant.MIN, Instant.MAX, null, null, null);

    String templatePath = "in-app/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findByIdAndRecipient_Id(notificationId, TRAINEE_ID)).thenReturn(
        Optional.of(foundHistory));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<HistoryDto> updatedHistory = service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status);

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(true));
    HistoryDto history = updatedHistory.get();

    assertThat("Unexpected status.", history.status(), is(status));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());

    // Check other fields are unchanged.
    assertThat("Unexpected ID.", history.id(), is(notificationId.toString()));
    assertThat("Unexpected TIS reference.", history.tisReference(), is(tisReferenceInfo));
    assertThat("Unexpected type.", history.type(), is(IN_APP));
    assertThat("Unexpected subject.", history.subject(), is(COJ_CONFIRMATION));
    assertThat("Unexpected subject text.", history.subjectText(), is("Test Subject"));
    assertThat("Unexpected contact.", history.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected sent at.", history.sentAt(), is(Instant.MIN));
    assertThat("Unexpected read at.", history.readAt(), is(Instant.MAX));

    ArgumentCaptor<History> historyPublished = ArgumentCaptor.forClass(History.class);
    verify(eventBroadcastService).publishNotificationsEvent(historyPublished.capture());

    History historyPublishedValue = historyPublished.getValue();
    HistoryDto publishedDto = mapper.toDto(historyPublishedValue, "Test Subject");
    assertThat("Unexpected history published.", publishedDto,
        is(history));
  }

  @Test
  void shouldFindNoOverdueNotificationsWhenNoneExist() {
    when(repository.findIdByStatusAndSentAtLessThanEqualOrderById(any(), any())).thenReturn(
        List.of());

    List<ObjectIdWrapper> allOverdue = service.findAllOverdue();

    assertThat("Unexpected overdue count.", allOverdue.size(), is(0));
  }

  @Test
  void shouldFindOverdueNotificationsWhenExists() {
    ObjectIdWrapper overdueId = new ObjectIdWrapper(ObjectId.get());
    when(repository.findIdByStatusAndSentAtLessThanEqualOrderById(any(), any())).thenReturn(
        List.of(overdueId));

    List<ObjectIdWrapper> allOverdue = service.findAllOverdue();

    assertThat("Unexpected overdue count.", allOverdue.size(), is(1));
    assertThat("Unexpected overdue ID.", allOverdue.get(0), sameInstance(overdueId));
  }

  @Test
  void shouldUseScheduledDueAtCurrentTimeWhenLookingForOverdueNotifications() {
    service.findAllOverdue();

    ArgumentCaptor<Instant> dueCaptor = ArgumentCaptor.captor();
    verify(repository).findIdByStatusAndSentAtLessThanEqualOrderById(eq(SCHEDULED),
        dueCaptor.capture());

    assertThat("Unexpected due timestamp.", dueCaptor.getValue(),
        closeTo(Instant.now().getEpochSecond(), 1));
  }

  @Test
  void shouldFindNoHistoryDtoForTraineeWhenNotificationsNotExist() {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(List.of());

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldFindHistoryDtosForTraineeWhenNotificationsExist(MessageType messageType,
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MAX, Instant.MIN, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1, history2));

    when(templateService.process(any(), any(), anyMap())).thenReturn("");

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(2));

    HistoryDto historyDto1 = historyDtos.get(0);
    assertThat("Unexpected history id.", historyDto1.id(), is(id1.toString()));
    TisReferenceInfo referenceInfo = historyDto1.tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history type.", historyDto1.type(), is(messageType));
    assertThat("Unexpected history subject.", historyDto1.subject(), is(notificationType));
    assertThat("Unexpected history contact.", historyDto1.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto1.sentAt(), is(Instant.MIN));
    assertThat("Unexpected history read at.", historyDto1.readAt(), is(Instant.MAX));

    HistoryDto historyDto2 = historyDtos.get(1);
    assertThat("Unexpected history id.", historyDto2.id(), is(id2.toString()));
    TisReferenceInfo referenceInfo2 = historyDto2.tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo2.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo2.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history type.", historyDto2.type(), is(messageType));
    assertThat("Unexpected history subject.", historyDto2.subject(), is(notificationType));
    assertThat("Unexpected history contact.", historyDto2.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto2.sentAt(), is(Instant.MAX));
    assertThat("Unexpected history read at.", historyDto2.readAt(), is(Instant.MIN));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldFindHistoryForTraineeWhenNotificationsExist(MessageType messageType,
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MAX, Instant.MIN, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1, history2));

    when(templateService.process(any(), any(), anyMap())).thenReturn("");

    List<History> histories = service.findAllHistoryForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", histories.size(), is(2));

    History historyReceived1 = histories.get(0);
    assertThat("Unexpected history id.", historyReceived1.id(), is(id1));
    TisReferenceInfo referenceInfo = historyReceived1.tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history type.", historyReceived1.type(), is(notificationType));
    RecipientInfo recipientInfoReceived = historyReceived1.recipient();
    assertThat("Unexpected history recipient type.", recipientInfoReceived.type(),
        is(messageType));
    assertThat("Unexpected history recipient contact.", recipientInfoReceived.contact(),
        is(TRAINEE_CONTACT));
    assertThat("Unexpected history recipient id.", recipientInfoReceived.id(),
        is(TRAINEE_ID));
    assertThat("Unexpected history sent at.", historyReceived1.sentAt(), is(Instant.MIN));
    assertThat("Unexpected history read at.", historyReceived1.readAt(), is(Instant.MAX));

    History historyReceived2 = histories.get(1);
    assertThat("Unexpected history id.", historyReceived2.id(), is(id2));
    TisReferenceInfo referenceInfo2 = historyReceived2.tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo2.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo2.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history type.", historyReceived2.type(), is(notificationType));
    RecipientInfo recipientInfoReceived2 = historyReceived2.recipient();
    assertThat("Unexpected history recipient type.", recipientInfoReceived2.type(),
        is(messageType));
    assertThat("Unexpected history recipient contact.", recipientInfoReceived2.contact(),
        is(TRAINEE_CONTACT));
    assertThat("Unexpected history recipient id.", recipientInfoReceived2.id(),
        is(TRAINEE_ID));
    assertThat("Unexpected history sent at.", historyReceived2.sentAt(), is(Instant.MAX));
    assertThat("Unexpected history read at.", historyReceived2.readAt(), is(Instant.MIN));
  }

  @Test
  void shouldFindNoHistoryForTraineeWhenSentNotificationsNotExist() {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(List.of());

    List<HistoryDto> historyDtos = service.findAllSentForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldFindHistoryForTraineeWhenSentNotificationsExist(MessageType messageType,
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    Instant timeNow = Instant.now();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, timeNow, Instant.MIN, SENT, null, null);

    ObjectId id3 = ObjectId.get();
    History history3 = new History(id3, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MAX, Instant.MIN, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history3, history2, history1));

    when(templateService.process(any(), any(), anyMap())).thenReturn("");

    List<HistoryDto> historyDtos = service.findAllSentForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(2));

    HistoryDto historyDto2 = historyDtos.get(0);
    assertThat("Unexpected history id.", historyDto2.id(), is(id2.toString()));
    TisReferenceInfo referenceInfo2 = historyDtos.get(0).tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo2.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo2.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history type.", historyDto2.type(), is(messageType));
    assertThat("Unexpected history subject.", historyDto2.subject(), is(notificationType));
    assertThat("Unexpected history contact.", historyDto2.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto2.sentAt(), is(timeNow));
    assertThat("Unexpected history read at.", historyDto2.readAt(), is(Instant.MIN));

    HistoryDto historyDto1 = historyDtos.get(1);
    assertThat("Unexpected history id.", historyDto1.id(), is(id1.toString()));
    TisReferenceInfo referenceInfo = historyDtos.get(0).tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history type.", historyDto1.type(), is(messageType));
    assertThat("Unexpected history subject.", historyDto1.subject(), is(notificationType));
    assertThat("Unexpected history contact.", historyDto1.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto1.sentAt(), is(Instant.MIN));
    assertThat("Unexpected history read at.", historyDto1.readAt(), is(Instant.MAX));
  }

  @Test
  void shouldGetPagedHistorySummaries() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    Instant timeNow = Instant.now();
    History history2 = new History(id2, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, timeNow, Instant.MIN, SENT, null, null);

    ObjectId id3 = ObjectId.get();
    History history3 = new History(id3, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, Instant.MAX, Instant.MIN, SENT, null, null);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    when(mongoTemplate.find(queryCaptor.capture(), eq(History.class))).thenReturn(
        List.of(history1, history2, history3));
    when(mongoTemplate.count(queryCaptor.capture(), eq(History.class))).thenReturn(3L);
    when(templateService.getTemplatePath(EMAIL, templateInfo.name(), templateInfo.version()))
        .thenReturn("path");
    when(templateService.process(anyString(), any(), (Map<String, Object>) any()))
        .thenReturn("subject text");

    Page<HistoryDto> dtos = service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(),
        PageRequest.of(1, 1));

    assertThat("Unexpected total elements.", dtos.getTotalElements(), is(3L));
    assertThat("Unexpected total pages.", dtos.getTotalPages(), is(3));
    assertThat("Unexpected pageable.", dtos.getPageable().isPaged(), is(true));
    assertThat("Unexpected page number.", dtos.getPageable().getPageNumber(), is(1));
    assertThat("Unexpected page size.", dtos.getPageable().getPageSize(), is(1));

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected limited flag.", queries.get(0).isLimited(), is(true));
    assertThat("Unexpected limit.", queries.get(0).getLimit(), is(1));
    assertThat("Unexpected skip.", queries.get(0).getSkip(), is(1L));

    // The second query is the count, which is unpaged.
    assertThat("Unexpected limited flag.", queries.get(1).isLimited(), is(false));
    assertThat("Unexpected limit.", queries.get(1).getLimit(), is(0));
    assertThat("Unexpected skip.", queries.get(1).getSkip(), is(-1L));
  }

  @Test
  void shouldGetUnpagedHistorySummaries() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    Instant timeNow = Instant.now();
    History history2 = new History(id2, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, timeNow, Instant.MIN, SENT, null, null);

    ObjectId id3 = ObjectId.get();
    History history3 = new History(id3, tisReferenceInfo, FORM_UPDATED, recipientInfo,
        templateInfo, null, Instant.MAX, Instant.MIN, SENT, null, null);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    when(mongoTemplate.find(queryCaptor.capture(), eq(History.class))).thenReturn(
        List.of(history1, history2, history3));
    when(mongoTemplate.find(queryCaptor.capture(), eq(History.class))).thenReturn(
        List.of(history1, history2, history3));
    when(mongoTemplate.count(queryCaptor.capture(), eq(History.class))).thenReturn(3L);
    when(templateService.getTemplatePath(EMAIL, templateInfo.name(), templateInfo.version()))
        .thenReturn("path");
    when(templateService.process(anyString(), any(), (Map<String, Object>) any()))
        .thenReturn("subject text");

    Page<HistoryDto> dtos = service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(),
        Pageable.unpaged());

    assertThat("Unexpected total elements.", dtos.getTotalElements(), is(3L));
    assertThat("Unexpected total pages.", dtos.getTotalPages(), is(1));
    assertThat("Unexpected pageable.", dtos.getPageable().isPaged(), is(false));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected limited flag.", query.isLimited(), is(false));
    assertThat("Unexpected limit.", query.getLimit(), is(0));
    assertThat("Unexpected skip.", query.getSkip(), is(0L));

    verify(mongoTemplate, never()).count(any(), eq(History.class));
  }

  @Test
  void shouldIncludeOnlySentWhenGettingHistorySummaries() {
    service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(History.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(History.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));

      Document sentAtFilter = queryObject.get("sentAt", Document.class);
      assertThat("Unexpected filter key count.", sentAtFilter.keySet(), hasSize(1));
      assertThat("Unexpected filter key.", sentAtFilter.keySet(), hasItem("$lt"));
      assertThat("Unexpected filter value.", sentAtFilter.get("$lt"), Matchers.notNullValue());
    });
  }

  @Test
  void shouldIncludeOnlyHistoriesOfTheTraineeWhenGettingHistorySummaries() {
    service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(History.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(History.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));
      assertThat("Unexpected filter value.", queryObject.get("recipient.id"), is(TRAINEE_ID));
    });
  }

  @Test
  void shouldApplyFiltersWhenGettingHistorySummaries() {
    service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(
        "type", "filterValue1",
        "status", "filterValue2"
    ), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(History.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(History.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(4));
      assertThat("Unexpected type filter value.", queryObject.get("recipient.type"),
          is("filterValue1"));
      assertThat("Unexpected status filter value.", queryObject.get("status"),
          is("filterValue2"));
    });
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      status | status
      subject | type
      sentAt | sentAt
      contact | recipient.contact
      """)
  void shouldApplySortWhenGettingUnpagedHistorySummaries(String external, String internal) {
    service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(),
        Pageable.unpaged(Sort.by(Sort.Order.asc(external))));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(History.class));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected sorted flag.", query.isSorted(), is(true));

    Document sortObject = query.getSortObject();
    assertThat("Unexpected sort count.", sortObject.keySet(), hasSize(1));
    assertThat("Unexpected sort direction.", sortObject.get(internal), is(1));

    verify(mongoTemplate, never()).count(any(), eq(History.class));
  }

  @Test
  void shouldApplySearchFiltersWhenGettingHistorySummaries() {
    service.findAllSentInPageForTrainee(TRAINEE_ID, Map.of(
        "keyword", "filterValue"
    ), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(History.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(History.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter key.", queryObject.keySet(), hasItem("$or"));

      List<Document> keywordFilter = queryObject.get("$or", List.class);
      assertThat("Unexpected filter value count.", keywordFilter, hasSize(4));

      Set<String> searchFields = Set.of("status", "type", "sentAt", "recipient.contact");
      Set<String> foundFields = new HashSet<>();

      for (Document filter : keywordFilter) {
        for (String key : searchFields) {
          if (filter.containsKey(key) && filter.get(key) != null) {
            foundFields.add(key);
            assertThat("Status filter value mismatch.", filter.get(key).toString(),
                containsString("filterValue"));
          }
        }
      }
      // make sure all search fields are included
      assertThat("Missing filters for some fields.", foundFields, equalTo(searchFields));
    });
  }

  @Test
  void shouldFindNoSentHistoryForTraineeRefAndTypeWhenNotificationsNotExist() {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(List.of());

    List<History> history = service.findAllSentEmailForTraineeByRefAndType(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID, PROGRAMME_CREATED);

    assertThat("Unexpected history count.", history.size(), is(0));
  }

  @Test
  void shouldFindAndSortSentHistoryForTraineeRefAndType() {
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History.RecipientInfo recipient = new History.RecipientInfo(TRAINEE_ID, EMAIL, null);
    History historyExample = History.builder()
        .recipient(recipient)
        .tisReference(tisReferenceInfo)
        .type(PLACEMENT_UPDATED_WEEK_12)
        .status(SENT)
        .build();
    Example<History> example = Example.of(historyExample);
    Sort sort = Sort.by("sentAt").descending();

    service.findAllSentEmailForTraineeByRefAndType(TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID,
        PLACEMENT_UPDATED_WEEK_12);

    verify(repository).findAll(example, sort);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void shouldFindNoHistoryForTraineeWhenScheduledInAppNotificationsNotExist() {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(List.of());

    List<History> history = service.findAllScheduledForTrainee(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", history.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldFindHistoryForTraineeWhenScheduledNotificationsExist(MessageType messageType,
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    Instant timeNow = Instant.now();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, timeNow, Instant.MIN, SENT, null, null);

    ObjectId id3 = ObjectId.get();
    History history3 = new History(id3, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MAX, Instant.MIN, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history3, history2, history1));

    when(templateService.process(any(), any(), anyMap())).thenReturn("");

    List<History> history = service.findAllScheduledForTrainee(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", history.size(), is(1));

    History returnedHistory1 = history.get(0);
    assertThat("Unexpected history id.", returnedHistory1.id(), is(id3));
    TisReferenceInfo referenceInfo2 = history.get(0).tisReference();
    assertThat("Unexpected history TIS reference type.", referenceInfo2.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected history TIS reference id.", referenceInfo2.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected history sent at.", returnedHistory1.sentAt(), is(Instant.MAX));
    assertThat("Unexpected history read at.", returnedHistory1.readAt(), is(Instant.MIN));
  }

  @Test
  void shouldFindNoFailedHistoryForTraineeWhenFailedNotificationsNotExist() {
    when(repository.findAllByRecipient_IdAndStatus(TRAINEE_ID, FAILED.name()))
        .thenReturn(List.of());

    List<History> failed = service.findAllFailedForTrainee(TRAINEE_ID);

    assertThat("Unexpected failed count.", failed.size(), is(0));
  }

  @Test
  void shouldFindFailedHistoryForTraineeWhenFailedNotificationsExist() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, PROGRAMME_CREATED, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, FAILED, null, Instant.MAX);

    when(repository.findAllByRecipient_IdAndStatus(TRAINEE_ID, FAILED.name()))
        .thenReturn(List.of(history1));

    List<History> failures = service.findAllFailedForTrainee(TRAINEE_ID);

    assertThat("Unexpected failed history count.", failures.size(), is(1));

    History failed1 = failures.get(0);
    assertThat("Unexpected failed history id.", failed1.id(), is(id1));
    TisReferenceInfo referenceInfo = failed1.tisReference();
    assertThat("Unexpected failed history TIS reference type.", referenceInfo.type(),
        is(TIS_REFERENCE_TYPE));
    assertThat("Unexpected failed history TIS reference id.", referenceInfo.id(),
        is(TIS_REFERENCE_ID));
    assertThat("Unexpected failed history notification type.", referenceInfo.type(),
        is(TIS_REFERENCE_TYPE));
    RecipientInfo recipientInfo1 = failed1.recipient();
    assertThat("Unexpected failed history contact.", recipientInfo1.contact(),
        is(TRAINEE_CONTACT));
    assertThat("Unexpected failed history message type.", recipientInfo1.type(),
        is(EMAIL));
    assertThat("Unexpected failed history contact id.", recipientInfo1.id(),
        is(TRAINEE_ID));
    assertThat("Unexpected failed history type.", failed1.type(), is(PROGRAMME_CREATED));
    assertThat("Unexpected failed history sent at.", failed1.sentAt(), is(Instant.MIN));
    assertThat("Unexpected failed history read at.", failed1.readAt(), is(Instant.MAX));
    assertThat("Unexpected failed history status.", failed1.status(), is(FAILED));
    assertThat("Unexpected failed history last retry.", failed1.lastRetry(), is(Instant.MAX));
  }

  @Test
  void shouldDeleteHistoryForTrainee() {

    service.deleteHistoryForTrainee(HISTORY_ID, TRAINEE_ID);

    verify(repository).deleteByIdAndRecipient_Id(HISTORY_ID, TRAINEE_ID);
    verify(eventBroadcastService).publishNotificationsDeleteEvent(HISTORY_ID);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldPopulateSubjectInFoundHistoryWhenInAppNotificationWithSubject(
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1));

    String templatePath = "type/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "rebuiltSubject");

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(1));

    HistoryDto historyDto = historyDtos.get(0);
    assertThat("Unexpected history subject text.", historyDto.subjectText(), is("rebuiltSubject"));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotPopulateSubjectInFoundHistoryWhenInAppNotificationWithNoSubject(
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1));

    String templatePath = "type/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "");

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(1));

    HistoryDto historyDto = historyDtos.get(0);
    assertThat("Unexpected history subject text.", historyDto.subjectText(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotPopulateSubjectInFoundHistoryWhenEmailNotification(
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    String templatePath = "email/test/template/v1.2.3";
    when(templateService.getTemplatePath(EMAIL, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1));

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(1));

    HistoryDto historyDto = historyDtos.get(0);
    assertThat("Unexpected history subject text.", historyDto.subjectText(), notNullValue());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetScheduledStatusInFoundHistoryWhenInAppNotificationWithFutureSentAt(
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MAX, null, UNREAD, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1));

    String templatePath = "type/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "rebuiltSubject");

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(1));

    HistoryDto historyDto = historyDtos.get(0);
    assertThat("Unexpected history status.", historyDto.status(), is(SCHEDULED));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotChangeStatusInFoundHistoryWhenInAppNotificationWithPastSentAt(
      NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.MIN, null, UNREAD, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1));

    String templatePath = "type/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "rebuiltSubject");

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(1));

    HistoryDto historyDto = historyDtos.get(0);
    assertThat("Unexpected history status.", historyDto.status(), is(UNREAD));
  }

  @Test
  void shouldNotRebuildMessageWhenNotificationNotFound() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    Optional<String> message = service.rebuildMessage(NOTIFICATION_ID);

    assertThat("Unexpected message.", message, is(Optional.empty()));

    verifyNoInteractions(templateService);
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldRebuildMessageWhenNotificationFound(MessageType messageType,
      NotificationType notificationType) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(notificationId, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.now(), Instant.now(), SENT, null, null);
    when(repository.findById(any())).thenReturn(Optional.of(history));

    String templatePath = "type/test/template/v1.2.3";
    when(templateService.getTemplatePath(messageType, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);

    String message = """
        <html>
          <p>Rebuilt message</p>
        </html>""";
    when(templateService.process(templatePath, Set.of(), TEMPLATE_VARIABLES)).thenReturn(message);

    Optional<String> rebuiltMessage = service.rebuildMessage(NOTIFICATION_ID);

    assertThat("Unexpected message presence.", rebuiltMessage.isPresent(), is(true));
    assertThat("Unexpected message.", rebuiltMessage.get(), is(message));
  }

  @Test
  void shouldNotRebuildMessageForTraineeWhenNotificationNotFound() {
    when(repository.findByIdAndRecipient_Id(any(), any())).thenReturn(Optional.empty());

    Optional<String> message = service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID);

    assertThat("Unexpected message.", message, is(Optional.empty()));

    verifyNoInteractions(templateService);
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldRebuildMessageForTraineeWhenNotificationFound(MessageType messageType,
      NotificationType notificationType) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    History history = new History(notificationId, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, null, Instant.now(), Instant.now(), SENT, null, null);
    when(repository.findByIdAndRecipient_Id(any(), any())).thenReturn(Optional.of(history));

    String templatePath = "type/test/template/v1.2.3";
    when(templateService.getTemplatePath(messageType, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);

    String message = """
        <html>
          <p>Rebuilt message</p>
        </html>""";
    when(templateService.process(eq(templatePath), any(), eq(TEMPLATE_VARIABLES))).thenReturn(
        message);

    Optional<String> rebuiltMessage = service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID);

    assertThat("Unexpected message presence.", rebuiltMessage.isPresent(), is(true));
    assertThat("Unexpected message.", rebuiltMessage.get(), is(message));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldRebuildMessageForTraineeWithContentSelectorWhenInAppNotificationFound(
      NotificationType notificationType) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(null, IN_APP, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, TEMPLATE_VARIABLES);
    History history = new History(notificationId, null, notificationType, recipientInfo,
        templateInfo, null, null, null, null, null, null);

    when(repository.findByIdAndRecipient_Id(any(), any())).thenReturn(Optional.of(history));
    when(templateService.process(any(), any(), eq(TEMPLATE_VARIABLES))).thenReturn("");

    service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID);

    verify(templateService).process(any(), eq(Set.of("content")), anyMap());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldRebuildMessageForTraineeWithNoSelectorWhenEmailNotificationFound(
      NotificationType notificationType) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(null, EMAIL, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, TEMPLATE_VARIABLES);
    History history = new History(notificationId, null, notificationType, recipientInfo,
        templateInfo, null, null, null, null, null, null);

    when(repository.findByIdAndRecipient_Id(any(), any())).thenReturn(Optional.of(history));
    when(templateService.process(any(), any(), eq(TEMPLATE_VARIABLES))).thenReturn("");

    service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID);

    verify(templateService).process(any(), eq(Set.of()), anyMap());
  }

  @Test
  void shouldNotUpdateStatusWhenTimestampIsOlderThanExisting() {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant existingTimestamp = Instant.now();
    Instant olderTimestamp = existingTimestamp.minusSeconds(60);

    History foundHistory = new History(notificationId, tisReferenceInfo, PROGRAMME_CREATED,
        recipientInfo, templateInfo, null, Instant.now(), null, UNREAD, null, null,
        existingTimestamp);

    String templatePath = "email/test/template/v1.2.3";
    when(templateService.getTemplatePath(EMAIL, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));
    when(repository.updateStatusIfNewer(notificationId, olderTimestamp, SENT, null)).thenReturn(0);

    Optional<HistoryDto> updatedHistory = service.updateStatus(NOTIFICATION_ID, SENT, null,
        olderTimestamp);
    HistoryDto expectedHistory = mapper.toDto(foundHistory, "Test Subject");

    assertThat("Unexpected updated history.", updatedHistory,
        is(Optional.of(expectedHistory)));
    verify(repository, never()).save(any());
    verify(repository).updateStatusIfNewer(notificationId, olderTimestamp, SENT, null);
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldUpdateStatusWhenTimestampIsNewerThanExisting() {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    Instant existingTimestamp = Instant.now();
    Instant newerTimestamp = existingTimestamp.plusSeconds(60);
    String statusDetail = "Updated status detail";

    History foundHistory = new History(notificationId, tisReferenceInfo, PROGRAMME_CREATED,
        recipientInfo, templateInfo, null, Instant.now(), null, UNREAD, null, null,
        existingTimestamp);

    History updatedHistory = new History(notificationId, tisReferenceInfo, PROGRAMME_CREATED,
        recipientInfo, templateInfo, null, Instant.now(), null, SENT, statusDetail, null,
        newerTimestamp);

    String templatePath = "email/test/template/v1.2.3";
    when(templateService.getTemplatePath(EMAIL, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));
    when(repository.updateStatusIfNewer(notificationId, newerTimestamp, SENT, statusDetail))
        .thenReturn(1);
    when(repository.findById(notificationId)).thenReturn(Optional.of(updatedHistory));

    Optional<HistoryDto> result = service.updateStatus(NOTIFICATION_ID, SENT, statusDetail,
        newerTimestamp);
    HistoryDto expectedHistory = mapper.toDto(updatedHistory, "Test Subject");

    assertThat("Unexpected updated history.", result,
        is(Optional.of(expectedHistory)));
    verify(repository, never()).save(any());
    verify(repository).updateStatusIfNewer(notificationId, newerTimestamp, SENT, statusDetail);
    verify(eventBroadcastService).publishNotificationsEvent(updatedHistory);
  }

  @Test
  void shouldNotMoveNotificationsWhenNoHistoryExists() {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc("oldId")).thenReturn(List.of());

    Map<String, Integer> movedStats = service.moveNotifications("oldId", "newId");

    Map<String, Integer> expectedMap = Map.of("notification", 0);
    assertThat("Unexpected moved form count.", movedStats, Matchers.is(expectedMap));

    verify(repository).findAllByRecipient_IdOrderBySentAtDesc("oldId");
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldMoveNotificationsWhenHistoryExists() {
    RecipientInfo oldRecipient = new RecipientInfo("oldId", EMAIL, "old@test.com");
    RecipientInfo newRecipient = new RecipientInfo("newId", EMAIL, "old@test.com");
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, PROGRAMME_CREATED, oldRecipient,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, PROGRAMME_DAY_ONE, oldRecipient,
        templateInfo, null, Instant.now(), null, UNREAD, null, null);

    History updatedHistory1 = new History(id1, tisReferenceInfo, PROGRAMME_CREATED, newRecipient,
        templateInfo, null, Instant.MIN, Instant.MAX, SENT, null, null);
    History updatedHistory2 = new History(id2, tisReferenceInfo, PROGRAMME_DAY_ONE, newRecipient,
        templateInfo, null, Instant.now(), null, UNREAD, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc("oldId"))
        .thenReturn(List.of(history1, history2));
    when(repository.save(any()))
        .thenReturn(updatedHistory1)
        .thenReturn(updatedHistory2);

    Map<String, Integer> movedStats = service.moveNotifications("oldId", "newId");

    Map<String, Integer> expectedMap = Map.of("notification", 2);
    assertThat("Unexpected moved form count.", movedStats, Matchers.is(expectedMap));

    ArgumentCaptor<History> savedHistoryCaptor = ArgumentCaptor.forClass(History.class);
    verify(repository, times(2)).save(savedHistoryCaptor.capture());

    List<History> savedHistories = savedHistoryCaptor.getAllValues();
    assertThat("Unexpected number of saved histories.", savedHistories.size(), is(2));

    History savedHistory1 = savedHistories.get(0);
    assertThat("Unexpected recipient ID.", savedHistory1.recipient().id(), is("newId"));
    assertThat("Unexpected history details.", savedHistory1.withRecipient(oldRecipient),
        is(history1));

    History savedHistory2 = savedHistories.get(1);
    assertThat("Unexpected recipient ID.", savedHistory2.recipient().id(), is("newId"));
    assertThat("Unexpected history details.", savedHistory2.withRecipient(oldRecipient),
        is(history2));

    verify(eventBroadcastService).publishNotificationsEvent(updatedHistory1);
    verify(eventBroadcastService).publishNotificationsEvent(updatedHistory2);
  }
}
