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

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class ConditionsOfJoiningListenerTest {

  private static final String PERSON_ID = "40";
  private static final Instant SYNCED_AT = Instant.now();

  private ConditionsOfJoiningListener listener;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new ConditionsOfJoiningListener(emailService);
  }

  @Test
  void shouldThrowExceptionWhenCojReceivedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any());

    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT));

    assertThrows(MessagingException.class, () -> listener.handleConditionsOfJoiningReceived(event));
  }

  @Test
  void shouldSetTraineeIdWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    verify(emailService).sendMessageToExistingUser(eq(PERSON_ID), any(), any());
  }

  @Test
  void shouldSetTemplateWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    verify(emailService).sendMessageToExistingUser(any(), eq("email/coj-confirmation"), any());
  }

  @Test
  void shouldNotIncludeSyncedAtWhenNullCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID,
        null);

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), nullValue());
  }

  @Test
  void shouldNotIncludeSyncedAtWhenCojReceivedWithNullSyncedAt() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID,
        new ConditionsOfJoining(null));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), nullValue());
  }

  @Test
  void shouldIncludeSyncedAtWhenCojReceivedWithValidSyncedAt() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected synced at.", templateVariables.get("syncedAt"), is(SYNCED_AT));
  }
}
