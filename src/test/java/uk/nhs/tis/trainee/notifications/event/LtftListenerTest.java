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
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.LTFT_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.SUPPORTED_RETURN_TO_TRAINING;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.TSS_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_APPROVED_TPD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED_TPD;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties.MessageTypeVersions;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.DiscussionsDto;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipDto;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.event.LtftListener.Contact;
import uk.nhs.tis.trainee.notifications.mapper.LtftEventMapper;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class LtftListenerTest {

  private static final String VERSION = "v1.2.3";
  private static final String TRAINEE_ID = "47165";
  private static final Instant TIMESTAMP = Instant.now();
  private static final String LTFT_NAME = "My LTFT";
  private static final String FORM_REFERENCE = "ltft_47165_002";
  private static final String LTFT_STATUS = "SUBMITTED";
  private static final String TPD_NAME = "TPD name";
  private static final String TPD_EMAIL = "tpd@email";
  private static final String PROGRAMME_NAME = "My Programme";
  private static final String PROGRAMME_DEANERY = "Test Deanery";
  private static final double PROGRAMME_WTE = 1.0;
  private static final LocalDate PROGRAMME_START_DATE = LocalDate.of(2024, 1, 1);
  private static final UserDetails USER_DETAILS = new UserDetails(
      true, "trainee@email.nhs", "Dr", "Gilliam", "Bob", "1234567");

  private LtftListener listener;
  private NotificationService notificationService;
  private EmailService emailService;
  private LtftEventMapper ltftEventMapper;

  @BeforeEach
  void setUp() {
    notificationService = mock(NotificationService.class);
    emailService = mock(EmailService.class);
    ltftEventMapper = mock(LtftEventMapper.class);
    when(ltftEventMapper.map(any())).thenAnswer(i -> i.getArguments()[0]);
    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        "ltft-approved-tpd", new MessageTypeVersions(VERSION, null),
        "ltft-approved", new MessageTypeVersions(VERSION, null),
        "ltft-updated", new MessageTypeVersions(VERSION, null),
        "ltft-submitted-tpd", new MessageTypeVersions(VERSION, null),
        "ltft-submitted", new MessageTypeVersions(VERSION, null),
        "ltft-unsubmitted", new MessageTypeVersions(VERSION, null)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, true);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED     | LTFT_APPROVED
      SUBMITTED    | LTFT_SUBMITTED
      UNSUBMITTED  | LTFT_UNSUBMITTED
      Other-Status | LTFT_UPDATED
      """)
  void shouldThrowExceptionWhenNoEmailTemplateAvailable(String state, NotificationType type) {
    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        type.getTemplateName(), new MessageTypeVersions(null, VERSION)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, true);

    assertThrows(IllegalArgumentException.class, () -> listener.handleLtftUpdate(event));
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED", "UNSUBMITTED", "Other-Status"})
  void shouldThrowExceptionWhenLtftUpdatedAndSendingFails(String state) throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessageToExistingUser(any(), any(), any(), any(), any());

    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();

    assertThrows(MessagingException.class, () -> listener.handleLtftUpdate(event));
  }

  @Test
  void shouldSetTraineeIdWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .state("")
        .build();

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(eq(TRAINEE_ID), any(), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED     | LTFT_APPROVED
      SUBMITTED    | LTFT_SUBMITTED
      UNSUBMITTED  | LTFT_UNSUBMITTED
      Other-Status | LTFT_UPDATED
      """)
  void shouldSetNotificationTypeWhenLtftUpdated(String state, NotificationType type)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .state(state)
        .build();

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), eq(type), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED     | LTFT_APPROVED    | v1.2.3
      Other-Status | LTFT_UPDATED     | v2.3.4
      SUBMITTED    | LTFT_SUBMITTED   | v3.4.5
      UNSUBMITTED  | LTFT_UNSUBMITTED | v4.5.6
      """)
  void shouldSetTemplateVersionWhenLtftUpdated(String state, NotificationType type, String version)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .state(state)
        .build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        type.getTemplateName(), new MessageTypeVersions(version, null)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, true);

    listener.handleLtftUpdate(event);

    verify(emailService).sendMessageToExistingUser(any(), any(), eq(version), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED", "UNSUBMITTED", "Other-Status"})
  void shouldPopulateTemplateVariablesWithContactsWhenLtftUpdated(String state)
      throws MessagingException {
    Set<LocalOfficeContactType> expectedContacts = Set.of(
        LTFT, LTFT_SUPPORT, SUPPORTED_RETURN_TO_TRAINING, TSS_SUPPORT);
    when(notificationService.getOwnerContactList("Test Deanery")).thenReturn(
        expectedContacts.stream()
            .map(ct -> Map.of(
                "contact", "https://test/" + ct,
                "contactTypeName", ct.getContactTypeName()
            ))
            .toList());
    when(notificationService.getOwnerContact(any(), any(), eq(TSS_SUPPORT),
        eq(""))).thenCallRealMethod();
    when(notificationService.getHrefTypeForContact(any())).thenCallRealMethod();

    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .programmeMembership(ProgrammeMembershipDto.builder()
            .managingDeanery("Test Deanery")
            .build())
        .state(state)
        .build();

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    Map<String, Contact> contacts = (Map<String, Contact>) templateVariables.get("contacts");

    assertThat("Unexpected contact count.", contacts.keySet(), hasSize(4));

    expectedContacts.forEach(ct -> {
      Contact contact = contacts.get(ct.name());
      assertThat("Unexpected contact link.", contact.contact(), is("https://test/" + ct));
      assertThat("Unexpected contact HREF type.", contact.type(), is("url"));
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED", "UNSUBMITTED", "Other-Status"})
  void shouldPopulateTemplateVariablesWithEventWhenLtftUpdated(String state)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected event.", templateVariables.get("var"), sameInstance(event));
  }

  @Test
  void shouldIncludeEventPropertiesWhenLtftUpdated() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formRef(FORM_REFERENCE)
        .formName(LTFT_NAME)
        .state(LTFT_STATUS)
        .timestamp(TIMESTAMP)
        .build();

    listener.handleLtftUpdate(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessageToExistingUser(any(), any(), any(),
        templateVarsCaptor.capture(), any());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    LtftUpdateEvent templateEvent = (LtftUpdateEvent) templateVariables.get("var");
    assertThat("Unexpected trainee ID.", templateEvent.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected form ref.", templateEvent.getFormRef(), is(FORM_REFERENCE));
    assertThat("Unexpected LTFT name.", templateEvent.getFormName(), is(LTFT_NAME));
    assertThat("Unexpected status.", templateEvent.getState(), is(LTFT_STATUS));
    assertThat("Unexpected event timestamp.", templateEvent.getTimestamp(), is(TIMESTAMP));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Other-status"})
  void shouldIgnoreNonMatchedNotificationTypesWhenLtftUpdatedForTpd(String state)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();
    listener.handleLtftUpdateTpd(event);
    verifyNoInteractions(emailService);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED  | LTFT_APPROVED_TPD
      SUBMITTED | LTFT_SUBMITTED_TPD
      """)
  void shouldThrowExceptionWhenNoTpdEmailTemplateAvailable(String state, NotificationType type) {
    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        type.getTemplateName(), new MessageTypeVersions(null, VERSION)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, true);

    assertThrows(IllegalArgumentException.class, () -> listener.handleLtftUpdateTpd(event));
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED"})
  void shouldThrowExceptionWhenLtftUpdatedAndSendingTpdFails(String state)
      throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());

    when(emailService.getRecipientAccount(any())).thenReturn(USER_DETAILS);

    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();

    assertThrows(MessagingException.class, () -> listener.handleLtftUpdateTpd(event));
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED"})
  void shouldSetTraineeIdWhenLtftUpdatedForTpd(String state) throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .state(state)
        .build();

    when(emailService.getRecipientAccount(TRAINEE_ID)).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    verify(emailService)
        .sendMessage(eq(TRAINEE_ID), any(), any(), any(), any(), any(), anyBoolean());
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED"})
  void shouldSetTpdEmailWhenLtftUpdatedForTpd(String state) throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .state(state)
        .discussions(DiscussionsDto.builder()
            .tpdName(TPD_NAME)
            .tpdEmail(TPD_EMAIL)
            .build())
        .build();

    when(emailService.getRecipientAccount(TRAINEE_ID)).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    verify(emailService)
        .sendMessage(any(), eq(TPD_EMAIL), any(), any(), any(), any(), anyBoolean());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetSendOrLogFlagWhenLtftApprovedForTpd(boolean emailNotificationsEnabled)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .state("APPROVED")
        .build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        LTFT_APPROVED_TPD.getTemplateName(), new MessageTypeVersions(VERSION, null)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, emailNotificationsEnabled);

    when(emailService.getRecipientAccount(TRAINEE_ID)).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    verify(emailService)
        .sendMessage(any(), any(), any(), any(), any(), any(), eq(!emailNotificationsEnabled));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetSendOrLogFlagWhenLtftSubmittedForTpd(boolean emailNotificationsEnabled)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .state("SUBMITTED")
        .build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        LTFT_SUBMITTED_TPD.getTemplateName(), new MessageTypeVersions(VERSION, null)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, emailNotificationsEnabled);

    when(emailService.getRecipientAccount(TRAINEE_ID)).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    verify(emailService)
        .sendMessage(any(), any(), any(), any(), any(), any(), eq(!emailNotificationsEnabled));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED    | LTFT_APPROVED_TPD
      SUBMITTED   | LTFT_SUBMITTED_TPD
      """)
  void shouldSetNotificationTypeWhenLtftUpdatedForTpd(String state, NotificationType type)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .state(state)
        .build();

    when(emailService.getRecipientAccount(any())).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    verify(emailService)
        .sendMessage(any(), any(), eq(type), any(), any(), any(), anyBoolean());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      APPROVED    | LTFT_APPROVED_TPD | v2.3.4
      SUBMITTED   | LTFT_SUBMITTED_TPD | v3.4.5
      """)
  void shouldSetTemplateVersionWhenLtftUpdatedForTpd(String state, NotificationType type,
      String version) throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .state(state)
        .build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        type.getTemplateName(), new MessageTypeVersions(version, null)
    ));
    listener = new LtftListener(notificationService, emailService, templateVersions,
        ltftEventMapper, true);

    when(emailService.getRecipientAccount(any())).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    verify(emailService)
        .sendMessage(any(), any(), any(), eq(version), any(), any(), anyBoolean());
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED"})
  void shouldPopulateTemplateVariablesWithContactsWhenLtftUpdatedForTpd(String state)
      throws MessagingException {
    Set<LocalOfficeContactType> expectedContacts = Set.of(
        LTFT, LTFT_SUPPORT, SUPPORTED_RETURN_TO_TRAINING, TSS_SUPPORT);
    when(notificationService.getOwnerContactList("Test Deanery")).thenReturn(
        expectedContacts.stream()
            .map(ct -> Map.of(
                "contact", "https://test/" + ct,
                "contactTypeName", ct.getContactTypeName()
            ))
            .toList());
    when(notificationService.getOwnerContact(any(), any(), eq(TSS_SUPPORT),
        eq(""))).thenCallRealMethod();
    when(notificationService.getHrefTypeForContact(any())).thenCallRealMethod();

    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .programmeMembership(ProgrammeMembershipDto.builder()
            .managingDeanery("Test Deanery")
            .build())
        .state(state)
        .build();

    when(emailService.getRecipientAccount(any())).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService)
        .sendMessage(any(), any(), any(), any(), templateVarsCaptor.capture(), any(), anyBoolean());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    Map<String, Contact> contacts = (Map<String, Contact>) templateVariables.get("contacts");

    assertThat("Unexpected contact count.", contacts.keySet(), hasSize(4));

    expectedContacts.forEach(ct -> {
      Contact contact = contacts.get(ct.name());
      assertThat("Unexpected contact link.", contact.contact(), is("https://test/" + ct));
      assertThat("Unexpected contact HREF type.", contact.type(), is("url"));
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"APPROVED", "SUBMITTED"})
  void shouldPopulateTemplateVariablesWithEventWhenLtftUpdatedForTpd(String state)
      throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder().state(state).build();

    when(emailService.getRecipientAccount(any())).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService)
        .sendMessage(any(), any(), any(), any(), templateVarsCaptor.capture(), any(), anyBoolean());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected event.", templateVariables.get("var"), sameInstance(event));
  }

  @Test
  void shouldIncludeEventPropertiesWhenLtftUpdatedForTpd() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formRef(FORM_REFERENCE)
        .formName(LTFT_NAME)
        .state(LTFT_STATUS)
        .discussions(DiscussionsDto.builder()
            .tpdName(TPD_NAME)
            .tpdEmail(TPD_EMAIL)
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .name(PROGRAMME_NAME)
            .managingDeanery(PROGRAMME_DEANERY)
            .wte(PROGRAMME_WTE)
            .startDate(PROGRAMME_START_DATE)
            .build())
        .timestamp(TIMESTAMP)
        .build();

    when(emailService.getRecipientAccount(TRAINEE_ID)).thenReturn(USER_DETAILS);

    listener.handleLtftUpdateTpd(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService)
        .sendMessage(any(), any(), any(), any(), templateVarsCaptor.capture(), any(), anyBoolean());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    LtftUpdateEvent templateEvent = (LtftUpdateEvent) templateVariables.get("var");
    assertThat("Unexpected trainee ID.", templateEvent.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected form ref.", templateEvent.getFormRef(), is(FORM_REFERENCE));
    assertThat("Unexpected LTFT name.", templateEvent.getFormName(), is(LTFT_NAME));
    assertThat("Unexpected status.", templateEvent.getState(), is(LTFT_STATUS));
    DiscussionsDto discussions = templateEvent.getDiscussions();
    assertThat("Unexpected TPD name.", discussions.tpdName(), is(TPD_NAME));
    assertThat("Unexpected TPD email.", discussions.tpdEmail(), is(TPD_EMAIL));
    ProgrammeMembershipDto programmeMembership = templateEvent.getProgrammeMembership();
    assertThat("Unexpected programme name.", programmeMembership.name(), is(PROGRAMME_NAME));
    assertThat("Unexpected programme deanery.", programmeMembership.managingDeanery(),
        is(PROGRAMME_DEANERY));
    assertThat("Unexpected programme WTE.", programmeMembership.wte(), is(PROGRAMME_WTE));
    assertThat("Unexpected programme start date.", programmeMembership.startDate(),
        is(PROGRAMME_START_DATE));
    assertThat("Unexpected event timestamp.", templateEvent.getTimestamp(), is(TIMESTAMP));
  }
}
