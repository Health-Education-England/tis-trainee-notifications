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

package uk.nhs.tis.trainee.notifications.service.helper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.REGISTER_TSS;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_COJ;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_FORM_R_PART_A;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_FORM_R_PART_B;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PERSON;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.helper.ProgrammeMembershipNotificationHelper.TEMPLATE_NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.helper.ProgrammeMembershipNotificationHelper.TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.JobDataMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationSummary;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeActionType;

/**
 * Tests for the ProgrammeMembershipNotificationHelper class.
 */
class ProgrammeMembershipNotificationHelperTest {

  private static final String ACTIONS_URL = "actions-url";
  private static final String ACTIONS_PROGRAMME_URL = "/api/action/{personId}/{programmeId}";

  private static final String PERSON_ID = "person-id";
  private static final String TIS_ID = "tis-id";

  private JobDataMap jobDataMap;
  private RestTemplate restTemplate;
  private ProgrammeMembershipNotificationHelper service;

  @BeforeEach
  void setUp() {
    jobDataMap = new JobDataMap(Map.of("key", "value"));
    restTemplate = mock(RestTemplate.class);
    service = new ProgrammeMembershipNotificationHelper(ACTIONS_URL, restTemplate);
  }

  @Test
  void shouldHandleActionsRestClientExceptions() {
    when(restTemplate.getForObject(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL), eq(List.class),
        anyMap())).thenThrow(new RestClientException("error"));

    assertDoesNotThrow(()
        -> service.addProgrammeReminderDetailsToJobMap(jobDataMap, PERSON_ID, TIS_ID));
  }

  @Test
  void shouldAddReminderJobDetails() {
    List<ActionDto> reminderActions = List.of(
        new ActionDto("action-id-1", SIGN_COJ.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
            Instant.now()),
        new ActionDto("action-id-2", SIGN_FORM_R_PART_A.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null, null),
        new ActionDto("action-id-3", SIGN_FORM_R_PART_B.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null, null),
        new ActionDto("action-id-4", REGISTER_TSS.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(PERSON_ID, PERSON), null, null, null)
    );
    when(restTemplate.getForObject(ACTIONS_URL + ACTIONS_PROGRAMME_URL, List.class,
        Map.of("personId", PERSON_ID, "programmeId", TIS_ID)))
        .thenReturn(reminderActions);

    service.addProgrammeReminderDetailsToJobMap(jobDataMap, PERSON_ID, TIS_ID);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(false));
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), is(false));
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(false));
  }

  @Test
  void shouldAddDefaultCompletedReminderIfActionMissing() {
    when(restTemplate.getForObject(ACTIONS_URL + ACTIONS_PROGRAMME_URL, List.class,
        Map.of("personId", PERSON_ID, "programmeId", TIS_ID)))
        .thenReturn(List.of());

    service.addProgrammeReminderDetailsToJobMap(jobDataMap, PERSON_ID, TIS_ID);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), is(true));
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(true));
  }

  @ParameterizedTest
  @EnumSource(ProgrammeActionType.class)
  void shouldIdentifyIfIncompleteActions(ProgrammeActionType actionType) {
    jobDataMap.put(actionType.name(), false);

    assertThat("Unexpected incomplete actions flag.",
        service.hasIncompleteProgrammeActions(jobDataMap), is(true));
  }

  @Test
  void shouldIdentifyIfAllActionsComplete() {
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      jobDataMap.put(actionType.name(), true);
    }

    assertThat("Unexpected incomplete actions flag.",
        service.hasIncompleteProgrammeActions(jobDataMap), is(false));
  }

  @Test
  void shouldRegardMissingActionsAsComplete() {
    assertThat("Unexpected incomplete actions flag.",
        service.hasIncompleteProgrammeActions(jobDataMap), is(false));
  }

  @Test
  void shouldNotAddWelcomeSentDateToJobMapIfHistoryNull() {
    service.addWelcomeSentDateToJobMap(jobDataMap, null);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @Test
  void shouldNotAddWelcomeSentDateToJobMapIfHistoryStatusNull() {
    History history = History.builder()
        .sentAt(Instant.now())
        .build();
    service.addWelcomeSentDateToJobMap(jobDataMap, history);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"SENT"})
  void shouldNotAddWelcomeSentDateToJobMapIfHistoryStatusNotSent(NotificationStatus status) {
    History history = History.builder()
        .status(status)
        .sentAt(Instant.now())
        .build();
    service.addWelcomeSentDateToJobMap(jobDataMap, history);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), nullValue());
  }

  @Test
  void shouldAddWelcomeSentDateToJobMapIfHistoryStatusSent() {
    Instant sentAt = Instant.now();
    History history = History.builder()
        .status(SENT)
        .sentAt(sentAt)
        .build();
    service.addWelcomeSentDateToJobMap(jobDataMap, history);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), is(sentAt));
  }

  @Test
  void shouldAddWelcomeRetryDateToJobMapIfHistoryStatusSent() {
    Instant sentAt = Instant.now().minusMillis(1000);
    Instant retryAt = Instant.now();
    History history = History.builder()
        .status(SENT)
        .lastRetry(retryAt)
        .sentAt(sentAt)
        .build();
    service.addWelcomeSentDateToJobMap(jobDataMap, history);

    assertThat("Unexpected welcome message in job data map.",
        jobDataMap.get(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD), is(retryAt));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_UPDATED_WEEK_12",
      "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldGetNotificationSummaryWithUnnecessaryReminderOverride(
      NotificationType notificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected unnecessary reminder flag", summary.unnecessaryReminder(),
        is(true));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_UPDATED_WEEK_12",
      "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldGetNotificationSummaryWithUnnecessaryReminderNotOverridden(
      NotificationType notificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    jobDataMap.put(String.valueOf(SIGN_COJ), false);
    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected unnecessary reminder flag", summary.unnecessaryReminder(),
        is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldGetNotificationSummaryForNonReminderJobsWithUnnecessaryReminderNotOverridden(
      NotificationType notificationType) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);
    jobDataMap.put(String.valueOf(SIGN_COJ), true); //should not really exist for these types
    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected unnecessary reminder flag", summary.unnecessaryReminder(),
        is(false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "test")
  void shouldGetNotificationSummaryWithJobName(String programmeName) {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
    jobDataMap.put(PROGRAMME_NAME_FIELD, programmeName);
    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected job name", summary.jobName(), is(programmeName));
  }

  @Test
  void shouldGetNotificationSummaryWithStartDate() {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
    jobDataMap.put(START_DATE_FIELD, LocalDate.of(2020, 1, 1));

    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected start date", summary.startDate(),
        is(LocalDate.of(2020, 1, 1)));
  }

  @Test
  void shouldGetNotificationSummaryWithNoStartDate() {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    assertThat("Unexpected non-null start date", summary.startDate(), nullValue());
  }

  @Test
  void shouldGetNotificationSummaryWithTisReference() {
    jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
    jobDataMap.put(TIS_ID_FIELD, "tis-id-123");
    NotificationSummary summary = service.getNotificationSummary(jobDataMap, false);

    assertThat("Unexpected null summary", summary, notNullValue());
    History.TisReferenceInfo tisRef = summary.tisReferenceInfo();
    assertThat("Unexpected null TIS reference", tisRef, notNullValue());
    assertThat("Unexpected TIS reference ID", tisRef.id(),
        is(jobDataMap.get(TIS_ID_FIELD)));
    assertThat("Unexpected TIS reference type", tisRef.type(),
        is(PROGRAMME_MEMBERSHIP));
  }

}
