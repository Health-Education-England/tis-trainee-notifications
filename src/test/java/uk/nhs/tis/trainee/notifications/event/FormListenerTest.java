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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class FormListenerTest {

  private static final String VERSION = "v1.2.3";

  private static final String PERSON_ID = "40";
  private static final Instant FORM_UPDATED_AT = Instant.now();

  private static final String FORM_NAME = "123.json";
  private static final String FORM_LIFECYCLE_STATE = "SUBMITTED";
  private static final String FORM_TYPE = "form-type";
  private static final Map<String, Object> FORM_CONTENT = new HashMap<>();

  private FormListener listener;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new FormListener(emailService, VERSION);
  }

  @Test
  void shouldThrowExceptionWhenFormUpdatedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any());

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    assertThrows(MessagingException.class, () -> listener.handleFormUpdate(event));
  }

  @Test
  void shouldSetTraineeIdWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    verify(emailService).sendMessageToExistingUser(eq(PERSON_ID), any(), any(), any(), any());
  }

  @Test
  void shouldSetNotificationTypeWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(FORM_UPDATED), any(), any(), any());
  }

  @Test
  void shouldSetTemplateVersionWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION), any(), any());
  }

  @Test
  void shouldIncludeFormNameWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected form name.", templateVariables.get("formName"), is(FORM_NAME));
  }

  @Test
  void shouldIncludeLifecycleStateWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected lifecycle state.", templateVariables.get("lifecycleState"),
        is(FORM_LIFECYCLE_STATE));
  }

  @Test
  void shouldIncludeFormTypeWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected form type.", templateVariables.get("formType"), is(FORM_TYPE));
  }

  @Test
  void shouldIncludeUpdateDateWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected form updated at.", templateVariables.get("eventDate"),
        is(FORM_UPDATED_AT));
  }
}
