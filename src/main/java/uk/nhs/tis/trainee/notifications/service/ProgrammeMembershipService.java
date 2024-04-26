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

import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.DEFERRAL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.E_PORTFOLIO;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.INDEMNITY_INSURANCE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.SPONSORSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for Programme memberships.
 */

@Slf4j
@Service
public class ProgrammeMembershipService {

  public static final String TIS_ID_FIELD = "tisId";
  public static final String PROGRAMME_NAME_FIELD = "programmeName";
  public static final String START_DATE_FIELD = "startDate";
  public static final String BLOCK_INDEMNITY_FIELD = "hasBlockIndemnity";
  public static final String LOCAL_OFFICE_CONTACT_FIELD = "localOfficeContact";
  public static final String LOCAL_OFFICE_CONTACT_TYPE_FIELD = "localOfficeContactType";
  public static final String COJ_SYNCED_FIELD = "conditionsOfJoiningSyncedAt";

  private static final List<String> INCLUDE_CURRICULUM_SUBTYPES
      = List.of("MEDICAL_CURRICULUM", "MEDICAL_SPR");
  private static final List<String> EXCLUDE_CURRICULUM_SPECIALTIES
      = List.of("PUBLIC HEALTH MEDICINE", "FOUNDATION");

  private final HistoryService historyService;
  private final InAppService inAppService;
  private final NotificationService notificationService;

  private final ZoneId timezone;

  private final String deferralVersion;
  private final String eportfolioVersion;
  private final String indemnityInsuranceVersion;
  private final String ltftVersion;
  private final String sponsorshipVersion;

  /**
   * Initialise the programme membership service.
   *
   * @param historyService            The history service to use.
   * @param inAppService              The in-app service to use.
   * @param notificationService       The notification service to use.
   * @param deferralVersion           The deferral version.
   * @param eportfolioVersion         The ePortfolio version.
   * @param indemnityInsuranceVersion The indemnity insurance version.
   * @param ltftVersion               The LTFT version.
   * @param sponsorshipVersion        The sponsorship version.
   */
  public ProgrammeMembershipService(HistoryService historyService, InAppService inAppService,
      NotificationService notificationService, @Value("${application.timezone}") ZoneId timezone,
      @Value("${application.template-versions.deferral.in-app}") String deferralVersion,
      @Value("${application.template-versions.e-portfolio.in-app}") String eportfolioVersion,
      @Value("${application.template-versions.indemnity-insurance.in-app}")
      String indemnityInsuranceVersion,
      @Value("${application.template-versions.less-than-full-time.in-app}") String ltftVersion,
      @Value("${application.template-versions.sponsorship.in-app}") String sponsorshipVersion) {
    this.historyService = historyService;
    this.inAppService = inAppService;
    this.notificationService = notificationService;
    this.timezone = timezone;
    this.deferralVersion = deferralVersion;
    this.eportfolioVersion = eportfolioVersion;
    this.indemnityInsuranceVersion = indemnityInsuranceVersion;
    this.ltftVersion = ltftVersion;
    this.sponsorshipVersion = sponsorshipVersion;
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
    LocalDate startDate = programmeMembership.getStartDate();
    if (startDate == null || startDate.isBefore(LocalDate.now(timezone))) {
      return true;
    }

    List<Curriculum> curricula = programmeMembership.getCurricula();
    if (curricula == null) {
      return true;
    }

    boolean hasMedicalSubType = curricula.stream()
        .filter(c -> c.curriculumSubType() != null)
        .map(c -> c.curriculumSubType().toUpperCase())
        .anyMatch(INCLUDE_CURRICULUM_SUBTYPES::contains);

    boolean hasExcludedSpecialty = curricula.stream()
        .filter(c -> c.curriculumSpecialty() != null)
        .map(c -> c.curriculumSpecialty().toUpperCase())
        .anyMatch(EXCLUDE_CURRICULUM_SPECIALTIES::contains);

    boolean hasSomeNonNullSpecialties = curricula.stream()
        .anyMatch(c -> c.curriculumSpecialty() != null);

    boolean excludedSpecialty = hasExcludedSpecialty || !hasSomeNonNullSpecialties;

    return !hasMedicalSubType || excludedSpecialty;
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

    Set<NotificationType> notificationTypes = new HashSet<>(
        NotificationType.getProgrammeUpdateNotificationTypes());
    notificationTypes.add(DEFERRAL);
    notificationTypes.add(E_PORTFOLIO);
    notificationTypes.add(INDEMNITY_INSURANCE);
    notificationTypes.add(LTFT);
    notificationTypes.add(SPONSORSHIP);

    for (NotificationType milestone : notificationTypes) {
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

      createDirectNotifications(programmeMembership, notificationsAlreadySent);
      createInAppNotifications(programmeMembership, notificationsAlreadySent);
    }
  }

  /**
   * Create "direct" notifications, such as email, which may be scheduled for a future date/time.
   *
   * @param programmeMembership      The updated programme membership.
   * @param notificationsAlreadySent Previously sent notifications.
   * @throws SchedulerException if any one of the notification jobs could not be scheduled.
   */
  private void createDirectNotifications(ProgrammeMembership programmeMembership,
      Map<NotificationType, Instant> notificationsAlreadySent) throws SchedulerException {

    NotificationType milestone = PROGRAMME_CREATED; //do not schedule other programme notifications
    boolean shouldSchedule = shouldScheduleNotification(milestone, notificationsAlreadySent);

    if (shouldSchedule) {
      log.info("Scheduling notification {} for {}.", milestone, programmeMembership.getTisId());
      //default to send notification immediately
      Date when = notificationService.getScheduleDate(LocalDate.now(), 1);

      JobDataMap jobDataMap = new JobDataMap();
      jobDataMap.put(TIS_ID_FIELD, programmeMembership.getTisId());
      jobDataMap.put(PERSON_ID_FIELD, programmeMembership.getPersonId());
      jobDataMap.put(PROGRAMME_NAME_FIELD, programmeMembership.getProgrammeName());
      jobDataMap.put(START_DATE_FIELD, programmeMembership.getStartDate());
      jobDataMap.put(TEMPLATE_OWNER_FIELD, programmeMembership.getManagingDeanery());
      jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, milestone);
      if (programmeMembership.getConditionsOfJoining() != null) {
        jobDataMap.put(COJ_SYNCED_FIELD,
            programmeMembership.getConditionsOfJoining().syncedAt());
      }
      // Note the status of the trainee will be retrieved when the job is executed, as will
      // their name and email address and LO contact details, not now.

      String jobId = milestone + "-" + programmeMembership.getTisId();
      try {
        notificationService.scheduleNotification(jobId, jobDataMap, when);
      } catch (SchedulerException e) {
        log.error("Failed to schedule notification {}: {}", jobId, e.toString());
        throw (e); //to allow message to be requeue-ed
      }
    }
  }

  /**
   * Create any relevant in-app notifications.
   *
   * @param programmeMembership      The updated programme membership.
   * @param notificationsAlreadySent Previously sent notifications.
   */
  private void createInAppNotifications(ProgrammeMembership programmeMembership,
      Map<NotificationType, Instant> notificationsAlreadySent) {
    // Create ePortfolio notification if the PM qualifies.
    boolean meetsCriteria = notificationService.meetsCriteria(programmeMembership, true, true);

    if (meetsCriteria) {
      // E_PORTFOLIO
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, E_PORTFOLIO,
          eportfolioVersion, Map.of());

      // INDEMNITY_INSURANCE
      boolean hasBlockIndemnity = programmeMembership.getCurricula().stream()
          .anyMatch(Curriculum::curriculumSpecialtyBlockIndemnity);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent,
          INDEMNITY_INSURANCE, indemnityInsuranceVersion,
          Map.of(BLOCK_INDEMNITY_FIELD, hasBlockIndemnity));

      String owner = programmeMembership.getManagingDeanery();
      List<Map<String, String>> contactList = notificationService.getOwnerContactList(owner);

      // LTFT
      String localOfficeContactLtft = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.LTFT, LocalOfficeContactType.TSS_SUPPORT, "");
      String localOfficeContactTypeLtft =
          notificationService.getHrefTypeForContact(localOfficeContactLtft);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, LTFT,
          ltftVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContactLtft,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactTypeLtft));

      // DEFERRAL
      String localOfficeContactDeferral = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.DEFERRAL, LocalOfficeContactType.TSS_SUPPORT, "");
      String localOfficeContactTypeDeferral =
          notificationService.getHrefTypeForContact(localOfficeContactDeferral);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, DEFERRAL,
          deferralVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContactDeferral,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactTypeDeferral));

      // SPONSORSHIP
      String localOfficeContactSponsorship = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.SPONSORSHIP, LocalOfficeContactType.TSS_SUPPORT, "");
      String localOfficeContactTypeSponsorship =
          notificationService.getHrefTypeForContact(localOfficeContactSponsorship);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, SPONSORSHIP,
          sponsorshipVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContactSponsorship,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactTypeSponsorship));
    }
  }

  /**
   * Create a unique in-app notification of the given type and version.
   *
   * @param programmeMembership      The updated programme membership.
   * @param notificationsAlreadySent Previously sent notifications.
   * @param notificationType         The type of notification being sent.
   * @param notificationVersion      The version of the notification.
   * @param extraVariables           Extra variables to include with the template, Programme Name
   *                                 and Start Date are populated automatically.
   */
  private void createUniqueInAppNotification(ProgrammeMembership programmeMembership,
      Map<NotificationType, Instant> notificationsAlreadySent, NotificationType notificationType,
      String notificationVersion, Map<String, Object> extraVariables) {
    boolean isUnique = !notificationsAlreadySent.containsKey(notificationType);

    if (isUnique) {
      TisReferenceInfo tisReference = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
          programmeMembership.getTisId());

      Map<String, Object> variables = new HashMap<>(extraVariables);
      variables.put(PROGRAMME_NAME_FIELD, programmeMembership.getProgrammeName());
      variables.put(START_DATE_FIELD, programmeMembership.getStartDate());

      boolean doNotSendJustLog = !notificationService.programmeMembershipIsNotifiable(
          programmeMembership, IN_APP);

      inAppService.createNotifications(programmeMembership.getPersonId(), tisReference,
          notificationType, notificationVersion, variables, doNotSendJustLog);
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
      notificationService.removeNotification(jobId); //remove existing notification if it exists
    }
  }

  /**
   * Helper function to determine whether a notification should be scheduled.
   *
   * @param milestone                The milestone to consider.
   * @param notificationsAlreadySent The notifications already sent for this entity.
   * @return true if it should be scheduled, false otherwise.
   */
  private boolean shouldScheduleNotification(NotificationType milestone,
      Map<NotificationType, Instant> notificationsAlreadySent) {

    //do not resend any notification
    if (notificationsAlreadySent.containsKey(milestone)) {
      return false;
    }

    return milestone == PROGRAMME_CREATED; //immediately notify of a new programme membership
  }
}
