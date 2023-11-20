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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for Programme memberships.
 */

@Slf4j
@Service
public class ProgrammeMembershipService {

  private static final String TRIGGER_ID_PREFIX = "trigger-";
  private static final Integer PAST_MILESTONE_SCHEDULE_DELAY_HOURS = 1;

  private static final List<String> INCLUDE_CURRICULUM_SUBTYPES
      = List.of("MEDICAL_CURRICULUM", "MEDICAL_SPR");
  private static final List<String> EXCLUDE_CURRICULUM_SPECIALTIES
      = List.of("PUBLIC HEALTH MEDICINE", "FOUNDATION");

  private final Scheduler scheduler;
  private final HistoryService historyService;

  public ProgrammeMembershipService(Scheduler scheduler, HistoryService historyService) {
    this.scheduler = scheduler;
    this.historyService = historyService;
  }

  /**
   * Determines whether a programme membership is excluded or not, on the basis of curricula.
   *
   * <p>Excluded means the trainee will not be notified (contacted) in respect of this
   * programme membership.
   *
   * <p>This will be TRUE if any of the following are true in relation to the curricula:
   * 1. None have curriculumSubType = MEDICAL_CURRICULUM or MEDICAL_SPR. 2. Any have specialtyName =
   * 'Public health medicine' or 'Foundation'.
   *
   * @param programmeMembership the Programme membership.
   * @return true if the programme membership is excluded.
   */
  public boolean isExcluded(ProgrammeMembership programmeMembership) {
    List<Curriculum> curricula = programmeMembership.getCurricula();
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
   * Get a map of notification types and the instant they were sent for a given trainee and
   * programme membership from the notification history.
   *
   * @param traineeId             The trainee TIS ID.
   * @param programmeMembershipId The programme membership TIS ID.
   * @return The map of notification types and when they were sent.
   */
  private Map<NotificationType, Instant> getNotificationsSent(String traineeId,
      String programmeMembershipId) {
    EnumMap<NotificationType, Instant> notifications = new EnumMap<>(NotificationType.class);
    List<HistoryDto> correspondence = historyService.findAllForTrainee(traineeId);
    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
      Optional<HistoryDto> sentItem = correspondence.stream()
          .filter(c -> c.tisReference() != null)
          .filter(c ->
              c.tisReference().type().equals(TisReferenceType.PROGRAMME_MEMBERSHIP)
                  && c.subject().equals(milestone)
                  && c.tisReference().id().equals(programmeMembershipId))
          .findFirst();
      sentItem.ifPresent(historyDto -> notifications.put(milestone, historyDto.sentAt()));
    }
    return notifications;
  }

  /**
   * Set up notifications for an updated programme membership.
   *
   * @param programmeMembership The updated programme membership.
   * @throws SchedulerException if any one of the notification jobs could not be scheduled.
   */
  public void addNotifications(ProgrammeMembership programmeMembership)
      throws SchedulerException {

    deleteNotifications(programmeMembership); //first delete any stale notifications

    boolean isExcluded = isExcluded(programmeMembership);
    log.info("Programme membership {}: excluded {}.", programmeMembership.getTisId(), isExcluded);

    if (!isExcluded) {
      Map<NotificationType, Instant> notificationsAlreadySent
          = getNotificationsSent(programmeMembership.getPersonId(), programmeMembership.getTisId());

      LocalDate startDate = programmeMembership.getStartDate();

      for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
        boolean shouldSchedule = shouldScheduleNotification(startDate, milestone,
            notificationsAlreadySent);

        if (shouldSchedule) {
          Integer daysBeforeStart = getNotificationDaysBeforeStart(milestone);
          Date when = getScheduleDate(startDate, daysBeforeStart);

          JobDataMap jobDataMap = new JobDataMap();
          jobDataMap.put("tisId", programmeMembership.getTisId());
          jobDataMap.put("personId", programmeMembership.getPersonId());
          jobDataMap.put("programmeName", programmeMembership.getProgrammeName());
          jobDataMap.put("startDate", programmeMembership.getStartDate());
          jobDataMap.put("notificationType", milestone);
          // Note the status of the trainee will be retrieved when the job is executed, as will
          // their name and email address, not now.

          String jobId = milestone + "-" + programmeMembership.getTisId();
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
   * Remove notifications for a programme membership.
   *
   * @param programmeMembership The programme membership.
   * @throws SchedulerException if any one of the notification jobs could not be removed.
   */
  public void deleteNotifications(ProgrammeMembership programmeMembership)
      throws SchedulerException {

    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {

      String jobId = milestone.toString() + "-" + programmeMembership.getTisId();
      removeNotification(jobId); //remove existing notification if it exists
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
        .storeDurably(false)
        .build();

    Trigger trigger = newTrigger()
        .withIdentity(TRIGGER_ID_PREFIX + jobId)
        .startAt(when)
        .build();

    Date scheduledDate = scheduler.scheduleJob(job, trigger);
    log.info("Notification for {} scheduled for {}", jobId, scheduledDate);
  }

  /**
   * Remove a scheduled notification if it exists.
   *
   * @param jobId The job id key to remove.
   * @throws SchedulerException if the scheduler failed in its duties (non-existent jobs do not
   *                            trigger this exception).
   */
  private void removeNotification(String jobId) throws SchedulerException {
    JobKey jobKey = new JobKey(jobId);
    // Delete the job and unschedule its triggers.
    // We do not simply remove the trigger, since a replacement job may have different data
    // (e.g. programme name).
    scheduler.deleteJob(jobKey);
    log.info("Removed any stale notification scheduled for {}", jobId);
  }

  /**
   * Get a future schedule for a notification from the programme start date and day offset.
   *
   * @param programmeStartDate The programme starting date.
   * @param daysBeforeStart    The number of days prior to the programme start date.
   * @return The notification scheduling date and time.
   */
  private Date getScheduleDate(LocalDate programmeStartDate, int daysBeforeStart) {
    Date milestone;
    LocalDate milestoneDate = programmeStartDate.minusDays(daysBeforeStart);
    if (!milestoneDate.isAfter(LocalDate.now())) {
      // 'Missed' milestones: schedule to be sent soon, but not immediately
      // in case of human editing 'jitter'.
      milestone = Date.from(Instant.now()
          .plus(PAST_MILESTONE_SCHEDULE_DELAY_HOURS*20, ChronoUnit.SECONDS));

    } else {
      // Future milestone.
      milestone = Date.from(milestoneDate
          .atStartOfDay()
          .atZone(ZoneId.systemDefault())
          .toInstant());
    }
    return milestone;
  }

  /**
   * Helper function to determine whether a notification should be scheduled.
   *
   * @return true if it should be scheduled, false otherwise.
   */
  private boolean shouldScheduleNotification(LocalDate programmeStartDate,
      NotificationType milestone, Map<NotificationType, Instant> notificationsAlreadySent) {

    //do not resend any notification
    if (notificationsAlreadySent.containsKey(milestone)) {
      return false;
    }

    Integer daysBeforeStart = getNotificationDaysBeforeStart(milestone);
    LocalDate milestoneDate = programmeStartDate.minusDays(daysBeforeStart);

    if (milestoneDate.isAfter(LocalDate.now())) {
      // A future notification, to be scheduled at the appropriate time
      // unless a more recent notification has already been sent
      return (notificationsAlreadySent.keySet().stream()
          .noneMatch(t -> getNotificationDaysBeforeStart(t) < daysBeforeStart));
    } else {
      // A past ('missed') notification, to be scheduled promptly
      // if it is the most recent missed notification.
      // Do not schedule if it is not the most recent missed notification, regardless of whether
      // any more recent ones have been sent or not.
      return (getPastNotifications(programmeStartDate).stream()
          .noneMatch(t -> getNotificationDaysBeforeStart(t) < daysBeforeStart));
    }
  }

  /**
   * Get the number of days in advance of the programme start to send the notification.
   *
   * @param notificationType The notification type.
   * @return The number of days before the programme start for the notification, or null if not a
   *         programme update notification type.
   */
  public Integer getNotificationDaysBeforeStart(NotificationType notificationType) {
    switch (notificationType) {
      case PROGRAMME_UPDATED_WEEK_8 -> {
        return 56;
      }
      case PROGRAMME_UPDATED_WEEK_4 -> {
        return 28;
      }
      case PROGRAMME_UPDATED_WEEK_1 -> {
        return 7;
      }
      case PROGRAMME_UPDATED_WEEK_0 -> {
        return 0;
      }
      default -> {
        return null;
      }
    }
  }

  /**
   * Retrieve a list of notifications for a given programme start date that should have been sent in
   * the past (including sent today).
   *
   * @param programmeStartDate The programme start date.
   * @return The list of past notifications.
   */
  private List<NotificationType> getPastNotifications(LocalDate programmeStartDate) {
    return NotificationType.getProgrammeUpdateNotificationTypes().stream()
        .filter(t -> !programmeStartDate.minusDays(getNotificationDaysBeforeStart(t))
            .isAfter(LocalDate.now()))
        .toList();
  }
}
