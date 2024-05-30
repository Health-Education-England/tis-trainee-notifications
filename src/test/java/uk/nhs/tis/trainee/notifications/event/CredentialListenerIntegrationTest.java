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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.CREDENTIAL_REVOKED;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import uk.nhs.tis.trainee.notifications.dto.CredentialEvent;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.TemplateService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

@SpringBootTest(classes = {CredentialListener.class, EmailService.class, TemplateService.class})
@ActiveProfiles("test")
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class CredentialListenerIntegrationTest {

  private static final String TRAINEE_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Mr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";

  private static final String CREDENTIAL_TYPE = "TestCredential";
  private static final Instant ISSUED_AT = Instant.parse("2024-01-01T00:00:00Z");

  private static final String DEFAULT_NEXT_STEPS_LINK = "https://local.notifications.com";
  private static final String PLACEMENT_NEXT_STEPS_LINK = "https://local.notifications.com/placements";
  private static final String PROGRAMME_NEXT_STEPS_LINK = "https://local.notifications.com/programmes";

  private static final String DEFAULT_SUBJECT = "Your DSP credential was revoked";
  private static final String DEFAULT_GREETING = "Dear Doctor,";
  private static final String DEFAULT_DETAIL = "You previously issued a DSP credential, this"
      + " credential has been revoked due to a change made by your local NHS England office.";
  private static final String DEFAULT_NEXT_STEPS = "To review the change and re-issue the"
      + " credential, please visit TIS Self-Service.";
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

  @Autowired
  private EmailService emailService;

  @Autowired
  private CredentialListener listener;

  @Value("${application.template-versions.credential-revoked.email}")
  private String templateVersion;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of(USER_ID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldSendDefaultRevocationNoticeWhenTemplateVariablesNotAvailable(String missingValue)
      throws Exception {
    CredentialEvent event = new CredentialEvent(null, missingValue, null, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, missingValue, missingValue, GMC));

    // Spy on the email service and inject a missing domain.
    EmailService emailService = spy(this.emailService);
    doAnswer(inv -> {
      inv.getArgument(3, Map.class).put("domain", URI.create(""));
      return inv.callRealMethod();
    }).when(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any());

    // Create a new listener instance to inject the spy.
    CredentialListener listener = new CredentialListener(emailService, templateVersion);
    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(DEFAULT_SUBJECT));

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
  void shouldSendFullyTailoredRevocationNoticeWhenAllTemplateVariablesAvailable() throws Exception {
    CredentialEvent event = new CredentialEvent(null, CREDENTIAL_TYPE, ISSUED_AT, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(),
        is("Your TestCredential credential was revoked"));

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
        is("You previously issued a TestCredential DSP credential on 01 January 2024, this"
            + " credential has been revoked due to a change made by your local NHS England"
            + " office."));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(),
        is("To review the change and re-issue the credential, please visit TIS Self-Service."));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(DEFAULT_NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendRevocationNoticeWithTailoredNameWhenAvailable() throws Exception {
    CredentialEvent event = new CredentialEvent(null, null, null, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(DEFAULT_SUBJECT));

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
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(DEFAULT_NEXT_STEPS_LINK));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Training Placement", "Training Programme"})
  void shouldSendRevocationNoticeWithTailoredCredentialTypeWhenAvailable(String credentialType)
      throws Exception {
    CredentialEvent event = new CredentialEvent(null, credentialType, null, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(),
        matchesPattern("Your Training \\w+ credential was revoked"));

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
        is("You previously issued a " + credentialType + " DSP credential, this credential has been"
            + " revoked due to a change made by your local NHS England office."));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"),
        startsWith(DEFAULT_NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendRevocationNoticeWithTailoredIssuedAtWhenAvailable() throws Exception {
    CredentialEvent event = new CredentialEvent(null, null, ISSUED_AT, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(DEFAULT_SUBJECT));

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
        is("You previously issued a DSP credential on 01 January 2024, this credential has been"
            + " revoked due to a change made by your local NHS England office."));

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Element disclaimer = bodyChildren.get(5);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(DEFAULT_NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendRevocationNoticeWithTailoredDomainWhenAvailable() throws Exception {
    CredentialEvent event = new CredentialEvent(null, null, null, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(DEFAULT_SUBJECT));

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
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(DEFAULT_NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendRevocationNoticeTailoredForTrainingPlacement() throws Exception {
    CredentialEvent event = new CredentialEvent(null, "Training Placement", null, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(),
        is("Your Training Placement credential was revoked"));

    Document content = Jsoup.parse((String) message.getContent());
    Elements bodyChildren = content.body().children();

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(PLACEMENT_NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendRevocationNoticeTailoredForTrainingProgramme() throws Exception {
    CredentialEvent event = new CredentialEvent(null, "Training Programme", null, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, null, null, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(),
        is("Your Training Programme credential was revoked"));

    Document content = Jsoup.parse((String) message.getContent());
    Elements bodyChildren = content.body().children();

    Element nextSteps = bodyChildren.get(3);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(PROGRAMME_NEXT_STEPS_LINK));
  }

  @Test
  void shouldStoreCredentialRevokedNotificationHistoryWhenMessageSent() throws MessagingException {
    CredentialEvent event = new CredentialEvent(null, CREDENTIAL_TYPE, ISSUED_AT, TRAINEE_ID);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleCredentialRevoked(event);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(CREDENTIAL_REVOKED));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(TRAINEE_ID));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(CREDENTIAL_REVOKED.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(6));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"),
        is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("credentialType"),
        is(CREDENTIAL_TYPE));
    assertThat("Unexpected template variable.", storedVariables.get("issuedAt"), is(ISSUED_AT));
    assertThat("Unexpected template variable.", storedVariables.get("domain"),
        is(URI.create(DEFAULT_NEXT_STEPS_LINK)));
  }
}
