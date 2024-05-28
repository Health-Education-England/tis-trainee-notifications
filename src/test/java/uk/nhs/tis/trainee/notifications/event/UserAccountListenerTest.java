/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.EMAIL_UPDATED_NEW;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.EMAIL_UPDATED_OLD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.WELCOME;

import jakarta.mail.MessagingException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.notifications.dto.AccountUpdatedEvent;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.InAppService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

class UserAccountListenerTest {

  private static final String UPDATED_EMAIL_VERSION_NEW = "v1.2.3";
  private static final String UPDATED_EMAIL_VERSION_OLD = "v2.3.4";
  private static final String WELCOME_VERSION = "v3.4.5";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String FAMILY_NAME = "Gilliam";
  private static final URI APP_DOMAIN = URI.create("https://local.notifications.com");
  private static final String EMAIL = "trainee@example.com";
  private static final String EMAIL_OLD = "trainee.old@example.com";

  private UserAccountListener listener;
  private EmailService emailService;
  private InAppService inAppService;
  private UserAccountService userAccountService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    inAppService = mock(InAppService.class);
    userAccountService = mock(UserAccountService.class);
    listener = new UserAccountListener(emailService, inAppService, userAccountService, APP_DOMAIN,
        UPDATED_EMAIL_VERSION_NEW, UPDATED_EMAIL_VERSION_OLD, WELCOME_VERSION);
  }

  @Test
  void shouldCreateWelcomeNotificationWhenAccountConfirmed() {
    AccountConfirmedEvent event = new AccountConfirmedEvent(USER_ID, TRAINEE_ID, EMAIL);

    listener.handleAccountConfirmation(event);

    verify(inAppService).createNotifications(TRAINEE_ID, null, WELCOME, WELCOME_VERSION, Map.of());
  }

  @ParameterizedTest
  @ValueSource(strings = {"EMAIL_UPDATED_OLD", "EMAIL_UPDATED_NEW"})
  void shouldThrowExceptionWhenEmailUpdateNotificationFails(NotificationType notificationType)
      throws MessagingException {
    when(userAccountService.getUserDetailsById(USER_ID.toString())).thenReturn(
        UserDetails.builder().build());

    doThrow(MessagingException.class).when(emailService)
        .sendMessage(any(), any(), eq(notificationType), any(), any(), any(), anyBoolean());

    AccountUpdatedEvent event = new AccountUpdatedEvent(USER_ID, TRAINEE_ID, EMAIL_OLD, EMAIL);
    assertThrows(MessagingException.class, () -> listener.handleAccountUpdate(event));
  }

  @Test
  void shouldSendEmailToPreviousEmailWhenAccountUpdated() throws MessagingException {
    UserDetails userDetails = UserDetails.builder()
        .familyName(FAMILY_NAME)
        .build();
    when(userAccountService.getUserDetailsById(USER_ID.toString())).thenReturn(userDetails);

    AccountUpdatedEvent event = new AccountUpdatedEvent(USER_ID, TRAINEE_ID, EMAIL_OLD, EMAIL);
    listener.handleAccountUpdate(event);

    ArgumentCaptor<Map<String, Object>> variableCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessage(eq(TRAINEE_ID), eq(EMAIL_OLD), eq(EMAIL_UPDATED_OLD),
        eq(UPDATED_EMAIL_VERSION_OLD), variableCaptor.capture(), eq(null), eq(false));

    Map<String, Object> variables = variableCaptor.getValue();
    assertThat("Unexpected variable count.", variables.size(), is(4));
    assertThat("Unexpected domain.", variables.get("domain"), is(APP_DOMAIN));
    assertThat("Unexpected family name.", variables.get("familyName"), is(FAMILY_NAME));
    assertThat("Unexpected previous email.", variables.get("previousEmail"), is(EMAIL_OLD));
    assertThat("Unexpected new email.", variables.get("newEmail"), is(EMAIL));
  }

  @Test
  void shouldSendEmailToNewEmailWhenAccountUpdated() throws MessagingException {
    UserDetails userDetails = UserDetails.builder()
        .familyName(FAMILY_NAME)
        .build();
    when(userAccountService.getUserDetailsById(USER_ID.toString())).thenReturn(userDetails);

    AccountUpdatedEvent event = new AccountUpdatedEvent(USER_ID, TRAINEE_ID, EMAIL_OLD, EMAIL);
    listener.handleAccountUpdate(event);

    ArgumentCaptor<Map<String, Object>> variableCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessage(eq(TRAINEE_ID), eq(EMAIL), eq(EMAIL_UPDATED_NEW),
        eq(UPDATED_EMAIL_VERSION_NEW), variableCaptor.capture(), eq(null), eq(false));

    Map<String, Object> variables = variableCaptor.getValue();
    assertThat("Unexpected variable count.", variables.size(), is(3));
    assertThat("Unexpected domain.", variables.get("domain"), is(APP_DOMAIN));
    assertThat("Unexpected family name.", variables.get("familyName"), is(FAMILY_NAME));
    assertThat("Unexpected new email.", variables.get("newEmail"), is(EMAIL));
  }
}
