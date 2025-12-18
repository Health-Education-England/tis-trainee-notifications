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
import static uk.nhs.tis.trainee.notifications.model.NotificationType.SPONSORSHIP;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.ONE_DAY_IN_SECONDS;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History;
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
  public static final String PERSON_ID_FIELD = "personId";
  public static final String CCT_DATE_FIELD = "cctDate";
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
  public static final Integer POG_12MONTH_NOTIFICATION_CUTOFF_MONTHS = 6;
  public static final Integer POG_ALL_NOTIFICATION_CUTOFF_WEEKS = 16;

  public static final List<String> INCLUDE_CURRICULUM_SUBTYPES
      = List.of("MEDICAL_CURRICULUM", "MEDICAL_SPR");
  public static final List<String> EXCLUDE_CURRICULUM_SPECIALTIES
      = List.of("PUBLIC HEALTH MEDICINE", "FOUNDATION");

  private final HistoryService historyService;
  private final InAppService inAppService;
  private final NotificationService notificationService;
  private final ProgrammeMembershipUtils pmUtils;

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
   * @param pmUtils                   The programme membership utilities to use.
   * @param dayOneVersion             The day one version.
   * @param deferralVersion           The deferral version.
   * @param eportfolioVersion         The ePortfolio version.
   * @param indemnityInsuranceVersion The indemnity insurance version.
   * @param ltftVersion               The LTFT version.
   * @param sponsorshipVersion        The sponsorship version.
   */
  public ProgrammeMembershipService(HistoryService historyService, InAppService inAppService,
      NotificationService notificationService, ProgrammeMembershipUtils pmUtils,
      @Value("${application.timezone}") ZoneId timezone,
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
    this.pmUtils = pmUtils;
    this.timezone = timezone;
    this.dayOneVersion = dayOneVersion;
    this.deferralVersion = deferralVersion;
    this.eportfolioVersion = eportfolioVersion;
    this.indemnityInsuranceVersion = indemnityInsuranceVersion;
    this.ltftVersion = ltftVersion;
    this.sponsorshipVersion = sponsorshipVersion;
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
    notificationTypes.addAll(NotificationType.getProgrammePogNotificationTypes());
    notificationTypes.addAll(NotificationType.getProgrammeInAppNotificationTypes());

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
   */
  public void addNotifications(ProgrammeMembership programmeMembership) {
    //first delete any stale notifications
    deleteScheduledNotificationsFromDb(programmeMembership);

    boolean isExcluded = pmUtils.isExcluded(programmeMembership);
    log.info("Programme membership {}: excluded {}.", programmeMembership.getTisId(), isExcluded);

    Map<NotificationType, History> notificationsAlreadySent = null;

    if (!isExcluded) {
      notificationsAlreadySent = getLatestNotificationsSent(programmeMembership.getPersonId(),
          programmeMembership.getTisId());
      createDirectProgrammeNotifications(programmeMembership, notificationsAlreadySent);
      createInAppNotifications(programmeMembership, notificationsAlreadySent);
    }

    boolean isExcludedPog = pmUtils.isExcludedPog(programmeMembership);
    log.info("Programme membership {}: excluded POG {}.", programmeMembership.getTisId(),
        isExcludedPog);
    if (!isExcludedPog) {
      if (notificationsAlreadySent == null) {
        //getLatestNotificationsSent is expensive, so only call it if we need to
        notificationsAlreadySent = getLatestNotificationsSent(programmeMembership.getPersonId(),
            programmeMembership.getTisId());
      }
      createDirectProgrammePogNotifications(programmeMembership, notificationsAlreadySent);
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
      Map<NotificationType, History> notificationsAlreadySent) {

    // Note the status of the trainee will be retrieved when the job is executed, as will
    // their name and email address and LO contact details.
    Map<String, Object> jobDataMap = new HashMap<>();
    pmUtils.addStandardProgrammeDetailsToJobMap(jobDataMap, programmeMembership);

    for (NotificationType notificationType
        : NotificationType.getActiveProgrammeUpdateNotificationTypes()) {

      boolean shouldSchedule = pmUtils.shouldScheduleNotification(
          notificationType, programmeMembership, notificationsAlreadySent);
      if (shouldSchedule) {
        log.info("Processing notification {} for {}.", notificationType,
            programmeMembership.getTisId());

        jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);

        doScheduleProgrammeNotification(notificationType, programmeMembership, jobDataMap,
            notificationsAlreadySent);
      }
    }
  }

  private void doScheduleProgrammeNotification(NotificationType notificationType,
      ProgrammeMembership programmeMembership, Map<String, Object> jobDataMap,
      Map<NotificationType, History> notificationsAlreadySent) {
    String jobId = notificationType + "-" + jobDataMap.get(TIS_ID_FIELD);
    Date scheduleWhen = pmUtils.whenScheduleProgrammeNotification(notificationType,
        programmeMembership, notificationsAlreadySent);
    if (scheduleWhen == null) {
      notificationService.executeNow(jobId, jobDataMap);
    } else {
      notificationService.scheduleNotification(jobId, jobDataMap, scheduleWhen, ONE_DAY_IN_SECONDS);
    }
  }

  /**
   * Create "direct" programme notifications for POG, such as email, which may be scheduled for a
   * future date/time.
   *
   * @param programmeMembership      The updated programme membership.
   * @param notificationsAlreadySent Previously sent notifications.
   */
  private void createDirectProgrammePogNotifications(ProgrammeMembership programmeMembership,
      Map<NotificationType, History> notificationsAlreadySent) {
    // Note the status of the trainee will be retrieved when the job is executed, as will
    // their name and email address and LO contact details.
    Map<String, Object> jobDataMap = new HashMap<>();
    pmUtils.addStandardProgrammeDetailsToJobMap(jobDataMap, programmeMembership);

    for (NotificationType notificationType
        : NotificationType.getProgrammePogNotificationTypes()) {

      boolean shouldSchedule = pmUtils.shouldSchedulePogNotification(
          notificationType, programmeMembership, notificationsAlreadySent);
      if (shouldSchedule) {
        log.info("Processing POG notification {} for {}.", notificationType,
            programmeMembership.getTisId());

        jobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, notificationType);

        doScheduleProgrammePogNotification(notificationType, programmeMembership, jobDataMap);
      } else {
        log.info("Not scheduling POG notification {} for {}.", notificationType,
            programmeMembership.getTisId());
      }
    }
  }

  /**
   * Schedule a programme POG notification.
   *
   * @param notificationType         The type of POG notification to schedule.
   * @param programmeMembership      The programme membership.
   * @param jobDataMap               The job data map.
   */
  private void doScheduleProgrammePogNotification(NotificationType notificationType,
      ProgrammeMembership programmeMembership, Map<String, Object> jobDataMap) {
    String jobId = notificationType + "-" + jobDataMap.get(TIS_ID_FIELD);
    Date scheduleWhen = pmUtils.whenScheduleProgrammePogNotification(notificationType,
        programmeMembership);
    if (scheduleWhen == null) {
      notificationService.executeNow(jobId, jobDataMap);
    } else {
      notificationService.scheduleNotification(jobId, jobDataMap, scheduleWhen, ONE_DAY_IN_SECONDS);
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
    variables.put(RO_NAME_FIELD,
        pmUtils.getRoName(programmeMembership.getResponsibleOfficer()));
    variables.put(DESIGNATED_BODY_FIELD, programmeMembership.getDesignatedBody());

    History.TisReferenceInfo tisReference = new History.TisReferenceInfo(
        TisReferenceType.PROGRAMME_MEMBERSHIP, programmeMembership.getTisId());
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
      boolean shouldSchedule = pmUtils.shouldScheduleNotification(
          notificationType, programmeMembership, notificationsAlreadySent);
      if (shouldSchedule) {
        Date scheduleWhen = pmUtils.whenScheduleDeferrableNotification(notificationType,
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
   * Remove scheduled notifications for a programme membership from DB.
   *
   * @param programmeMembership The programmeMembership.
   */
  public void deleteScheduledNotificationsFromDb(ProgrammeMembership programmeMembership) {

    List<History> scheduledHistories = historyService
        .findAllScheduledForTrainee(programmeMembership.getPersonId(), PROGRAMME_MEMBERSHIP,
            programmeMembership.getTisId());

    for (History history : scheduledHistories) {
      log.info("Deleting scheduled programme membership notification {} (person {}, PM {}).",
          history.id(), programmeMembership.getPersonId(), programmeMembership.getTisId());
      historyService.deleteHistoryForTrainee(history.id(), programmeMembership.getPersonId());
    }
  }
}
