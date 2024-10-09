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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.MULTIPART_MIXED_VALUE;
import static org.springframework.http.MediaType.MULTIPART_RELATED_VALUE;
import static org.springframework.http.MediaType.TEXT_HTML_VALUE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.StoredFile;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.TemplateService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

@SpringBootTest(classes = {ConditionsOfJoiningListener.class, EmailService.class,
    TemplateService.class})
@ActiveProfiles("test")
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class ConditionsOfJoiningListenerIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Mr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";
  private static final Instant SYNCED_AT = Instant.parse("2023-08-01T00:00:00Z");
  private static final String APP_DOMAIN = "https://local.notifications.com";
  private static final String NEXT_STEPS_LINK = APP_DOMAIN + "/programmes";

  private static final String DEFAULT_GREETING = "Dear Doctor,";
  private static final String DEFAULT_DETAIL = "We want to inform you that your local NHS England"
      + " office has received your signed Conditions of Joining.";
  private static final String DEFAULT_NEXT_STEPS = "You can access a PDF of your signed Conditions"
      + " of Joining by visiting TIS Self-Service.";
  private static final String DEFAULT_DISCLAIMER = "This email is intended only for use by the "
      + "named addressee. It may contain confidential and/or privileged information. If you are "
      + "not the intended recipient, you should contact us immediately and should not disclose, "
      + "use, or rely on this email. We do not accept any liability arising from a third-party "
      + "taking action, or refraining from taking action, on the basis of information contained "
      + "in this email. Thank you.";

  @MockBean
  private JavaMailSender mailSender;

  @MockBean
  private UserAccountService userAccountService;

  @MockBean
  private HistoryService historyService;

  @MockBean
  private S3Template s3Template;

  @Autowired
  private EmailService emailService;

  @Autowired
  private ConditionsOfJoiningListener listener;

  @Value("${application.template-versions.coj-confirmation.email}")
  private String templateVersion;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldSendDefaultCojConfirmationWhenTemplateVariablesNotAvailable(String missingValue)
      throws Exception {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(null), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, missingValue, missingValue, GMC));

    // Spy on the email service and inject a missing domain.
    EmailService emailService = spy(this.emailService);
    doAnswer(inv -> {
      inv.getArgument(3, Map.class).put("domain", URI.create(""));
      return inv.callRealMethod();
    }).when(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any(), any());

    // Create a new listener instance to inject the spy.
    ConditionsOfJoiningListener listener = new ConditionsOfJoiningListener(emailService,
        templateVersion);
    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(2);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(""));
  }

  @Test
  void shouldSendFullyTailoredCojConfirmationWhenAllTemplateVariablesAvailable() throws Exception {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element eventDetail = bodyChildren.get(2);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local NHS England office has received your signed"
            + " Conditions of Joining on 01 August 2023."));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(),
        is("You can access a PDF of your signed Conditions of Joining by visiting TIS"
            + " Self-Service."));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredNameWhenAvailable() throws Exception {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(null), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element eventDetail = bodyChildren.get(2);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredLocalOfficeWhenAvailable() throws Exception {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(null), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(2);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local NHS England office has received your signed"
            + " Conditions of Joining."));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredSyncedAtWhenAvailable() throws Exception {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(2);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local NHS England office has received your signed"
            + " Conditions of Joining on 01 August 2023."));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredDomainWhenAvailable() throws Exception {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(null), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(2);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendMultipartCojConfirmationWhenPdfAvailable() throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    S3Resource s3Resource = mock(S3Resource.class);
    when(s3Resource.getFilename()).thenReturn("file.pdf");
    when(s3Resource.contentType()).thenReturn(APPLICATION_PDF_VALUE);
    when(s3Resource.getContentAsByteArray()).thenReturn("test".getBytes());
    when(s3Template.download("my-bucket", "my-key.pdf")).thenReturn(s3Resource);

    StoredFile pdf = new StoredFile("my-bucket", "my-key.pdf");
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(null), pdf);

    listener.handleConditionsOfJoiningPublished(event);

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
    Document document = Jsoup.parse((String) content.getContent());
    Elements bodyChildren = document.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(6));

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
  void shouldStoreCojPublishedNotificationHistoryWhenMessageSent() throws MessagingException {
    CojPublishedEvent event = new CojPublishedEvent(PERSON_ID,
        new ConditionsOfJoining(SYNCED_AT), null);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleConditionsOfJoiningPublished(event);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(COJ_CONFIRMATION));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(PERSON_ID));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(COJ_CONFIRMATION.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(5));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"), is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("syncedAt"), is(SYNCED_AT));
    assertThat("Unexpected template variable.", storedVariables.get("domain"),
        is(URI.create(APP_DOMAIN)));
  }
}
