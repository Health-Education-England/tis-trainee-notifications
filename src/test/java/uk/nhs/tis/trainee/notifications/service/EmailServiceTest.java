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

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;

import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class EmailServiceTest {

  private static final NotificationType NOTIFICATION_TYPE = NotificationType.COJ_CONFIRMATION;
  private static final String RECIPIENT = "anthony.gilliam@tis.nhs.uk";
  private static final String TRAINEE_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String SENDER = "sender@test.email";
  private static final URI APP_DOMAIN = URI.create("local.notifications.com");
  private static final String TIMEZONE = "Europe/London";

  private EmailService service;
  private UserAccountService userAccountService;
  private HistoryService historyService;
  private JavaMailSender mailSender;
  private TemplateEngine templateEngine;

  @BeforeEach
  void setUp() {
    userAccountService = mock(UserAccountService.class);
    historyService = mock(HistoryService.class);
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of(USER_ID));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, null));

    mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    templateEngine = mock(TemplateEngine.class);
    when(templateEngine.process(any(), any(), any(Context.class))).thenReturn("");

    service = new EmailService(userAccountService, historyService, mailSender, templateEngine,
        SENDER, APP_DOMAIN, TIMEZONE);
  }

  @Test
  void shouldThrowExceptionWhenSendingWithoutTraineeId() {
    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(null, NOTIFICATION_TYPE, null, null));

    verifyNoInteractions(userAccountService);
  }

  @Test
  void shouldThrowExceptionWhenSendingToExistingUserAndTraineeIdNotFound() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of());

    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null, null));
  }

  @Test
  void shouldThrowExceptionWhenSendingToExistingUserAndAndMultipleTraineeIdResults() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(
        Set.of(USER_ID, UUID.randomUUID().toString()));

    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null, null));
  }

  @Test
  void shouldThrowExceptionWhenSendingToExistingUserAndUserDetailsNotFound() {
    when(userAccountService.getUserDetails(USER_ID)).thenThrow(UserNotFoundException.class);

    assertThrows(UserNotFoundException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldThrowExceptionWhenSendingToExistingUserAndEmailNotFound(String email) {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(email, "Gilliam"));

    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null, null));
  }

  @Test
  void shouldThrowExceptionWhenSendingWithNoNotificationType() {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, "Gilliam"));

    assertThrows(NullPointerException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, null, "", null));
  }

  @Test
  void shouldGetNameFromUserAccountWhenNoNameProvided() throws MessagingException {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, "Gilliam"));

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of());

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected name variable.", context.getVariable("name"), is("Gilliam"));
  }

  @Test
  void shouldUseProvidedNameWhenNameProvided() throws MessagingException {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, "Gilliam"));

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of("name", "Maillig"));

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected name variable.", context.getVariable("name"), is("Maillig"));
  }

  @Test
  void shouldGetDomainFromPropertiesWhenNoDomainProvided() throws MessagingException {
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of());

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected domain variable.", context.getVariable("domain"), is(APP_DOMAIN));
  }

  @Test
  void shouldUseProvidedDomainWhenDomainProvided() throws MessagingException {
    URI domain = URI.create("local.override.com");

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of("domain", domain));

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected domain variable.", context.getVariable("domain"), is(domain));
  }

  @Test
  void shouldLocalizeTimestampWhenTimezoneGmt() throws MessagingException {
    Instant instant = Instant.parse("2021-02-03T04:05:06Z");
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of("timestamp", instant));

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    Object timestamp = context.getVariable("timestamp");
    assertThat("Unexpected timestamp type.", timestamp, instanceOf(ZonedDateTime.class));

    ZonedDateTime zonedDateTime = (ZonedDateTime) timestamp;
    assertThat("Unexpected synced at day.", zonedDateTime.getDayOfMonth(), is(3));
    assertThat("Unexpected synced at month.", zonedDateTime.getMonth(), is(Month.FEBRUARY));
    assertThat("Unexpected synced at year.", zonedDateTime.getYear(), is(2021));
    assertThat("Unexpected synced at zone.", zonedDateTime.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldLocalizeTimestampWhenTimezoneBst() throws MessagingException {
    Instant instant = Instant.parse("2021-08-03T23:05:06Z");
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of("timestamp", instant));

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    Object timestamp = context.getVariable("timestamp");
    assertThat("Unexpected timestamp type.", timestamp, instanceOf(ZonedDateTime.class));

    ZonedDateTime zonedDateTime = (ZonedDateTime) timestamp;
    assertThat("Unexpected synced at day.", zonedDateTime.getDayOfMonth(), is(4));
    assertThat("Unexpected synced at month.", zonedDateTime.getMonth(), is(Month.AUGUST));
    assertThat("Unexpected synced at year.", zonedDateTime.getYear(), is(2021));
    assertThat("Unexpected synced at zone.", zonedDateTime.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldSendMessageToUserAccountEmail() throws MessagingException {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails("anthony.gilliam@tis.nhs.uk", ""));

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Address[] toRecipients = message.getRecipients(RecipientType.TO);
    assertThat("Unexpected recipient count.", toRecipients.length, is(1));
    assertThat("Unexpected recipient.", toRecipients[0].toString(), is(RECIPIENT));

    Address[] allRecipients = message.getAllRecipients();
    assertThat("Unexpected recipient count.", allRecipients.length, is(1));
  }

  @Test
  void shouldSendMessageFromSender() throws MessagingException {
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Address[] senders = message.getFrom();
    assertThat("Unexpected sender count.", senders.length, is(1));
    assertThat("Unexpected sender.", senders[0].toString(), is(SENDER));
  }

  @Test
  void shouldSendMessageWithSubject() throws MessagingException {
    String template = "Test subject";
    when(templateEngine.process(any(), eq(Set.of("subject")), any())).thenReturn(template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(template));
  }

  @Test
  void shouldSendMessageWithContent() throws MessagingException, IOException {
    String template = "<div>Test message body</div>";
    when(templateEngine.process(any(), eq(Set.of("content")), any())).thenReturn(template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected content.", message.getContent(), is(template));

    DataHandler dataHandler = message.getDataHandler();
    assertThat("Unexpected content type.", dataHandler.getContentType(),
        is("text/html;charset=UTF-8"));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldProcessTheTemplateWhenSendingMessage(NotificationType notificationType)
      throws MessagingException {
    String templateVersion = "v1.2.3";

    service.sendMessageToExistingUser(TRAINEE_ID, notificationType, templateVersion,
        Map.of("key1", "value1"));

    ArgumentCaptor<Set<String>> selectorCaptor = ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine, times(2)).process(
        eq("email/" + notificationType.getTemplateName() + "/v1.2.3"), selectorCaptor.capture(),
        contextCaptor.capture());

    List<Set<String>> selectors = selectorCaptor.getAllValues();
    assertThat("Unexpected selector.", selectors.get(0), is(Set.of("subject")));
    assertThat("Unexpected selector.", selectors.get(1), is(Set.of("content")));

    List<Context> contexts = contextCaptor.getAllValues();
    Context context = contexts.get(0);
    assertThat("Unexpected template variable.", context.getVariable("key1"), is("value1"));

    context = contexts.get(1);
    assertThat("Unexpected template variable.", context.getVariable("key1"), is("value1"));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldStoreHistoryWhenMessageSent(NotificationType notificationType)
      throws MessagingException {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, "Gilliam"));
    String templateVersion = "v1.2.3";

    service.sendMessageToExistingUser(TRAINEE_ID, notificationType, templateVersion,
        Map.of("key1", "value1"));

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.forClass(History.class);
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), nullValue());
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(TRAINEE_ID));
    assertThat("Unexpected message type.", recipient.type(), is(EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(RECIPIENT));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(3));
    assertThat("Unexpected template variable.", storedVariables.get("key1"), is("value1"));
    assertThat("Unexpected template variable.", storedVariables.get("name"), is("Gilliam"));
    assertThat("Unexpected template variable.", storedVariables.get("domain"), is(APP_DOMAIN));
  }
}
