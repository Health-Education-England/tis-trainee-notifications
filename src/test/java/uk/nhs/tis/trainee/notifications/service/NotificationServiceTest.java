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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;
import static uk.nhs.tis.trainee.notifications.model.HrefType.ABSOLUTE_URL;
import static uk.nhs.tis.trainee.notifications.model.HrefType.NON_HREF;
import static uk.nhs.tis.trainee.notifications.model.HrefType.PROTOCOL_EMAIL;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.GMC_UPDATE;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.ONBOARDING_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.GMC_REJECTED_LO;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.GMC_REJECTED_TRAINEE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.GMC_UPDATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_ROLLOUT_2024_CORRECTION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.DEFAULT_NO_CONTACT_MESSAGE;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.DUMMY_USER_ROLES;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.ONE_DAY_IN_SECONDS;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_CONTACT_HREF_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.PLACEMENT_SPECIALTY_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.PLACEMENT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.shaded.org.apache.commons.lang3.time.DateUtils;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties.MessageTypeVersions;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContact;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationSummary;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.Placement;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;

class NotificationServiceTest {

  private static final String TEMPLATE_VERSION = "template-version";
  private static final String SERVICE_URL = "the-url";
  private static final String REFERENCE_URL = "reference-url";
  private static final String ACCOUNT_DETAILS_URL =
      SERVICE_URL + "/api/trainee-profile/account-details/{tisId}";
  private static final String JOB_KEY_STRING = "job-key";
  private static final JobKey JOB_KEY = new JobKey(JOB_KEY_STRING);
  private static final String TIS_ID = "tis-id";
  private static final String PERSON_ID = "person-id";
  private static final LocalDate START_DATE = LocalDate.now();
  private static final Integer NOTIFICATION_DELAY = 60;
  private static final String WHITELIST_1 = "123";
  private static final String WHITELIST_2 = "456";
  private static final List<String> NOT_WHITELISTED = List.of(WHITELIST_1, WHITELIST_2);
  private static final List<String> WHITELISTED = List.of(WHITELIST_1, WHITELIST_2, PERSON_ID);
  private static final String TIMEZONE = "Europe/London";

  private static final String LOCAL_OFFICE = "local office";
  private static final String LOCAL_OFFICE_CONTACT = "local office contact";

  private static final String PROGRAMME_NAME = "the programme";
  private static final NotificationType PM_NOTIFICATION_TYPE = PROGRAMME_CREATED;

  private static final String PLACEMENT_SPECIALTY = "specialty";
  private static final String PLACEMENT_TYPE = "placement type";
  private static final NotificationType PLACEMENT_NOTIFICATION_TYPE = PLACEMENT_UPDATED_WEEK_12;

  private static final String COGNITO_EMAIL = "cognito@email";
  private static final String COGNITO_FAMILY_NAME = "cognito-family-name";
  private static final String COGNITO_GIVEN_NAME = "cognito-given-name";
  private static final String USER_EMAIL = "email@address";
  private static final String USER_TITLE = "title";
  private static final String USER_FAMILY_NAME = "family-name";
  private static final String USER_GIVEN_NAME = "given-name";
  private static final String USER_GMC = "1234567";

  private JobDetail programmeJobDetails;
  private JobDetail placementJobDetails;
  private JobDataMap programmeJobDataMap;
  private JobDataMap placementJobDataMap;

  private NotificationService service;
  private NotificationService serviceWhitelisted;
  private EmailService emailService;
  private HistoryService historyService;
  private ProgrammeMembershipActionsService programmeMembershipActionsService;
  private RestTemplate restTemplate;
  private Scheduler scheduler;
  private MessagingControllerService messagingControllerService;
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  void setUp() {
    jobExecutionContext = mock(JobExecutionContext.class);
    emailService = mock(EmailService.class);
    historyService = mock(HistoryService.class);
    programmeMembershipActionsService = mock(ProgrammeMembershipActionsService.class);
    restTemplate = mock(RestTemplate.class);
    scheduler = mock(Scheduler.class);
    messagingControllerService = mock(MessagingControllerService.class);

    programmeJobDataMap = new JobDataMap();
    programmeJobDataMap.put(TIS_ID_FIELD, TIS_ID);
    programmeJobDataMap.put(PERSON_ID_FIELD, PERSON_ID);
    programmeJobDataMap.put(PROGRAMME_NAME_FIELD, PROGRAMME_NAME);
    programmeJobDataMap.put(TEMPLATE_OWNER_FIELD, LOCAL_OFFICE);
    programmeJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PM_NOTIFICATION_TYPE.toString());
    programmeJobDataMap.put(START_DATE_FIELD, START_DATE);

    NotificationSummary programmeNotificationSummary
        = new NotificationSummary(PROGRAMME_NAME, START_DATE,
            new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID), false);
    when(programmeMembershipActionsService.getNotificationSummary(any()))
        .thenReturn(programmeNotificationSummary);

    placementJobDataMap = new JobDataMap();
    placementJobDataMap.put(TIS_ID_FIELD, TIS_ID);
    placementJobDataMap.put(PERSON_ID_FIELD, PERSON_ID);
    placementJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD,
        PLACEMENT_NOTIFICATION_TYPE.toString());
    placementJobDataMap.put(START_DATE_FIELD, START_DATE);
    placementJobDataMap.put(PLACEMENT_TYPE_FIELD, PLACEMENT_TYPE);
    placementJobDataMap.put(TEMPLATE_OWNER_FIELD, LOCAL_OFFICE);
    placementJobDataMap.put(PLACEMENT_SPECIALTY_FIELD, PLACEMENT_SPECIALTY);

    programmeJobDetails = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(programmeJobDataMap)
        .build();

    placementJobDetails = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(placementJobDataMap)
        .build();

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(
        Arrays.stream(NotificationType.values()).collect(Collectors.toMap(
            NotificationType::getTemplateName,
            e -> new MessageTypeVersions(TEMPLATE_VERSION, null)
        )));

    service = new NotificationService(emailService, historyService,
        programmeMembershipActionsService, restTemplate, scheduler, messagingControllerService,
        templateVersions, SERVICE_URL, REFERENCE_URL, NOTIFICATION_DELAY, NOT_WHITELISTED,
        TIMEZONE);
    serviceWhitelisted = new NotificationService(emailService, historyService,
        programmeMembershipActionsService, restTemplate, scheduler, messagingControllerService,
        templateVersions, SERVICE_URL, REFERENCE_URL, NOTIFICATION_DELAY, WHITELISTED, TIMEZONE);
  }

  @Test
  void shouldNotSendNotificationWhenTemplateVersionIsEmpty() {

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(
        Arrays.stream(NotificationType.values()).collect(Collectors.toMap(
            NotificationType::getTemplateName,
            e -> new MessageTypeVersions(null, null)
        )));

    service = new NotificationService(emailService, historyService,
        programmeMembershipActionsService, restTemplate, scheduler, messagingControllerService,
        templateVersions, SERVICE_URL, REFERENCE_URL, NOTIFICATION_DELAY, NOT_WHITELISTED,
        TIMEZONE);

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(TIS_ID_FIELD, TIS_ID);
    jobDataMap.put(PERSON_ID_FIELD, PERSON_ID);
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, "PROGRAMME_CREATED");

    JobDetail jobDetail = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(jobDataMap)
        .build();

    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);

    when(messagingControllerService.isValidRecipient(PERSON_ID, MessageType.EMAIL))
        .thenReturn(true);

    when(restTemplate.getForObject(eq(ACCOUNT_DETAILS_URL), eq(UserDetails.class), anyMap()))
        .thenReturn(mock(UserDetails.class));

    when(emailService.getRecipientAccountByEmail(any())).thenReturn(null);

    Exception exception = assertThrows(IllegalArgumentException.class,
        () -> service.execute(jobExecutionContext));

    assertThat(exception.getMessage(),
        is("No email template version found for notification type '{}'."));
    verifyNoInteractions(emailService);
  }

  @Test
  void shouldSendNotificationWhenTemplateVersionIsUnrecognised() throws MessagingException {

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(TIS_ID_FIELD, TIS_ID);
    jobDataMap.put(PERSON_ID_FIELD, PERSON_ID);
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, "PROGRAMME_DAY_ONE");

    JobDetail jobDetail = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(jobDataMap)
        .build();

    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);

    when(messagingControllerService.isValidRecipient(PERSON_ID, MessageType.EMAIL))
        .thenReturn(true);

    when(restTemplate.getForObject(eq(ACCOUNT_DETAILS_URL), eq(UserDetails.class), anyMap()))
        .thenReturn(mock(UserDetails.class));

    when(emailService.getRecipientAccountByEmail(any())).thenReturn(null);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(true));
    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSetNotificationResultWhenSuccessfullyExecutedForProgrammeNotification() {
    UserDetails userAccountDetails =
        new UserDetails(
            true, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(PERSON_ID, MessageType.EMAIL))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID))
        .thenReturn(true);

    service.execute(jobExecutionContext);

    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSetNotificationResultWhenSuccessfullyExecutedForPlacementNotification() {
    UserDetails userAccountDetails =
        new UserDetails(
            true, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(placementJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(PERSON_ID, MessageType.EMAIL))
        .thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(true);

    service.execute(jobExecutionContext);

    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldThrowExceptionWhenUserDetailsCannotBeFound() {
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);

    when(emailService.getRecipientAccountByEmail(any())).thenReturn(null);
    when(restTemplate.getForObject(any(), any(), anyMap())).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () -> service.execute(jobExecutionContext));

    verify(jobExecutionContext, never()).setResult(any());
  }

  @Test
  void shouldThrowExceptionWhenTraineeDetailsRestClientExceptions() {
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);

    when(emailService.getRecipientAccountByEmail(any())).thenThrow(
        new IllegalArgumentException("error"));
    when(restTemplate.getForObject(any(), any(), anyMap()))
        .thenThrow(new RestClientException("error"));

    assertThrows(IllegalArgumentException.class, () -> service.execute(jobExecutionContext));

    verify(jobExecutionContext, never()).setResult(any());
  }

  @Test
  void shouldHandleCognitoExceptions() {
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);

    UserDetails userAccountDetails = new UserDetails(false, USER_EMAIL, USER_TITLE,
        USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    when(emailService.getRecipientAccountByEmail(any())).thenThrow(UserNotFoundException.class);

    assertDoesNotThrow(() -> service.execute(jobExecutionContext));

    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldRethrowEmailServiceExceptions() throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(PERSON_ID, MessageType.EMAIL))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID))
        .thenReturn(true);

    doThrow(new MessagingException("error"))
        .when(emailService).sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());

    assertThrows(RuntimeException.class, () -> service.execute(jobExecutionContext));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldLogPlacementEmailWhenNotMatchBothCriteria(boolean apiResult)
      throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(jobExecutionContext.getJobDetail()).thenReturn(placementJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(apiResult);
    when(messagingControllerService.isPlacementInPilot2024(any(), any())).thenReturn(!apiResult);
    when(messagingControllerService.isPlacementInRollout2024(any(), any())).thenReturn(!apiResult);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(true));
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSendPlacementRolloutCorrectionEmailRegardlessOfRolloutOrPilotStatus(boolean isValid)
      throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    placementJobDetails.getJobDataMap().put(TEMPLATE_NOTIFICATION_TYPE_FIELD,
        PLACEMENT_ROLLOUT_2024_CORRECTION.toString());
    when(jobExecutionContext.getJobDetail()).thenReturn(placementJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(isValid);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(!isValid));
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldLogProgrammeCreatedEmailWhenNotMatchAllCriteria(boolean apiResult)
      throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(apiResult);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(!apiResult);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(apiResult);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(any(), any()))
        .thenReturn(apiResult);
    NotificationSummary programmeNotificationSummaryUnnecessary
        = new NotificationSummary(PROGRAMME_NAME, START_DATE,
              new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID), false);
    when(programmeMembershipActionsService.getNotificationSummary(any()))
        .thenReturn(programmeNotificationSummaryUnnecessary);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(true));
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSendPlacementEmailWhenNotMatchBothCriteriaButInWhiteList(boolean apiResult)
      throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(jobExecutionContext.getJobDetail()).thenReturn(placementJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(apiResult);
    when(messagingControllerService.isPlacementInPilot2024(any(), any())).thenReturn(!apiResult);
    when(messagingControllerService.isPlacementInRollout2024(any(), any())).thenReturn(!apiResult);

    serviceWhitelisted.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSendProgrammeCreatedEmailWhenNotMatchAllCriteriaButInWhiteList(boolean apiResult)
      throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(apiResult);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(!apiResult);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(apiResult);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(any(), any()))
        .thenReturn(apiResult);

    serviceWhitelisted.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSendPlacementEmailWhenMatchBothCriteria() throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(placementJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any())).thenReturn(true);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSendPlacementEmailWhenNotPilotButRollout() throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(placementJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any())).thenReturn(false);
    when(messagingControllerService.isPlacementInRollout2024(any(), any())).thenReturn(true);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSendProgrammeCreatedEmailWhenMatchAllCriteria() throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSendProgrammeCreatedEmailWhenNotPilotButRollout() throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(any(), any()))
        .thenReturn(true);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldSendProgrammeReminderEmailWhenMatchAllCriteria(NotificationType notificationType)
      throws MessagingException {
    programmeJobDetails.getJobDataMap().put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    UserDetails userAccountDetails = new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(false));
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldSkipProgrammeReminderEmailWhenNoIncompleteActions(NotificationType notificationType)
      throws MessagingException {
    programmeJobDetails.getJobDataMap().put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    UserDetails userAccountDetails = new UserDetails(
        false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    NotificationSummary programmeNotificationSummaryUnnecessary
        = new NotificationSummary(PROGRAMME_NAME, START_DATE,
              new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID), true);
    when(programmeMembershipActionsService.getNotificationSummary(any()))
        .thenReturn(programmeNotificationSummaryUnnecessary);

    service.execute(jobExecutionContext);

    verify(emailService, never())
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());
    verify(jobExecutionContext).setResult(any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PLACEMENT_UPDATED_WEEK_12", "PLACEMENT_ROLLOUT_2024_CORRECTION",
          "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE", "PROGRAMME_UPDATED_WEEK_12",
          "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIgnoreNonActiveProgrammeOrPlacementJobs(NotificationType notificationType)
      throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    programmeJobDetails.getJobDataMap().put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    service.execute(jobExecutionContext);

    verify(emailService, never())
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());
    verify(jobExecutionContext, never()).setResult(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void shouldExecuteNowWithNullEmailWhenNoEmail(String email) throws MessagingException {
    UserDetails userAccountDetails = UserDetails.builder()
        .isRegistered(false)
        .email(email)
        .build();
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any())).thenReturn(
        true);

    JobDataMap jobData = new JobDataMap(Map.of(
        PERSON_ID_FIELD, PERSON_ID,
        TIS_ID_FIELD, TIS_ID,
        TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED
    ));

    service.executeNow(JOB_KEY_STRING, jobData);

    verify(emailService, never()).getRecipientAccountByEmail(any());
    verify(emailService).sendMessage(eq(PERSON_ID), isNull(), any(), any(), anyMap(), any(),
        eq(false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldExecuteNowWithNullEmailWhenEmailNoLongerAvailable(String email)
      throws MessagingException {
    UserDetails userAccountDetails = UserDetails.builder()
        .isRegistered(false)
        .email(email)
        .build();
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    JobDataMap jobData = new JobDataMap(Map.of(
        PERSON_ID_FIELD, PERSON_ID,
        TIS_ID_FIELD, TIS_ID,
        TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED,
        "email", "existing@example.com"
    ));

    service.executeNow(JOB_KEY_STRING, jobData);

    verify(emailService, never()).getRecipientAccountByEmail(any());
    verify(emailService).sendMessage(eq(PERSON_ID), isNull(), any(), any(), anyMap(), any(),
        eq(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PLACEMENT_UPDATED_WEEK_12", "PLACEMENT_ROLLOUT_2024_CORRECTION",
          "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE", "PROGRAMME_UPDATED_WEEK_12",
          "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldNotSendEmailWhenNotificationTypeNotCorrect(NotificationType notificationType) {

    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    boolean result = service.shouldActuallySendEmail(notificationType, PERSON_ID, TIS_ID);

    assertThat("Unexpected actuallySendEmail boolean.", result, is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_CREATED", "PROGRAMME_DAY_ONE",
      "PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldScheduleProgrammeMembershipNotification() throws SchedulerException {
    NotificationType notificationType = NotificationType.PROGRAMME_CREATED;

    LocalDate expectedDate = START_DATE.minusDays(0);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    String jobId = notificationType + "-" + TIS_ID;
    service.scheduleNotification(jobId, programmeJobDataMap, when, 0L);

    // create job in scheduler
    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.captor();
    verify(scheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + jobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(when));

    // save scheduled history in DB
    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
    assertThat("Unexpected attachments.", history.attachments(), nullValue());
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());
    assertThat("Unexpected status.", history.status(), is(SCHEDULED));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());

    History.RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(PERSON_ID));
    assertThat("Unexpected message type.", recipient.type(), is(EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(USER_EMAIL));

    TisReferenceInfo tisReference = history.tisReference();
    assertThat("Unexpected reference table.", tisReference.type(), is(PROGRAMME_MEMBERSHIP));
    assertThat("Unexpected reference id key.", tisReference.id(), is(TIS_ID));

    History.TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(TEMPLATE_VERSION));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable.", storedVariables.get(TIS_ID_FIELD),
        is(TIS_ID));
    assertThat("Unexpected template variable.", storedVariables.get(START_DATE_FIELD),
        is(START_DATE));
    assertThat("Unexpected template variable.", storedVariables.get(PERSON_ID_FIELD),
        is(PERSON_ID));
  }

  @Test
  void shouldSchedulePlacementNotification() throws SchedulerException {
    NotificationType notificationType = NotificationType.PLACEMENT_UPDATED_WEEK_12;

    LocalDate expectedDate = START_DATE.minusDays(84);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any()))
        .thenReturn(true);

    String jobId = notificationType + "-" + TIS_ID;
    service.scheduleNotification(jobId, placementJobDataMap, when, 0L);

    // create job in scheduler
    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.captor();
    verify(scheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + jobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(when));

    // save scheduled history in DB
    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification id.", history.id(), notNullValue());
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
    assertThat("Unexpected attachments.", history.attachments(), nullValue());
    assertThat("Unexpected sent at.", history.sentAt(), notNullValue());
    assertThat("Unexpected status.", history.status(), is(SCHEDULED));
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());

    History.RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient id.", recipient.id(), is(PERSON_ID));
    assertThat("Unexpected message type.", recipient.type(), is(EMAIL));
    assertThat("Unexpected contact.", recipient.contact(), is(USER_EMAIL));

    TisReferenceInfo tisReference = history.tisReference();
    assertThat("Unexpected reference table.", tisReference.type(), is(PLACEMENT));
    assertThat("Unexpected reference id key.", tisReference.id(), is(TIS_ID));

    History.TemplateInfo templateInfo = history.template();
    assertThat("Unexpected template name.", templateInfo.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templateInfo.version(), is(TEMPLATE_VERSION));

    Map<String, Object> storedVariables = templateInfo.variables();
    assertThat("Unexpected template variable.", storedVariables.get(TIS_ID_FIELD),
        is(TIS_ID));
    assertThat("Unexpected template variable.", storedVariables.get(START_DATE_FIELD),
        is(START_DATE));
    assertThat("Unexpected template variable.", storedVariables.get(PERSON_ID_FIELD),
        is(PERSON_ID));
  }

  @Test
  void shouldNotSaveSchedulePmNotificationHistoryWhenRecipientNotValid() throws SchedulerException {
    LocalDate expectedDate = START_DATE.minusDays(0);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    String jobId = NotificationType.PROGRAMME_CREATED + "-" + TIS_ID;
    service.scheduleNotification(jobId, programmeJobDataMap, when, 0L);

    verify(historyService, never()).save(any());
  }

  @Test
  void shouldNotSaveSchedulePmNotificationHistoryWhenNotNewStarter() throws SchedulerException {
    LocalDate expectedDate = START_DATE.minusDays(0);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    String jobId = NotificationType.PROGRAMME_CREATED + "-" + TIS_ID;
    service.scheduleNotification(jobId, programmeJobDataMap, when, 0L);

    verify(historyService, never()).save(any());
  }

  @Test
  void shouldNotSaveSchedulePmNotificationHistoryWhenNotInPilot() throws SchedulerException {
    LocalDate expectedDate = START_DATE.minusDays(0);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(any(), any()))
        .thenReturn(false);

    String jobId = NotificationType.PROGRAMME_DAY_ONE + "-" + TIS_ID;
    service.scheduleNotification(jobId, programmeJobDataMap, when, 0L);

    verify(historyService, never()).save(any());
  }

  @Test
  void shouldNotSaveSchedulePlacementNotificationWhenRecipientNotValid() throws SchedulerException {
    LocalDate expectedDate = START_DATE.minusDays(84);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any()))
        .thenReturn(true);

    String jobId = NotificationType.PLACEMENT_UPDATED_WEEK_12 + "-" + TIS_ID;
    service.scheduleNotification(jobId, placementJobDataMap, when, 0L);

    verify(historyService, never()).save(any());
  }

  @Test
  void shouldNotSaveSchedulePlacementNotificationWhenNotInPilot() throws SchedulerException {
    LocalDate expectedDate = START_DATE.minusDays(84);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any()))
        .thenReturn(false);

    String jobId = NotificationType.PLACEMENT_UPDATED_WEEK_12 + "-" + TIS_ID;
    service.scheduleNotification(jobId, placementJobDataMap, when, 0L);

    verify(historyService, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE",
          "PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldNotSaveScheduleNotificationWhenNotCorrectNotificationType(
      NotificationType notificationType) {
    placementJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD,
        notificationType.toString());

    LocalDate expectedDate = START_DATE.minusDays(84);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    service.saveScheduleHistory(placementJobDataMap, when);

    verify(historyService, never()).save(any());
  }

  @Test
  void shouldNotSaveScheduleNotificationAndThrowExceptionIfTemplateVersionMissing() {
    TemplateVersionsProperties templateVersions = mock(TemplateVersionsProperties.class);
    when(templateVersions.getTemplateVersion(any(), any())).thenReturn(Optional.empty());
    service = new NotificationService(emailService, historyService,
        programmeMembershipActionsService, restTemplate, scheduler, messagingControllerService,
        templateVersions, SERVICE_URL, REFERENCE_URL, NOTIFICATION_DELAY, NOT_WHITELISTED,
        TIMEZONE);

    LocalDate expectedDate = START_DATE.minusDays(84);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isPlacementInPilot2024(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    assertThrows(IllegalArgumentException.class, ()
        -> service.saveScheduleHistory(placementJobDataMap, when));

    verify(historyService, never()).save(any());
  }

  @Test
  void shouldAddStandardJobDetailsToNotification() throws MessagingException {
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, ONBOARDING_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, LOCAL_OFFICE_CONTACT);
    contacts.add(contact1);

    when(restTemplate
        .getForObject("reference-url/api/local-office-contact-by-lo-name/{localOfficeName}",
            List.class, Map.of("localOfficeName", LOCAL_OFFICE))).thenReturn(contacts);

    service.execute(jobExecutionContext);

    ArgumentCaptor<Map<String, Object>> jobDetailsCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<TisReferenceInfo> tisReferenceInfoCaptor = ArgumentCaptor.captor();

    verify(emailService).sendMessage(eq(PERSON_ID), eq(USER_EMAIL), eq(PROGRAMME_CREATED),
        eq(TEMPLATE_VERSION), jobDetailsCaptor.capture(), tisReferenceInfoCaptor.capture(),
        anyBoolean());

    Map<String, Object> jobDetailMap = jobDetailsCaptor.getValue();

    assertThat("Unexpected owner.", jobDetailMap.get(TEMPLATE_OWNER_FIELD),
        is(LOCAL_OFFICE));
    assertThat("Unexpected owner contact.", jobDetailMap.get(TEMPLATE_OWNER_CONTACT_FIELD),
        is(LOCAL_OFFICE_CONTACT));
    assertThat("Unexpected contact href.", jobDetailMap.get(TEMPLATE_CONTACT_HREF_FIELD),
        is(NON_HREF.toString()));
    assertThat("Unexpected GMC validity.", jobDetailMap.get("isValidGmc"), is(true));

    TisReferenceInfo tisReferenceInfo = tisReferenceInfoCaptor.getValue();
    assertThat("Unexpected TIS reference info type", tisReferenceInfo.type(),
        is(PROGRAMME_MEMBERSHIP));
    assertThat("Unexpected TIS reference info id", tisReferenceInfo.id(),
        is(TIS_ID));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_UPDATED_WEEK_12",
      "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldAddReminderJobDetailsToNotification(NotificationType notificationType) {

    programmeJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType.toString());
    programmeJobDetails = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(programmeJobDataMap)
        .build();
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);
    History welcomeNotification = History.builder().build();
    when(historyService.findScheduledEmailForTraineeByRefAndType(PERSON_ID, PROGRAMME_MEMBERSHIP,
        TIS_ID, PROGRAMME_CREATED)).thenReturn(welcomeNotification);

    service.execute(jobExecutionContext);

    verify(programmeMembershipActionsService).getActions(PERSON_ID, TIS_ID);
    verify(programmeMembershipActionsService).addActionsToJobMap(any(), any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2",
          "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldNotAddReminderJobDetailsToInapplicableNotification(NotificationType notificationType) {

    programmeJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType.toString());
    programmeJobDetails = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(programmeJobDataMap)
        .build();
    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    UserDetails userAccountDetails =
        new UserDetails(
            false, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    service.execute(jobExecutionContext);

    verify(programmeMembershipActionsService, never()).addActionsToJobMap(any(), any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"123456", "12345678", "abcdefg"})
  void shouldFlagInvalidGmcInStandardJobDetailsWhenGmcInvalid(String gmcNumber)
      throws MessagingException {
    UserDetails userAccountDetails = new UserDetails(false, USER_EMAIL, USER_TITLE,
        USER_FAMILY_NAME, USER_GIVEN_NAME, gmcNumber);

    when(jobExecutionContext.getJobDetail()).thenReturn(programmeJobDetails);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);
    when(messagingControllerService.isValidRecipient(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any()))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any()))
        .thenReturn(true);

    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, ONBOARDING_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, LOCAL_OFFICE_CONTACT);
    contacts.add(contact1);

    when(restTemplate
        .getForObject("reference-url/api/local-office-contact-by-lo-name/{localOfficeName}",
            List.class, Map.of("localOfficeName", LOCAL_OFFICE))).thenReturn(contacts);

    service.execute(jobExecutionContext);

    ArgumentCaptor<Map<String, Object>> jobDetailsCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<TisReferenceInfo> tisReferenceInfoCaptor = ArgumentCaptor.captor();

    verify(emailService).sendMessage(eq(PERSON_ID), eq(USER_EMAIL), eq(PROGRAMME_CREATED),
        eq(TEMPLATE_VERSION), jobDetailsCaptor.capture(), tisReferenceInfoCaptor.capture(),
        anyBoolean());

    Map<String, Object> jobDetailMap = jobDetailsCaptor.getValue();
    assertThat("Unexpected GMC.", jobDetailMap.get("gmcNumber"), is(gmcNumber));
    assertThat("Unexpected GMC validity.", jobDetailMap.get("isValidGmc"), is(false));
  }

  @Test
  void shouldRemoveNotification() throws SchedulerException {
    String jobId = NotificationType.PROGRAMME_CREATED + "-" + TIS_ID;

    service.removeNotification(jobId);

    JobKey expectedJobKey = new JobKey(jobId);
    verify(scheduler).deleteJob(expectedJobKey);
  }

  @ParameterizedTest
  @ValueSource(strings = {USER_GMC, "  " + USER_GMC, USER_GMC + "  ", "  " + USER_GMC + "  "})
  @NullSource
  void shouldMapUserDetailsWhenCognitoAndTssAccountsExist(String gmcNumber) {
    UserDetails cognitoAccountDetails =
        new UserDetails(true, COGNITO_EMAIL, null, COGNITO_FAMILY_NAME,
            COGNITO_GIVEN_NAME, null, List.of("should ignore"));
    UserDetails traineeProfileDetails =
        new UserDetails(null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME,
            gmcNumber, List.of("role"));

    UserDetails expectedResult =
        service.mapUserDetails(cognitoAccountDetails, traineeProfileDetails);

    assertThat("Unexpected isRegister", expectedResult.isRegistered(), is(true));
    assertThat("Unexpected email", expectedResult.email(), is(COGNITO_EMAIL));
    assertThat("Unexpected title", expectedResult.title(), is(USER_TITLE));
    assertThat("Unexpected family name", expectedResult.familyName(), is(COGNITO_FAMILY_NAME));
    assertThat("Unexpected given name", expectedResult.givenName(), is(COGNITO_GIVEN_NAME));
    assertThat("Unexpected user role size", expectedResult.role().size(), is(1));
    assertThat("Unexpected user role", expectedResult.role().get(0), is("role"));
    if (gmcNumber == null) {
      assertThat("Unexpected gmc number.", expectedResult.gmcNumber(), nullValue());
      assertDoesNotThrow(() ->
          service.mapUserDetails(cognitoAccountDetails, traineeProfileDetails));
    } else {
      assertThat("Unexpected gmc number.", expectedResult.gmcNumber(), is(USER_GMC));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {USER_GMC, "  " + USER_GMC, USER_GMC + "  ", "  " + USER_GMC + "  "})
  @NullSource
  void shouldMapUserDetailsWhenCognitoAccountNotExist(String gmcNumber) {
    UserDetails traineeProfileDetails =
        new UserDetails(null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME,
            gmcNumber, List.of("role"));

    UserDetails expectedResult = service.mapUserDetails(null, traineeProfileDetails);

    assertThat("Unexpected isRegister", expectedResult.isRegistered(), is(false));
    assertThat("Unexpected email", expectedResult.email(), is(USER_EMAIL));
    assertThat("Unexpected title", expectedResult.title(), is(USER_TITLE));
    assertThat("Unexpected family name", expectedResult.familyName(), is(USER_FAMILY_NAME));
    assertThat("Unexpected given name", expectedResult.givenName(), is(USER_GIVEN_NAME));
    assertThat("Unexpected user role size", expectedResult.role().size(), is(1));
    assertThat("Unexpected user role", expectedResult.role().get(0), is("role"));
    if (gmcNumber == null) {
      assertThat("Unexpected gmc number.", expectedResult.gmcNumber(), nullValue());
      assertDoesNotThrow(() -> service.mapUserDetails(null, traineeProfileDetails));
    } else {
      assertThat("Unexpected gmc number.", expectedResult.gmcNumber(), is(USER_GMC));
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldMapUserDetailsWithNullEmailWhenEmailMissing(String email) {
    UserDetails traineeDetails = UserDetails.builder()
        .email(email)
        .build();

    UserDetails userDetails = service.mapUserDetails(null, traineeDetails);

    assertThat("Unexpected email.", userDetails.email(), nullValue());
  }

  @Test
  void shouldMapUserDetailsWhenTssAccountNotExist() {
    UserDetails cognitoAccountDetails =
        new UserDetails(
            true, COGNITO_EMAIL, null, COGNITO_FAMILY_NAME, COGNITO_GIVEN_NAME, null);

    UserDetails expectedResult = service.mapUserDetails(cognitoAccountDetails, null);

    assertThat("Unexpected user details", expectedResult, nullValue());
  }

  @Test
  void shouldMapUserDetailsWhenBothCognitoAccountAndTraineeProfileNotExist() {
    UserDetails expectedResult = service.mapUserDetails(null, null);

    assertThat("Unexpected user details", expectedResult, nullValue());
  }

  @Test
  void shouldScheduleMissedMilestonesImmediately() {
    Date expectedMilestone = Date.from(Instant.now()
        .plus(NOTIFICATION_DELAY, ChronoUnit.MINUTES));
    Date expectedNearestMinute = DateUtils.round(expectedMilestone, Calendar.MINUTE);

    Date scheduledDate = service.getScheduleDate(LocalDate.MIN, 0);
    Date scheduledNearestMinute = DateUtils.round(scheduledDate, Calendar.MINUTE);

    assertThat("Unexpected scheduled date", scheduledNearestMinute, is(expectedNearestMinute));
  }

  @Test
  void shouldScheduleFutureMilestonesAtUpToNineHoursAfterStartOfCorrectDay() {
    LocalDate startDate = LocalDate.now().plusMonths(12);
    int daysBeforeStart = 100;
    LocalDate milestoneDate = startDate.minusDays(daysBeforeStart);
    Date expectedAfter = Date.from(milestoneDate
        .atStartOfDay().minus(1, ChronoUnit.MILLIS)
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());
    Date expectedBefore = Date.from(milestoneDate
        .atStartOfDay().plusHours(9)
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    Date scheduledDate = service.getScheduleDate(startDate, daysBeforeStart);

    assertThat("Unexpected early scheduled date", scheduledDate.after(expectedAfter),
        is(true));
    assertThat("Unexpected late scheduled date", scheduledDate.before(expectedBefore),
        is(true));
  }

  @Test
  void shouldScheduleJobsWithRandomness() throws SchedulerException {
    LocalDate startDate = LocalDate.now().plusMonths(12);
    int daysBeforeStart = 100;
    Date scheduledDate = service.getScheduleDate(startDate, daysBeforeStart);
    JobDataMap jobDataMap = new JobDataMap(Map.of(
        PERSON_ID_FIELD, PERSON_ID,
        TIS_ID_FIELD, TIS_ID,
        TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED
    ));
    UserDetails userAccountDetails = new UserDetails(false, USER_EMAIL, USER_TITLE,
        USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);
    when(emailService.getRecipientAccountByEmail(USER_EMAIL)).thenReturn(userAccountDetails);
    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    service.scheduleNotification("id1", jobDataMap, scheduledDate, ONE_DAY_IN_SECONDS);
    service.scheduleNotification("id2", jobDataMap, scheduledDate, ONE_DAY_IN_SECONDS);
    service.scheduleNotification("id3", jobDataMap, scheduledDate, ONE_DAY_IN_SECONDS);

    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
    verify(scheduler, times(3)).scheduleJob(any(), triggerCaptor.capture());
    //there is a less than 1 in 10^9 chance that all three dates are the same
    assertThat("Unexpected repeated scheduled date: either you are exceptionally unlucky "
            + "or something is wrong.",
        triggerCaptor.getAllValues().stream().map(Trigger::getStartTime).distinct().count(),
        not(1));
  }

  @Test
  void shouldDisplayMissedInAppMilestonesImmediately() {
    Instant expectedMilestone = Instant.now();

    Instant scheduledDate = service.calculateInAppDisplayDate(LocalDate.MIN, 0);

    assertThat("Unexpected display date", scheduledDate.truncatedTo(ChronoUnit.MINUTES),
        is(expectedMilestone.truncatedTo(ChronoUnit.MINUTES)));
  }

  @Test
  void shouldDisplayFutureInAppMilestonesAtStartOfCorrectDay() {
    LocalDate startDate = LocalDate.now().plusMonths(12);
    int daysBeforeStart = 100;
    LocalDate milestoneDate = startDate.minusDays(daysBeforeStart);
    Instant expectedMilestone = milestoneDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant();

    Instant scheduledDate = service.calculateInAppDisplayDate(startDate, daysBeforeStart);

    assertThat("Unexpected display date", scheduledDate, is(expectedMilestone));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMeetPmCriteriaWhenIsNewStarterAndIsInPilotOrRollout(boolean stateValue) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(stateValue);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(PERSON_ID, TIS_ID))
        .thenReturn(!stateValue);
    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID))
        .thenReturn(true);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, true, true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(true));

    verify(messagingControllerService).isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID);
    verify(messagingControllerService).isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID);
  }

  @Test
  void shouldNotMeetPmCriteriaWhenNotNewStarterAndNotInPilotOrRollout() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(PERSON_ID, TIS_ID))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID))
        .thenReturn(false);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, true, true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(false));

    // Verify that we short-circuit on the first false result.
    verify(messagingControllerService, never()).isProgrammeMembershipInPilot2024(any(), any());
  }

  @Test
  void shouldMeetPmCriteriaWhenIsNewStarterAndAndPilotCheckSkipped() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID)).thenReturn(
        true);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, true,
        false);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(true));
  }

  @Test
  void shouldNotMeetPmCriteriaWhenNotNewStarterAndAndPilotCheckSkipped() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID)).thenReturn(
        false);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, true,
        false);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(false));

    verify(messagingControllerService).isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID);
    verifyNoMoreInteractions(messagingControllerService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMeetPmCriteriaWhenNewStartCheckSkippedAndIsInPilotOrRollout(boolean stateValue) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(stateValue);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(PERSON_ID, TIS_ID))
        .thenReturn(!stateValue);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, false,
        true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(true));

    verify(messagingControllerService).isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID);
    verify(messagingControllerService).isProgrammeMembershipInRollout2024(PERSON_ID, TIS_ID);
    verifyNoMoreInteractions(messagingControllerService);
  }

  @Test
  void shouldNotMeetPmCriteriaWhenNewStartCheckSkippedAndNotInPilotOrRollout() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(false);
    when(messagingControllerService.isProgrammeMembershipInRollout2024(PERSON_ID, TIS_ID))
        .thenReturn(false);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, false,
        true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(false));

    verify(messagingControllerService).isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID);
    verify(messagingControllerService).isProgrammeMembershipInRollout2024(PERSON_ID, TIS_ID);
    verifyNoMoreInteractions(messagingControllerService);
  }

  @Test
  void shouldMeetPmCriteriaWhenAllChecksSkipped() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, false,
        false);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(true));

    verifyNoInteractions(messagingControllerService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMeetPlacementCriteriaWhenIsInPilotOrRollout(boolean stateValue) {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isPlacementInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(stateValue);
    when(messagingControllerService.isPlacementInRollout2024(PERSON_ID, TIS_ID))
        .thenReturn(!stateValue);

    boolean meetsCriteria = service.meetsCriteria(placement, true);

    assertThat("Unexpected unmet placement criteria.", meetsCriteria, is(true));

    verify(messagingControllerService).isPlacementInPilot2024(PERSON_ID, TIS_ID);
  }

  @Test
  void shouldNotMeetPlacementCriteriaWhenNotInPilotOrRollout() {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isPlacementInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(false);
    when(messagingControllerService.isPlacementInRollout2024(PERSON_ID, TIS_ID))
        .thenReturn(false);

    boolean meetsCriteria = service.meetsCriteria(placement, true);

    assertThat("Unexpected unmet placement criteria.", meetsCriteria, is(false));

    verify(messagingControllerService).isPlacementInPilot2024(PERSON_ID, TIS_ID);
  }

  @Test
  void shouldMeetPlacementCriteriaWhenIsPilotCheckSkipped() {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    boolean meetsCriteria = service.meetsCriteria(placement, false);

    assertThat("Unexpected unmet placement criteria.", meetsCriteria, is(true));
    verifyNoMoreInteractions(messagingControllerService);
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void programmeMembershipShouldBeNotifiableWhenIsValidRecipient(MessageType messageType) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isValidRecipient(PERSON_ID, messageType)).thenReturn(true);

    boolean isNotifiablePm = service.programmeMembershipIsNotifiable(programmeMembership,
        messageType);

    assertThat("Unexpected programme membership is notifiable value.", isNotifiablePm,
        is(true));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void programmeMembershipShouldNotBeNotifiableWhenIsInvalidRecipient(MessageType messageType) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isValidRecipient(PERSON_ID, messageType)).thenReturn(false);

    boolean isNotifiablePm = service.programmeMembershipIsNotifiable(programmeMembership,
        messageType);

    assertThat("Unexpected programme membership is notifiable value.", isNotifiablePm,
        is(false));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void placementShouldBeNotifiableWhenIsValidRecipient(MessageType messageType) {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isValidRecipient(PERSON_ID, messageType)).thenReturn(true);

    boolean isNotifiablePlacement = service.placementIsNotifiable(placement, messageType);

    assertThat("Unexpected placement is notifiable value.", isNotifiablePlacement,
        is(true));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void placementShouldNotBeNotifiableWhenIsInvalidRecipient(MessageType messageType) {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isValidRecipient(PERSON_ID, messageType)).thenReturn(false);

    boolean isNotifiablePlacement = service.placementIsNotifiable(placement, messageType);

    assertThat("Unexpected placement is notifiable value.", isNotifiablePlacement,
        is(false));
  }

  @Test
  void shouldGetUrlHrefTypeForUrlContact() {
    assertThat("Unexpected contact href type.",
        service.getHrefTypeForContact("https://a.validwebsite.com"),
        is(ABSOLUTE_URL.getHrefTypeName()));
  }

  @Test
  void shouldGetEmailHrefTypeForEmailContact() {
    assertThat("Unexpected contact href type.",
        service.getHrefTypeForContact("some@email.com"),
        is(PROTOCOL_EMAIL.getHrefTypeName()));
  }

  @Test
  void shouldGetNonValidHrefTypeForOtherTypeContact() {
    assertThat("Unexpected contact href type.",
        service.getHrefTypeForContact("some@email.com, also@another.com"),
        is(NON_HREF.getHrefTypeName()));
  }

  @Test
  void shouldGetContactWhenContactTypeExists() {
    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.TSS_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, "one@email.com, another@email.com");
    contacts.add(contact1);
    Map<String, String> contact2 = new HashMap<>();
    contact2.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.DEFERRAL.getContactTypeName());
    contact2.put(CONTACT_FIELD, "onboarding");
    contacts.add(contact2);

    String ownerContact = service.getOwnerContact(contacts,
        LocalOfficeContactType.TSS_SUPPORT, LocalOfficeContactType.DEFERRAL);

    assertThat("Unexpected owner contact.", ownerContact, is(contact1.get(CONTACT_FIELD)));
  }

  @Test
  void shouldGetFallbackContactWhenContactMissing() {
    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.TSS_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, "one@email.com, another@email.com");
    contacts.add(contact1);

    String ownerContact = service.getOwnerContact(contacts,
        LocalOfficeContactType.ONBOARDING_SUPPORT, LocalOfficeContactType.TSS_SUPPORT);

    assertThat("Unexpected owner contact.", ownerContact, is(contact1.get(CONTACT_FIELD)));
  }

  @Test
  void shouldGetDefaultNoContactWhenContactAndFallbackMissing() {
    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.TSS_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, "one@email.com, another@email.com");
    contacts.add(contact1);

    String ownerContact = service.getOwnerContact(contacts,
        LocalOfficeContactType.ONBOARDING_SUPPORT, LocalOfficeContactType.DEFERRAL);

    assertThat("Unexpected owner contact.", ownerContact, is(DEFAULT_NO_CONTACT_MESSAGE));
  }

  @Test
  void shouldGetDefaultNoContactWhenContactMissingAndFallbackNull() {
    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.TSS_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, "one@email.com, another@email.com");
    contacts.add(contact1);

    String ownerContact = service.getOwnerContact(contacts,
        LocalOfficeContactType.ONBOARDING_SUPPORT, null);

    assertThat("Unexpected owner contact.", ownerContact, is(DEFAULT_NO_CONTACT_MESSAGE));
  }

  @Test
  void shouldUseCustomDefaultNoContactWhenContactAndFallbackMissing() {
    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.TSS_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, "one@email.com, another@email.com");
    contacts.add(contact1);

    String ownerContact = service.getOwnerContact(contacts,
        LocalOfficeContactType.ONBOARDING_SUPPORT, LocalOfficeContactType.DEFERRAL, "testDefault");

    assertThat("Unexpected owner contact.", ownerContact, is("testDefault"));
  }

  @Test
  void shouldUseCustomDefaultNoContactWhenContactMissingAndFallbackNull() {
    List<Map<String, String>> contacts = new ArrayList<>();
    Map<String, String> contact1 = new HashMap<>();
    contact1.put(CONTACT_TYPE_FIELD, LocalOfficeContactType.TSS_SUPPORT.getContactTypeName());
    contact1.put(CONTACT_FIELD, "one@email.com, another@email.com");
    contacts.add(contact1);

    String ownerContact = service.getOwnerContact(contacts,
        LocalOfficeContactType.ONBOARDING_SUPPORT, null, "testDefault");

    assertThat("Unexpected owner contact.", ownerContact, is("testDefault"));
  }

  @Test
  void shouldGetEmptyContactListIfReferenceServiceFailure() {
    doThrow(new RestClientException("error"))
        .when(restTemplate).getForObject(any(), any(), anyMap());

    List<Map<String, String>> contactList = service.getOwnerContactList("a local office");

    assertThat("Unexpected owner contact list.", contactList.size(), is(0));
  }

  @Test
  void shouldGetEmptyContactListIfLocalOfficeNull() {
    List<Map<String, String>> contactList = service.getOwnerContactList(null);

    assertThat("Unexpected owner contact.", contactList.size(), is(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567", "0000000"})
  void shouldValidateGmcReturnTrueFor7ConsecutiveNumerical(String gmcNumber) {

    boolean isValidGmc = service.isValidGmc(gmcNumber);

    assertThat("Unexpected validate GMC result.", isValidGmc, is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abcdefg", "0000g00", "12345678", ""})
  void shouldValidateGmcReturnFalseForNot7ConsecutiveNumerical(String gmcNumber) {

    boolean isValidGmc = service.isValidGmc(gmcNumber);

    assertThat("Unexpected validate GMC result.", isValidGmc, is(false));
  }

  @Test
  void shouldValidateGmcReturnFalseWhenGmcNull() {

    boolean isValidGmc = service.isValidGmc(null);

    assertThat("Unexpected validate GMC result.", isValidGmc, is(false));
  }

  @Test
  void shouldGetTraineeDetails() {
    UserDetails userAccountDetails =
        new UserDetails(
            null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(restTemplate.getForObject(ACCOUNT_DETAILS_URL, UserDetails.class,
        Map.of(TIS_ID_FIELD, PERSON_ID))).thenReturn(userAccountDetails);

    UserDetails result = service.getTraineeDetails(PERSON_ID);

    assertThat("Unexpected email.", result.email(), is(userAccountDetails.email()));
    assertThat("Unexpected title.", result.title(), is(userAccountDetails.title()));
    assertThat("Unexpected family name.", result.familyName(),
        is(userAccountDetails.familyName()));
    assertThat("Unexpected given name.", result.givenName(),
        is(userAccountDetails.givenName()));
    assertThat("Unexpected gmc.", result.gmcNumber(), is(userAccountDetails.gmcNumber()));
  }

  @Test
  void shouldReturnNullWhenRestClientExceptionsInGetTraineeDetails() {
    when(restTemplate.getForObject(any(), any(), anyMap()))
        .thenThrow(new RestClientException("error"));

    UserDetails result = service.getTraineeDetails(PERSON_ID);

    assertThat("Unexpected result.", result, is(nullValue()));
  }

  @ParameterizedTest
  @EnumSource(LocalOfficeContactType.class)
  void shouldReturnEmptySetWhenRestClientExceptionsInGetTraineeLocalOfficeContacts(
      LocalOfficeContactType contactType) {
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenThrow(new RestClientException("error"));

    Set<LocalOfficeContact> result = service.getTraineeLocalOfficeContacts(PERSON_ID, contactType);

    assertThat("Unexpected result.", result.size(), is(0));
  }

  @ParameterizedTest
  @EnumSource(LocalOfficeContactType.class)
  void shouldReturnEmptySetWhenTraineeLocalOfficeContactsEmpty(LocalOfficeContactType contactType) {
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(new ResponseEntity<>(HttpStatus.OK));

    Set<LocalOfficeContact> result = service.getTraineeLocalOfficeContacts(PERSON_ID, contactType);

    assertThat("Unexpected result.", result.size(), is(0));
  }

  @ParameterizedTest
  @EnumSource(LocalOfficeContactType.class)
  void shouldGetTraineeLocalOfficeContacts(LocalOfficeContactType contactType) {
    Set<LocalOfficeContact> localOfficeContacts
        = Set.of(new LocalOfficeContact("contact", "local office"));

    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType),
        eq(Map.of(TIS_ID_FIELD, PERSON_ID, CONTACT_TYPE_FIELD, contactType))))
        .thenReturn(ResponseEntity.of(Optional.of(localOfficeContacts)));

    Set<LocalOfficeContact> result = service.getTraineeLocalOfficeContacts(PERSON_ID, contactType);

    assertThat("Unexpected local offices.", result.size(), is(1));
    LocalOfficeContact localOfficeContact = localOfficeContacts.iterator().next();
    LocalOfficeContact resultLo = result.iterator().next();
    assertThat("Unexpected local office contact.", resultLo.contact(),
        is(localOfficeContact.contact()));
    assertThat("Unexpected local office name.", resultLo.localOffice(),
        is(localOfficeContact.localOffice()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"https://a.url.com", "something else", "encoded%40thing.com"})
  void shouldVerifyContactIsNotEmail(String contact) {
    assertThat("Unexpected is-email check.", service.isLocalOfficeContactEmail(contact),
        is(false));
  }

  @Test
  void shouldVerifyContactIsEmail() {
    assertThat("Unexpected is-email check.", service.isLocalOfficeContactEmail("a@b.com"),
        is(true));
  }

  @ParameterizedTest
  @EnumSource(LocalOfficeContactType.class)
  void shouldNotSendEmailIfNoLocalOfficeContact(LocalOfficeContactType localOfficeContactType)
      throws MessagingException {
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(new ResponseEntity<>(HttpStatus.OK));
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);
    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", List.of("role"));

    service.sendLocalOfficeMail(userDetails, PERSON_ID, GMC_UPDATE, new HashMap<>(), "",
        GMC_UPDATED);
    Set<String> sentTo = service.sendLocalOfficeMail(userDetails, PERSON_ID, localOfficeContactType,
        new HashMap<>(), "", GMC_REJECTED_LO);

    assertThat("Unexpected sent to set.", sentTo.size(), is(0));
    verifyNoInteractions(emailService);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "not an email")
  void shouldNotSendEmailIfLocalOfficeHasNoEmail(String contact) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact(contact, "local office"));
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(ResponseEntity.of(Optional.of(localOfficeContacts)));
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);
    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", List.of("role"));

    Set<String> sentTo = service.sendLocalOfficeMail(userDetails, PERSON_ID, GMC_UPDATE,
        new HashMap<>(), "", GMC_UPDATED);

    assertThat("Unexpected sent to set.", sentTo.size(), is(0));
    verifyNoInteractions(emailService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldLogEmailIfMessagingNotEnabled(boolean isMessagingEnabled) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact@email.com", "local office"));
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(ResponseEntity.of(Optional.of(localOfficeContacts)));
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(isMessagingEnabled);
    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", List.of("role"));

    service.sendLocalOfficeMail(userDetails, PERSON_ID, GMC_UPDATE, new HashMap<>(), "",
        GMC_UPDATED);

    verify(emailService)
        .sendMessage(any(), eq("contact@email.com"), any(), any(), any(), any(),
            eq(!isMessagingEnabled));
  }

  @ParameterizedTest
  @MethodSource("provideAllDummyUserRoles")
  void shouldLogLoEmailIfDummyTrainee(String dummyRole) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact@email.com", "local office"));
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(ResponseEntity.of(Optional.of(localOfficeContacts)));
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);
    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", List.of("role", dummyRole));

    service.sendLocalOfficeMail(userDetails, PERSON_ID, GMC_UPDATE, new HashMap<>(), "",
        GMC_UPDATED);

    verify(emailService)
        .sendMessage(any(), eq("contact@email.com"), any(), any(), any(), any(), eq(true));
  }

  @ParameterizedTest
  @EnumSource(LocalOfficeContactType.class)
  void shouldSendLocalOfficeMailToDistinctEmailContactsAndReturnAlphabeticSet(
      LocalOfficeContactType localOfficeContactType) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact2@email.com", "local office"));
    localOfficeContacts.add(new LocalOfficeContact("contact1@email.com", "name2"));
    localOfficeContacts.add(new LocalOfficeContact("contact2@email.com", "name3"));
    localOfficeContacts.add(new LocalOfficeContact("a@email.com", "name4"));
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(ResponseEntity.of(Optional.of(localOfficeContacts)));

    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", List.of("role"));
    Map<String, Object> templateVars = new HashMap<>();
    Set<String> sentTo = service.sendLocalOfficeMail(userDetails, PERSON_ID, localOfficeContactType,
        templateVars, "", GMC_REJECTED_LO);

    assertThat("Unexpected sent to set.", sentTo.size(), is(3));
    verify(emailService, times(3)).sendMessage(
        eq(PERSON_ID), any(), eq(GMC_REJECTED_LO), eq(""), eq(templateVars), eq(null),
        anyBoolean());
    assertThat("Unexpected sent to element.", sentTo.contains("contact1@email.com"), is(true));
    assertThat("Unexpected sent to element.", sentTo.contains("contact2@email.com"), is(true));
    assertThat("Unexpected sent to element.", sentTo.contains("a@email.com"), is(true));
    //verify ordering of set is alphabetic
    Object[] sentToArray = sentTo.toArray();
    assertThat("Unexpected sent to ordering.", sentToArray[0].equals("a@email.com"));
    assertThat("Unexpected sent to ordering.", sentToArray[1].equals("contact1@email.com"));
    assertThat("Unexpected sent to ordering.", sentToArray[2].equals("contact2@email.com"));
  }

  @ParameterizedTest
  @EnumSource(LocalOfficeContactType.class)
  void shouldSendLocalOfficeMailWithCorrectDetails(
      LocalOfficeContactType localOfficeContactType) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact@email.com", "local office"));
    ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
        = new ParameterizedTypeReference<>() {};
    when(restTemplate.exchange(any(), any(), any(), eq(loContactListListType), anyMap()))
        .thenReturn(ResponseEntity.of(Optional.of(localOfficeContacts)));
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", List.of("role"));
    Map<String, Object> templateVars = new HashMap<>();
    templateVars.put("key", "value");
    service.sendLocalOfficeMail(userDetails, PERSON_ID, localOfficeContactType, templateVars,
        TEMPLATE_VERSION, GMC_REJECTED_LO);

    ArgumentCaptor<Map<String, Object>> sentTemplateCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessage(
        eq(PERSON_ID), eq("contact@email.com"), eq(GMC_REJECTED_LO), eq(TEMPLATE_VERSION),
        sentTemplateCaptor.capture(), eq(null), eq(false));

    Map<String, Object> sentTemplate = sentTemplateCaptor.getValue();
    assertThat("Unexpected sent template size.", sentTemplate.size(), is(1));
    assertThat("Unexpected sent template element.", sentTemplate.get("key"), is("value"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldNotSendTraineeEmailIfNoEmailAddress(String email) throws MessagingException {

    service.sendTraineeMail(PERSON_ID, email, new HashMap<>(), "", GMC_REJECTED_TRAINEE);

    verifyNoInteractions(emailService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSendTraineeEmailWithCorrectDetails(boolean isMessagingEnabled)
      throws MessagingException {
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(isMessagingEnabled);

    Map<String, Object> templateVars = new HashMap<>();
    templateVars.put("key", "value");
    service.sendTraineeMail(PERSON_ID, "email@email.com", templateVars, TEMPLATE_VERSION,
        GMC_REJECTED_TRAINEE);

    ArgumentCaptor<Map<String, Object>> sentTemplateCaptor = ArgumentCaptor.captor();
    verify(emailService).sendMessage(
        eq(PERSON_ID), eq("email@email.com"), eq(GMC_REJECTED_TRAINEE), eq(TEMPLATE_VERSION),
        sentTemplateCaptor.capture(), eq(null), eq(!isMessagingEnabled));

    Map<String, Object> sentTemplate = sentTemplateCaptor.getValue();
    assertThat("Unexpected sent template size.", sentTemplate.size(), is(1));
    assertThat("Unexpected sent template element.", sentTemplate.get("key"), is("value"));
  }

  @Test
  void shouldNotTreatNullRoleAsDummy() {
    UserDetails userDetails = new UserDetails(
        true, "traineeemail", "title", "family", "given", "1111111", null);

    boolean isDummy = service.userHasDummyRole(userDetails);

    assertThat("Unexpected dummy user check.", isDummy, is(false));
  }

  /**
   * Provide all dummy user roles.
   *
   * @return The stream of dummy / placeholder / test user roles.
   */
  private static Stream<String> provideAllDummyUserRoles() {
    return DUMMY_USER_ROLES.stream();
  }
}
