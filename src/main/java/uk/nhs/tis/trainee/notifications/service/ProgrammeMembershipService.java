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

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.Curriculum;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.model.NotificationMilestoneType;

/**
 * A service for Programme memberships.
 */

@Slf4j
@Service
public class ProgrammeMembershipService {

  private static final String TRIGGER_ID_PREFIX = "trigger-";

  private static final List<String> INCLUDE_CURRICULUM_SUBTYPES
      = List.of("MEDICAL_CURRICULUM", "MEDICAL_SPR");
  private static final List<String> EXCLUDE_CURRICULUM_SPECIALTIES
      = List.of("PUBLIC HEALTH MEDICINE", "FOUNDATION");

  private final Scheduler scheduler;

  public ProgrammeMembershipService(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Determines whether a programme membership is excluded or not, on the basis of curricula.
   *
   * <p>Excluded means the trainee will not be notified (contacted) in respect of this
   * programme membership.
   *
   * <p>This will be TRUE if any of the following are true in relation to the curricula:
   * 1. None have curriculumSubType = MEDICAL_CURRICULUM or MEDICAL_SPR 2. Any have specialtyName =
   * 'Public health medicine' 3. Any have specialtyName = 'Foundation'.
   *
   * @param programmeMembership the Programme membership.
   * @return true if the programme membership is excluded.
   */
  public boolean isExcluded(ProgrammeMembershipEvent programmeMembership) {
    List<Curriculum> curricula = programmeMembership.curricula();
    if (curricula == null) {
      return true;
    }

    boolean hasMedicalSubType = curricula.stream()
        .map(c -> c.curriculumSubType().toUpperCase())
        .anyMatch(INCLUDE_CURRICULUM_SUBTYPES::contains);

    boolean hasExcludedSpecialty = curricula.stream()
        .map(c -> c.curriculumSpecialty().toUpperCase())
        .anyMatch(EXCLUDE_CURRICULUM_SPECIALTIES::contains);

    return !hasMedicalSubType || hasExcludedSpecialty;
  }

  /**
   * (Re)schedule all notification for an updated programme membership.
   *
   * @param programmeMembership The updated programme membership.
   * @throws SchedulerException if any one of the notification jobs could not be scheduled.
   */
  public void scheduleNotifications(ProgrammeMembershipEvent programmeMembership)
      throws SchedulerException {

    boolean isExcluded = isExcluded(programmeMembership);
    log.info("Programme membership {}: excluded {}.", programmeMembership.tisId(), isExcluded);

    LocalDate startDate = programmeMembership.startDate();
    for (NotificationMilestoneType milestone : NotificationMilestoneType.values()) {

      String jobId = milestone.toString() + "-" + programmeMembership.tisId();
      removeNotification(jobId); //remove existing notification if it exists

      if (!isExcluded) {
        Date when = getMilestoneDate(startDate, milestone.getDaysBeforeStart());
        if (when != null) { //only future notifications are scheduled
          JobDataMap jobDataMap = new JobDataMap();
          jobDataMap.put("tisId", programmeMembership.tisId());
          jobDataMap.put("personId", programmeMembership.personId());
          jobDataMap.put("programmeName", programmeMembership.programmeName());
          jobDataMap.put("startDate", programmeMembership.startDate());
          // TODO other details e.g. programme name.
          // But note the status of the trainee will be retrieved when the job is executed, as will
          // their name and email address, not now.
          try {
            scheduleNotification(jobId, jobDataMap, when);
          } catch (SchedulerException e) {
            log.error("Failed to schedule notification {}: {}", jobId, e.toString());
            throw (e); //to allow message to be requeue-ed
          }
        }
      }
    }

  }

  /**
   * Schedule a programme membership notification.
   *
   * @param jobId      The job id. This must be unique for programme membership and notification
   *                   milestone.
   * @param jobDataMap The map of job data.
   * @param when       The date to schedule the notification to be sent.
   * @throws SchedulerException if the job could not be scheduled.
   */
  private void scheduleNotification(String jobId, JobDataMap jobDataMap, Date when)
      throws SchedulerException {
    JobDetail job = newJob(NotificationService.class)
        .withIdentity(jobId)
        .usingJobData(jobDataMap)
        //.storeDurably() TODO decide if we want to keep a record of triggered notifications:
        // if so, may be better to write explicitly to another table when the job is triggered
        .build();
    Trigger trigger = newTrigger()
        .withIdentity(TRIGGER_ID_PREFIX + jobId)
        .startAt(when)
        .build();

    Date scheduledDate = scheduler.scheduleJob(job, trigger);
    log.info("Notification for {} scheduled for {}", jobId, scheduledDate);
  }

  /**
   * Remove a scheduled notification.
   *
   * @param jobId The job id key to remove.
   * @throws SchedulerException if the scheduler failed in its duties.
   */
  private void removeNotification(String jobId) throws SchedulerException {
// scenarios:
    // 1. no job exists --> do nothing
    // 2. a job exists and it has been triggered --> not if we don't storeDurable(), so ignore for now
    // 3. a job exists and it has not been triggered - delete
    JobKey jobKey = new JobKey(jobId);
    // Delete the job and unschedule its triggers.
    // We do not simply remove the trigger, since the job data may be updated
    scheduler.deleteJob(jobKey);
    log.info("Removed stale notification scheduled for {}", jobId);
  }

  /**
   * Get a future date for a milestone from the programme start date and day offset.
   *
   * @param programmeStartDate The programme starting date.
   * @param daysBeforeStart    The number of days prior to the programme start date.
   * @return The milestone date and time, or null if it would be in the past.
   */
  private Date getMilestoneDate(LocalDate programmeStartDate, int daysBeforeStart) {
    Date milestone = null;
    LocalDate milestoneDate = programmeStartDate.minusDays(daysBeforeStart);
    if (!milestoneDate.isBefore(LocalDate.now())) {
      milestone = Date.from(milestoneDate
          .atStartOfDay() //TODO: do we want to send them all at once?
          .atZone(ZoneId.systemDefault())
          .toInstant());
    }
    return milestone;
  }
}
