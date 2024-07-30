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
import static uk.nhs.tis.trainee.notifications.model.NotificationType.DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.DEFERRAL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.E_PORTFOLIO;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.INDEMNITY_INSURANCE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.SPONSORSHIP;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
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
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for Programme memberships.
 */

@Slf4j
@Service
public class ProgrammeMembershipService {

  public static final String TIS_ID_FIELD = "tisId";
  public static final String PROGRAMME_NAME_FIELD = "programmeName";
  public static final String PROGRAMME_NUMBER_FIELD = "programmeNumber";
  public static final String START_DATE_FIELD = "startDate";
  public static final String BLOCK_INDEMNITY_FIELD = "hasBlockIndemnity";
  public static final String LOCAL_OFFICE_CONTACT_FIELD = "localOfficeContact";
  public static final String LOCAL_OFFICE_CONTACT_TYPE_FIELD = "localOfficeContactType";
  public static final String COJ_SYNCED_FIELD = "conditionsOfJoiningSyncedAt";
  public static final String GMC_NUMBER_FIELD = "gmcNumber";
  public static final String RO_NAME_FIELD = "roName";
  public static final String DESIGNATED_BODY_FIELD = "designatedBody";
  public static final Integer DEFERRAL_IF_MORE_THAN_DAYS = 89;

  private static final List<String> INCLUDE_CURRICULUM_SUBTYPES
      = List.of("MEDICAL_CURRICULUM", "MEDICAL_SPR");
  private static final List<String> EXCLUDE_CURRICULUM_SPECIALTIES
      = List.of("PUBLIC HEALTH MEDICINE", "FOUNDATION");

  private final HistoryService historyService;
  private final InAppService inAppService;
  private final NotificationService notificationService;

  private final ZoneId timezone;

  private final String dayOneVersion;
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
   * @param dayOneVersion             The day one version.
   * @param deferralVersion           The deferral version.
   * @param eportfolioVersion         The ePortfolio version.
   * @param indemnityInsuranceVersion The indemnity insurance version.
   * @param ltftVersion               The LTFT version.
   * @param sponsorshipVersion        The sponsorship version.
   */
  public ProgrammeMembershipService(HistoryService historyService, InAppService inAppService,
      NotificationService notificationService, @Value("${application.timezone}") ZoneId timezone,
      @Value("${application.template-versions.day-one.in-app}") String dayOneVersion,
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
    this.dayOneVersion = dayOneVersion;
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
        .map(c -> c.curriculumSpecialty().toUpperCase())
        .anyMatch(EXCLUDE_CURRICULUM_SPECIALTIES::contains);

    return !hasMedicalSubType || hasExcludedSpecialty;
  }

  /**
   * Get a map of notification types and the most recent one that was sent for a given trainee and
   * programme membership from the notification history.
   *
   * @param traineeId             The trainee TIS ID.
   * @param programmeMembershipId The programme membership TIS ID.
   * @return The map of notification types and the notification item most recently sent.
   */
  private Map<NotificationType, History> getLatestNotificationsSent(String traineeId,
      String programmeMembershipId) {
    EnumMap<NotificationType, History> notifications = new EnumMap<>(NotificationType.class);
    List<History> correspondence = historyService.findAllHistoryForTrainee(traineeId);

    Set<NotificationType> notificationTypes = new HashSet<>(
        NotificationType.getProgrammeUpdateNotificationTypes());
    notificationTypes.add(DEFERRAL);
    notificationTypes.add(E_PORTFOLIO);
    notificationTypes.add(INDEMNITY_INSURANCE);
    notificationTypes.add(LTFT);
    notificationTypes.add(SPONSORSHIP);
    notificationTypes.add(DAY_ONE);
    notificationTypes.add(PROGRAMME_DAY_ONE);


    for (NotificationType milestone : notificationTypes) {
      Optional<History> sentItem = correspondence.stream()
          .filter(c -> c.tisReference() != null)
          .filter(c ->
              c.tisReference().type().equals(TisReferenceType.PROGRAMME_MEMBERSHIP)
                  && c.type().equals(milestone)
                  && c.tisReference().id().equals(programmeMembershipId))
          .max(Comparator.comparing(History::sentAt)); //get most recent sent
      sentItem.ifPresent(history -> notifications.put(milestone, history));
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

    //first delete any stale notifications
    deleteNotifications(programmeMembership);
    deleteScheduledInAppNotifications(programmeMembership);

    boolean isExcluded = isExcluded(programmeMembership);
    log.info("Programme membership {}: excluded {}.", programmeMembership.getTisId(), isExcluded);

    if (!isExcluded) {
      Map<NotificationType, History> notificationsAlreadySent
          = getLatestNotificationsSent(programmeMembership.getPersonId(),
          programmeMembership.getTisId());

      createDirectProgrammeNotifications(programmeMembership, notificationsAlreadySent);
      createInAppNotifications(programmeMembership, notificationsAlreadySent);
    }
  }

  /**
   * Create "direct" programme notifications, such as email, which may be scheduled for a future
   * date/time.
   *
   * @param programmeMembership      The updated programme membership.
   * @param notificationsAlreadySent Previously sent notifications.
   */
  private void createDirectProgrammeNotifications(ProgrammeMembership programmeMembership,
      Map<NotificationType, History> notificationsAlreadySent) throws SchedulerException {

    LocalDate startDate = programmeMembership.getStartDate();

    // Note the status of the trainee will be retrieved when the job is executed, as will
    // their name and email address and LO contact details.

    // PROGRAMME_CREATED
    boolean shouldScheduleProgrammeCreated = shouldScheduleNotification(PROGRAMME_CREATED,
        programmeMembership, notificationsAlreadySent);
    if (shouldScheduleProgrammeCreated) {
      log.info("Processing notification {} for {}.", PROGRAMME_CREATED,
          programmeMembership.getTisId());

      JobDataMap pmCreatedJobDataMap = new JobDataMap();
      pmCreatedJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_CREATED);
      pmCreatedJobDataMap.put(TIS_ID_FIELD, programmeMembership.getTisId());
      pmCreatedJobDataMap.put(PERSON_ID_FIELD, programmeMembership.getPersonId());
      pmCreatedJobDataMap.put(PROGRAMME_NAME_FIELD, programmeMembership.getProgrammeName());
      pmCreatedJobDataMap.put(PROGRAMME_NUMBER_FIELD, programmeMembership.getProgrammeNumber());
      pmCreatedJobDataMap.put(START_DATE_FIELD, startDate);
      pmCreatedJobDataMap.put(TEMPLATE_OWNER_FIELD, programmeMembership.getManagingDeanery());
      if (programmeMembership.getConditionsOfJoining() != null) {
        pmCreatedJobDataMap.put(COJ_SYNCED_FIELD,
            programmeMembership.getConditionsOfJoining().syncedAt());
      }

      String jobId = PROGRAMME_CREATED + "-" + programmeMembership.getTisId();
      Date scheduleWhen = whenScheduleDeferredNotification(PROGRAMME_CREATED, programmeMembership,
          notificationsAlreadySent);
      if (scheduleWhen == null) {
        notificationService.executeNow(jobId, pmCreatedJobDataMap);
      } else {
        notificationService.scheduleNotification(jobId, pmCreatedJobDataMap, scheduleWhen);
      }
    }

    // PROGRAMME_DAY_ONE
    boolean shouldScheduleProgrammeDayOne = shouldScheduleNotification(PROGRAMME_DAY_ONE,
        programmeMembership, notificationsAlreadySent);
    if (shouldScheduleProgrammeDayOne) {
      log.info("Processing notification {} for {}.", PROGRAMME_DAY_ONE,
          programmeMembership.getTisId());

      JobDataMap pmDayOneJobDataMap = new JobDataMap();
      pmDayOneJobDataMap.put(TIS_ID_FIELD, programmeMembership.getTisId());
      pmDayOneJobDataMap.put(PERSON_ID_FIELD, programmeMembership.getPersonId());
      pmDayOneJobDataMap.put(PROGRAMME_NAME_FIELD, programmeMembership.getProgrammeName());
      pmDayOneJobDataMap.put(PROGRAMME_NUMBER_FIELD, programmeMembership.getProgrammeNumber());
      pmDayOneJobDataMap.put(START_DATE_FIELD, startDate);
      pmDayOneJobDataMap.put(TEMPLATE_OWNER_FIELD, programmeMembership.getManagingDeanery());
      pmDayOneJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, PROGRAMME_DAY_ONE);
      pmDayOneJobDataMap.put(RO_NAME_FIELD, getRoName(programmeMembership.getResponsibleOfficer()));
      pmDayOneJobDataMap.put(DESIGNATED_BODY_FIELD, programmeMembership.getDesignatedBody());

      String jobId = PROGRAMME_DAY_ONE + "-" + programmeMembership.getTisId();
      if (startDate.isBefore(LocalDate.now(timezone).plusDays(1))) {
        notificationService.executeNow(jobId, pmDayOneJobDataMap);
      } else {
        notificationService.scheduleNotification(jobId, pmDayOneJobDataMap,
            Date.from(startDate.atStartOfDay(timezone).toInstant()));
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
      Map<NotificationType, History> notificationsAlreadySent) {
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

      UserDetails userTraineeDetails = notificationService.getTraineeDetails(
          programmeMembership.getPersonId());
      String gmcNumber = (userTraineeDetails != null && userTraineeDetails.gmcNumber() != null)
          ? userTraineeDetails.gmcNumber().trim() : "unknown";

      // LTFT
      String localOfficeContactLtft = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.LTFT, LocalOfficeContactType.TSS_SUPPORT, "");
      String localOfficeContactTypeLtft =
          notificationService.getHrefTypeForContact(localOfficeContactLtft);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, LTFT,
          ltftVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContactLtft,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactTypeLtft,
              GMC_NUMBER_FIELD, gmcNumber));

      // DEFERRAL
      String localOfficeContactDeferral = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.DEFERRAL, LocalOfficeContactType.TSS_SUPPORT, "");
      String localOfficeContactTypeDeferral =
          notificationService.getHrefTypeForContact(localOfficeContactDeferral);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, DEFERRAL,
          deferralVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContactDeferral,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactTypeDeferral,
              GMC_NUMBER_FIELD, gmcNumber));

      // SPONSORSHIP
      String localOfficeContactSponsorship = notificationService.getOwnerContact(contactList,
          LocalOfficeContactType.SPONSORSHIP, LocalOfficeContactType.TSS_SUPPORT, "");
      String localOfficeContactTypeSponsorship =
          notificationService.getHrefTypeForContact(localOfficeContactSponsorship);
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, SPONSORSHIP,
          sponsorshipVersion, Map.of(
              LOCAL_OFFICE_CONTACT_FIELD, localOfficeContactSponsorship,
              LOCAL_OFFICE_CONTACT_TYPE_FIELD, localOfficeContactTypeSponsorship,
              GMC_NUMBER_FIELD, gmcNumber));

      // DAY_ONE
      createUniqueInAppNotification(programmeMembership, notificationsAlreadySent, DAY_ONE,
          dayOneVersion, Map.of());
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
      Map<NotificationType, History> notificationsAlreadySent, NotificationType notificationType,
      String notificationVersion, Map<String, Object> extraVariables) {

    Map<String, Object> variables = new HashMap<>(extraVariables);
    variables.put(PROGRAMME_NAME_FIELD, programmeMembership.getProgrammeName());
    variables.put(PROGRAMME_NUMBER_FIELD, programmeMembership.getProgrammeNumber());
    variables.put(START_DATE_FIELD, programmeMembership.getStartDate());
    variables.put(RO_NAME_FIELD, getRoName(programmeMembership.getResponsibleOfficer()));
    variables.put(DESIGNATED_BODY_FIELD, programmeMembership.getDesignatedBody());

    TisReferenceInfo tisReference = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        programmeMembership.getTisId());
    boolean doNotSendJustLog = !notificationService.programmeMembershipIsNotifiable(
        programmeMembership, IN_APP);

    boolean isUnique = !notificationsAlreadySent.containsKey(notificationType);
    if (isUnique) {
      // send on programme start day for future Day One notification
      if (notificationType.equals(DAY_ONE)) {
        inAppService.createNotifications(programmeMembership.getPersonId(), tisReference,
            notificationType, notificationVersion, variables, doNotSendJustLog,
            programmeMembership.getStartDate().atStartOfDay(timezone).toInstant());
      } else {
        inAppService.createNotifications(programmeMembership.getPersonId(), tisReference,
            notificationType, notificationVersion, variables, doNotSendJustLog);
      }
    } else {
      boolean shouldSchedule = shouldScheduleNotification(notificationType, programmeMembership,
          notificationsAlreadySent);
      if (shouldSchedule) {
        Date scheduleWhen = whenScheduleDeferredNotification(notificationType,
            programmeMembership, notificationsAlreadySent);
        if (scheduleWhen == null) {
          inAppService.createNotifications(programmeMembership.getPersonId(), tisReference,
              notificationType, notificationVersion, variables, doNotSendJustLog);
        } else {
          inAppService.createNotifications(programmeMembership.getPersonId(), tisReference,
              notificationType, notificationVersion, variables, doNotSendJustLog,
              scheduleWhen.toInstant());
        }
      }
    }
  }

  /**
   * Remove notifications for a programme membership from scheduler.
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
   * Remove scheduled in-app notifications for a programme membership.
   *
   * @param programmeMembership The programmeMembership.
   */
  protected void deleteScheduledInAppNotifications(ProgrammeMembership programmeMembership) {

    List<History> scheduledHistories = historyService
        .findAllScheduledInAppForTrainee(programmeMembership.getPersonId(), PROGRAMME_MEMBERSHIP,
            programmeMembership.getTisId());

    for (History history : scheduledHistories) {
      historyService.deleteHistoryForTrainee(history.id(), programmeMembership.getPersonId());
    }
  }

  /**
   * Helper function to determine whether a notification should be scheduled.
   *
   * @param notificationType         The notification type.
   * @param programmeMembership      The updated programme membership to consider.
   * @param notificationsAlreadySent The notifications already sent for this entity.
   * @return true if it should be scheduled, false otherwise.
   */
  protected boolean shouldScheduleNotification(NotificationType notificationType,
      ProgrammeMembership programmeMembership,
      Map<NotificationType, History> notificationsAlreadySent) {

    //only resend deferred notifications
    if (notificationsAlreadySent.containsKey(notificationType)) {
      History lastSent = notificationsAlreadySent.get(notificationType);
      LocalDate oldStartDate = getProgrammeStartDate(lastSent);
      boolean isDeferral = oldStartDate != null
          && programmeMembership.getStartDate() != null
          && oldStartDate.plusDays(DEFERRAL_IF_MORE_THAN_DAYS)
          .isBefore(programmeMembership.getStartDate());
      log.info("Programme membership {} is deferral: {} (old start date {}, new start date {})",
          programmeMembership.getTisId(), isDeferral, oldStartDate,
          programmeMembership.getStartDate());
      return isDeferral;
    }

    return true; //send new notifications
  }

  /**
   * Helper function to determine when a deferred notification should be scheduled.
   *
   * @param notificationType         The notification type.
   * @param programmeMembership      The updated programme membership to consider.
   * @param notificationsAlreadySent The notifications already sent for this entity.
   * @return the date it should be scheduled, or null if it should be sent immediately.
   */
  private Date whenScheduleDeferredNotification(NotificationType notificationType,
      ProgrammeMembership programmeMembership,
      Map<NotificationType, History> notificationsAlreadySent) {

    //schedule deferred notifications with the same lead time as the original notification
    if (notificationsAlreadySent.containsKey(notificationType)) {
      History lastSent = notificationsAlreadySent.get(notificationType);
      LocalDate oldStartDate = getProgrammeStartDate(lastSent);
      LocalDate newStartDate = programmeMembership.getStartDate();
      if (lastSent.sentAt() != null && oldStartDate != null) {
        LocalDateTime oldSentDateTime = lastSent.sentAt().atZone(timezone).toLocalDateTime();
        long leadDays = Duration.between(oldSentDateTime, oldStartDate.atStartOfDay()).toDays();
        LocalDate newSend = newStartDate.minusDays(leadDays);
        if (newSend.isAfter(LocalDate.now())) {
          return Date.from(newSend.atStartOfDay(timezone).toInstant());
        }
      }
      return null; //send immediately if newSend is not in the future, or any data missing
    }

    return null; //send new notification immediately
  }

  /**
   * Get the programme start date from a saved history item.
   *
   * @param history The history to inspect.
   * @return The programme start date, or null if it is missing or unparseable.
   */
  private LocalDate getProgrammeStartDate(History history) {
    if (history.template() != null
        && history.template().variables() != null
        && history.template().variables().get(START_DATE_FIELD) != null) {
      //to be deferrable, a programme-related notification must include the START_DATE_FIELD
      try {
        return (LocalDate) history.template().variables().get(START_DATE_FIELD);
      } catch (Exception e) {
        log.error("Error: unparseable startDate in history (should be a LocalDate): '{}'",
            history.template().variables().get(START_DATE_FIELD));
      }
    }
    return null;
  }

  /**
   * Get the RO name from RO first name and last name.
   *
   * @param responsibleOfficer The details of the responsible officer.
   * @return The ro name to be shown in notifications, or empty string if it is missing.
   */
  private String getRoName(ResponsibleOfficer responsibleOfficer) {

    if (responsibleOfficer != null) {
      return ((responsibleOfficer.firstName() == null ? "" : responsibleOfficer.firstName())
          + " " + (responsibleOfficer.lastName() == null ? "" : responsibleOfficer.lastName())
      ).trim();
    }
    return "";
  }
}
