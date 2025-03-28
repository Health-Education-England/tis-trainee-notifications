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

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import jakarta.mail.MessagingException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import java.time.Instant;

class LtftListenerIntegrationTest {

  private static final String TEMPLATE_VERSION = "v1.2.3";
  private static final String TIS_TRAINEE_ID = "123456";
  private static final String LTFT_NAME = "ltft_name";
  private static final String STATUS = "Approved";
  private static final Instant TIMESTAMP = Instant.parse("2025-03-15T10:00:00Z");
  private static final String FORM_REF = "ltft_47165_001";
  private static final Map<String, Object> LTFT_CONTENT_DTO = Map.of();

  @Mock
  private EmailService emailService;

  @InjectMocks
  private LtftListener ltftListener;

  @Captor
  private ArgumentCaptor<Map<String, Object>> templateVariablesCaptor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ltftListener = new LtftListener(emailService, TEMPLATE_VERSION);
  }

  @Test
  void ShouldSendEmailWithCorrectDetails() throws MessagingException {
    LtftUpdateEvent event = createLtftUpdateEvent();

    ltftListener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(
        eq(TIS_TRAINEE_ID),
        eq(LTFT_UPDATED),
        eq(TEMPLATE_VERSION),
        templateVariablesCaptor.capture(),
        isNull()
    );

    Map<String, Object> templateVariables = templateVariablesCaptor.getValue();
    assertEquals(LTFT_NAME, templateVariables.get("ltftName"));
    assertEquals(STATUS, templateVariables.get("status"));
    assertEquals(TIMESTAMP, templateVariables.get("timestamp"));
    assertEquals(FORM_REF, templateVariables.get("formRef"));
  }

  @Test
  void ShouldLogMessageWhenEmailIsSent() throws MessagingException {
    LtftUpdateEvent event = createLtftUpdateEvent();

    ltftListener.handleLtftUpdate(event);

    verify(emailService, times(1)).sendMessageToExistingUser(
        anyString(), any(), anyString(), anyMap(), any());
  }

  @Test
  void ShouldHandleMessagingExceptionGracefully() throws MessagingException {
    LtftUpdateEvent event = createLtftUpdateEvent();
    doThrow(new MessagingException("Email error")).when(emailService)
        .sendMessageToExistingUser(anyString(), any(), anyString(), anyMap(), any());

    assertThrows(MessagingException.class, () -> ltftListener.handleLtftUpdate(event));
    verify(emailService, times(1)).sendMessageToExistingUser(
        eq(TIS_TRAINEE_ID), eq(LTFT_UPDATED), eq(TEMPLATE_VERSION), anyMap(), isNull());
  }

  private LtftUpdateEvent createLtftUpdateEvent() {

    LtftUpdateEvent.LtftStatus.StatusDetails statusDetails =
        new LtftUpdateEvent.LtftStatus.StatusDetails(STATUS, TIMESTAMP);

    LtftUpdateEvent.LtftStatus ltftStatus = new LtftUpdateEvent.LtftStatus(statusDetails);

    return new LtftUpdateEvent(LTFT_NAME, ltftStatus, TIS_TRAINEE_ID, FORM_REF, TIMESTAMP, LTFT_CONTENT_DTO);
  }
}

