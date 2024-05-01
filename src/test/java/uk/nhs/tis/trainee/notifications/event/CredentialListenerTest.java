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
import static uk.nhs.tis.trainee.notifications.model.NotificationType.CREDENTIAL_REVOKED;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.CredentialEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class CredentialListenerTest {

  private static final String VERSION = "v1.2.3";

  private static final String TRAINEE_ID = "40";

  private static final UUID CREDENTIAL_ID = UUID.randomUUID();
  private static final Instant ISSUED_AT = Instant.now();

  private static final String TRAINING_PLACEMENT = "Training Placement";
  private static final String TRAINING_PROGRAMME = "Training Programme";

  private CredentialListener listener;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new CredentialListener(emailService, VERSION);
  }

  @ParameterizedTest
  @ValueSource(strings = {TRAINING_PLACEMENT, TRAINING_PROGRAMME})
  void shouldThrowExceptionWhenCredentialRevokedAndSendingFails(String credentialType)
      throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any());

    CredentialEvent event = new CredentialEvent(CREDENTIAL_ID, credentialType, ISSUED_AT,
        TRAINEE_ID);

    assertThrows(MessagingException.class, () -> listener.handleCredentialRevoked(event));
  }

  @ParameterizedTest
  @ValueSource(strings = {TRAINING_PLACEMENT, TRAINING_PROGRAMME})
  void shouldSetTraineeIdWhenCredentialRevoked(String credentialType) throws MessagingException {
    CredentialEvent event = new CredentialEvent(CREDENTIAL_ID, credentialType, ISSUED_AT,
        TRAINEE_ID);

    listener.handleCredentialRevoked(event);

    verify(emailService).sendMessageToExistingUser(eq(TRAINEE_ID), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {TRAINING_PLACEMENT, TRAINING_PROGRAMME})
  void shouldSetNotificationTypeWhenCredentialRevoked(String credentialType)
      throws MessagingException {
    CredentialEvent event = new CredentialEvent(CREDENTIAL_ID, credentialType, ISSUED_AT,
        TRAINEE_ID);

    listener.handleCredentialRevoked(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(CREDENTIAL_REVOKED), any(), any(),
        any());
  }

  @ParameterizedTest
  @ValueSource(strings = {TRAINING_PLACEMENT, TRAINING_PROGRAMME})
  void shouldSetTemplateVersionWhenCredentialRevoked(String credentialType)
      throws MessagingException {
    CredentialEvent event = new CredentialEvent(CREDENTIAL_ID, credentialType, ISSUED_AT,
        TRAINEE_ID);

    listener.handleCredentialRevoked(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(VERSION), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {TRAINING_PLACEMENT, TRAINING_PROGRAMME})
  void shouldIncludeCredentialTypeWhenCredentialRevoked(String credentialType)
      throws MessagingException {
    CredentialEvent event = new CredentialEvent(CREDENTIAL_ID, credentialType, ISSUED_AT,
        TRAINEE_ID);

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected credential type.", templateVariables.get("credentialType"),
        is(credentialType));
  }

  @ParameterizedTest
  @ValueSource(strings = {TRAINING_PLACEMENT, TRAINING_PROGRAMME})
  void shouldIncludeIssuedAtWhenCredentialRevoked(String credentialType) throws MessagingException {
    CredentialEvent event = new CredentialEvent(CREDENTIAL_ID, credentialType, ISSUED_AT,
        TRAINEE_ID);

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected issued at.", templateVariables.get("issuedAt"), is(ISSUED_AT));
  }
}
