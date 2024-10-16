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
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.StoredFile;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class ConditionsOfJoiningListenerTest {

  private static final String VERSION = "v1.2.3";
  private static final String PERSON_ID = "40";
  private static final Instant SYNCED_AT = Instant.now();

  private ConditionsOfJoiningListener listener;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new ConditionsOfJoiningListener(emailService, VERSION);
  }

  @Test
  void shouldThrowExceptionWhenCojPublishedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any(), any());

    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(SYNCED_AT),
        null);

    assertThrows(MessagingException.class,
        () -> listener.handleConditionsOfJoiningPublished(event));
  }

  @Test
  void shouldSetTraineeIdWhenCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(SYNCED_AT),
        null);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(eq(PERSON_ID), any(), any(), any(), any(),
        any());
  }

  @Test
  void shouldSetNotificationTypeWhenCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(SYNCED_AT),
        null);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(COJ_CONFIRMATION), any(), any(),
        any(), any());
  }

  @Test
  void shouldSetNotificationVersionWhenCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(SYNCED_AT),
        null);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION), any(), any(), any());
  }

  @Test
  void shouldSetPdfWhenCojPublished() throws MessagingException {
    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(SYNCED_AT),
        pdf);

    listener.handleConditionsOfJoiningPublished(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any(), eq(pdf));
  }

  @Test
  void shouldNotIncludeSyncedAtWhenNullCojPublished() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, null, null);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), nullValue());
  }

  @Test
  void shouldNotIncludeSyncedAtWhenCojPublishedWithNullSyncedAt() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(null), null);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), nullValue());
  }

  @Test
  void shouldIncludeSyncedAtWhenCojPublishedWithValidSyncedAt() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID, new ConditionsOfJoining(SYNCED_AT),
        null);

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), is(SYNCED_AT));
  }
}
