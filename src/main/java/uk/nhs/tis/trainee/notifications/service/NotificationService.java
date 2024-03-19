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
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A service for executing notification scheduling jobs.
 */
@Slf4j
@Component
public class NotificationService implements Job {

  public static final String API_TRAINEE_DETAILS = "/api/trainee-profile/account-details/{tisId}";
  private static final String TRIGGER_ID_PREFIX = "trigger-";
  protected static final Integer PAST_MILESTONE_SCHEDULE_DELAY_HOURS = 1;
  private final EmailService emailService;
  private final RestTemplate restTemplate;
  private final String templateVersion;
  private final String serviceUrl;
  private final Scheduler scheduler;

  /**
   * Initialise the Notification Service.
   *
   * @param emailService    The Email Service to use.
   * @param restTemplate    The REST template.
   * @param templateVersion The email template version.
   * @param serviceUrl      The URL for the tis-trainee-details service to use for profile
   *                        information.
   */
  public NotificationService(EmailService emailService, RestTemplate restTemplate,
      Scheduler scheduler,
      @Value("${application.template-versions.form-updated.email}") String templateVersion,
      @Value("${service.trainee.url}") String serviceUrl) {
    this.emailService = emailService;
    this.restTemplate = restTemplate;
    this.scheduler = scheduler;
    this.templateVersion = templateVersion;
    this.serviceUrl = serviceUrl;
  }

  /**
   * Execute a given notification job.
   *
   * @param jobExecutionContext The job execution context.
   */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    boolean justLogEmail = true;
    String jobKey = jobExecutionContext.getJobDetail().getKey().toString();
    Map<String, String> result = new HashMap<>();
    JobDataMap jobDetails = jobExecutionContext.getJobDetail().getJobDataMap();

    // get job details according to notification type
    String personId = "";
    String jobName = "";
    TisReferenceInfo tisReferenceInfo = null;
    LocalDate startDate = null;
    NotificationType notificationType =
        NotificationType.valueOf(jobDetails.get(NOTIFICATION_TYPE_FIELD).toString());

    if (NotificationType.getProgrammeUpdateNotificationTypes().contains(notificationType)) {
      personId = jobDetails.getString(ProgrammeMembershipService.PERSON_ID_FIELD);
      jobName = jobDetails.getString(ProgrammeMembershipService.PROGRAMME_NAME_FIELD);
      startDate = (LocalDate) jobDetails.get(ProgrammeMembershipService.START_DATE_FIELD);
      tisReferenceInfo = new TisReferenceInfo(PROGRAMME_MEMBERSHIP,
          jobDetails.get(ProgrammeMembershipService.TIS_ID_FIELD).toString());

    } else if (notificationType == NotificationType.PLACEMENT_UPDATED_WEEK_12) {
      personId = jobDetails.getString(PlacementService.PERSON_ID_FIELD);
      jobName = jobDetails.getString(PlacementService.PLACEMENT_TYPE_FIELD);
      startDate = (LocalDate) jobDetails.get(PlacementService.START_DATE_FIELD);
      tisReferenceInfo = new TisReferenceInfo(PLACEMENT,
          jobDetails.get(PlacementService.TIS_ID_FIELD).toString());
      String localOfficeName = jobDetails.getString(PlacementService.PLACEMENT_OWNER_FIELD);
      String specialty = jobDetails.getString(PlacementService.PLACEMENT_SPECIALTY_FIELD);
      justLogEmail = !isInPilot(localOfficeName, specialty, startDate);
    }

    UserDetails userCognitoAccountDetails = getCognitoAccountDetails(personId);
    UserDetails userTraineeDetails = getTraineeDetails(personId);
    UserDetails userAccountDetails = mapUserDetails(userCognitoAccountDetails, userTraineeDetails);

    if (userAccountDetails != null) {
      jobDetails.putIfAbsent("isRegistered", userAccountDetails.isRegistered());
      jobDetails.putIfAbsent("title", userAccountDetails.title());
      jobDetails.putIfAbsent("familyName", userAccountDetails.familyName());
      jobDetails.putIfAbsent("givenName", userAccountDetails.givenName());
      jobDetails.putIfAbsent("email", userAccountDetails.email());
      jobDetails.putIfAbsent("gmcNumber", userAccountDetails.gmcNumber());

      try {
        emailService.sendMessage(personId, userAccountDetails.email(), notificationType,
            templateVersion, jobDetails.getWrappedMap(), tisReferenceInfo, justLogEmail);
      } catch (MessagingException e) {
        throw new RuntimeException(e);
      }

      log.info("Sent {} notification for {} ({}, starting {}) to {} using template {}", jobKey,
          jobDetails.getString(TIS_ID_FIELD), jobName, startDate, userAccountDetails.email(),
          templateVersion);
      Instant processedOn = Instant.now();
      result.put("status", "sent " + processedOn.toString());
      jobExecutionContext.setResult(result);
    } else {
      log.info("No notification could be sent, no TSS details found for tisId {}", personId);
    }
  }

  /**
   * Schedule a notification.
   *
   * @param jobId      The job id. This must be unique for programme membership / placement and
   *                   notification milestone.
   * @param jobDataMap The map of job data.
   * @param when       The date to schedule the notification to be sent.
   * @throws SchedulerException if the job could not be scheduled.
   */
  public void scheduleNotification(String jobId, JobDataMap jobDataMap, Date when)
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
  public void removeNotification(String jobId) throws SchedulerException {
    JobKey jobKey = new JobKey(jobId);
    // Delete the job and unschedule its triggers.
    // We do not simply remove the trigger, since a replacement job may have different data
    // (e.g. programme name).
    scheduler.deleteJob(jobKey);
    log.info("Removed any stale notification scheduled for {}", jobId);
  }

  /**
   * Get a future schedule for a notification from the start date and day offset.
   *
   * @param startDate       The starting date.
   * @param daysBeforeStart The number of days prior to the start date.
   * @return The notification scheduling date and time.
   */
  public Date getScheduleDate(LocalDate startDate, int daysBeforeStart) {
    Date milestone;
    LocalDate milestoneDate = startDate.minusDays(daysBeforeStart);
    if (!milestoneDate.isAfter(LocalDate.now())) {
      // 'Missed' milestones: schedule to be sent soon, but not immediately
      // in case of human editing 'jitter'.
      milestone = Date.from(Instant.now()
          .plus(PAST_MILESTONE_SCHEDULE_DELAY_HOURS, ChronoUnit.HOURS));
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
   * Map the user details from Cognito and trainee-profile. (Map gmcNumber from the Trainee Details
   * profile, map email and familyName from Cognito if they have signed-up to TIS Self-Service, or
   * from the Trainee Details profile if not)
   *
   * @param userCognitoAccountDetails The registered user account details from Cognito.
   * @param userTraineeDetails        The user account details from Trainee Profile.
   * @return The user account details, or null if not found.
   */
  public UserDetails mapUserDetails(UserDetails userCognitoAccountDetails,
      UserDetails userTraineeDetails) {
    if (userCognitoAccountDetails != null && userTraineeDetails != null) {
      return new UserDetails(true,
          userCognitoAccountDetails.email(),
          userTraineeDetails.title(),
          userCognitoAccountDetails.familyName(),
          userCognitoAccountDetails.givenName(),
          userTraineeDetails.gmcNumber());
    } else if (userTraineeDetails != null) {
      //no TSS account or duplicate accounts in Cognito
      return new UserDetails(false,
          userTraineeDetails.email(),
          userTraineeDetails.title(),
          userTraineeDetails.familyName(),
          userTraineeDetails.givenName(),
          userTraineeDetails.gmcNumber());
    } else {
      return null;
    }
  }

  /**
   * Get the user account details from Cognito if they have signed-up to TIS Self-Service.
   *
   * @param personId The person ID to search for.
   * @return The user account details, or null if not found or duplicate.
   */
  private UserDetails getCognitoAccountDetails(String personId) {
    try {
      return emailService.getRecipientAccount(personId);
    } catch (IllegalArgumentException e) {
      //no TSS account or duplicate accounts
      return null;
    }
  }

  /**
   * Get the user details from the Trainee Details profile if not.
   *
   * @param personId The person ID to search for.
   * @return The user trainee profile details, or null if not found.
   */
  private UserDetails getTraineeDetails(String personId) {
    try {
      return restTemplate.getForObject(serviceUrl + API_TRAINEE_DETAILS, UserDetails.class,
          Map.of(TIS_ID_FIELD, personId));
    } catch (RestClientException rce) {
      log.warn("Exception occur when requesting trainee account-details endpoint for trainee "
          + personId + ": " + rce);
      //no trainee details profile
      return null;
    }
  }

  /**
   * TEMPORARY (I hope). Identifies placements that fall within the pilot group 2024.
   *
   * @param localOffice The placement local office.
   * @param specialty   The placement specialty.
   * @param startDate   The placement start date.
   * @return true if the placement is in the pilot group, otherwise false.
   */
  protected boolean isInPilot(String localOffice, String specialty, LocalDate startDate) {

    // for now, say no-one is in pilot
    return false;
  }
}
