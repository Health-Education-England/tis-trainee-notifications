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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
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
import uk.nhs.tis.trainee.notifications.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.TemplateService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

@SpringBootTest(classes = {FormListener.class, EmailService.class, TemplateService.class})
@ActiveProfiles("test")
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class FormListenerIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Mr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";

  private static final Instant FORM_UPDATED_AT = Instant.parse("2023-08-01T00:00:00Z");
  private static final String APP_DOMAIN = "https://local.notifications.com";
  private static final String NEXT_STEPS_LINK = APP_DOMAIN + "/form-type";

  private static final String FORM_NAME = "123.json";
  private static final String FORM_SUBMITTED = "SUBMITTED";
  private static final String FORM_UNSUBMITTED = "UNSUBMITTED";
  private static final String FORM_DELETED = "DELETED";
  private static final String FORM_TYPE = "form-type";
  private static final Map<String, Object> FORM_CONTENT = new HashMap<>();

  private static final String DEFAULT_GREETING = "Dear Doctor,";
  private static final String DEFAULT_DETAIL = "We want to inform you that your FormR has been "
      + "updated.";
  private static final String DEFAULT_NEXT_STEPS = "If this is unexpected then please contact your "
      + "local NHS England office for further details.";
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
  private FormListener listener;

  @Value("${application.template-versions.form-updated.email}")
  private String templateVersion;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldSendDefaultFormNotificationWhenTemplateVariablesNotAvailable(String missingValue)
      throws Exception {
    FormUpdateEvent event = new FormUpdateEvent(missingValue, missingValue, PERSON_ID,
        missingValue, null, FORM_CONTENT);

    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, missingValue, missingValue, GMC));

    // Spy on the email service and inject a missing domain.
    EmailService emailService = spy(this.emailService);
    doAnswer(inv -> {
      inv.getArgument(3, Map.class).put("domain", URI.create(""));
      return inv.callRealMethod();
    }).when(emailService).sendMessageToExistingUser(any(), any(), any(), any(), any());

    FormListener listener = new FormListener(emailService, templateVersion);
    listener.handleFormUpdate(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    assertThat("Unexpected missing event content.", content.getElementById("OTHER"),
        notNullValue());

    Element disclaimer = bodyChildren.get(4);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element eventDetail = content.getElementById("OTHER").children().get(0);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = content.getElementById("OTHER").children().get(1);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(0));
  }

  @Test
  void shouldSendFullyTailoredFormSubmittedNotificationWhenAllTemplateVariablesAvailable()
      throws Exception {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_SUBMITTED, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleFormUpdate(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element disclaimer = bodyChildren.get(4);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    assertThat("Unexpected missing event content.", content.getElementById(FORM_SUBMITTED),
        notNullValue());

    Element eventDetail = content.getElementById(FORM_SUBMITTED).children().get(0);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local NHS England office has received your FormR "
            + "on 01 August 2023."));

    Element nextSteps = content.getElementById(FORM_SUBMITTED).children().get(1);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(),
        is("You can access your PDF signed FormR by visiting TIS Self-Service."));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"),
        is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendFullyTailoredFormUnsubmittedNotificationWhenAllTemplateVariablesAvailable()
      throws Exception {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_UNSUBMITTED, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleFormUpdate(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    assertThat("Unexpected missing event content.", content.getElementById(FORM_UNSUBMITTED),
        notNullValue());

    Element disclaimer = bodyChildren.get(4);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element eventDetail = content.getElementById(FORM_UNSUBMITTED).children().get(0);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local NHS England office has returned your FormR "
            + "for modification on 01 August 2023."));

    Element nextSteps = content.getElementById(FORM_UNSUBMITTED).children().get(1);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(),
        is("You can update and re-submit your FormR by visiting TIS Self-Service."));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"),
        is(NEXT_STEPS_LINK));
  }

  @Test
  void shouldSendFullyTailoredFormDeletedNotificationWhenAllTemplateVariablesAvailable()
      throws Exception {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_DELETED, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleFormUpdate(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    assertThat("Unexpected missing event content.", content.getElementById(FORM_DELETED),
        notNullValue());

    Element disclaimer = bodyChildren.get(4);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element eventDetail = content.getElementById(FORM_DELETED).children().get(0);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local NHS England office has deleted your FormR "
            + "on 01 August 2023."));

    Element nextSteps = content.getElementById(FORM_DELETED).children().get(1);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(),
        is("If you did not request this then please contact your local NHS England office "
            + "for further details."));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(0));
  }

  @Test
  void shouldStoreFormUpdatedNotificationHistoryWhenMessageSent() throws MessagingException {
    FormUpdateEvent event = new FormUpdateEvent(FORM_NAME, FORM_SUBMITTED, PERSON_ID,
        FORM_TYPE, FORM_UPDATED_AT, FORM_CONTENT);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleFormUpdate(event);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(FORM_UPDATED));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(PERSON_ID));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(FORM_UPDATED.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(), is(8));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"), is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("lifecycleState"),
        is(FORM_SUBMITTED));
    assertThat("Unexpected template variable.", storedVariables.get("formType"), is(FORM_TYPE));
    assertThat("Unexpected template variable.", storedVariables.get("formName"), is(FORM_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("eventDate"),
        is(FORM_UPDATED_AT));
    assertThat("Unexpected template variable.", storedVariables.get("domain"),
        is(URI.create(APP_DOMAIN)));
  }
}
