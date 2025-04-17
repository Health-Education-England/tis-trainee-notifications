/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import io.awspring.cloud.s3.S3Template;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
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
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftContent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftStatus;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.TemplateService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

@SpringBootTest(classes = {LtftListener.class, EmailService.class, TemplateService.class})
@ActiveProfiles("test")
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class LtftListenerIntegrationTest {

  private static final String TIS_TRAINEE_ID = "123456";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TITLE = "Dr";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";
  private static final String GMC = "111111";

  private static final String DEFAULT_GREETING = "Dear Doctor,";
  private static final String DEFAULT_DETAIL = "The status of your LTFT application has changed.";
  private static final String DEFAULT_APP_REF = "Application Reference: (not set)";
  private static final String DEFAULT_DISCLAIMER = "This email is intended only for use by the "
      + "named addressee. It may contain confidential and/or privileged information. If you are "
      + "not the intended recipient, you should contact us immediately and should not disclose, "
      + "use, or rely on this email. We do not accept any liability arising from a third-party "
      + "taking action, or refraining from taking action, on the basis of information contained "
      + "in this email. Thank you.";

  private static final String LTFT_NAME = "ltft_name";
  private static final String STATUS = "APPROVED";
  private static final Instant TIMESTAMP = Instant.parse("2025-03-15T10:00:00Z");
  private static final String DBC = "1-1RSSPZ7";
  private static final String FORM_REF = "ltft_47165_001";

  private final LtftUpdateEvent.LtftContent.ProgrammeMembershipDetails programmeMembershipDetails =
      new LtftUpdateEvent.LtftContent.ProgrammeMembershipDetails(DBC);

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
  private LtftListener listener;

  @Value("${application.template-versions.ltft-updated.email}")
  private String templateVersion;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(TIS_TRAINEE_ID)).thenReturn(Set.of(USER_ID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldThrowExceptionWhenTemplateVersionNotConfigured(String missingValue) {
    LtftStatus status = new LtftStatus(
        new LtftStatus.StatusDetails(missingValue, null)
    );
    LtftContent ltftContent = new LtftContent(missingValue, programmeMembershipDetails);
    LtftUpdateEvent event = new LtftUpdateEvent(TIS_TRAINEE_ID, missingValue, ltftContent, status);

    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, missingValue, missingValue, GMC));

    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> listener.handleLtftUpdate(event),
        "Expected handleLtftUpdate to throw when template version is not configured"
    );

    if(missingValue != null){
      assertThat(thrown.getMessage(), is("No template version configured for LTFT state: " + missingValue));

    } else {
      assertThat(thrown.getMessage(), is("LTFT update state is " + missingValue + " for trainee " + TIS_TRAINEE_ID));
    }
  }

  @Test
  void shouldSendFullyTailoredLtftUpdatedNotificationWhenAllTemplateVariablesAvailable()
      throws Exception {
    LtftStatus status = new LtftStatus(
        new LtftStatus.StatusDetails(STATUS, TIMESTAMP)
    );
    LtftContent ltftContent = new LtftContent(LTFT_NAME, programmeMembershipDetails);
    LtftUpdateEvent event = new LtftUpdateEvent(TIS_TRAINEE_ID, FORM_REF, ltftContent, status);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleLtftUpdate(event);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());

    Elements bodyChildren = content.body().children();
    assertThat("Unexpected body children count.", bodyChildren.size(), is(8));

    Element logo = bodyChildren.get(0);
    assertThat("Unexpected element tag.", logo.tagName(), is("div"));

    Element disclaimer = bodyChildren.get(7);
    assertThat("Unexpected disclaimer.", disclaimer.text(), is(DEFAULT_DISCLAIMER));

    Element greeting = bodyChildren.get(1);
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));

    Element detail = bodyChildren.get(2);
    assertThat("Unexpected detail.", detail.text(),
        is("The status of your LTFT application (ref: ltft_47165_001) has changed "
            + "to APPROVED on 15 March 2025."));

    Element applicationRef = bodyChildren.get(5);
    assertThat("Unexpected application reference.", applicationRef.text(),
        is("Application Reference: ltft_name"));
  }

  @Test
  void shouldStoreLtftUpdatedNotificationHistoryWhenMessageSent() throws MessagingException {
    LtftStatus status = new LtftStatus(
        new LtftStatus.StatusDetails(STATUS, TIMESTAMP)
    );
    LtftContent ltftContent = new LtftContent(LTFT_NAME, programmeMembershipDetails);
    LtftUpdateEvent event = new LtftUpdateEvent(TIS_TRAINEE_ID, FORM_REF, ltftContent, status);
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, EMAIL, TITLE, FAMILY_NAME, GIVEN_NAME, GMC));

    listener.handleLtftUpdate(event);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(LTFT_UPDATED));
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());

    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(TIS_TRAINEE_ID));
    assertThat("Unexpected message type.", recipient.type(), is(MessageType.EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(EMAIL));

    TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(LTFT_UPDATED.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(templateVersion));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable count.", storedVariables.size(),
        is(10));
    assertThat("Unexpected template variable.", storedVariables.get("familyName"),
        is(FAMILY_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("givenName"),
        is(GIVEN_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("ltftName"),
        is(LTFT_NAME));
    assertThat("Unexpected template variable.", storedVariables.get("status"),
        is(STATUS));
    assertThat("Unexpected template variable.", storedVariables.get("formRef"),
        is(FORM_REF));
    assertThat("Unexpected template variable.", storedVariables.get("eventDate"),
        is(TIMESTAMP));
  }
}

