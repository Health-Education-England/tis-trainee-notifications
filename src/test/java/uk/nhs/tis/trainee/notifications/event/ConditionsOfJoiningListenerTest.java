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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.dto.StoredFile;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

class ConditionsOfJoiningListenerTest {

  private static final String VERSION = "v1.2.3";
  private static final String PERSON_ID = "40";
  private static final UUID PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID();
  private static final Instant SYNCED_AT = Instant.now();

  private ConditionsOfJoiningListener listener;
  private HistoryService historyService;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    emailService = mock(EmailService.class);
    listener = new ConditionsOfJoiningListener(historyService, emailService, VERSION);
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void shouldSkipCojPublishedWhenCojConfirmationSent(MessageType messageType)
      throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), pdf);

    TisReferenceInfo tisReference = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        PROGRAMME_MEMBERSHIP_ID.toString());
    HistoryDto history = new HistoryDto(null, tisReference, messageType, COJ_CONFIRMATION, null,
        null, null, null, null, null);
    when(historyService.findAllSentForTrainee(PERSON_ID)).thenReturn(List.of(history));

    listener.handleConditionsOfJoiningPublished(event);

    verifyNoInteractions(emailService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE, names = "COJ_CONFIRMATION")
  void shouldNotSkipCojPublishedWhenNoPreviousCojConfirmation(NotificationType notificationType)
      throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), pdf);

    TisReferenceInfo tisReference = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        PROGRAMME_MEMBERSHIP_ID.toString());
    HistoryDto history = new HistoryDto(null, tisReference, null, notificationType, null, null,
        null, null, null, null);
    when(historyService.findAllSentForTrainee(PERSON_ID)).thenReturn(List.of(history));

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE, names = "COJ_CONFIRMATION")
  void shouldNotSkipCojPublishedWhenPreviousCojConfirmationHasNoTisReference(
      NotificationType notificationType) throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), pdf);

    TisReferenceInfo tisReference1 = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        PROGRAMME_MEMBERSHIP_ID.toString());
    HistoryDto history1 = new HistoryDto(null, tisReference1, null, notificationType, null, null,
        null, null, null, null);

    HistoryDto history2 = new HistoryDto(null, null, null, COJ_CONFIRMATION, null, null, null, null,
        null, null);
    when(historyService.findAllSentForTrainee(PERSON_ID)).thenReturn(List.of(history1, history2));

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE, names = "COJ_CONFIRMATION")
  void shouldNotSkipCojPublishedWhenPreviousCojConfirmationIsDifferentPm(
      NotificationType notificationType) throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), pdf);

    TisReferenceInfo tisReference1 = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        PROGRAMME_MEMBERSHIP_ID.toString());
    HistoryDto history1 = new HistoryDto(null, tisReference1, null, notificationType, null, null,
        null, null, null, null);

    TisReferenceInfo tisReference2 = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        UUID.randomUUID().toString());
    HistoryDto history2 = new HistoryDto(null, tisReference2, null, COJ_CONFIRMATION, null, null,
        null, null, null, null);
    when(historyService.findAllSentForTrainee(PERSON_ID)).thenReturn(List.of(history1, history2));

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldThrowExceptionWhenCojPublishedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any(), any());

    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), null);

    assertThrows(MessagingException.class,
        () -> listener.handleConditionsOfJoiningPublished(event));
  }

  @Test
  void shouldSetTraineeIdWhenCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), null);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(eq(PERSON_ID), any(), any(), any(), any(),
        any());
  }

  @Test
  void shouldSetNotificationTypeWhenCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), null);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(COJ_CONFIRMATION), any(), any(),
        any(), any());
  }

  @Test
  void shouldSetNotificationVersionWhenCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), null);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION), any(), any(), any());
  }

  @Test
  void shouldSetTisReferenceWhenCojPublished() throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), pdf);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<TisReferenceInfo> referenceCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(), any(),
        referenceCaptor.capture(), any());

    TisReferenceInfo tisReference = referenceCaptor.getValue();
    assertThat("Unexpected reference id.", tisReference.id(),
        is(PROGRAMME_MEMBERSHIP_ID.toString()));
    assertThat("Unexpected reference type.", tisReference.type(), is(PROGRAMME_MEMBERSHIP));
  }

  @Test
  void shouldSetPdfWhenCojPublished() throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), pdf);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any(), eq(pdf));
  }

  @Test
  void shouldNotIncludeSyncedAtWhenNullCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID, null, null);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), nullValue());
  }

  @Test
  void shouldNotIncludeSyncedAtWhenCojPublishedWithNullSyncedAt() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(null), null);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), nullValue());
  }

  @Test
  void shouldIncludeSyncedAtWhenCojPublishedWithValidSyncedAt() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, PROGRAMME_MEMBERSHIP_ID,
        new ConditionsOfJoining(SYNCED_AT), null);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), is(SYNCED_AT));
  }
}
