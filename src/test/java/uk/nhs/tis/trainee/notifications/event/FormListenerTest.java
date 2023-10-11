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
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import java.net.URI;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

class FormListenerTest {

  private static final URI APP_DOMAIN = URI.create("https://local.notifications.com");
  private static final String TIMEZONE = "Europe/London";

  private static final String PERSON_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final Instant FORM_UPDATED_AT = Instant.now();

  private static final String FORM_NAME = "123.json";
  private static final String FORM_LIFECYCLE_STATE = "SUBMITTED";
  private static final String FORM_TYPE = "form-type";
  private static final Map<String, Object> FORM_CONTENT = new HashMap<>();

  private FormListener listener;
  private UserAccountService userAccountService;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    userAccountService = mock(UserAccountService.class);
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(new UserAccountDetails("", ""));

    emailService = mock(EmailService.class);
    listener = new FormListener(userAccountService, emailService, APP_DOMAIN, TIMEZONE);
  }

  @Test
  void shouldThrowExceptionWhenFormReceivedWithoutPersonId() {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, null,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    assertThrows(IllegalArgumentException.class,
        () -> listener.handleFormUpdate(event));
  }

  @Test
  void shouldThrowExceptionWhenFormUpdatedAndPersonIdNotFound() {
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of());

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    assertThrows(IllegalArgumentException.class,
        () -> listener.handleFormUpdate(event));
  }

  @Test
  void shouldThrowExceptionWhenFormUpdatedAndMultiplePersonIdResults() {
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(
        Set.of(USER_ID, UUID.randomUUID().toString()));

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    assertThrows(IllegalArgumentException.class,
        () -> listener.handleFormUpdate(event));
  }

  @Test
  void shouldThrowExceptionWhenFormUpdateddAndUserDetailsNotFound() {
    when(userAccountService.getUserDetails(USER_ID)).thenThrow(UserNotFoundException.class);

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    assertThrows(UserNotFoundException.class,
        () -> listener.handleFormUpdate(event));
  }

  @Test
  void shouldThrowExceptionWhenFormUpdatedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService).sendMessage(any(), any(), any(), any());

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    assertThrows(MessagingException.class, () -> listener.handleFormUpdate(event));
  }


  @Test
  void shouldSetDestinationWhenFormUpdated() throws MessagingException {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails("anthony.gilliam@tis.nhs.uk", ""));

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    verify(emailService).sendMessage(eq("anthony.gilliam@tis.nhs.uk"), any(), any(), any());
  }

  @Test
  void shouldSetSubjectWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    verify(emailService).sendMessage(any(), eq("Your Form R has been updated"), any(),
        any());
  }

  @Test
  void shouldSetTemplateWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    verify(emailService).sendMessage(any(), any(), eq("email/form-updated"), any());
  }

  @Test
  void shouldIncludeDomainWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected domain.", templateVariables.get("domain"), is(APP_DOMAIN));
  }

  @Test
  void shouldIncludeFormNameWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected form name.", templateVariables.get("formName"),
        is(FORM_NAME));
  }

  @Test
  void shouldIncludeLifecycleStateWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected lifecycle state.", templateVariables.get("lifecycleState"),
        is(FORM_LIFECYCLE_STATE));
  }

  @Test
  void shouldIncludeFormTypeWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected form type.", templateVariables.get("formType"),
        is(FORM_TYPE));
  }

  @Test
  void shouldIncludeGmtEventDateWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, Instant.parse("2021-02-03T04:05:06Z"), FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    ZonedDateTime eventDate = (ZonedDateTime) templateVariables.get("eventDate");

    assertThat("Unexpected event date day.", eventDate.getDayOfMonth(), is(3));
    assertThat("Unexpected event date month.", eventDate.getMonth(), is(Month.FEBRUARY));
    assertThat("Unexpected event date year.", eventDate.getYear(), is(2021));
    assertThat("Unexpected event date zone.", eventDate.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldIncludeBstEventDateWhenFormUpdated() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, Instant.parse("2021-08-03T23:05:06Z"), FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    ZonedDateTime eventDate = (ZonedDateTime) templateVariables.get("eventDate");

    assertThat("Unexpected event date day.", eventDate.getDayOfMonth(), is(4));
    assertThat("Unexpected event date month.", eventDate.getMonth(), is(Month.AUGUST));
    assertThat("Unexpected event date year.", eventDate.getYear(), is(2021));
    assertThat("Unexpected event date zone.", eventDate.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldIncludeNameWhenFormUpdatedAndNameAvailable() throws MessagingException {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails("", "Gilliam"));

    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected name.", templateVariables.get("name"), is("Dr Gilliam"));
  }

  @Test
  void shouldAddressDoctorWhenFormUpdatedAndNameNotAvailable() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_LIFECYCLE_STATE, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);

    listener.handleFormUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected name.", templateVariables.get("name"), is("Doctor"));
  }
}
