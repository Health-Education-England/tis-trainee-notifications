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
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.MULTIPART_MIXED_VALUE;
import static org.springframework.http.MediaType.MULTIPART_RELATED_VALUE;
import static org.springframework.http.MediaType.TEXT_HTML_VALUE;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.PENDING;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.dto.StoredFile;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class EmailServiceTest {

  private static final NotificationType NOTIFICATION_TYPE = NotificationType.COJ_CONFIRMATION;
  private static final String RECIPIENT = "anthony.gilliam@tis.nhs.uk";
  private static final String GMC = "111111";
  private static final String TRAINEE_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String SENDER = "sender@test.email";
  private static final URI APP_DOMAIN = URI.create("local.notifications.com");
  private static final TisReferenceType REFERENCE_TABLE = PLACEMENT;
  private static final String REFERENCE_KEY = "the-key";
  private static final String DEFAULT_EMAIL_HASH = "00000000000000000000000000000000";

  private EmailService service;
  private UserAccountService userAccountService;
  private HistoryService historyService;
  private JavaMailSender mailSender;
  private TemplateService templateService;
  private S3Template s3Template;

  @BeforeEach
  void setUp() {
    userAccountService = mock(UserAccountService.class);
    historyService = mock(HistoryService.class);
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of(USER_ID));
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, GMC));

    mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    templateService = mock(TemplateService.class);
    when(templateService.buildContext(any())).thenAnswer(
        inv -> new Context(null, (Map<String, Object>) inv.getArguments()[0]));
    when(templateService.process(any(), any(), (Context) any())).thenReturn("");

    s3Template = mock(S3Template.class);

    service = new EmailService(userAccountService, historyService, mailSender, templateService,
        s3Template, SENDER, APP_DOMAIN);
  }

  @Test
  void shouldThrowExceptionWhenSendingWithoutTraineeId() {
    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(null, NOTIFICATION_TYPE, null,
            null, null));

    verifyNoInteractions(userAccountService);
  }

  @Test
  void shouldThrowExceptionWhenSendingToExistingUserAndTraineeIdNotFound() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of());

    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null,
            null, null));
  }

  @Test
  void shouldThrowExceptionWhenSendingToExistingUserAndAndMultipleTraineeIdResults() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(
        Set.of(USER_ID, UUID.randomUUID().toString()));

    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null,
            null, null));
  }

  @Test
  void shouldThrowExceptionWhenSendingToExistingUserAndUserDetailsNotFound() {
    when(userAccountService.getUserDetailsById(USER_ID)).thenThrow(UserNotFoundException.class);

    assertThrows(UserNotFoundException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null,
            null, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldThrowExceptionWhenSendingToExistingUserAndEmailNotFound(String email) {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, email, "Mr", "Gilliam", "Anthony", GMC));

    assertThrows(IllegalArgumentException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, null,
            null, null));
  }

  @Test
  void shouldThrowExceptionWhenSendingWithNoNotificationType() {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam", "Anthony", GMC));

    assertThrows(NullPointerException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, null, "",
            null, null));
  }

  @Test
  void shouldGetNameFromUserAccountWhenNoNameProvided() throws MessagingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam", "Anthony", GMC));

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of(),
        null);

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateService, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected family name variable.", context.getVariable("familyName"),
        is("Gilliam"));
  }

  @Test
  void shouldUseProvidedNameWhenNameProvided() throws MessagingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam", "Anthony", GMC));

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of("familyName", "Maillig"), null);

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateService, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected family name variable.", context.getVariable("familyName"),
        is("Maillig"));
  }

  @Test
  void shouldGetDomainFromPropertiesWhenNoDomainProvided() throws MessagingException {
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateService, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected domain variable.", context.getVariable("domain"),
        is(APP_DOMAIN));
  }

  @Test
  void shouldUseProvidedDomainWhenDomainProvided() throws MessagingException {
    URI domain = URI.create("local.override.com");

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of("domain", domain), null);

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateService, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected domain variable.", context.getVariable("domain"), is(domain));
  }

  @Test
  void shouldIncludeHashedEmailInTemplateProperties() throws MessagingException {
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateService, atLeastOnce()).process(any(), any(), contextCaptor.capture());

    String expectedHashedEmail = service.createMd5Hash(RECIPIENT);

    Context context = contextCaptor.getValue();
    assertThat("Unexpected hashed email.", context.getVariable("hashedEmail"),
        is(expectedHashedEmail));
  }

  @Test
  void shouldSendMessageToUserAccountEmail() throws MessagingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, "anthony.gilliam@tis.nhs.uk", "", "",
            "", GMC));

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
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
    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Address[] senders = message.getFrom();
    assertThat("Unexpected sender count.", senders.length, is(1));
    assertThat("Unexpected sender.", senders[0].toString(), is(SENDER));
  }

  @Test
  void shouldSendMessageWithSubject() throws MessagingException {
    String template = "Test subject";
    when(templateService.process(any(), eq(Set.of("subject")), (Context) any())).thenReturn(
        template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(template));
  }

  @Test
  void shouldSendMessageWithContent() throws MessagingException, IOException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected text content.", message.getContent(), is(template));

    DataHandler dataHandler = message.getDataHandler();
    assertThat("Unexpected content type.", dataHandler.getContentType(),
        is("text/html;charset=UTF-8"));
  }

  @Test
  void shouldSendMessageWithNoAttachment() throws MessagingException, IOException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null, null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected text content.", message.getContent(), is(template));

    DataHandler dataHandler = message.getDataHandler();
    assertThat("Unexpected content type.", dataHandler.getContentType(),
        is("text/html;charset=UTF-8"));
  }

  @Test
  void shouldThrowMessageExceptionWhenAttachmentInvalid() throws IOException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    S3Resource s3Resource = mock(S3Resource.class);
    when(s3Resource.getFilename()).thenReturn("file.pdf");
    when(s3Resource.contentType()).thenReturn(APPLICATION_PDF_VALUE);
    when(s3Resource.getContentAsByteArray()).thenThrow(IOException.class);
    when(s3Template.download("my-bucket", "key/file.pdf")).thenReturn(s3Resource);

    MessagingException exception = assertThrows(MessagingException.class,
        () -> service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "", Map.of(), null,
            new StoredFile("my-bucket", "key/file.pdf")));

    assertThat("Unexpected exception message.", exception.getMessage(),
        is("Unable to read file 'my-bucket:key/file.pdf'."));
  }

  @Test
  void shouldSendMessageWithAttachment() throws MessagingException, IOException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    S3Resource s3Resource = mock(S3Resource.class);
    when(s3Resource.getFilename()).thenReturn("file.pdf");
    when(s3Resource.contentType()).thenReturn(APPLICATION_PDF_VALUE);
    when(s3Resource.getContentAsByteArray()).thenReturn("test".getBytes());
    when(s3Template.download("my-bucket", "key/file.pdf")).thenReturn(s3Resource);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null, new StoredFile("my-bucket", "key/file.pdf"));

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected content java type.", message.getContent(),
        instanceOf(MimeMultipart.class));

    MimeMultipart mixedContent = (MimeMultipart) message.getContent();
    assertThat("Unexpected content type.", mixedContent.getContentType(),
        startsWith(MULTIPART_MIXED_VALUE));
    assertThat("Unexpected content part count.", mixedContent.getCount(), is(2));

    MimeMultipart relatedContent = (MimeMultipart) mixedContent.getBodyPart(0).getContent();
    assertThat("Unexpected content type.", relatedContent.getContentType(),
        startsWith(MULTIPART_RELATED_VALUE));
    assertThat("Unexpected content part count.", relatedContent.getCount(), is(1));

    DataHandler content = relatedContent.getBodyPart(0).getDataHandler();
    assertThat("Unexpected content type.", content.getContentType(), startsWith(TEXT_HTML_VALUE));
    assertThat("Unexpected content.", content.getContent(), is(template));

    DataHandler attachment = mixedContent.getBodyPart(1).getDataHandler();
    assertThat("Unexpected attachment type.", attachment.getContentType(),
        is(APPLICATION_PDF_VALUE));
    assertThat("Unexpected attachment name.", attachment.getName(), is("file.pdf"));

    Object attachmentContent = attachment.getContent();
    assertThat("Unexpected attachment.", attachmentContent, instanceOf(ByteArrayInputStream.class));
    assertThat("Unexpected attachment content.",
        ((ByteArrayInputStream) attachmentContent).readAllBytes(), is("test".getBytes()));
  }

  @Test
  void shouldSendMessageWithNotificationIdHeader() throws MessagingException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    String notificationId = message.getHeader("NotificationId", "");
    assertThat("Unexpected notification ID header.", notificationId, notNullValue());
    assertThat("Unexpected notification ID validity.", ObjectId.isValid(notificationId),
        is(true));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldProcessTheTemplateWhenSendingMessage(NotificationType notificationType)
      throws MessagingException {
    when(templateService.getTemplatePath(EMAIL, notificationType, "v1.2.3")).thenReturn(
        "template/path");

    service.sendMessageToExistingUser(TRAINEE_ID, notificationType, "v1.2.3",
        Map.of("key1", "value1"), null);

    verify(templateService, times(2)).process(eq("template/path"),
        any(), (Context) any());

    ArgumentCaptor<Set<String>> selectorCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateService, times(2)).process(any(), selectorCaptor.capture(),
        contextCaptor.capture());

    List<Set<String>> selectors = selectorCaptor.getAllValues();
    assertThat("Unexpected selector.", selectors.get(0), is(Set.of("subject")));
    assertThat("Unexpected selector.", selectors.get(1), is(Set.of("content")));

    List<Context> contexts = contextCaptor.getAllValues();
    Context context = contexts.get(0);
    assertThat("Unexpected template variable.", context.getVariable("key1"),
        is("value1"));

    context = contexts.get(1);
    assertThat("Unexpected template variable.", context.getVariable("key1"),
        is("value1"));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldStoreHistoryWhenMessageSent(NotificationType notificationType)
      throws MessagingException {
    when(historyService.findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam",
            "Anthony", GMC));
    String templateVersion = "v1.2.3";
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(REFERENCE_TABLE, REFERENCE_KEY);

    service.sendMessageToExistingUser(TRAINEE_ID, notificationType, templateVersion,
        Map.of("key1", "value1"), tisReferenceInfo);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());
    assertThat("Unexpected status.", history.status(), is(PENDING));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(TRAINEE_ID));
    assertThat("Unexpected message type.", recipient.type(), is(EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(RECIPIENT));

    TisReferenceInfo tisReference = history.tisReference();
    assertThat("Unexpected reference table.", tisReference.type(), is(REFERENCE_TABLE));
    assertThat("Unexpected reference id key.", tisReference.id(), is(REFERENCE_KEY));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(5));
    assertThat("Unexpected template variable.", storedVariables.get("key1"), is("value1"));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"),
        is("Gilliam"));
    assertThat("Unexpected template variable.", storedVariables.get("domain"), is(APP_DOMAIN));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldUpdateAndDeleteOrphanHistoryWhenScheduledHistoryFound(
      NotificationType notificationType)
      throws MessagingException {
    ObjectId notificationId = ObjectId.get();
    History latestScheduledHistory = new History(notificationId, null, notificationType,
        null, null, null, null, null, SCHEDULED, null, null);
    History redundantScheduledHistory = new History(notificationId, null, notificationType,
        null, null, null, null, null, SCHEDULED, null, null);
    when(historyService.findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(latestScheduledHistory);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam",
            "Anthony", GMC));
    when(historyService.findAllScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(List.of(redundantScheduledHistory));
    String templateVersion = "v1.2.3";
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(REFERENCE_TABLE, REFERENCE_KEY);

    service.sendMessageToExistingUser(TRAINEE_ID, notificationType, templateVersion,
        Map.of("key1", "value1"), tisReferenceInfo);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), is(notificationId));
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());
    assertThat("Unexpected status.", history.status(), is(PENDING));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());
    assertThat("Unexpected latest event at.", history.latestStatusEventAt(), nullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(TRAINEE_ID));
    assertThat("Unexpected message type.", recipient.type(), is(EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(RECIPIENT));

    TisReferenceInfo tisReference = history.tisReference();
    assertThat("Unexpected reference table.", tisReference.type(), is(REFERENCE_TABLE));
    assertThat("Unexpected reference id key.", tisReference.id(), is(REFERENCE_KEY));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(5));
    assertThat("Unexpected template variable.", storedVariables.get("key1"), is("value1"));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"),
        is("Gilliam"));
    assertThat("Unexpected template variable.", storedVariables.get("domain"), is(APP_DOMAIN));

    verify(historyService).deleteHistoryForTrainee(redundantScheduledHistory.id(), TRAINEE_ID);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldUpdateHistoryWhenMessageResent(NotificationType notificationType)
      throws MessagingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam",
            "Anthony", GMC));
    String templateVersion = "v1.2.3";
    Map<String, Object> variables = new HashMap<>();
    variables.put("key1", "value1");
    variables.put("key2", null);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(),
        templateVersion, variables);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(REFERENCE_TABLE, REFERENCE_KEY);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, RECIPIENT);
    Instant sentAt = Instant.MIN;

    ObjectId notificationId = ObjectId.get();
    History toResend = new History(notificationId, tisReferenceInfo, notificationType,
        recipientInfo,
        templateInfo, null, sentAt, null, FAILED, "bounced", null);
    service.resendMessage(toResend, "newemailaddress");

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), is(notificationId));
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
    assertThat("Unexpected sent at.", history.sentAt(), is(sentAt));
    assertThat("Unexpected status.", history.status(), is(PENDING));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());
    assertThat("Unexpected last retry.", history.lastRetry(), notNullValue());
    assertThat("Unexpected latest event at.", history.latestStatusEventAt(), nullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(TRAINEE_ID));
    assertThat("Unexpected message type.", recipient.type(), is(EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is("newemailaddress"));

    TisReferenceInfo tisReference = history.tisReference();
    assertThat("Unexpected reference table.", tisReference.type(), is(REFERENCE_TABLE));
    assertThat("Unexpected reference id key.", tisReference.id(), is(REFERENCE_KEY));

    TemplateInfo savedTemplateInfo = history.template();
    assertThat("Unexpected template name.", savedTemplateInfo.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", savedTemplateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(2));
    assertThat("Unexpected template variable.", storedVariables.get("key1"), is("value1"));
    assertThat("Unexpected template variable.", storedVariables.get("key2"), nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = MessageType.class, mode = Mode.EXCLUDE, names = "EMAIL")
  void shouldNotResendNonEmailMessageTypes(MessageType messageType)
      throws MessagingException {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, "Mr", "Gilliam",
            "Anthony", GMC));
    String templateVersion = "v1.2.3";
    Map<String, Object> variables = new HashMap<>();
    variables.put("key1", "value1");
    TemplateInfo templateInfo = new TemplateInfo(PROGRAMME_CREATED.getTemplateName(),
        templateVersion, variables);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(REFERENCE_TABLE, REFERENCE_KEY);
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, messageType, RECIPIENT);
    Instant sentAt = Instant.MIN;

    ObjectId notificationId = ObjectId.get();
    History toResend = new History(notificationId, tisReferenceInfo, PROGRAMME_CREATED,
        recipientInfo, templateInfo, null, sentAt, null, FAILED, "bounced", null);
    service.resendMessage(toResend, "newemailaddress");

    verify(mailSender, never()).send(any(MimeMessage.class));
    verify(historyService, never()).save(any());
  }

  @Test
  void shouldSendNotificationIdHeaderMatchingHistoryId() throws MessagingException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    service.sendMessageToExistingUser(TRAINEE_ID, NOTIFICATION_TYPE, "",
        Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    String headerId = message.getHeader("NotificationId", "");

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    ObjectId historyId = history.id();
    assertThat("Unexpected notification id.", headerId, is(historyId.toString()));
  }

  @Test
  void shouldNotActuallySendMessageIfFlagged() throws MessagingException {
    String template = "<div>Test message body</div>";
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(REFERENCE_TABLE, REFERENCE_KEY);
    History scheduledHistory = new History(ObjectId.get(), null, NOTIFICATION_TYPE,
        null, null, null, null, null, SCHEDULED, null, null);
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);
    when(historyService.findAllScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(List.of(scheduledHistory));

    service.sendMessage(TRAINEE_ID, RECIPIENT, NOTIFICATION_TYPE, "", Map.of("key1", "val1"),
        tisReferenceInfo, true);

    verify(mailSender, never()).send((MimeMessage) any());
    verify(historyService, never()).save(any());
    verify(historyService).deleteHistoryForTrainee(scheduledHistory.id(), TRAINEE_ID);
  }

  @Test
  void shouldNotActuallySendMessageIfNoRecipient() throws MessagingException {
    String template = "<div>Test message body</div>";
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(REFERENCE_TABLE, REFERENCE_KEY);
    History scheduledHistory = new History(ObjectId.get(), null, NOTIFICATION_TYPE,
        null, null, null, null, null, SCHEDULED, null, null);
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);
    when(historyService.findAllScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(List.of(scheduledHistory));

    service.sendMessage(TRAINEE_ID, null, NOTIFICATION_TYPE, "", new HashMap<>(),
        tisReferenceInfo, false);

    verify(mailSender, never()).send((MimeMessage) any());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected recipient contact.", history.recipient().contact(), nullValue());
    assertThat("Unexpected status.", history.status(), is(FAILED));
    assertThat("Unexpected status detail.", history.statusDetail(),
        is("No email address available."));

    verify(historyService).deleteHistoryForTrainee(scheduledHistory.id(), TRAINEE_ID);
  }

  @Test
  void shouldSendMessageIfNotFlagged() throws MessagingException {
    String template = "<div>Test message body</div>";
    when(templateService.process(any(), eq(Set.of("content")), (Context) any())).thenReturn(
        template);

    service.sendMessage(TRAINEE_ID, RECIPIENT, NOTIFICATION_TYPE, "", new HashMap<>(),
        null, false);

    verify(mailSender).send((MimeMessage) any());
    verify(historyService).save(any());
  }

  @Test
  void shouldNotThrowExceptionFromUnexpectedContent() throws IOException, MessagingException {
    MimeMessage mimeMessage = mock(MimeMessage.class);
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    when(mimeMessage.getContent()).thenThrow(IOException.class);

    assertDoesNotThrow(() -> service.sendMessage(TRAINEE_ID, RECIPIENT, NOTIFICATION_TYPE,
        "v1.2.3", new HashMap<>(), null, true));
  }

  @Test
  void shouldGetRecipientAccountByEmail() {
    service.getRecipientAccountByEmail(RECIPIENT);

    verify(userAccountService).getUserDetailsByEmail(RECIPIENT);
  }

  @Test
  void shouldThrowExceptionGettingRecipientAccountByIdWhenNoMatchingTraineeId() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of());

    assertThrows(IllegalArgumentException.class, () -> service.getRecipientAccount(TRAINEE_ID));

    verify(userAccountService, never()).getUserDetailsById(USER_ID);
  }

  @Test
  void shouldGetRecipientAccountByIdWhenSingleMatchingTraineeId() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of(USER_ID));

    service.getRecipientAccount(TRAINEE_ID);

    verify(userAccountService).getUserDetailsById(USER_ID);
  }

  @Test
  void shouldThrowExceptionGettingRecipientAccountByIdWhenMultipleMatchingTraineeIds() {
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of("one", "two"));

    assertThrows(IllegalArgumentException.class, () -> service.getRecipientAccount(TRAINEE_ID));

    verify(userAccountService, never()).getUserDetailsById(USER_ID);
  }

  @Test
  void shouldUseDefaultHashIfMd5NotAvailable() {
    MockedStatic<MessageDigest> mockedMessageDigest = Mockito.mockStatic(MessageDigest.class);
    mockedMessageDigest.when(() -> MessageDigest.getInstance(any()))
        .thenThrow(NoSuchAlgorithmException.class);

    String hash = service.createMd5Hash("some input");
    assertThat("Unexpected default hash.", hash, is(DEFAULT_EMAIL_HASH));
    mockedMessageDigest.close();
  }

  @Test
  void shouldUseDefaultHashIfInputIsNull() {
    String hash = service.createMd5Hash(null);
    assertThat("Unexpected default hash.", hash, is(DEFAULT_EMAIL_HASH));
  }
}
