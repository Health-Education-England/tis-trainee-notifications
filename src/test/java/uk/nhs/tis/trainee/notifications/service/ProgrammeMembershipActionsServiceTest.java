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

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.REGISTER_TSS;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_COJ;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_FORM_R_PART_A;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_FORM_R_PART_B;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PERSON;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipActionsService.TEMPLATE_NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipActionsService.TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.JobDataMap;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationSummary;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeActionType;

/**
 * Tests for the ProgrammeMembershipActionsService class.
 */
class ProgrammeMembershipActionsServiceTest {

  private static final String ACTIONS_URL = "actions-url";
  private static final String ACTIONS_PROGRAMME_URL = "/api/action/{personId}/{programmeId}";

  private static final String PERSON_ID = "person-id";
  private static final String TIS_ID = "tis-id";
  private static final LocalDate START_DATE = LocalDate.now().plusYears(1);
  private static final NotificationType NOTIFICATION_TYPE = PROGRAMME_UPDATED_WEEK_12;

  private JobDataMap jobDataMap;
  private RestTemplate restTemplate;
  private HistoryService historyService;
  private ProgrammeMembershipActionsService service;

  private static final ParameterizedTypeReference<Set<ActionDto>> actionSetType
      = new ParameterizedTypeReference<>() {};

  @BeforeEach
  void setUp() {
    jobDataMap = new JobDataMap(Map.of("tisId", TIS_ID,
        "notificationType", NOTIFICATION_TYPE,
        "personId", PERSON_ID,
        "programmeName", "programme-name",
        "startDate", START_DATE));
    restTemplate = mock(RestTemplate.class);
    historyService = mock(HistoryService.class);
    service = new ProgrammeMembershipActionsService(ACTIONS_URL, restTemplate, historyService);
  }

  @Test
  void shouldHandleActionsRestClientExceptions() {
    doThrow(RestClientException.class)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    assertDoesNotThrow(()
        -> service.addActionsToJobMap(PERSON_ID, jobDataMap));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIgnoreNonReminderNotificationTypes(NotificationType notificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), nullValue());
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), nullValue());
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), nullValue());
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(REGISTER_TSS.toString()), nullValue());
    assertThat("Unexpected welcome notification date in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @Test
  void shouldAddReminderJobDetails() {
    Set<ActionDto> reminderActions = Set.of(
        new ActionDto("action-id-1", SIGN_COJ.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
            Instant.now()),
        new ActionDto("action-id-2", SIGN_FORM_R_PART_A.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
            null),
        new ActionDto("action-id-3", SIGN_FORM_R_PART_B.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
            null),
        new ActionDto("action-id-4", REGISTER_TSS.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(PERSON_ID, PERSON), null, null,
            Instant.MIN));
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(reminderActions);
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());
    History welcomeHistory = History.builder()
        .status(SENT)
        .sentAt(Instant.MIN)
        .build();
    when(historyService
        .findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(welcomeHistory);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), is(true)); //was completed
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(false));
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), is(false));
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(REGISTER_TSS.toString()), is(true));
    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD),
        is(Instant.MIN));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldAddDefaultCompletedReminderIfActionMissing(Set<ActionDto> actions) {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(actions);
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), is(true));
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(REGISTER_TSS.toString()), is(true));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIdentifyIfNotUnnecessaryReminder(NotificationType reminderNotificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, reminderNotificationType);
    Set<ActionDto> reminderActions = Set.of(
        new ActionDto("action-id-1", SIGN_COJ.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
            null));
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(reminderActions);
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected unnecessary reminder flag.",
        service.getNotificationSummary().unnecessaryReminder(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIdentifyIfNotificationNotReminder(NotificationType notReminderNotificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notReminderNotificationType);
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected unnecessary reminder flag.",
        service.getNotificationSummary().unnecessaryReminder(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIdentifyIfUnnecessaryReminderIfMissing(NotificationType reminderNotificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, reminderNotificationType);
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected necessary reminder flag.",
        service.getNotificationSummary().unnecessaryReminder(), is(true));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIdentifyIfUnnecessaryReminder(NotificationType reminderNotificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, reminderNotificationType);
    Set<ActionDto> reminderActions = new HashSet<>();
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      reminderActions.add(new ActionDto("action-id-" + actionType, actionType.toString(),
          PERSON_ID, new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
          Instant.now()));
    }
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(reminderActions);
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected necessary reminder flag.",
        service.getNotificationSummary().unnecessaryReminder(), is(true));
  }

  @Test
  void shouldNotAddWelcomeSentDateToJobMapIfHistoryNull() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @Test
  void shouldNotAddWelcomeSentDateToJobMapIfHistoryStatusNull() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    History history = History.builder()
        .sentAt(Instant.MIN)
        .build();
    when(historyService
        .findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(history);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"SENT"})
  void shouldNotAddWelcomeSentDateToJobMapIfHistoryStatusNotSent(NotificationStatus status) {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    History history = History.builder()
        .status(status)
        .sentAt(Instant.now())
        .build();
    when(historyService
        .findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(history);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @Test
  void shouldAddWelcomeSentDateToJobMapIfHistoryStatusSent() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    Instant sentAt = Instant.now();
    History history = History.builder()
        .status(SENT)
        .sentAt(sentAt)
        .build();
    when(historyService
        .findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(history);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), is(sentAt));
  }

  @Test
  void shouldAddWelcomeRetryDateToJobMapIfHistoryStatusSent() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    Instant sentAt = Instant.now().minusMillis(1000);
    Instant retryAt = Instant.now();
    History history = History.builder()
        .status(SENT)
        .lastRetry(retryAt)
        .sentAt(sentAt)
        .build();
    when(historyService
        .findScheduledEmailForTraineeByRefAndType(any(), any(), any(), any()))
        .thenReturn(history);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), is(retryAt));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "test")
  void shouldGetNotificationSummaryWithJobName(String programmeName) {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    jobDataMap.put(PROGRAMME_NAME_FIELD, programmeName);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);

    NotificationSummary summary = service.getNotificationSummary();

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected job name", summary.jobName(), is(programmeName));
  }

  @Test
  void shouldGetNotificationSummaryWithStartDate() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    jobDataMap.put(START_DATE_FIELD, LocalDate.of(2020, 1, 1));

    service.addActionsToJobMap(PERSON_ID, jobDataMap);
    NotificationSummary summary = service.getNotificationSummary();

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected start date", summary.startDate(),
        is(LocalDate.of(2020, 1, 1)));
  }

  @Test
  void shouldGetNotificationSummaryWithNoStartDate() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    jobDataMap.put(START_DATE_FIELD, null);

    service.addActionsToJobMap(PERSON_ID, jobDataMap);
    NotificationSummary summary = service.getNotificationSummary();

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected non-null start date", summary.startDate(), nullValue());
  }

  @Test
  void shouldGetNotificationSummaryWithTisReference() {
    ResponseEntity<Set<ActionDto>> responseEntity = ResponseEntity.ok(Set.of());
    doReturn(responseEntity)
        .when(restTemplate).exchange(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL),
            eq(HttpMethod.GET), eq(null), eq(actionSetType), anyMap());

    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
    jobDataMap.put(TIS_ID_FIELD, "tis-id-123");

    service.addActionsToJobMap(PERSON_ID, jobDataMap);
    NotificationSummary summary = service.getNotificationSummary();

    assertThat("Unexpected null summary", summary, notNullValue());
    History.TisReferenceInfo tisRef = summary.tisReferenceInfo();
    assertThat("Unexpected null TIS reference", tisRef, notNullValue());
    assertThat("Unexpected TIS reference ID", tisRef.id(),
        is(jobDataMap.get(TIS_ID_FIELD)));
    assertThat("Unexpected TIS reference type", tisRef.type(),
        is(PROGRAMME_MEMBERSHIP));
  }
}
