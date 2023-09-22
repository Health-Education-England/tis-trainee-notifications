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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.MessagingException;
import java.net.URI;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.service.EmailService;

class ConditionsOfJoiningListenerTest {

  private static final URI APP_DOMAIN = URI.create("https://local.notifications.com");
  private static final String TIMEZONE = "Europe/London";

  private static final String TRAINEE_ID = "40";
  private static final String MANAGING_DEANERY = "deanery1";
  private static final Instant SYNCED_AT = Instant.now();

  private ConditionsOfJoiningListener listener;
  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    listener = new ConditionsOfJoiningListener(emailService, APP_DOMAIN, TIMEZONE);
  }

  @Test
  void shouldThrowExceptionWhenCojReceivedWithoutTraineeId() {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(null, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    assertThrows(IllegalArgumentException.class,
        () -> listener.handleConditionsOfJoiningReceived(event));
  }

  @Test
  void shouldSetDestinationWhenCojReceived() throws MessagingException {
    // TODO: rewrite for Cognito lookup.
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    verify(emailService).sendMessage(eq("anthony.gilliam@tis.nhs.uk"), any(), any(), any());
  }

  @Test
  void shouldSetSubjectWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    verify(emailService).sendMessage(any(), eq("We've received your Conditions of Joining"), any(),
        any());
  }

  @Test
  void shouldSetTemplateWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    verify(emailService).sendMessage(any(), any(), eq("email/coj-confirmation"), any());
  }

  @Test
  void shouldIncludeDomainWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected domain.", templateVariables.get("domain"), is(APP_DOMAIN));
  }

  @Test
  void shouldIncludeDeaneryWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected managing deanery.", templateVariables.get("managingDeanery"),
        is(MANAGING_DEANERY));
  }

  @Test
  void shouldIncludeGmtSyncedAtWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(Instant.parse("2021-02-03T04:05:06Z")));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    ZonedDateTime syncedAt = (ZonedDateTime) templateVariables.get("syncedAt");

    assertThat("Unexpected synced at day.", syncedAt.getDayOfMonth(), is(3));
    assertThat("Unexpected synced at month.", syncedAt.getMonth(), is(Month.FEBRUARY));
    assertThat("Unexpected synced at year.", syncedAt.getYear(), is(2021));
    assertThat("Unexpected synced at zone.", syncedAt.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldIncludeBstSyncedAtWhenCojReceived() throws MessagingException {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(Instant.parse("2021-08-03T23:05:06Z")));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    ZonedDateTime syncedAt = (ZonedDateTime) templateVariables.get("syncedAt");

    assertThat("Unexpected synced at day.", syncedAt.getDayOfMonth(), is(4));
    assertThat("Unexpected synced at month.", syncedAt.getMonth(), is(Month.AUGUST));
    assertThat("Unexpected synced at year.", syncedAt.getYear(), is(2021));
    assertThat("Unexpected synced at zone.", syncedAt.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldIncludeNameWhenCojReceived() throws MessagingException {
    // TODO: rewrite for Cognito lookup.
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TRAINEE_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(emailService).sendMessage(any(), any(), any(), templateVarsCaptor.capture());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected name.", templateVariables.get("name"), is("Dr Gilliam"));
  }
}
