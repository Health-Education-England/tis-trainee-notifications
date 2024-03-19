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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.COJ_SYNCED_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import uk.nhs.tis.trainee.notifications.dto.CojSignedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class ProgrammeMembershipServiceTest {

  private static final String MEDICAL_CURRICULUM_1 = "Medical_curriculum";
  private static final String MEDICAL_CURRICULUM_2 = "Medical_spr";
  private static final String EXCLUDE_SPECIALTY_1 = "Public health medicine";
  private static final String EXCLUDE_SPECIALTY_2 = "Foundation";

  private static final String TIS_ID = "123";
  private static final String PERSON_ID = "abc";
  private static final String PROGRAMME_NAME = "the programme";
  private static final LocalDate START_DATE = LocalDate.now().plusYears(1);
  //set a year in the future to allow all notifications to be scheduled

  private static final Curriculum IGNORED_CURRICULUM
      = new Curriculum("some-subtype", "some-specialty");

  ProgrammeMembershipService service;
  Scheduler scheduler;
  HistoryService historyService;

  @BeforeEach
  void setUp() {
    scheduler = mock(Scheduler.class);
    historyService = mock(HistoryService.class);
    service = new ProgrammeMembershipService(scheduler, historyService);
  }

  @ParameterizedTest
  @ValueSource(strings = {MEDICAL_CURRICULUM_1, MEDICAL_CURRICULUM_2})
  void shouldNotExcludePmWithMedicalSubtypeAndNoExcludedSpecialties(String subtype) {
    Curriculum theCurriculum = new Curriculum(subtype, "some-specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(theCurriculum, IGNORED_CURRICULUM));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(false));
  }

  @Test
  void shouldExcludePmWithNoMedicalSubtype() {
    List<Curriculum> curricula = List.of(IGNORED_CURRICULUM);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(curricula);

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldExcludePmWithNoCurricula(List<Curriculum> curricula) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(curricula);

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {EXCLUDE_SPECIALTY_1, EXCLUDE_SPECIALTY_2})
  void shouldExcludePmWithExcludedSpecialty(String specialty) {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, specialty);
    Curriculum anotherCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(theCurriculum, anotherCurriculum));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldRemoveStaleNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
      String jobId = milestone.toString() + "-" + TIS_ID;
      JobKey jobKey = new JobKey(jobId);
      verify(scheduler).deleteJob(jobKey);
    }
  }

  @Test
  void shouldNotAddNotificationsIfExcluded() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    verify(scheduler, never()).scheduleJob(any(), any());
  }

  @Test
  void shouldAddNotificationsIfNotExcluded() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));

    service.addNotifications(programmeMembership);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    int jobsToSchedule = NotificationType.getProgrammeUpdateNotificationTypes().size();
    verify(scheduler, times(jobsToSchedule))
        .scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    //verify the details of the last job scheduled
    NotificationType milestone = NotificationType.PROGRAMME_UPDATED_WEEK_0;
    String expectedJobId = milestone + "-" + TIS_ID;
    JobKey expectedJobKey = new JobKey(expectedJobId);

    JobDetail jobDetail = jobDetailCaptor.getValue();
    assertThat("Unexpected job id key.", jobDetail.getKey(), is(expectedJobKey));
    JobDataMap jobDataMap = jobDetail.getJobDataMap();
    assertThat("Unexpected tisId.", jobDataMap.get(TIS_ID_FIELD), is(TIS_ID));
    assertThat("Unexpected personId.", jobDataMap.get(PERSON_ID_FIELD), is(PERSON_ID));
    assertThat("Unexpected programme.", jobDataMap.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", jobDataMap.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected CoJ synced at.", jobDataMap.get(COJ_SYNCED_FIELD),
        is(Instant.MIN));

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + expectedJobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    LocalDate expectedDate = START_DATE
        .minusDays(service.getNotificationDaysBeforeStart(milestone));
    Date expectedWhen = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(expectedWhen));
  }

  @Test
  void shouldNotScheduleSentNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_ID),
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_8, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    int jobsToSchedule = NotificationType.getProgrammeUpdateNotificationTypes().size() - 1;
    verify(scheduler, times(jobsToSchedule))
        .scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    //verify the details of the last job scheduled, i.e. the 0-week notification
    NotificationType milestone = NotificationType.PROGRAMME_UPDATED_WEEK_0;
    String expectedJobId = milestone + "-" + TIS_ID;
    JobKey expectedJobKey = new JobKey(expectedJobId);

    JobDetail jobDetail = jobDetailCaptor.getValue();
    assertThat("Unexpected job id key.", jobDetail.getKey(), is(expectedJobKey));

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + expectedJobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    LocalDate expectedDate = START_DATE
        .minusDays(service.getNotificationDaysBeforeStart(milestone));
    Date expectedWhen = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(expectedWhen));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getNonProgrammeUpdateNotificationTypes")
  void shouldIgnoreNonPmUpdateSentNotifications(NotificationType notificationType)
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_ID),
        MessageType.EMAIL,
        notificationType, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(scheduler, times(NotificationType.getProgrammeUpdateNotificationTypes().size()))
        .scheduleJob(any(), any());
  }

  @Test
  void shouldIgnoreNonPmTypeSentNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_ID),
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_8, null, //to avoid masking the test condition
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(scheduler, times(NotificationType.getProgrammeUpdateNotificationTypes().size()))
        .scheduleJob(any(), any());
  }

  @Test
  void shouldIgnoreOtherPmUpdateSentNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, "another id"),
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_8, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(scheduler, times(NotificationType.getProgrammeUpdateNotificationTypes().size()))
        .scheduleJob(any(), any());
  }

  @Test
  void shouldScheduleMostRecentMissedNotification() throws SchedulerException {
    LocalDate dateToday = LocalDate.now();

    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(dateToday);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    //the zero-day notification should be scheduled, but no other missed notifications
    verify(scheduler).scheduleJob(any(), triggerCaptor.capture());

    Trigger trigger = triggerCaptor.getValue();
    Date expectedLaterThan = Date.from(Instant.now());

    assertThat("Unexpected trigger start time",
        trigger.getStartTime().after(expectedLaterThan), is(true));
    LocalDate scheduledDate = trigger.getStartTime().toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate();
    assertThat("Unexpected trigger start time", scheduledDate, is(dateToday));
  }

  @Test
  void shouldScheduleMostRecentMissedAndFutureNotifications() throws SchedulerException {
    LocalDate dateThreeWeeksTime = LocalDate.now().plusWeeks(3);

    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(dateThreeWeeksTime);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    //the 8-week notification should be omitted in favour of the 4-week one.
    verify(scheduler, times(3)).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    List<JobDetail> jobDetail = jobDetailCaptor.getAllValues();

    JobKey expectedJobKey4 = new JobKey(NotificationType.PROGRAMME_UPDATED_WEEK_4 + "-" + TIS_ID);
    assertThat("Unexpected job id key.", jobDetail.get(0).getKey(), is(expectedJobKey4));

    JobKey expectedJobKey1 = new JobKey(NotificationType.PROGRAMME_UPDATED_WEEK_1 + "-" + TIS_ID);
    assertThat("Unexpected job id key.", jobDetail.get(1).getKey(), is(expectedJobKey1));

    JobKey expectedJobKey0 = new JobKey(NotificationType.PROGRAMME_UPDATED_WEEK_0 + "-" + TIS_ID);
    assertThat("Unexpected job id key.", jobDetail.get(2).getKey(), is(expectedJobKey0));

    verify(scheduler, times(NotificationType.getProgrammeUpdateNotificationTypes().size()))
        .deleteJob(any());
    verifyNoMoreInteractions(scheduler);
  }

  @Test
  void shouldNotScheduleFutureNotificationsIfAnyCloserToTheStartDateHaveBeenSent()
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_ID),
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_0, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(scheduler, never()).scheduleJob(any(), any());
    //since the 0-week notification has already been sent
  }

  @Test
  void shouldNotFailOnHistoryWithoutTisReferenceInfo()
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        null,
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_0, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    assertDoesNotThrow(() -> service.addNotifications(programmeMembership),
        "Unexpected addNotifications failure");
  }

  @Test
  void shouldIgnoreHistoryWithoutTisReferenceInfo() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        null,
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_8, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(scheduler, times(NotificationType.getProgrammeUpdateNotificationTypes().size()))
        .scheduleJob(any(), any());
  }

  @Test
  void shouldRethrowSchedulerExceptions() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    when(scheduler.scheduleJob(any(), any())).thenThrow(new SchedulerException("error"));

    assertThrows(SchedulerException.class,
        () -> service.addNotifications(programmeMembership));
  }

  @Test
  void shouldDeleteNotifications() throws SchedulerException {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);

    service.deleteNotifications(programmeMembership);

    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
      String jobId = milestone.toString() + "-" + TIS_ID;
      JobKey jobKey = new JobKey(jobId);
      verify(scheduler).deleteJob(jobKey);
    }
  }

  @Test
  void shouldHandleUnknownNotificationTypesForNotificationDays() {
    assertThat("Unexpected days before start.",
        service.getNotificationDaysBeforeStart(NotificationType.COJ_CONFIRMATION), nullValue());
  }

  @Test
  void shouldIgnoreMissingConditionsOfJoining() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);

    int jobsToSchedule = NotificationType.getProgrammeUpdateNotificationTypes().size();
    verify(scheduler, times(jobsToSchedule))
        .scheduleJob(jobDetailCaptor.capture(), any());

    //verify the details of the last job scheduled
    JobDataMap jobDataMap = jobDetailCaptor.getValue().getJobDataMap();
    assertThat("Unexpected CoJ synced at.", jobDataMap.get(COJ_SYNCED_FIELD),
        is(nullValue()));
  }
}
