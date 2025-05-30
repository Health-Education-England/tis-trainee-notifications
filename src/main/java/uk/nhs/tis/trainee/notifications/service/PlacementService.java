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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.NON_EMPLOYMENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_INFORMATION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_ROLLOUT_2024_CORRECTION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.USEFUL_INFORMATION;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.ONE_DAY_IN_SECONDS;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationEvent;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.Placement;

/**
 * A service for Placement.
 */

@Slf4j
@Service
public class PlacementService {

  public static final String TIS_ID_FIELD = "tisId";
  public static final String START_DATE_FIELD = "startDate";
  public static final String PLACEMENT_TYPE_FIELD = "placementType";
  public static final String PLACEMENT_SPECIALTY_FIELD = "specialty";
  public static final String PLACEMENT_SITE_FIELD = "site";
  public static final String LOCAL_OFFICE_CONTACT_FIELD = "localOfficeContact";
  public static final String LOCAL_OFFICE_CONTACT_TYPE_FIELD = "localOfficeContactType";
  public static final String GMC_NUMBER_FIELD = "gmcNumber";

  public static final List<String> PLACEMENT_TYPES_TO_ACT_ON
      = List.of("In post", "In post - Acting up", "In Post - Extension");

  private final HistoryService historyService;
  private final NotificationService notificationService;
  private final InAppService inAppService;
  private final ZoneId timezone;
  private final String placementInfoVersion;
  private final String placementUsefulInfoVersion;
  private final String nonEmploymentVersion;

  /**
   * Initialise the Placement Service.
   *
   * @param historyService             The history Service to use.
   * @param notificationService        The notification Service to use.
   * @param inAppService               The in-app service to use.
   * @param placementInfoVersion       The placement information in-app notification version.
   * @param placementUsefulInfoVersion The placement useful information in-app notification
   *                                   version.
   * @param nonEmploymentVersion       The non employment in-app notification version.
   */
  public PlacementService(HistoryService historyService, NotificationService notificationService,
      InAppService inAppService, @Value("${application.timezone}") ZoneId timezone,
      @Value("${application.template-versions.placement-information.in-app}")
      String placementInfoVersion,
      @Value("${application.template-versions.placement-useful-information.in-app}")
      String placementUsefulInfoVersion,
      @Value("${application.template-versions.non-employment.in-app}")
      String nonEmploymentVersion) {
    this.historyService = historyService;
    this.notificationService = notificationService;
    this.inAppService = inAppService;
    this.timezone = timezone;
    this.placementInfoVersion = placementInfoVersion;
    this.placementUsefulInfoVersion = placementUsefulInfoVersion;
    this.nonEmploymentVersion = nonEmploymentVersion;
  }

  /**
   * Determines whether a placement is excluded or not, on the basis of placement type.
   *
   * <p>Excluded means the trainee will not be notified (contacted) in respect of this
   * placement.
   *
   * <p>Placement will only be included if the placement type begin with `In Post`.
   *
   * @param placement the Placement.
   * @return true if the placement is excluded.
   */
  public boolean isExcluded(Placement placement) {
    LocalDate startDate = placement.getStartDate();
    if (startDate == null || startDate.isBefore(LocalDate.now(timezone))) {
      return true;
    }

    if (placement.getPlacementType() == null) {
      return true; //should not happen, but some legacy data has no placement type set.
    }
    return (PLACEMENT_TYPES_TO_ACT_ON.stream()
        .noneMatch(placement.getPlacementType()::equalsIgnoreCase));
  }

  /**
   * Get a map of notifications and their event status for a given trainee and placement from the
   * notification history.
   *
   * @param traineeId   The trainee TIS ID.
   * @param placementId The placement TIS ID.
   * @return The map of notification types and event status.
   */
  private Map<NotificationType, NotificationEvent> getNotificationsEvents(String traineeId,
      String placementId) {
    EnumMap<NotificationType, NotificationEvent> notificationTypeEvents
        = new EnumMap<>(NotificationType.class);
    List<HistoryDto> correspondence = historyService.findAllForTrainee(traineeId);

    Set<NotificationType> notificationTypes = new HashSet<>();
    notificationTypes.add(PLACEMENT_UPDATED_WEEK_12);
    notificationTypes.add(PLACEMENT_INFORMATION);
    notificationTypes.add(USEFUL_INFORMATION);
    notificationTypes.add(NON_EMPLOYMENT);
    notificationTypes.add(PLACEMENT_ROLLOUT_2024_CORRECTION);

    for (NotificationType milestone : notificationTypes) {
      Optional<HistoryDto> historyItem = correspondence.stream()
          .filter(c -> c.tisReference() != null)
          .filter(c ->
              c.tisReference().type().equals(PLACEMENT)
                  && c.subject().equals(milestone)
                  && c.tisReference().id().equals(placementId))
          .findFirst();
      historyItem.ifPresent(
          historyDto -> notificationTypeEvents.put(milestone,
              new NotificationEvent(historyDto.status(), historyDto.sentAt())));
    }
    return notificationTypeEvents;
  }

  /**
   * Set up notifications for an updated placement.
   *
   * @param placement The updated placement.
   * @throws SchedulerException if any one of the notification jobs could not be scheduled.
   */
  public void addNotifications(Placement placement)
      throws SchedulerException {

    //first delete any stale notifications
    deleteNotificationsFromScheduler(placement);
    deleteScheduledNotificationsFromDb(placement);

    boolean isExcluded = isExcluded(placement);
    log.info("Placement {}: excluded {}.", placement.getTisId(), isExcluded);

    if (!isExcluded) {
      Map<NotificationType, NotificationEvent> notificationsRecorded
          = getNotificationsEvents(placement.getPersonId(), placement.getTisId());

      createDirectNotifications(placement, notificationsRecorded);
      createInAppNotifications(placement, notificationsRecorded);
    }
  }

  /**
   * Remove notifications for a placement from scheduler.
   *
   * @param placement The placement.
   * @throws SchedulerException if any one of the notification jobs could not be removed.
   */
  public void deleteNotificationsFromScheduler(Placement placement)
      throws SchedulerException {
    String jobId = PLACEMENT_UPDATED_WEEK_12 + "-" + placement.getTisId();
    notificationService.removeNotification(jobId); //remove existing notification if it exists
    String jobId2 = PLACEMENT_ROLLOUT_2024_CORRECTION + "-" + placement.getTisId();
    notificationService.removeNotification(jobId2); //remove existing notification if it exists
  }

  /**
   * Get the number of days in advance of the placement start to send the notification.
   *
   * @param notificationType The notification type.
   * @return The number of days before the placement start for the notification, or null if not a
   *     placement update notification type.
   */
  public Integer getNotificationDaysBeforeStart(NotificationType notificationType) {
    if (notificationType.equals(PLACEMENT_UPDATED_WEEK_12)
        || notificationType.equals(PLACEMENT_INFORMATION)
        || notificationType.equals(USEFUL_INFORMATION)
        || notificationType.equals(NON_EMPLOYMENT)) {
      return 84;
    } else {
      return null;
    }
  }

  /**
   * Create "direct" notifications, such as email, which may be scheduled for a future date/time.
   *
   * @param placement             The updated placement.
   * @param notificationsRecorded Notification types and their recorded event.
   */
  private void createDirectNotifications(Placement placement,
      Map<NotificationType, NotificationEvent> notificationsRecorded)
      throws SchedulerException {

    LocalDate startDate = placement.getStartDate();

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(TIS_ID_FIELD, placement.getTisId());
    jobDataMap.put(PERSON_ID_FIELD, placement.getPersonId());
    jobDataMap.put(START_DATE_FIELD, placement.getStartDate());
    jobDataMap.put(PLACEMENT_TYPE_FIELD, placement.getPlacementType());
    jobDataMap.put(PLACEMENT_SPECIALTY_FIELD, placement.getSpecialty());
    jobDataMap.put(PLACEMENT_SITE_FIELD, placement.getSite());
    jobDataMap.put(TEMPLATE_OWNER_FIELD, placement.getOwner());

    boolean shouldScheduleWeek12 = shouldScheduleNotification(notificationsRecorded, startDate);

    if (shouldScheduleWeek12) {
      log.info("Scheduling notification {} for {}.",
          PLACEMENT_UPDATED_WEEK_12, placement.getTisId());
      Integer daysBeforeStart = getNotificationDaysBeforeStart(PLACEMENT_UPDATED_WEEK_12);
      Date when = notificationService.getScheduleDate(startDate, daysBeforeStart);

      jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PLACEMENT_UPDATED_WEEK_12);

      // Note the status of the trainee will be retrieved when the job is executed, as will
      // their name and email address, and the contact details of the owner LO, not now.

      String jobId = PLACEMENT_UPDATED_WEEK_12 + "-" + placement.getTisId();
      try {
        notificationService.scheduleNotification(jobId, jobDataMap, when, ONE_DAY_IN_SECONDS);
      } catch (SchedulerException e) {
        log.error("Failed to schedule notification {}: {}", jobId, e.toString());
        throw (e); //to allow message to be requeue-ed
      }
    }

    boolean shouldScheduleRolloutCorrection
        = shouldScheduleRolloutCorrectionNotification(placement, notificationsRecorded);

    if (shouldScheduleRolloutCorrection) {
      log.info("Scheduling notification {} for {}.",
          PLACEMENT_ROLLOUT_2024_CORRECTION, placement.getTisId());
      jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PLACEMENT_ROLLOUT_2024_CORRECTION);

      String jobId = PLACEMENT_ROLLOUT_2024_CORRECTION + "-" + placement.getTisId();
      try {
        Date sendInMinute = Date.from(Instant.now().plus(1, ChronoUnit.MINUTES));
        notificationService.scheduleNotification(jobId, jobDataMap, sendInMinute, 0L);
      } catch (SchedulerException e) {
        log.error("Failed to schedule notification {}: {}", jobId, e.toString());
        throw (e); //to allow message to be requeue-ed
      }
    }
  }

  /**
   * Helper function to determine whether a notification should be scheduled.
   *
   * @param notificationsRecorded The current recorded notification types and their events.
   * @param startDate             The placement start date.
   * @return true if it should be scheduled, false otherwise.
   */
  private boolean shouldScheduleNotification(
      Map<NotificationType, NotificationEvent> notificationsRecorded, LocalDate startDate) {

    if (startDate == null || startDate.isBefore(LocalDate.now())) {
      return false;
    }
    //do not resend any notification
    return (!notificationsRecorded.containsKey(PLACEMENT_UPDATED_WEEK_12));
  }

  /**
   * Helper function to determine whether a rollout correction notification should be scheduled.
   *
   * @param placement             The placement that triggered the notification.
   * @param notificationsRecorded The current recorded notification types and their events.
   * @return true if it should be scheduled, false otherwise.
   */
  private boolean shouldScheduleRolloutCorrectionNotification(Placement placement,
      Map<NotificationType, NotificationEvent> notificationsRecorded) {
    if (notificationsRecorded.containsKey(PLACEMENT_ROLLOUT_2024_CORRECTION)) {
      return false; //do not resend
    } else {
      boolean shouldHaveNotification = notificationService.meetsCriteria(placement, true);
      boolean hasSentNotification = false;
      boolean hasNotificationType = notificationsRecorded.containsKey(PLACEMENT_UPDATED_WEEK_12);
      if (hasNotificationType) {
        NotificationEvent notificationEvent = notificationsRecorded.get(PLACEMENT_UPDATED_WEEK_12);
        hasSentNotification = notificationEvent.notificationStatus() == NotificationStatus.SENT;
      }
      return (!shouldHaveNotification && hasSentNotification);
    }
  }

  /**
   * Create any relevant in-app notifications.
   *
   * @param placement             The updated placement.
   * @param notificationsRecorded The current recorded notification types and their events.
   */
  private void createInAppNotifications(Placement placement,
      Map<NotificationType, NotificationEvent> notificationsRecorded) {
    boolean meetsCriteria = notificationService.meetsCriteria(placement, true);

    if (meetsCriteria) {
      String owner = placement.getOwner();
      List<Map<String, String>> contactList = notificationService.getOwnerContactList(owner);
      String localOfficeContact = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.TSS_SUPPORT, null);
      String localOfficeContactType =
          notificationService.getHrefTypeForContact(localOfficeContact);

      UserDetails userTraineeDetails = notificationService.getTraineeDetails(
          placement.getPersonId());
      String gmcNumber = (userTraineeDetails != null && userTraineeDetails.gmcNumber() != null)
          ? userTraineeDetails.gmcNumber().trim() : "unknown";

      // PLACEMENT_INFORMATION
      createUniqueInAppNotification(placement, notificationsRecorded, PLACEMENT_INFORMATION,
          placementInfoVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContact,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactType,
              GMC_NUMBER_FIELD, gmcNumber));

      // PLACEMENT_USEFUL_INFORMATION
      createUniqueInAppNotification(placement, notificationsRecorded,
          USEFUL_INFORMATION, placementUsefulInfoVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContact,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactType,
              GMC_NUMBER_FIELD, gmcNumber));

      // NON_EMPLOYMENT
      createUniqueInAppNotification(placement, notificationsRecorded, NON_EMPLOYMENT,
          nonEmploymentVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContact,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactType,
              GMC_NUMBER_FIELD, gmcNumber));
    }
  }

  /**
   * Create a unique in-app notification of the given type and version.
   *
   * @param placement             The updated placement.
   * @param notificationsRecorded Previously sent notifications.
   * @param notificationType      The type of notification being sent.
   * @param notificationVersion   The version of the notification.
   * @param extraVariables        Extra variables to include with the template, Specialty, Site
   *                              Known As and Start Date are populated automatically.
   */
  private void createUniqueInAppNotification(Placement placement,
      Map<NotificationType, NotificationEvent> notificationsRecorded,
      NotificationType notificationType, String notificationVersion,
      Map<String, Object> extraVariables) {

    boolean isUnique = !notificationsRecorded.containsKey(notificationType);
    if (isUnique) {
      Map<String, Object> variables = new HashMap<>(extraVariables);
      variables.put(START_DATE_FIELD, placement.getStartDate());
      variables.put(PLACEMENT_SPECIALTY_FIELD, placement.getSpecialty());
      variables.put(PLACEMENT_SITE_FIELD, placement.getSite());

      boolean doNotSendJustLog = !notificationService.placementIsNotifiable(placement, IN_APP);
      History.TisReferenceInfo tisReference =
          new History.TisReferenceInfo(PLACEMENT, placement.getTisId());

      Integer daysBeforeStart = getNotificationDaysBeforeStart(notificationType);
      Instant sentAt = notificationService
          .calculateInAppDisplayDate(placement.getStartDate(), daysBeforeStart);

      inAppService.createNotifications(placement.getPersonId(), tisReference,
          notificationType, notificationVersion, variables, doNotSendJustLog, sentAt);
    }
  }

  /**
   * Remove scheduled notifications for a placement from DB.
   *
   * @param placement The placement.
   */
  public void deleteScheduledNotificationsFromDb(Placement placement) {

    List<History> scheduledHistories = historyService
        .findAllScheduledForTrainee(placement.getPersonId(), PLACEMENT, placement.getTisId());

    for (History history : scheduledHistories) {
      historyService.deleteHistoryForTrainee(history.id(), placement.getPersonId());
    }
  }
}
