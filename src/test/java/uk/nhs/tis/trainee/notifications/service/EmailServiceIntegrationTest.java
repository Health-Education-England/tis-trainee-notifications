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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_CONTACT_HREF_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.GMC_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NUMBER_FIELD;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

@SpringBootTest(classes = {EmailService.class, TemplateService.class})
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class EmailServiceIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String RECIPIENT = "test@tis.nhs.uk";
  private static final String GMC = "111111";
  private static final String TEMPLATE_VERSION = "v1.0.0";
  private static final String PROGRAM_NO = "SW111";
  private static final LocalDate PLACEMENT_START_DATE = LocalDate.now();


  @MockBean
  private JavaMailSender mailSender;

  @MockBean
  private HistoryService historyService;

  @MockBean
  private UserAccountService userAccountService;

  @MockBean
  private RestTemplate restTemplate;

  @Autowired
  private EmailService service;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldIncludeSubjectInTemplates(NotificationType notificationType, String templateVersion)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), not(emptyOrNullString()));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldIncludeContentInTemplates(NotificationType notificationType, String templateVersion)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected content.", (String) message.getContent(), not(emptyOrNullString()));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldGreetExistingUsersConsistentlyWhenNameNotAvailable(NotificationType notificationType,
      String templateVersion) throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    Element greeting = body.children().get(getGreetingElementIndex(notificationType));
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Doctor,"));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldGreetExistingUsersConsistentlyWhenNameAvailable(NotificationType notificationType,
      String templateVersion) throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, "Gilliam", "Anthony", null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of(), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    if (notificationType.equals(PLACEMENT_UPDATED_WEEK_12)
        || notificationType.equals(PROGRAMME_DAY_ONE)) {
      Element greeting = body.children().get(getGreetingElementIndex(notificationType));
      assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
      assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Anthony Gilliam,"));
    } else {
      Element greeting = body.children().get(getGreetingElementIndex(notificationType));
      assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
      assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));
    }
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldGreetExistingUsersConsistentlyWhenNameProvided(NotificationType notificationType,
      String templateVersion) throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, "Gilliam", "Anthony", null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion,
        Map.of("familyName", "Maillig"), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    if (notificationType.equals(PLACEMENT_UPDATED_WEEK_12)
        || notificationType.equals(PROGRAMME_DAY_ONE)) {
      Element greeting = body.children().get(getGreetingElementIndex(notificationType));
      assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
      assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Anthony Maillig,"));
    } else {
      Element greeting = body.children().get(getGreetingElementIndex(notificationType));
      assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
      assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Maillig,"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "123090", "873091", "555592", "647593", "033594"})
  void shouldUseSegmentAinPlacement12WeekTemplateIfGmcNumberEmptyOrEnds0to4(String gmc)
      throws Exception {
    //version is fixed, since a different test will inevitably be needed for later revisions
    String templateVersion = "v1.0.0";
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, "Gilliam", "Anthony", null));

    service.sendMessageToExistingUser(PERSON_ID, PLACEMENT_UPDATED_WEEK_12,
        templateVersion, Map.of("familyName", "Maillig", "gmcNumber", gmc), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    Element survey = body.children().get(19);
    assertThat("Unexpected survey segment.",
        survey.text().contains("Did you find this email useful?"), is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"123095", "873096", "555597", "647598", "033599", "UNKNOWN", "na"})
  void shouldUseSegmentBinPlacement12WeekTemplateIfGmcNumberEnds5orGreater(String gmc)
      throws Exception {
    //version is fixed, since a different test will inevitably be needed for later revisions
    String templateVersion = "v1.0.0";
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, "Gilliam", "Anthony", null));

    service.sendMessageToExistingUser(PERSON_ID, PLACEMENT_UPDATED_WEEK_12,
        templateVersion, Map.of("familyName", "Maillig", "gmcNumber", gmc), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    Element survey = body.children().get(19);
    assertThat("Unexpected survey segment.",
        survey.text().contains("Did you find this email useful?"), is(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldIncludeGmcInMailtoSubjectWhenGmcAvailable(NotificationType notificationType)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, GMC));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, TEMPLATE_VERSION,
        Map.of(TEMPLATE_CONTACT_HREF_FIELD, "email", GMC_NUMBER_FIELD, GMC), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    String mailTo = body.getElementById("loMail").attributes().get("href");
    assertThat("Unexpected gmc.", mailTo.contains("GMC: " + GMC), is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldIncludeUnknownGmcInMailtoSubjectWhenGmcIsEmpty(NotificationType notificationType)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, GMC));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, TEMPLATE_VERSION,
        Map.of(TEMPLATE_CONTACT_HREF_FIELD, "email", GMC_NUMBER_FIELD, ""), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    String mailTo = body.getElementById("loMail").attributes().get("href");
    assertThat("Unexpected gmc.", mailTo.contains("GMC: unknown"), is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldIncludeUnknownGmcInMailtoSubjectWhenGmcIsMissing(NotificationType notificationType)
      throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, TEMPLATE_VERSION,
        Map.of(TEMPLATE_CONTACT_HREF_FIELD, "email"), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    String mailTo = body.getElementById("loMail").attributes().get("href");
    assertThat("Unexpected gmc.", mailTo.contains("GMC: unknown"), is(true));
  }

  @Test
  void shouldIncludeProgNoInMailtoSubjectWhenProgNoAvailable() throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, GMC));

    service.sendMessageToExistingUser(PERSON_ID, PROGRAMME_CREATED, TEMPLATE_VERSION,
        Map.of(TEMPLATE_CONTACT_HREF_FIELD, "email", PROGRAMME_NUMBER_FIELD, PROGRAM_NO), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    String mailTo = body.getElementById("loMail").attributes().get("href");
    assertThat("Unexpected programme number.", mailTo.contains("Prog No: " + PROGRAM_NO), is(true));
  }

  @Test
  void shouldIncludeUnknownProgNoInMailtoSubjectWhenProgNoIsMissing() throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, GMC));

    service.sendMessageToExistingUser(PERSON_ID, PROGRAMME_CREATED, TEMPLATE_VERSION,
        Map.of(TEMPLATE_CONTACT_HREF_FIELD, "email"), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    String mailTo = body.getElementById("loMail").attributes().get("href");
    assertThat("Unexpected programme number.", mailTo.contains("Prog No: unknown"), is(true));
  }

  @Test
  void shouldIncludeStartDateInMailtoSubjectWhenStartDateAvailable() throws Exception {
    when(userAccountService.getUserDetailsById(USER_ID)).thenReturn(
        new UserDetails(true, RECIPIENT, null, null, null, GMC));

    service.sendMessageToExistingUser(PERSON_ID, PLACEMENT_UPDATED_WEEK_12, TEMPLATE_VERSION,
        Map.of(TEMPLATE_CONTACT_HREF_FIELD, "email", START_DATE_FIELD, PLACEMENT_START_DATE), null);

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.captor();
    verify(mailSender).send(messageCaptor.capture());

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    String expectedStartDate = PLACEMENT_START_DATE.format(formatter);

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    String mailTo = body.getElementById("loMail").attributes().get("href");
    assertThat("Unexpected start date.", mailTo.contains("Start Date: " + expectedStartDate),
        is(true));
  }

  int getGreetingElementIndex(NotificationType notificationType) {
    return switch (notificationType) {
      case PLACEMENT_UPDATED_WEEK_12, PROGRAMME_CREATED, PROGRAMME_DAY_ONE, EMAIL_UPDATED_NEW,
          EMAIL_UPDATED_OLD, COJ_CONFIRMATION, CREDENTIAL_REVOKED, FORM_UPDATED -> 1;
      default -> 0;
    };
  }
}
