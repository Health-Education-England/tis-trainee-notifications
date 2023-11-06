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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import uk.nhs.tis.trainee.notifications.dto.Curriculum;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.model.NotificationMilestoneType;

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

  @BeforeEach
  void setUp() {
    scheduler = mock(Scheduler.class);
    service = new ProgrammeMembershipService(scheduler);
  }

  @ParameterizedTest
  @ValueSource(strings = {MEDICAL_CURRICULUM_1, MEDICAL_CURRICULUM_2})
  void shouldNotExcludePmWithMedicalSubtypeAndNoExcludedSpecialties(String subtype) {
    Curriculum theCurriculum = new Curriculum(subtype, "some-specialty");
    List<Curriculum> curricula = List.of(theCurriculum, IGNORED_CURRICULUM);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(false));
  }

  @Test
  void shouldExcludePmWithNoMedicalSubtype() {
    List<Curriculum> curricula = List.of(IGNORED_CURRICULUM);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldExcludePmWithNoCurricula(List<Curriculum> curricula) {
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {EXCLUDE_SPECIALTY_1, EXCLUDE_SPECIALTY_2})
  void shouldExcludePmWithExcludedSpecialty(String specialty) {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, specialty);
    Curriculum anotherCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty");
    List<Curriculum> curricula = List.of(theCurriculum, anotherCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldRemoveStaleNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1);
    List<Curriculum> curricula = List.of(theCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(TIS_ID, PERSON_ID, PROGRAMME_NAME, START_DATE, curricula);

    service.scheduleNotifications(event);

    for (NotificationMilestoneType milestone : NotificationMilestoneType.values()) {
      String jobId = milestone.toString() + "-" + TIS_ID;
      JobKey jobKey = new JobKey(jobId);
      verify(scheduler).deleteJob(jobKey);
    }
  }

  @Test
  void shouldNotScheduleNotificationsIfExcluded() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1);
    List<Curriculum> curricula = List.of(theCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(TIS_ID, PERSON_ID, PROGRAMME_NAME, START_DATE, curricula);

    service.scheduleNotifications(event);

    verify(scheduler, never()).scheduleJob(any(), any());
  }

  @Test
  void shouldScheduleNotificationsIfNotExcluded() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    List<Curriculum> curricula = List.of(theCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(TIS_ID, PERSON_ID, PROGRAMME_NAME, START_DATE, curricula);

    service.scheduleNotifications(event);

    ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    int jobsToSchedule = NotificationMilestoneType.values().length;
    verify(scheduler, times(jobsToSchedule))
        .scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

    //verify the details of the last job scheduled
    NotificationMilestoneType milestone = NotificationMilestoneType.values()[jobsToSchedule - 1];
    String expectedJobId = milestone.toString() + "-" + TIS_ID;
    JobKey expectedJobKey = new JobKey(expectedJobId);

    JobDetail jobDetail = jobDetailCaptor.getValue();
    assertThat("Unexpected job id key.", jobDetail.getKey(), is(expectedJobKey));
    JobDataMap jobDataMap = jobDetail.getJobDataMap();
    assertThat("Unexpected tisId.", jobDataMap.get("tisId"), is(TIS_ID));
    assertThat("Unexpected personId.", jobDataMap.get("personId"), is(PERSON_ID));
    assertThat("Unexpected programme.", jobDataMap.get("programmeName"),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", jobDataMap.get("startDate"), is(START_DATE));

    Trigger trigger = triggerCaptor.getValue();
    TriggerKey expectedTriggerKey = TriggerKey.triggerKey("trigger-" + expectedJobId);
    assertThat("Unexpected trigger id", trigger.getKey(), is(expectedTriggerKey));
    LocalDate expectedDate = START_DATE.minusDays(milestone.getDaysBeforeStart());
    Date expectedWhen = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
    assertThat("Unexpected trigger start time", trigger.getStartTime(), is(expectedWhen));
  }

  @Test
  void shouldNotScheduleNotificationsInThePast() throws SchedulerException {
    LocalDate dateYesterday = LocalDate.now().minusDays(1);

    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    List<Curriculum> curricula = List.of(theCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(TIS_ID, PERSON_ID, PROGRAMME_NAME, dateYesterday, curricula);

    service.scheduleNotifications(event);

    verify(scheduler, never()).scheduleJob(any(), any());
  }

  @Test
  void shouldRethrowSchedulerExceptions() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty");
    List<Curriculum> curricula = List.of(theCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(TIS_ID, PERSON_ID, PROGRAMME_NAME, START_DATE, curricula);

    when(scheduler.scheduleJob(any(), any())).thenThrow(new SchedulerException("error"));

    assertThrows(SchedulerException.class,
        () -> service.scheduleNotifications(event));
  }
}
