/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.migration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import uk.nhs.tis.trainee.notifications.service.NotificationService;


class InsertScheduledEmailHistoryTest {

  private static final String GROUP_NAME_1 = "group1";
  private static final String GROUP_NAME_2 = "group2";
  private static final String JOB_KEY_1A = "job-key-1a";
  private static final String JOB_KEY_1B = "job-key-1b";
  private static final String JOB_KEY_2A = "job-key-2a";
  private static final String JOB_KEY_2B = "job-key-2b";
  private static final String TRIGGER_1A = "trigger-1a";
  private static final String TRIGGER_1B = "trigger-1b";
  private static final String TRIGGER_2A = "trigger-2a";
  private static final String TRIGGER_2B = "trigger-2b";

  private InsertScheduledEmailHistory migration;
  private Scheduler scheduler;
  private NotificationService notificationService;

  private List<String> groupNames;
  private Set<JobKey> jobKeys1;
  private Set<JobKey> jobKeys2;
  private JobKey jobKey1a;
  private JobKey jobKey1b;
  private JobKey jobKey2a;
  private JobKey jobKey2b;
  private Trigger trigger1a;
  private Trigger trigger1b;
  private Trigger trigger2a;
  private Trigger trigger2b;
  private JobDetail jobDetail1a;
  private JobDetail jobDetail1b;
  private JobDetail jobDetail2a;
  private JobDetail jobDetail2b;
  private JobDataMap jobDataMap1a;
  private JobDataMap jobDataMap1b;
  private JobDataMap jobDataMap2a;
  private JobDataMap jobDataMap2b;


  @BeforeEach
  void setUp() {
    scheduler = mock(Scheduler.class);
    notificationService = mock(NotificationService.class);
    migration = new InsertScheduledEmailHistory(scheduler, notificationService);

    groupNames = List.of(GROUP_NAME_1, GROUP_NAME_2);
    jobKey1a = new JobKey(JOB_KEY_1A);
    jobKey1b = new JobKey(JOB_KEY_1B);
    jobKey2a = new JobKey(JOB_KEY_2A);
    jobKey2b = new JobKey(JOB_KEY_2B);
    jobKeys1 = Set.of(jobKey1a, jobKey1b);
    jobKeys2 = Set.of(jobKey2a, jobKey2b);
    trigger1a = newTrigger()
        .withIdentity(TRIGGER_1A)
        .startAt(new Date())
        .build();
    trigger1b = newTrigger()
        .withIdentity(TRIGGER_1B)
        .startAt(new Date())
        .build();
    trigger2a = newTrigger()
        .withIdentity(TRIGGER_2A)
        .startAt(new Date())
        .build();
    trigger2b = newTrigger()
        .withIdentity(TRIGGER_2B)
        .startAt(new Date())
        .build();

    jobDataMap1a = new JobDataMap();
    jobDataMap1a.put(PERSON_ID_FIELD, "1A");
    jobDataMap1a.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PLACEMENT_UPDATED_WEEK_12);
    jobDataMap1b = new JobDataMap();
    jobDataMap1b.put(PERSON_ID_FIELD, "1B");
    jobDataMap1b.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PLACEMENT_UPDATED_WEEK_12);
    jobDataMap2a = new JobDataMap();
    jobDataMap2a.put(PERSON_ID_FIELD, "2A");
    jobDataMap2a.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
    jobDataMap2b = new JobDataMap();
    jobDataMap2b.put(PERSON_ID_FIELD, "2B");
    jobDataMap2b.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_DAY_ONE);

    jobDetail1a = newJob(NotificationService.class)
        .usingJobData(jobDataMap1a)
        .storeDurably(false)
        .build();
    jobDetail1b = newJob(NotificationService.class)
        .usingJobData(jobDataMap1b)
        .storeDurably(false)
        .build();
    jobDetail2a = newJob(NotificationService.class)
        .usingJobData(jobDataMap2a)
        .storeDurably(false)
        .build();
    jobDetail2b = newJob(NotificationService.class)
        .usingJobData(jobDataMap2b)
        .storeDurably(false)
        .build();
  }

  @Test
  void shouldInsertScheduledEmailHistory() throws SchedulerException {
    when(scheduler.getJobGroupNames()).thenReturn(groupNames);
    when(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP_NAME_1))).thenReturn(jobKeys1);
    when(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP_NAME_2))).thenReturn(jobKeys2);

    when((List<Trigger>) scheduler.getTriggersOfJob(jobKey1a)).thenReturn(List.of(trigger1a));
    when((List<Trigger>) scheduler.getTriggersOfJob(jobKey1b)).thenReturn(List.of(trigger1b));
    when((List<Trigger>) scheduler.getTriggersOfJob(jobKey2a)).thenReturn(List.of(trigger2a));
    when((List<Trigger>) scheduler.getTriggersOfJob(jobKey2b)).thenReturn(List.of(trigger2b));

    when(scheduler.getJobDetail(jobKey1a)).thenReturn(jobDetail1a);
    when(scheduler.getJobDetail(jobKey1b)).thenReturn(jobDetail1b);
    when(scheduler.getJobDetail(jobKey2a)).thenReturn(jobDetail2a);
    when(scheduler.getJobDetail(jobKey2b)).thenReturn(jobDetail2b);

    migration.migrate();

    verify(notificationService).saveScheduleHistory(eq(jobDetail1a.getJobDataMap()), any());
    verify(notificationService).saveScheduleHistory(eq(jobDetail1b.getJobDataMap()), any());
    verify(notificationService).saveScheduleHistory(eq(jobDetail2a.getJobDataMap()), any());
    verify(notificationService).saveScheduleHistory(eq(jobDetail2b.getJobDataMap()), any());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(notificationService);
  }
}
