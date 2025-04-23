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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class LtftListenerTest {

  private static final String VERSION = "v1.2.3";

  private static final String TRAINEE_ID = "47165";
  private static final Instant TIMESTAMP = Instant.now();
  private static final String LTFT_NAME = "My LTFT";
  private static final String FORM_REFERENCE = "ltft_47165_002";
  private static final String LTFT_STATUS = "SUBMITTED";

  private LtftListener listener;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new LtftListener(emailService, VERSION);
  }

  @Test
  void shouldThrowExceptionWhenLtftUpdatedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any());

    LtftUpdateEvent event = LtftUpdateEvent.builder().build();

    assertThrows(MessagingException.class, () -> listener.handleLtftUpdate(event));
  }

  @Test
  void shouldSetTraineeIdWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .build();

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(eq(TRAINEE_ID), any(), any(), any(), any());
  }

  @Test
  void shouldSetNotificationTypeWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder().build();

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(LTFT_UPDATED), any(), any(), any());
  }

  @Test
  void shouldSetTemplateVersionWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder().build();

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION), any(), any());
  }

  @Test
  void shouldPopulateTemplateVariablesWithEventWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder().build();

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected event.", templateVariables.get("var"), sameInstance(event));
  }

  @Test
  void shouldIncludeEventPropertiesWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formRef(FORM_REFERENCE)
        .formName(LTFT_NAME)
        .state(LTFT_STATUS)
        .timestamp(TIMESTAMP)
        .build();

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    LtftUpdateEvent templateEvent = (LtftUpdateEvent) templateVariables.get("var");
    assertThat("Unexpected trainee ID.", templateEvent.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected form ref.", templateEvent.getFormRef(), is(FORM_REFERENCE));
    assertThat("Unexpected LTFT name.", templateEvent.getFormName(), is(LTFT_NAME));
    assertThat("Unexpected status.", templateEvent.getState(), is(LTFT_STATUS));
    assertThat("Unexpected event timestamp.", templateEvent.getTimestamp(), is(TIMESTAMP));
  }
}
