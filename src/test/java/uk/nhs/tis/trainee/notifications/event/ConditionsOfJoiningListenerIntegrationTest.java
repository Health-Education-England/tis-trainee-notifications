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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

@SpringBootTest(classes = {ConditionsOfJoiningListener.class, EmailService.class})
@ActiveProfiles("test")
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class ConditionsOfJoiningListenerIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String MANAGING_DEANERY = "Mars LO";
  private static final Instant SYNCED_AT = Instant.parse("2023-08-01T00:00:00Z");
  private static final String NEXT_STEPS_LINK = "https://local.notifications.com/programmes";
  private static final String SURVEY_LINK = "https://forms.gle/P2cdQUgTDWqjUodJA";

  private static final String DEFAULT_GREETING = "Dear Doctor,";
  private static final String DEFAULT_DETAIL = "We want to inform you that your local deanery"
      + " office has received your signed Conditions of Joining.";
  private static final String DEFAULT_NEXT_STEPS = "You can access a PDF of your signed Conditions"
      + " of Joining by visiting TIS Self-Service.";

  @MockBean
  private JavaMailSender mailSender;

  @MockBean
  private UserAccountService userAccountService;

  @Autowired
  private EmailService emailService;

  @Autowired
  private ConditionsOfJoiningListener listener;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldSendDefaultCojConfirmationWhenTemplateVariablesNotAvailable(String missingValue)
      throws Exception {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID, missingValue,
        new ConditionsOfJoining(null));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(EMAIL, missingValue));

    // Spy on the email service and inject a missing domain.
    EmailService emailService = spy(this.emailService);
    doAnswer(inv -> {
      inv.getArgument(2, Map.class).put("domain", URI.create(""));
      return inv.callRealMethod();
    }).when(emailService).sendMessageToExistingUser(any(), any(), any());

    // Create a new listener instance to inject the spy.
    ConditionsOfJoiningListener listener = new ConditionsOfJoiningListener(emailService);
    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element greeting = bodyChildren.get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(1);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = bodyChildren.get(2);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(""));

    Element surveyPara = content.getElementById("SURVEY_LINK");
    Elements surveyLinks = surveyPara.getElementsByTag("a");
    assertThat("Unexpected survey link count.", surveyLinks.size(), is(1));
    Element surveyLink = surveyLinks.get(0);
    assertThat("Unexpected survey link.", surveyLink.attr("href"),
        is(SURVEY_LINK));
  }

  @Test
  void shouldSendFullyTailoredCojConfirmationWhenAllTemplateVariablesAvailable() throws Exception {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(SYNCED_AT));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(EMAIL, FAMILY_NAME));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element greeting = bodyChildren.get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element eventDetail = bodyChildren.get(1);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local deanery office (Mars LO) has received your signed"
            + " Conditions of Joining on 01 August 2023."));

    Element nextSteps = bodyChildren.get(2);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(),
        is("You can access a PDF of your signed Conditions of Joining by visiting TIS"
            + " Self-Service."));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));

    Element surveyPara = content.getElementById("SURVEY_LINK");
    Elements surveyLinks = surveyPara.getElementsByTag("a");
    assertThat("Unexpected survey link count.", surveyLinks.size(), is(1));
    Element surveyLink = surveyLinks.get(0);
    assertThat("Unexpected survey link.", surveyLink.attr("href"),
        is(SURVEY_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredNameWhenAvailable() throws Exception {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID, null,
        new ConditionsOfJoining(null));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(EMAIL, FAMILY_NAME));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element greeting = bodyChildren.get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element eventDetail = bodyChildren.get(1);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = bodyChildren.get(2);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));

    Element surveyPara = content.getElementById("SURVEY_LINK");
    Elements surveyLinks = surveyPara.getElementsByTag("a");
    assertThat("Unexpected survey link count.", surveyLinks.size(), is(1));
    Element surveyLink = surveyLinks.get(0);
    assertThat("Unexpected survey link.", surveyLink.attr("href"),
        is(SURVEY_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredLocalOfficeWhenAvailable() throws Exception {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID, MANAGING_DEANERY,
        new ConditionsOfJoining(null));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(EMAIL, null));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element greeting = bodyChildren.get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(1);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local deanery office (Mars LO) has received your signed"
            + " Conditions of Joining."));

    Element nextSteps = bodyChildren.get(2);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));

    Element surveyPara = content.getElementById("SURVEY_LINK");
    Elements surveyLinks = surveyPara.getElementsByTag("a");
    assertThat("Unexpected survey link count.", surveyLinks.size(), is(1));
    Element surveyLink = surveyLinks.get(0);
    assertThat("Unexpected survey link.", surveyLink.attr("href"),
        is(SURVEY_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredSyncedAtWhenAvailable() throws Exception {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID, null,
        new ConditionsOfJoining(SYNCED_AT));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(EMAIL, null));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element greeting = bodyChildren.get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(1);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(),
        is("We want to inform you that your local deanery office has received your signed"
            + " Conditions of Joining on 01 August 2023."));

    Element nextSteps = bodyChildren.get(2);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));

    Element surveyPara = content.getElementById("SURVEY_LINK");
    Elements surveyLinks = surveyPara.getElementsByTag("a");
    assertThat("Unexpected survey link count.", surveyLinks.size(), is(1));
    Element surveyLink = surveyLinks.get(0);
    assertThat("Unexpected survey link.", surveyLink.attr("href"),
        is(SURVEY_LINK));
  }

  @Test
  void shouldSendCojConfirmationWithTailoredDomainWhenAvailable() throws Exception {
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(PERSON_ID, null,
        new ConditionsOfJoining(null));
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(EMAIL, null));

    listener.handleConditionsOfJoiningReceived(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(5));

    Element greeting = bodyChildren.get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is(DEFAULT_GREETING));

    Element eventDetail = bodyChildren.get(1);
    assertThat("Unexpected element tag.", eventDetail.tagName(), is("p"));
    assertThat("Unexpected event detail.", eventDetail.text(), is(DEFAULT_DETAIL));

    Element nextSteps = bodyChildren.get(2);
    assertThat("Unexpected element tag.", nextSteps.tagName(), is("p"));
    assertThat("Unexpected next steps.", nextSteps.text(), is(DEFAULT_NEXT_STEPS));

    Elements nextStepsLinks = nextSteps.getElementsByTag("a");
    assertThat("Unexpected next steps link count.", nextStepsLinks.size(), is(1));
    Element tssLink = nextStepsLinks.get(0);
    assertThat("Unexpected next steps link.", tssLink.attr("href"), is(NEXT_STEPS_LINK));

    Element surveyPara = content.getElementById("SURVEY_LINK");
    Elements surveyLinks = surveyPara.getElementsByTag("a");
    assertThat("Unexpected survey link count.", surveyLinks.size(), is(1));
    Element surveyLink = surveyLinks.get(0);
    assertThat("Unexpected survey link.", surveyLink.attr("href"),
        is(SURVEY_LINK));
  }
}
