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

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import uk.nhs.tis.trainee.notifications.service.NotificationService;


/**
 * Insert SCHEDULED email history records from Quartz to MongoDB.
 */
@Slf4j
@ChangeUnit(id = "insertScheduledEmailHistory", order = "5")
public class InsertScheduledEmailHistory {

  private final Scheduler scheduler;
  private final NotificationService notificationService;

  public InsertScheduledEmailHistory(Scheduler scheduler,
                                     NotificationService notificationService) {
    this.scheduler = scheduler;
    this.notificationService = notificationService;
  }

  /**
   * Insert SCHEDULED email history records.
   */
  @Execution
  public void migrate() throws SchedulerException {
    for (String groupName : scheduler.getJobGroupNames()) {
      for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {

        // Get scheduled job
        List<Trigger> triggers = (List<Trigger>) scheduler.getTriggersOfJob(jobKey);
        String jobName = jobKey.getName();
        Date when = triggers.get(0).getNextFireTime();
        JobDataMap jobDetails = scheduler.getJobDetail(jobKey).getJobDataMap();
        log.info("Processing scheduled email from Quartz: [jobName] : " + jobName + " - " + when);

        notificationService.saveScheduleHistory(jobDetails, when);
      }
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'InsertScheduledEmailHistory' migration.");
  }
}
