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
import static uk.nhs.tis.trainee.notifications.model.HrefType.ABSOLUTE_URL;
import static uk.nhs.tis.trainee.notifications.model.HrefType.NON_HREF;
import static uk.nhs.tis.trainee.notifications.model.HrefType.PROTOCOL_EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import jakarta.mail.MessagingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContact;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationSummary;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.Placement;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;

/**
 * A service for executing notification scheduling jobs.
 */
@Slf4j
@Component
public class NotificationService implements Job {

  protected static final String DEFAULT_NO_CONTACT_MESSAGE = "your local office";
  protected static final List<String> DUMMY_USER_ROLES = List.of("Placeholder", "Dummy Record");
  protected static final String JOB_RESULT_STATUS = "status";

  public static final String API_TRAINEE_DETAILS = "/api/trainee-profile/account-details/{tisId}";
  public static final String API_TRAINEE_LOCAL_OFFICE_CONTACTS
      = "/api/trainee-profile/local-office-contacts/{tisId}/{contactTypeName}";
  public static final String API_GET_OWNER_CONTACT
      = "/api/local-office-contact-by-lo-name/{localOfficeName}";
  private static final String TRIGGER_ID_PREFIX = "trigger-";
  public static final long ONE_DAY_IN_SECONDS = 24 * 60 * 60L;

  public static final String TEMPLATE_NOTIFICATION_TYPE_FIELD = "notificationType";
  public static final String TEMPLATE_OWNER_CONTACT_FIELD = "localOfficeContact";
  public static final String TEMPLATE_CONTACT_HREF_FIELD = "contactHref";
  public static final String TEMPLATE_OWNER_FIELD = "localOfficeName";
  public static final String TEMPLATE_OWNER_WEBSITE_FIELD = "localOfficeWebsite";
  public static final String PERSON_ID_FIELD = "personId";
  public static final String OWNER_FIELD = "localOfficeName";
  public static final String CONTACT_TYPE_FIELD = "contactTypeName";
  public static final String CONTACT_FIELD = "contact";
  public static final String CC_OF_FIELD = "ccOfSentTo";

  private final EmailService emailService;
  private final HistoryService historyService;
  private final ProgrammeMembershipActionsService programmeMembershipActionService;
  private final RestTemplate restTemplate;
  private final TemplateVersionsProperties templateVersions;
  private final String serviceUrl;
  private final String referenceUrl;
  private final Scheduler scheduler;
  private final MessagingControllerService messagingControllerService;
  private final List<String> notificationsWhitelist;
  private final String timezone;
  protected final Integer immediateNotificationDelayMinutes;

  private final Random random;

  /**
   * Initialise the Notification Service.
   *
   * @param emailService               The Email Service to use.
   * @param restTemplate               The REST template.
   * @param scheduler                  The messaging scheduler.
   * @param messagingControllerService The messaging controller service to control whether to
   *                                   dispatch messages.
   * @param templateVersions           The notification template versions.
   * @param serviceUrl                 The URL for the tis-trainee-details service to use for
   *                                   profile information.
   * @param referenceUrl               The URL for the tis-trainee-reference service to use for
   *                                   local office information.
   * @param notificationsWhitelist     The whitelist of (tester) trainee TIS IDs.
   */
  public NotificationService(EmailService emailService, HistoryService historyService,
      ProgrammeMembershipActionsService programmeMembershipActionService,
      RestTemplate restTemplate, Scheduler scheduler,
      MessagingControllerService messagingControllerService,
      TemplateVersionsProperties templateVersions,
      @Value("${service.trainee.url}") String serviceUrl,
      @Value("${service.reference.url}") String referenceUrl,
      @Value("${application.immediate-notifications-delay-minutes}") Integer notificationDelay,
      @Value("${application.notifications-whitelist}") List<String> notificationsWhitelist,
      @Value("${application.timezone}") String timezone) {
    this.emailService = emailService;
    this.historyService = historyService;
    this.programmeMembershipActionService = programmeMembershipActionService;
    this.restTemplate = restTemplate;
    this.scheduler = scheduler;
    this.templateVersions = templateVersions;
    this.serviceUrl = serviceUrl;
    this.referenceUrl = referenceUrl;
    this.messagingControllerService = messagingControllerService;
    this.immediateNotificationDelayMinutes = notificationDelay;
    this.notificationsWhitelist = notificationsWhitelist;
    this.timezone = timezone;
    this.random = new Random();
  }

  /**
   * Process a job immediately, sending the notification if appropriate.
   *
   * @param jobKey     The descriptive job identifier.
   * @param jobDetails The job details.
   * @return The result map with status details if successful.
   */
  public Map<String, String> executeNow(String jobKey, JobDataMap jobDetails) {
    Map<String, String> result = new HashMap<>();
    NotificationSummary notificationSummary = NotificationSummary.builder().build();

    // get job details according to notification type
    String personId = jobDetails.getString(PERSON_ID_FIELD);

    // Enrich User Account and Local Office details to jobDetails
    enrichJobDetails(jobDetails);

    UserDetails userTraineeDetails = getTraineeDetails(personId);
    UserDetails userCognitoAccountDetails = getCognitoAccountDetails(userTraineeDetails.email());
    UserDetails userAccountDetails = mapUserDetails(userCognitoAccountDetails, userTraineeDetails);

    NotificationType notificationType =
        NotificationType.valueOf(jobDetails.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());


    if (NotificationType.getActiveProgrammeUpdateNotificationTypes().contains(notificationType)) {
      String programmeId = jobDetails.getString(TIS_ID_FIELD);
      Set<ActionDto> actions = programmeMembershipActionService.getActions(personId, programmeId);
      programmeMembershipActionService.addActionsToJobMap(jobDetails, actions);
      notificationSummary = programmeMembershipActionService.getNotificationSummary(jobDetails);

    } else if (notificationType == NotificationType.PLACEMENT_UPDATED_WEEK_12
        || notificationType == NotificationType.PLACEMENT_ROLLOUT_2024_CORRECTION) {

      String jobName = jobDetails.getString(PlacementService.PLACEMENT_TYPE_FIELD);
      LocalDate startDate = (LocalDate) jobDetails.get(PlacementService.START_DATE_FIELD);
      History.TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(PLACEMENT,
          jobDetails.get(PlacementService.TIS_ID_FIELD).toString());
      notificationSummary = new NotificationSummary(jobName, startDate, tisReferenceInfo);
    }

    if (notificationSummary.unnecessaryReminder()) {
      log.info("Skipping unnecessary reminder for {} notification for {} ({}, starting {}) "
              + "to {}",
          jobKey,
          jobDetails.getString(TIS_ID_FIELD), notificationSummary.jobName(),
          notificationSummary.startDate(), userAccountDetails.email());
      result.put(JOB_RESULT_STATUS, "skipped unnecessary reminder");
      return result;
    }

    if (notificationSummary.tisReferenceInfo() != null) {
      if (userAccountDetails != null) {
        Optional<String> templateVersion = templateVersions.getTemplateVersion(notificationType,
            EMAIL);

        if (templateVersion.isEmpty()) {
          throw new IllegalArgumentException(
              "No email template version found for notification type '{}'.");
        }
        try {
          emailService.sendMessage(personId, userAccountDetails.email(), notificationType,
              templateVersion.get(), jobDetails.getWrappedMap(),
              notificationSummary.tisReferenceInfo(),
              !shouldActuallySendEmail(
                  notificationType, personId, notificationSummary.tisReferenceInfo().id()));
        } catch (MessagingException e) {
          throw new RuntimeException(e);
        }

        log.info("Executed {} notification for {} ({}, starting {}) to {} using template {}",
            jobKey,
            jobDetails.getString(TIS_ID_FIELD), notificationSummary.jobName(),
            notificationSummary.startDate(), userAccountDetails.email(), templateVersion.get());
        Instant processedOn = Instant.now();
        result.put(JOB_RESULT_STATUS, "sent " + processedOn.toString());
      } else {
        log.info("No notification could be sent, no TSS details found for tisId {}", personId);
      }
    }
    return result;
  }

  /**
   * Execute a given scheduled notification job.
   *
   * @param jobExecutionContext The job execution context.
   */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    String jobKey = jobExecutionContext.getJobDetail().getKey().toString();
    JobDataMap jobDetails = jobExecutionContext.getJobDetail().getJobDataMap();
    Map<String, String> result = executeNow(jobKey, jobDetails);
    if (result.get(JOB_RESULT_STATUS) != null) {
      jobExecutionContext.setResult(result);
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
  private void scheduleNotification(String jobId, JobDataMap jobDataMap, Date when)
      throws SchedulerException {
    // schedule notification in Scheduler
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

    // save SCHEDULED history in DB
    saveScheduleHistory(jobDataMap, when);
  }

  /**
   * Schedule a notification with a randomised offset.
   *
   * @param jobId           The job id. This must be unique for programme membership / placement and
   *                        notification milestone.
   * @param jobDataMap      The map of job data.
   * @param when            The date to schedule the notification to be sent.
   * @param windowInSeconds The randomised window in seconds.
   * @throws SchedulerException if the job could not be scheduled.
   */
  public void scheduleNotification(String jobId, JobDataMap jobDataMap, Date when,
      long windowInSeconds) throws SchedulerException {
    long randomOffset = random.nextLong(windowInSeconds + 1);
    Date randomisedWhen = Date.from(when.toInstant().plusSeconds(randomOffset));
    scheduleNotification(jobId, jobDataMap, randomisedWhen);
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
          .plus(immediateNotificationDelayMinutes, ChronoUnit.MINUTES));
    } else {
      // Future milestone.
      milestone = Date.from(milestoneDate
          .atStartOfDay()
          .atZone(ZoneId.of(timezone))
          .toInstant());
    }
    return milestone;
  }

  /**
   * Get a display date for an in-app notification from the start date and day offset.
   *
   * @param startDate       The starting date.
   * @param daysBeforeStart The number of days prior to the start date.
   * @return The in-app notification display date and time.
   */
  public Instant calculateInAppDisplayDate(LocalDate startDate, int daysBeforeStart) {
    LocalDate milestoneDate = startDate.minusDays(daysBeforeStart);
    if (!milestoneDate.isAfter(LocalDate.now())) {
      // 'Missed' milestones: display immediately
      return Instant.now();
    } else {
      // Future milestone.
      return milestoneDate
          .atStartOfDay()
          .atZone(ZoneId.of(timezone))
          .toInstant();
    }
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
          (userTraineeDetails.gmcNumber() != null ? userTraineeDetails.gmcNumber().trim() : null),
          userTraineeDetails.role());
    } else if (userTraineeDetails != null) {
      //no TSS account or duplicate accounts in Cognito
      String email = userTraineeDetails.email();
      return new UserDetails(false,
          email == null || email.isBlank() ? null : email,
          userTraineeDetails.title(),
          userTraineeDetails.familyName(),
          userTraineeDetails.givenName(),
          (userTraineeDetails.gmcNumber() != null ? userTraineeDetails.gmcNumber().trim() : null),
          userTraineeDetails.role());
    } else {
      return null;
    }
  }

  /**
   * Build notification details for sending message and DB history.
   *
   * @param jobDetails The job details.
   */
  protected JobDataMap enrichJobDetails(JobDataMap jobDetails) {
    String personId = jobDetails.getString(PERSON_ID_FIELD);
    UserDetails userTraineeDetails = getTraineeDetails(personId);

    if (userTraineeDetails == null) {
      String message = String.format(
          "The requested notification is for unknown or unavailable trainee '%s'.", personId);
      throw new IllegalArgumentException(message);
    }
    UserDetails userCognitoAccountDetails = getCognitoAccountDetails(userTraineeDetails.email());

    String owner = jobDetails.getString(TEMPLATE_OWNER_FIELD);
    List<Map<String, String>> ownerContactList = getOwnerContactList(owner);
    String contact = getOwnerContact(ownerContactList, LocalOfficeContactType.ONBOARDING_SUPPORT,
        LocalOfficeContactType.TSS_SUPPORT);
    jobDetails.putIfAbsent(TEMPLATE_OWNER_CONTACT_FIELD, contact);
    jobDetails.putIfAbsent(TEMPLATE_CONTACT_HREF_FIELD, getHrefTypeForContact(contact));
    String website = getOwnerContact(ownerContactList, LocalOfficeContactType.LOCAL_OFFICE_WEBSITE,
        null);
    jobDetails.putIfAbsent(TEMPLATE_OWNER_WEBSITE_FIELD, website);

    UserDetails userAccountDetails = mapUserDetails(userCognitoAccountDetails, userTraineeDetails);
    if (userAccountDetails != null) {
      jobDetails.putIfAbsent("isRegistered", userAccountDetails.isRegistered());
      jobDetails.putIfAbsent("title", userAccountDetails.title());
      jobDetails.putIfAbsent("familyName", userAccountDetails.familyName());
      jobDetails.putIfAbsent("givenName", userAccountDetails.givenName());
      jobDetails.putIfAbsent("email", userAccountDetails.email());
      jobDetails.putIfAbsent("gmcNumber", userAccountDetails.gmcNumber());
    }

    jobDetails.putIfAbsent("isValidGmc", isValidGmc(jobDetails.getString("gmcNumber")));

    return jobDetails;
  }

  /**
   * Determine if the email should actually be sent out.
   *
   * @param notificationType The notification type.
   * @param personId         The person Id.
   * @param tisReferenceId   The TIS reference Id.
   * @return the boolean if the email should be sent out.
   */
  protected boolean shouldActuallySendEmail(NotificationType notificationType, String personId,
      String tisReferenceId) {
    boolean actuallySendEmail = false; // default to log email only
    boolean inWhitelist = notificationsWhitelist.contains(personId);

    if (NotificationType.getActiveProgrammeUpdateNotificationTypes().contains(notificationType)) {

      ProgrammeMembership minimalPm = new ProgrammeMembership();
      minimalPm.setPersonId(personId);
      minimalPm.setTisId(tisReferenceId);
      actuallySendEmail
          = inWhitelist
          || (messagingControllerService.isValidRecipient(personId, MessageType.EMAIL)
          && meetsCriteria(minimalPm, true, true));

    } else if (notificationType == NotificationType.PLACEMENT_UPDATED_WEEK_12) {

      boolean inPilotOrRollout
          = messagingControllerService.isPlacementInPilot2024(personId, tisReferenceId)
          || (messagingControllerService.isPlacementInRollout2024(personId, tisReferenceId));
      actuallySendEmail = inWhitelist
          || (messagingControllerService.isValidRecipient(personId, MessageType.EMAIL)
          && inPilotOrRollout);
    } else if (notificationType == NotificationType.PLACEMENT_ROLLOUT_2024_CORRECTION) {
      actuallySendEmail = inWhitelist
          || messagingControllerService.isValidRecipient(personId, MessageType.EMAIL);
    }
    log.debug("Actually send email [{}]: for person {} and entity {} and notification {}",
        actuallySendEmail, personId, tisReferenceId, notificationType);
    return actuallySendEmail;
  }

  /**
   * Store history in DB for scheduled notification.
   *
   * @param jobDetails The job details.
   */
  public void saveScheduleHistory(JobDataMap jobDetails, Date when) {
    jobDetails = enrichJobDetails(jobDetails);

    String personId = jobDetails.getString(PERSON_ID_FIELD);
    NotificationType notificationType =
        NotificationType.valueOf(jobDetails.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());

    // get TIS Reference Info
    TisReferenceInfo tisReferenceInfo = null;
    if (NotificationType.getActiveProgrammeUpdateNotificationTypes().contains(notificationType)) {
      tisReferenceInfo = new TisReferenceInfo(PROGRAMME_MEMBERSHIP,
          jobDetails.get(ProgrammeMembershipService.TIS_ID_FIELD).toString());
    } else if (notificationType == NotificationType.PLACEMENT_UPDATED_WEEK_12) {
      tisReferenceInfo = new TisReferenceInfo(PLACEMENT,
          jobDetails.get(PlacementService.TIS_ID_FIELD).toString());
    }

    // get Recipient Info
    History.RecipientInfo recipientInfo = new History.RecipientInfo(
        personId, EMAIL, jobDetails.getString("email"));

    Optional<String> templateVersion = templateVersions.getTemplateVersion(notificationType,
        EMAIL);

    if (templateVersion.isEmpty()) {
      throw new IllegalArgumentException(
          "No email template version found for notification type '{}'.");
    }

    History.TemplateInfo templateInfo = new History.TemplateInfo(notificationType.getTemplateName(),
        templateVersion.get(), jobDetails.getWrappedMap());

    // Only save when notificationType is correct and in Pilot/Rollout. We ignore the completion
    // status of programme actions since these could change before the notification is sent.
    if (tisReferenceInfo != null
        && shouldActuallySendEmail(notificationType, personId, tisReferenceInfo.id())) {

      // Save SCHEDULED History in DB
      History history = new History(
          ObjectId.get(),
          tisReferenceInfo,
          notificationType,
          recipientInfo,
          templateInfo,
          null,
          when.toInstant(),
          null,
          NotificationStatus.SCHEDULED,
          null,
          null);
      historyService.save(history);
    }
  }

  /**
   * Get the user account details from Cognito if they have signed-up to TIS Self-Service.
   *
   * @param email The person ID to search for.
   * @return The user account details, or null if not found or duplicate.
   */
  private UserDetails getCognitoAccountDetails(String email) {
    try {
      return email == null || email.isBlank() ? null
          : emailService.getRecipientAccountByEmail(email);
    } catch (UserNotFoundException e) {
      return null;
    }
  }

  /**
   * Get the user details from the Trainee Details profile if not.
   *
   * @param personId The person ID to search for.
   * @return The user trainee profile details, or null if not found.
   */
  public UserDetails getTraineeDetails(String personId) {
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
   * Get a trainee's local office contact(s) of the given type from the Trainee Details service.
   *
   * @param personId    The person ID to search for.
   * @param contactType The contact type to search for.
   * @return The trainee's local office contacts, or null if trainee not found.
   */
  public Set<LocalOfficeContact> getTraineeLocalOfficeContacts(String personId,
      LocalOfficeContactType contactType) {
    try {
      ParameterizedTypeReference<Set<LocalOfficeContact>> loContactListListType
          = new ParameterizedTypeReference<>() {};
      Set<LocalOfficeContact> localOfficeContacts =
          restTemplate.exchange(serviceUrl + API_TRAINEE_LOCAL_OFFICE_CONTACTS,
                  HttpMethod.GET, null, loContactListListType,
                  Map.of(TIS_ID_FIELD, personId, CONTACT_TYPE_FIELD, contactType))
              .getBody();
      return localOfficeContacts == null ? Collections.emptySet() : localOfficeContacts;
    } catch (RestClientException rce) {
      log.warn("Exception requesting local-office-contacts endpoint for trainee {} type {}: {}",
          personId, contactType, rce.toString());
      return Collections.emptySet();
    }
  }

  /**
   * Check if a LO contact is an email. The assumption is that contacts are either an email or a URL
   * (this is the validation applied in TIS), so only very basic checking is applied.
   *
   * @param contact The contact to check.
   * @return True if it looks like an email, otherwise false.
   */
  public boolean isLocalOfficeContactEmail(String contact) {
    return (contact != null && contact.contains("@"));
  }

  /**
   * Send (or log) email to the distinct list of local office contacts of given type for the
   * trainee.
   *
   * @param traineeDetails    The details of the trainee.
   * @param traineeId         The id of the trainee.
   * @param contactType       The type of local office contact to notify.
   * @param templateVariables The template variables.
   * @param templateVersion   The template version.
   * @param notificationType  The notification type (template type).
   * @return The set of distinct email addresses to which the mail was sent (or logged) in
   *         alphabetic order.
   * @throws MessagingException If the email(s) could not be sent.
   */
  public Set<String> sendLocalOfficeMail(UserDetails traineeDetails, String traineeId,
      LocalOfficeContactType contactType, Map<String, Object> templateVariables,
      String templateVersion, NotificationType notificationType) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts
        = getTraineeLocalOfficeContacts(traineeId, contactType);
    Set<String> sentTo = new TreeSet<>();

    boolean canSendMail = messagingControllerService.isMessagingEnabled(MessageType.EMAIL)
        && !userHasDummyRole(traineeDetails); //do not notify (real) LO of dummy users
    if (!localOfficeContacts.isEmpty()) {
      //since some LO's share a contact we need to eliminate possible duplicates:
      Set<String> distinctContacts = localOfficeContacts.stream()
          .map(LocalOfficeContact::contact).filter(Objects::nonNull).collect(Collectors.toSet());
      for (String loContact : distinctContacts) {
        if (isLocalOfficeContactEmail(loContact)) {
          emailService.sendMessage(traineeId, loContact, notificationType, templateVersion,
              templateVariables, null, !canSendMail);
          sentTo.add(loContact);
          log.info("{} local office notification {} for trainee {} to {}.", notificationType,
              (canSendMail ? "sent" : "logged"), traineeId, loContact);
        } else {
          log.info("{} local office notification skipped for trainee {} to non-email {}.",
              notificationType, traineeId, loContact);
        }
      }
    } else {
      log.warn("{} local office notification not processed for trainee {}: no local office "
              + "contacts.", notificationType, traineeId);
    }
    return sentTo;
  }

  /**
   * Determine if a user is a dummy / placeholder user from their roles.
   *
   * @param userDetails The user details.
   * @return true if they have a designated role, otherwise false.
   */
  protected boolean userHasDummyRole(UserDetails userDetails) {
    if (userDetails.role() == null) {
      return false;
    }
    return userDetails.role().stream().anyMatch(DUMMY_USER_ROLES::contains);
  }

  /**
   * Send (or log) an email to the trainee.
   *
   * @param traineeId         The id of the trainee.
   * @param traineeEmail      The email address of the trainee send the mail to.
   * @param templateVariables The template variables.
   * @param templateVersion   The template version.
   * @param notificationType  The notification type (template type).
   *
   * @throws MessagingException If the email could not be sent.
   */
  public void sendTraineeMail(String traineeId, String traineeEmail,
      Map<String, Object> templateVariables, String templateVersion,
      NotificationType notificationType) throws MessagingException {

    boolean canSendMail = messagingControllerService.isMessagingEnabled(MessageType.EMAIL);
    if (traineeEmail != null && !traineeEmail.isBlank()) {
      emailService.sendMessage(traineeId, traineeEmail, notificationType, templateVersion,
          templateVariables, null, !canSendMail);
      log.info("{} trainee notification {} for trainee {} to {}.",
          (canSendMail ? "sent" : "logged"), notificationType, traineeId, traineeEmail);

    } else {
      log.warn("{} trainee notification skipped for trainee {} without email address.",
          notificationType, traineeId);
    }
  }

  /**
   * Check whether a programme membership meets the selected notification criteria.
   *
   * @param programmeMembership The programme membership to check.
   * @param checkNewStarter     Whether the trainee must be a new starter.
   * @param checkPilot          Whether the trainee must be in a pilot or rollout.
   * @return true if all criteria met, or false if one or more criteria fail.
   */
  public boolean meetsCriteria(ProgrammeMembership programmeMembership,
      boolean checkNewStarter, boolean checkPilot) {
    String traineeId = programmeMembership.getPersonId();
    String pmId = programmeMembership.getTisId();

    if (checkNewStarter) {
      boolean isNewStarter = messagingControllerService.isProgrammeMembershipNewStarter(traineeId,
          pmId);

      if (!isNewStarter) {
        log.info("Skipping notification creation as trainee {} is not a new starter.", traineeId);
        return false;
      }
    }

    if (checkPilot) {
      boolean isInPilot
          = messagingControllerService.isProgrammeMembershipInPilot2024(traineeId, pmId);

      boolean isInRollout
          = messagingControllerService.isProgrammeMembershipInRollout2024(traineeId, pmId);

      if (!isInPilot && !isInRollout) {
        log.info("Skipping notification creation as trainee {} is not in the pilot or the rollout.",
            traineeId);
        return false;
      }
    }

    return true;
  }

  /**
   * Check whether a placement meets the selected notification criteria.
   *
   * @param placement  The placement to check.
   * @param checkPilot Whether the trainee must be in a pilot or rollout.
   * @return true if all criteria met, or false if one or more criteria fail.
   */
  public boolean meetsCriteria(Placement placement, boolean checkPilot) {
    String traineeId = placement.getPersonId();
    String pmId = placement.getTisId();

    if (checkPilot) {
      boolean isInPilot
          = messagingControllerService.isPlacementInPilot2024(traineeId, pmId);

      boolean isInRollout
          = messagingControllerService.isPlacementInRollout2024(traineeId, pmId);

      if (!isInPilot && !isInRollout) {
        log.info("Skipping notification creation as trainee {} is not in the pilot or the rollout.",
            traineeId);
        return false;
      }
    }

    return true;
  }

  /**
   * Check whether a programme membership's trainee should receive the given message-type
   * notification.
   *
   * @param programmeMembership The programme membership to check.
   * @param messageType         The potential notification message type.
   * @return true if the trainee should receive the notification, otherwise false.
   */
  public boolean programmeMembershipIsNotifiable(ProgrammeMembership programmeMembership,
      MessageType messageType) {
    String traineeId = programmeMembership.getPersonId();
    return messagingControllerService.isValidRecipient(traineeId, messageType);
  }

  /**
   * Check whether a placement's trainee should receive the given message-type notification.
   *
   * @param placement   The placement to check.
   * @param messageType The potential notification message type.
   * @return true if the trainee should receive the notification, otherwise false.
   */
  public boolean placementIsNotifiable(Placement placement, MessageType messageType) {
    String traineeId = placement.getPersonId();
    return messagingControllerService.isValidRecipient(traineeId, messageType);
  }

  /**
   * Validate the stored GMC number of the trainee.
   *
   * @param gmcNumber The GMC number to validate.
   * @return true if it is 7 consecutive numerical digits string, otherwise false.
   */
  public boolean isValidGmc(String gmcNumber) {
    if (gmcNumber == null) {
      return false;
    }
    return (gmcNumber.length() == 7 && StringUtils.isNumeric(gmcNumber));
  }

  /**
   * Retrieve the full list of contacts for a local office from Trainee Reference Service.
   *
   * @param localOfficeName The local office name.
   * @return The list of contacts, or an empty list if there is an error.
   */
  public List<Map<String, String>> getOwnerContactList(String localOfficeName) {
    if (localOfficeName != null) {
      try {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> ownerContactList
            = restTemplate.getForObject(referenceUrl + API_GET_OWNER_CONTACT,
            List.class, Map.of(OWNER_FIELD, localOfficeName));
        return ownerContactList == null ? new ArrayList<>() : ownerContactList;
      } catch (RestClientException rce) {
        log.warn("Exception occurred when requesting reference local-office-contact-by-lo-name "
            + "endpoint: " + rce);
      }
    }
    return new ArrayList<>();
  }

  /**
   * Get specified owner contact from a list of contacts.
   *
   * @param ownerContactList    The owner contact list to search.
   * @param contactType         The contact type to return.
   * @param fallbackContactType if the contactType is not available, return this contactType
   *                            instead.
   * @return The specific contact of the owner, or a default message if not found.
   */
  protected String getOwnerContact(List<Map<String, String>> ownerContactList,
      LocalOfficeContactType contactType, LocalOfficeContactType fallbackContactType) {
    return getOwnerContact(ownerContactList, contactType, fallbackContactType,
        DEFAULT_NO_CONTACT_MESSAGE);
  }

  /**
   * Get specified owner contact from a list of contacts.
   *
   * @param ownerContactList    The owner contact list to search.
   * @param contactType         The contact type to return.
   * @param fallbackContactType if the contactType is not available, return this contactType
   *                            instead.
   * @param defaultMessage      The default message if the contact was not found.
   * @return The specific contact of the owner, or the default message if not found.
   */
  public String getOwnerContact(List<Map<String, String>> ownerContactList,
      LocalOfficeContactType contactType, LocalOfficeContactType fallbackContactType,
      String defaultMessage) {

    Optional<Map<String, String>> ownerContact = ownerContactList.stream()
        .filter(c ->
            c.get(CONTACT_TYPE_FIELD).equalsIgnoreCase(contactType.getContactTypeName()))
        .findFirst();
    if (ownerContact.isEmpty() && fallbackContactType != null) {
      ownerContact = ownerContactList.stream()
          .filter(c ->
              c.get(CONTACT_TYPE_FIELD)
                  .equalsIgnoreCase(fallbackContactType.getContactTypeName()))
          .findFirst();
    }
    return ownerContact.map(oc -> oc.get(CONTACT_FIELD))
        .orElse(defaultMessage);
  }

  /**
   * Return a href type for a contact. It is assumed to be either a URL or an email address. There
   * is minimal checking that it is a validly formatted email address.
   *
   * @param contact The contact string, expected to be either an email address or a URL.
   * @return "email" if it looks like an email address, "url" if it looks like a URL, and "NOT_HREF"
   *     otherwise.
   */
  public String getHrefTypeForContact(String contact) {
    try {
      new URL(contact);
      return ABSOLUTE_URL.getHrefTypeName();
    } catch (MalformedURLException e) {
      if (contact.contains("@") && !contact.contains(" ")) {
        return PROTOCOL_EMAIL.getHrefTypeName();
      } else {
        return NON_HREF.getHrefTypeName();
      }
    }
  }
}
