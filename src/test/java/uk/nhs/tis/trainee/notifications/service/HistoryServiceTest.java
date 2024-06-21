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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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

  private static final TisReferenceType TIS_REFERENCE_TYPE = TisReferenceType.PLACEMENT;
  private static final String TIS_REFERENCE_ID = UUID.randomUUID().toString();

  private HistoryService service;
  private HistoryRepository repository;
  private TemplateService templateService;
  private EventBroadcastService eventBroadcastService;
  private HistoryMapper mapper = new HistoryMapperImpl();

  @BeforeEach
  void setUp() {
    repository = mock(HistoryRepository.class);
    templateService = mock(TemplateService.class);
    eventBroadcastService = mock(EventBroadcastService.class);
    service = new HistoryService(repository, templateService, eventBroadcastService,
        mapper);
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
        templateInfo, sent, read, SENT, null, null);

    ObjectId id = new ObjectId();
    when(repository.save(history)).then(inv -> {
      History saving = inv.getArgument(0);
      assertThat("Unexpected ID.", saving.id(), nullValue());
      return new History(id, saving.tisReference(), saving.type(), saving.recipient(),
          saving.template(), saving.sentAt(), saving.readAt(), SENT, null, null);
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
        "Status: update");

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "SENT", "DELETED"})
  void shouldThrowExceptionWhenUpdatingEmailHistoryWithInvalidStatus(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(NOTIFICATION_ID, status, ""));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"ARCHIVED", "READ", "UNREAD", "DELETED"})
  void shouldUpdateValidStatusWhenEmailHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<HistoryDto> updatedHistory = service.updateStatus(NOTIFICATION_ID, status,
        "Status: update");

    assertThat("Unexpected history presence.", updatedHistory.isPresent(), is(true));
    HistoryDto history = updatedHistory.get();

    assertThat("Unexpected status.", history.status(), is(status));
    assertThat("Unexpected status detail.", history.statusDetail(), is("Status: update"));

    // Check other fields are unchanged.
    assertThat("Unexpected ID.", history.id(), is(notificationId.toString()));
    assertThat("Unexpected TIS reference.", history.tisReference(), is(tisReferenceInfo));
    assertThat("Unexpected type.", history.type(), is(EMAIL));
    assertThat("Unexpected subject.", history.subject(), is(COJ_CONFIRMATION));
    assertThat("Unexpected subject.", history.subjectText(), nullValue());
    assertThat("Unexpected contact.", history.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected sent at.", history.sentAt(), is(Instant.MIN));
    assertThat("Unexpected read at.", history.readAt(), is(Instant.MAX));

    ArgumentCaptor<History> historyPublished = ArgumentCaptor.forClass(History.class);
    verify(eventBroadcastService).publishNotificationsEvent(historyPublished.capture());

    History historyPublishedValue = historyPublished.getValue();
    assertThat("Unexpected history published.", mapper.toDto(historyPublishedValue),
        is(history));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"ARCHIVED", "READ", "UNREAD", "DELETED"})
  void shouldThrowExceptionWhenUpdatingInAppHistoryWithInvalidStatus(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(NOTIFICATION_ID, status, ""));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "SENT", "DELETED"})
  void shouldUpdateValidStatusWhenInAppHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, Instant.MIN, Instant.MAX, null, null, null);

    String templatePath = "in-app/test/template/v1.2.3";
    when(templateService.getTemplatePath(IN_APP, TEMPLATE_NAME, TEMPLATE_VERSION)).thenReturn(
        templatePath);
    when(templateService.process(templatePath, Set.of("subject"), TEMPLATE_VARIABLES)).thenReturn(
        "Test Subject");

    when(repository.findById(notificationId)).thenReturn(Optional.of(foundHistory));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<HistoryDto> updatedHistory = service.updateStatus(NOTIFICATION_ID, status,
        "Status: update");

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
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE, names = {"FAILED", "SENT"})
  void shouldThrowExceptionWhenUpdatingEmailHistoryForTraineeWithInvalidStatus(
      NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findByIdAndRecipient_Id(notificationId, TRAINEE_ID)).thenReturn(
        Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"ARCHIVED", "READ", "UNREAD", "DELETED"})
  void shouldUpdateValidStatusForTraineeWhenEmailHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, Instant.MIN, Instant.MAX, null, null, null);

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
    assertThat("Unexpected subject text.", history.subjectText(), nullValue());
    assertThat("Unexpected contact.", history.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected sent at.", history.sentAt(), is(Instant.MIN));
    assertThat("Unexpected read at.", history.readAt(), is(Instant.MAX));

    ArgumentCaptor<History> historyPublished = ArgumentCaptor.forClass(History.class);
    verify(eventBroadcastService).publishNotificationsEvent(historyPublished.capture());

    History historyPublishedValue = historyPublished.getValue();
    assertThat("Unexpected history published.", mapper.toDto(historyPublishedValue),
        is(history));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"ARCHIVED", "READ", "UNREAD", "DELETED"})
  void shouldThrowExceptionWhenUpdatingInAppHistoryForTraineeWithInvalidStatus(
      NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    History foundHistory = new History(notificationId, null, COJ_CONFIRMATION, recipientInfo, null,
        Instant.MIN, Instant.MAX, null, null, null);

    when(repository.findByIdAndRecipient_Id(notificationId, TRAINEE_ID)).thenReturn(
        Optional.of(foundHistory));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status));

    verify(repository, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE,
      names = {"FAILED", "SENT", "DELETED"})
  void shouldUpdateValidStatusForTraineeWhenInAppHistoryFound(NotificationStatus status) {
    ObjectId notificationId = new ObjectId(NOTIFICATION_ID);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, IN_APP, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History foundHistory = new History(notificationId, tisReferenceInfo, COJ_CONFIRMATION,
        recipientInfo, templateInfo, Instant.MIN, Instant.MAX, null, null, null);

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
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, Instant.MAX, Instant.MIN, SENT, null, null);

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
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, Instant.MAX, Instant.MIN, SENT, null, null);

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
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    Instant timeNow = Instant.now();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, timeNow, Instant.MIN, SENT, null, null);

    ObjectId id3 = ObjectId.get();
    History history3 = new History(id3, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, Instant.MAX, Instant.MIN, SENT, null, null);

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
  void shouldFindNoHistoryForTraineeWhenScheduledInAppNotificationsNotExist() {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(List.of());

    List<History> history = service.findAllScheduledInAppForTrainee(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    assertThat("Unexpected history count.", history.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldFindHistoryForTraineeWhenScheduledInAppNotificationsExist(MessageType messageType,
                                                             NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

    ObjectId id2 = ObjectId.get();
    Instant timeNow = Instant.now();
    History history2 = new History(id2, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, timeNow, Instant.MIN, SENT, null, null);

    ObjectId id3 = ObjectId.get();
    History history3 = new History(id3, tisReferenceInfo, notificationType, recipientInfo,
        templateInfo, Instant.MAX, Instant.MIN, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history3, history2, history1));

    when(templateService.process(any(), any(), anyMap())).thenReturn("");

    List<History> history = service.findAllScheduledInAppForTrainee(
        TRAINEE_ID, TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    if (messageType.equals(IN_APP)) {
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
    } else {
      assertThat("Unexpected history count.", history.size(), is(0));
    }
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
        templateInfo, Instant.MIN, Instant.MAX, FAILED, null, Instant.MAX);

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
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

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
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

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
        templateInfo, Instant.MIN, Instant.MAX, SENT, null, null);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1));

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(1));

    HistoryDto historyDto = historyDtos.get(0);
    assertThat("Unexpected history subject text.", historyDto.subjectText(), nullValue());
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
        templateInfo, Instant.now(), Instant.now(), SENT, null, null);
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
        templateInfo, Instant.now(), Instant.now(), SENT, null, null);
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
        templateInfo, null, null, null, null, null);

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
        templateInfo, null, null, null, null, null);

    when(repository.findByIdAndRecipient_Id(any(), any())).thenReturn(Optional.of(history));
    when(templateService.process(any(), any(), eq(TEMPLATE_VARIABLES))).thenReturn("");

    service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID);

    verify(templateService).process(any(), eq(Set.of()), anyMap());
  }
}
