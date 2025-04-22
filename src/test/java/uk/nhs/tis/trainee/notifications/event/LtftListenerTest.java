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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.model.LifecycleState;
import uk.nhs.tis.trainee.notifications.service.LtftService;

class LtftListenerTest {
  private LtftListener listener;
  private LtftService ltftService;

  @BeforeEach
  void setUp() {
    ltftService = mock(LtftService.class);
    listener = new LtftListener(ltftService);
  }

  @Test
  void shouldSendApprovedNotificationWhenStateIsApproved() throws MessagingException {
    LtftUpdateEvent ltft = buildLtft(LifecycleState.APPROVED);

    listener.handleLtftUpdate(ltft);

    verify(ltftService).sendLtftApprovedNotification(ltft);
  }

  @Test
  void shouldSendUpdatedNotificationWhenStateIsNotApproved() throws MessagingException {
    LtftUpdateEvent ltft = buildLtft(LifecycleState.UNSUBMITTED);

    listener.handleLtftUpdate(ltft);

    verify(ltftService).sendLtftUpdatedNotification(ltft);
  }

  @Test
  void shouldThrowExceptionWhenSendApprovedNotificationFails() throws MessagingException {
    doThrow(MessagingException.class).when(ltftService)
        .sendLtftApprovedNotification(any());

    LtftUpdateEvent ltft = buildLtft(LifecycleState.APPROVED);

    assertThrows(MessagingException.class, () -> listener.handleLtftUpdate(ltft));
  }

  @Test
  void shouldThrowExceptionWhenSendUpdatedNotificationFails() throws MessagingException {
    doThrow(MessagingException.class).when(ltftService)
        .sendLtftUpdatedNotification(any());

    LtftUpdateEvent ltft = buildLtft(LifecycleState.UNSUBMITTED);

    assertThrows(MessagingException.class, () -> listener.handleLtftUpdate(ltft));
  }

  private LtftUpdateEvent buildLtft(LifecycleState state) {
    LtftUpdateEvent.StatusDto.StatusInfoDto current = LtftUpdateEvent.StatusDto.StatusInfoDto.builder()
        .state(state)
        .build();
    LtftUpdateEvent.StatusDto status = LtftUpdateEvent.StatusDto.builder()
        .current(current)
        .build();
    return LtftUpdateEvent.builder()
        .status(status)
        .build();
  }


//
//  @Test
//  void shouldSetTraineeIdWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    verify(emailService).sendMessageToExistingUser(eq(TRAINEE_TIS_ID), any(), any(), any(), any());
//  }
//
//  @Test
//  void shouldSetNotificationTypeWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    verify(emailService).sendMessageToExistingUser(any(), eq(LTFT_UPDATED), any(), any(), any());
//  }
//
//  @Test
//  void shouldSetTemplateVersionWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION), any(), any());
//  }
//
//  @Test
//  void shouldIncludeFormNameWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
//    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
//        templateVarsCaptor.capture(), any());
//
//    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
//    assertThat("Unexpected LTFT name.", templateVariables.get("ltftName"), is(LTFT_NAME));
//  }
//
//  @Test
//  void shouldIncludeLifecycleStateWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
//    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
//        templateVarsCaptor.capture(), any());
//
//    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
//    assertThat("Unexpected LTFT status.", templateVariables.get("status"),
//        is(LTFT_STATUS));
//  }
//
//  @Test
//  void shouldIncludeFormTypeWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
//    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
//        templateVarsCaptor.capture(), any());
//
//    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
//    assertThat("Unexpected form ref.", templateVariables.get("formRef"), is(FORM_REFERENCE));
//  }
//
//  @Test
//  void shouldIncludeUpdateDateWhenLtftUpdated() throws MessagingException {
//    LtftUpdateEvent event
//        = new LtftUpdateEvent(TRAINEE_TIS_ID, FORM_REFERENCE, ltftContent, ltftStatus);
//
//    listener.handleLtftUpdate(event);
//
//    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
//    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
//        templateVarsCaptor.capture(), any());
//
//    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
//    assertThat("Unexpected form updated at.", templateVariables.get("eventDate"),
//        is(TIMESTAMP));
//  }
}
