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

import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;

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
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;
import uk.nhs.tis.trainee.notifications.service.PlacementService;
import uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService;


/**
 * Insert SCHEDULED email history records from Quartz to MongoDB.
 */
@Slf4j
@ChangeUnit(id = "insertScheduledEmailHistory", order = "5")
public class InsertScheduledEmailHistory {

  private final Scheduler scheduler;
  private final NotificationService notificationService;
  private final HistoryService historyService;

  /**
   * Migration constructor.
   */
  public InsertScheduledEmailHistory(Scheduler scheduler,
                                     NotificationService notificationService,
                                     HistoryService historyService) {
    this.scheduler = scheduler;
    this.notificationService = notificationService;
    this.historyService = historyService;
  }

  /**
   * Insert SCHEDULED email history records.
   */
  @Execution
  public void migrate() throws SchedulerException {
    for (String groupName : scheduler.getJobGroupNames()) {
      for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {

        // Get scheduled job from Quartz
        List<Trigger> triggers = (List<Trigger>) scheduler.getTriggersOfJob(jobKey);
        String jobName = jobKey.getName();
        Date when = triggers.get(0).getNextFireTime();
        JobDataMap jobDetails = scheduler.getJobDetail(jobKey).getJobDataMap();

        // Check if it's already migrated to DB
        History scheduledHistory = null;
        NotificationType notificationType =
            NotificationType.valueOf(jobDetails.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());
        History.TisReferenceInfo tisReferenceInfo =
            getTisReferenceInfo(jobDetails, notificationType);
        String personId = jobDetails.getString(PERSON_ID_FIELD);
        if (tisReferenceInfo != null) {
          scheduledHistory = historyService.findScheduledEmailForTraineeByRefAndType(
              personId, tisReferenceInfo.type(), tisReferenceInfo.id(), notificationType);
          // Only migrate when it is not exist in DB
          if (scheduledHistory == null) {
            try {
              log.info("Processing scheduled email from Quartz: [jobName] : "
                  + jobName + " - " + when);
              notificationService.saveScheduleHistory(jobDetails, when);
            } catch (Exception e) {
              log.error("Unable to save scheduled history {} in DB due to an error: {} ",
                  jobName, e.toString());
            }
          }
        }
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

  private History.TisReferenceInfo getTisReferenceInfo(JobDataMap jobDetails,
                                                       NotificationType notificationType) {
    if (notificationType == NotificationType.PROGRAMME_CREATED
        || notificationType == NotificationType.PROGRAMME_DAY_ONE) {
      return new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP,
          jobDetails.get(ProgrammeMembershipService.TIS_ID_FIELD).toString());

    } else if (notificationType == NotificationType.PLACEMENT_UPDATED_WEEK_12) {
      return new History.TisReferenceInfo(PLACEMENT,
          jobDetails.get(PlacementService.TIS_ID_FIELD).toString());
    }
    return null;
  }
}
