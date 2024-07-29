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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;
import static uk.nhs.tis.trainee.notifications.model.HrefType.ABSOLUTE_URL;
import static uk.nhs.tis.trainee.notifications.model.HrefType.NON_HREF;
import static uk.nhs.tis.trainee.notifications.model.HrefType.PROTOCOL_EMAIL;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.ONBOARDING_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.DEFAULT_NO_CONTACT_MESSAGE;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.shaded.org.apache.commons.lang3.time.DateUtils;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.MessageType;
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
  private static final String USER_GMC = "111111";

  private JobDetail programmeJobDetails;
  private JobDetail placementJobDetails;
  private JobDataMap programmeJobDataMap;
  private JobDataMap placementJobDataMap;

  private NotificationService service;
  private NotificationService serviceWhitelisted;
  private EmailService emailService;
  private RestTemplate restTemplate;
  private Scheduler scheduler;
  private MessagingControllerService messagingControllerService;
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  void setUp() {
    jobExecutionContext = mock(JobExecutionContext.class);
    emailService = mock(EmailService.class);
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

    service = new NotificationService(emailService, restTemplate, scheduler,
        messagingControllerService, TEMPLATE_VERSION, SERVICE_URL, REFERENCE_URL,
        NOTIFICATION_DELAY, NOT_WHITELISTED, TIMEZONE);
    serviceWhitelisted = new NotificationService(emailService, restTemplate, scheduler,
        messagingControllerService, TEMPLATE_VERSION, SERVICE_URL, REFERENCE_URL,
        NOTIFICATION_DELAY, WHITELISTED, TIMEZONE);
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
  void shouldThrowExceptionWhenRestClientExceptions() {
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

    service.execute(jobExecutionContext);

    verify(emailService).sendMessage(any(), any(), any(), any(), any(), any(), eq(true));
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

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldIgnoreNonProgrammeOrPlacementJobs(NotificationType notificationType)
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

    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any())).thenReturn(true);
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

    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(any(), any())).thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipInPilot2024(any(), any())).thenReturn(
        true);

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

  @Test
  void shouldScheduleProgrammeMembershipNotification() throws SchedulerException {
    String jobId = PROGRAMME_CREATED + "-" + TIS_ID;

    LocalDate expectedDate = START_DATE.minusDays(0);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    service.scheduleNotification(jobId, programmeJobDataMap, when);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.captor();
    verify(scheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + jobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(when));
  }

  @Test
  void shouldSchedulePlacementNotification() throws SchedulerException {
    String jobId = NotificationType.PLACEMENT_UPDATED_WEEK_12 + "-" + TIS_ID;

    LocalDate expectedDate = START_DATE.minusDays(84);
    Date when = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    service.scheduleNotification(jobId, placementJobDataMap, when);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.captor();
    verify(scheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + jobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(when));
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
    when(messagingControllerService.isValidRecipient(any(), any())).thenReturn(true);
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

    TisReferenceInfo tisReferenceInfo = tisReferenceInfoCaptor.getValue();
    assertThat("Unexpected TIS reference info type", tisReferenceInfo.type(),
        is(PROGRAMME_MEMBERSHIP));
    assertThat("Unexpected TIS reference info id", tisReferenceInfo.id(),
        is(TIS_ID));
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
        new UserDetails(
            true, COGNITO_EMAIL, null, COGNITO_FAMILY_NAME, COGNITO_GIVEN_NAME, null);
    UserDetails traineeProfileDetails =
        new UserDetails(
            null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, gmcNumber);

    UserDetails expectedResult =
        service.mapUserDetails(cognitoAccountDetails, traineeProfileDetails);

    assertThat("Unexpected isRegister", expectedResult.isRegistered(), is(true));
    assertThat("Unexpected email", expectedResult.email(), is(COGNITO_EMAIL));
    assertThat("Unexpected title", expectedResult.title(), is(USER_TITLE));
    assertThat("Unexpected family name", expectedResult.familyName(), is(COGNITO_FAMILY_NAME));
    assertThat("Unexpected given name", expectedResult.givenName(), is(COGNITO_GIVEN_NAME));
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
        new UserDetails(
            null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, gmcNumber);

    UserDetails expectedResult = service.mapUserDetails(null, traineeProfileDetails);

    assertThat("Unexpected isRegister", expectedResult.isRegistered(), is(false));
    assertThat("Unexpected email", expectedResult.email(), is(USER_EMAIL));
    assertThat("Unexpected title", expectedResult.title(), is(USER_TITLE));
    assertThat("Unexpected family name", expectedResult.familyName(), is(USER_FAMILY_NAME));
    assertThat("Unexpected given name", expectedResult.givenName(), is(USER_GIVEN_NAME));
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
  void shouldScheduleFutureMilestonesAtStartOfCorrectDay() {
    LocalDate startDate = LocalDate.now().plusMonths(12);
    int daysBeforeStart = 100;
    LocalDate milestoneDate = startDate.minusDays(daysBeforeStart);
    Date expectedMilestone = Date.from(milestoneDate
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant());

    Date scheduledDate = service.getScheduleDate(startDate, daysBeforeStart);

    assertThat("Unexpected scheduled date", scheduledDate, is(expectedMilestone));
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

  @Test
  void shouldMeetPmCriteriaWhenIsNewStarterAndIsInPilot() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID))
        .thenReturn(true);
    when(messagingControllerService.isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID))
        .thenReturn(true);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, true, true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(true));

    verify(messagingControllerService).isProgrammeMembershipNewStarter(PERSON_ID, TIS_ID);
    verify(messagingControllerService).isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID);
  }

  @Test
  void shouldNotMeetPmCriteriaWhenNotNewStarterAndNotInPilot() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID))
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

  @Test
  void shouldMeetPmCriteriaWhenNewStartCheckSkippedAndIsInPilot() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID)).thenReturn(
        true);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, false,
        true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(true));

    verify(messagingControllerService).isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID);
    verifyNoMoreInteractions(messagingControllerService);
  }

  @Test
  void shouldNotMeetPmCriteriaWhenNewStartCheckSkippedAndNotInPilot() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);

    when(messagingControllerService.isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID)).thenReturn(
        false);

    boolean meetsCriteria = service.meetsCriteria(programmeMembership, false,
        true);

    assertThat("Unexpected unmet programme membership criteria.", meetsCriteria, is(false));

    verify(messagingControllerService).isProgrammeMembershipInPilot2024(PERSON_ID, TIS_ID);
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

  @Test
  void shouldMeetPlacementCriteriaWhenIsInPilot() {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isPlacementInPilot2024(PERSON_ID, TIS_ID)).thenReturn(true);

    boolean meetsCriteria = service.meetsCriteria(placement, true);

    assertThat("Unexpected unmet placement criteria.", meetsCriteria, is(true));

    verify(messagingControllerService).isPlacementInPilot2024(PERSON_ID, TIS_ID);
  }

  @Test
  void shouldNotMeetPlacementCriteriaWhenNotInPilot() {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isPlacementInPilot2024(PERSON_ID, TIS_ID))
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

    assertThat("Unexpected programme membership is notifiable value.", isNotifiablePm, is(true));
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

    assertThat("Unexpected programme membership is notifiable value.", isNotifiablePm, is(false));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void placementShouldBeNotifiableWhenIsValidRecipient(MessageType messageType) {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isValidRecipient(PERSON_ID, messageType)).thenReturn(true);

    boolean isNotifiablePm = service.placementIsNotifiable(placement, messageType);

    assertThat("Unexpected placement is notifiable value.", isNotifiablePm, is(true));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void placementShouldNotBeNotifiableWhenIsInvalidRecipient(MessageType messageType) {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);

    when(messagingControllerService.isValidRecipient(PERSON_ID, messageType)).thenReturn(false);

    boolean isNotifiablePm = service.placementIsNotifiable(placement, messageType);

    assertThat("Unexpected placement is notifiable value.", isNotifiablePm, is(false));
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
}
