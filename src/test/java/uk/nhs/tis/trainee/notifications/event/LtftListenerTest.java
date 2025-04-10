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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftContent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class LtftListenerTest {

  private static final String VERSION_SUBMITTED = "v1.2.3-submitted";
  private static final String VERSION_UPDATED = "v1.2.3-updated";

  private static final String TRAINEE_TIS_ID = "47165";
  private static final Instant TIMESTAMP = Instant.now();
  private static final String LTFT_NAME = "My LTFT";
  private static final String FORM_REFERENCE = "ltft_47165_002";

  private LtftListener listener;
  private EmailService emailService;

  private final LtftContent ltftContent = new LtftContent(LTFT_NAME);

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new LtftListener(emailService, VERSION_SUBMITTED, VERSION_UPDATED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldThrowExceptionWhenLtftUpdatedAndSendingFails(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any());

    assertThrows(MessagingException.class, () -> listener.handleLtftUpdate(event));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldSetTraineeIdWhenLtftUpdated(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(eq(TRAINEE_TIS_ID), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldSetNotificationTypeWhenLtftUpdated(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(LTFT_UPDATED), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED"})
  void shouldUseSubmittedTemplateVersionWhenStatusIsSubmitted(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION_SUBMITTED), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED"})
  void shouldUseUpdatedTemplateVersionWhenStatusIsNotSubmitted(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION_UPDATED), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldIncludeFormNameWhenLtftUpdated(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), any(), captor.capture(), any());

    assertThat("Unexpected LTFT name.", captor.getValue().get("ltftName"), is(LTFT_NAME));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldIncludeLifecycleStateWhenLtftUpdated(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), any(), captor.capture(), any());

    assertThat("Unexpected LTFT status.", captor.getValue().get("status"), is(status));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldIncludeFormTypeWhenLtftUpdated(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), any(), captor.capture(), any());

    assertThat("Unexpected form ref.", captor.getValue().get("formRef"), is(FORM_REFERENCE));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED"})
  void shouldIncludeLocalOfficeWhenLtftUpdated2(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), any(), captor.capture(), any());

    assertThat("Unexpected local office.", captor.getValue().get("localOfficeName"), is(FORM_REFERENCE));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "APPROVED"})
  void shouldIncludeUpdateDateWhenLtftUpdated(String status) throws MessagingException {
    LtftUpdateEvent event = buildEvent(status);

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessageToExistingUser(any(), any(), any(), captor.capture(), any());

    assertThat("Unexpected form updated at.", captor.getValue().get("eventDate"), is(TIMESTAMP));
  }

  private LtftUpdateEvent buildEvent(String status) {
    LtftUpdateEvent.LtftStatus.StatusDetails statusDetails =
        new LtftUpdateEvent.LtftStatus.StatusDetails(status, TIMESTAMP);
    LtftUpdateEvent.LtftStatus ltftStatus = new LtftUpdateEvent.LtftStatus(statusDetails);
    return new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
  }
}
