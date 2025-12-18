/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.CCT_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.COJ_SYNCED_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DEFERRAL_IF_MORE_THAN_DAYS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DESIGNATED_BODY_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.EXCLUDE_CURRICULUM_SPECIALTIES;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.INCLUDE_CURRICULUM_SUBTYPES;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.POG_12MONTH_NOTIFICATION_CUTOFF_MONTHS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.POG_ALL_NOTIFICATION_CUTOFF_WEEKS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.RO_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;

/**
 * Utility/helper methods for ProgrammeMembershipService.
 */
@Slf4j
@Component
public class ProgrammeMembershipUtils {

  private final ZoneId timezone;

  /**
   * Construct a ProgrammeMembershipUtils.
   *
   * @param timezone the application timezone.
   */
  ProgrammeMembershipUtils(@Value("${application.timezone}") ZoneId timezone) {
    this.timezone = timezone;
  }

  /**
   * Add standard programme details to a notification job data map.
   *
   * @param jobDataMap          the job data map.
   * @param programmeMembership the programme membership to use.
   */
  public void addStandardProgrammeDetailsToJobMap(Map<String, Object> jobDataMap,
      ProgrammeMembership programmeMembership) {
    jobDataMap.put(TIS_ID_FIELD, programmeMembership.getTisId());
    jobDataMap.put(PERSON_ID_FIELD, programmeMembership.getPersonId());
    jobDataMap.put(PROGRAMME_NAME_FIELD, programmeMembership.getProgrammeName());
    jobDataMap.put(PROGRAMME_NUMBER_FIELD, programmeMembership.getProgrammeNumber());
    jobDataMap.put(START_DATE_FIELD, programmeMembership.getStartDate());
    jobDataMap.put(TEMPLATE_OWNER_FIELD, programmeMembership.getManagingDeanery());
    if (programmeMembership.getConditionsOfJoining() != null) {
      jobDataMap.put(COJ_SYNCED_FIELD, programmeMembership.getConditionsOfJoining().syncedAt());
    }
    jobDataMap.put(RO_NAME_FIELD, getRoName(programmeMembership.getResponsibleOfficer()));
    jobDataMap.put(DESIGNATED_BODY_FIELD, programmeMembership.getDesignatedBody());
    jobDataMap.put(CCT_DATE_FIELD, getProgrammeCctDate(programmeMembership));
  }

  /**
   * Get the Responsible Officer's name.
   *
   * @param responsibleOfficer the Responsible Officer.
   * @return the formatted Responsible Officer's name.
   */
  public String getRoName(ResponsibleOfficer responsibleOfficer) {
    if (responsibleOfficer != null) {
      return ((responsibleOfficer.firstName() == null ? "" : responsibleOfficer.firstName())
          + " " + (responsibleOfficer.lastName() == null ? "" : responsibleOfficer.lastName())
      ).trim();
    }
    return "";
  }

  /**
   * Get the number of days before the programme start date for a given notification type.
   *
   * @param notificationType the notification type.
   * @return the number of days before the start date.
   */
  public Integer getDaysBeforeStartForNotification(NotificationType notificationType) {
    return switch (notificationType) {
      case PROGRAMME_DAY_ONE -> 0; // Day One is sent on the start date
      case PROGRAMME_UPDATED_WEEK_12 -> 84;
      case PROGRAMME_UPDATED_WEEK_8 -> 56;
      case PROGRAMME_UPDATED_WEEK_4 -> 28;
      case PROGRAMME_UPDATED_WEEK_2 -> 14;
      case PROGRAMME_UPDATED_WEEK_1 -> 7;
      case PROGRAMME_UPDATED_WEEK_0 -> 0;
      default -> null; //not applicable
    };
  }

  /**
   * Get the number of days before the programme end date for a given notification type.
   *
   * @param notificationType the notification type.
   * @return the number of days before the end date.
   */
  public Integer getDaysBeforeEndForNotification(NotificationType notificationType) {
    if (notificationType == NotificationType.PROGRAMME_POG_MONTH_12) {
      return 365;
    }
    return null;
  }

  /**
   * Determine when a deferrable notification should be scheduled.
   *
   * @param notificationType         the notification type.
   * @param programmeMembership      the programme membership to consider.
   * @param notificationsAlreadySent the notifications already sent for this entity.
   * @return the date when the notification should be scheduled, or null if it should be sent
   *     immediately.
   */
  public Date whenScheduleDeferrableNotification(NotificationType notificationType,
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
   * Get the Programme membership's start date from a saved history item.
   *
   * @param history The history to inspect.
   * @return The start date, or null if it is missing or unparseable.
   */
  public LocalDate getProgrammeStartDate(History history) {
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
   * Get the Programme membership's CCT date from a History record.
   *
   * @param history the History record.
   * @return The CCT date, or null if not available.
   */
  public LocalDate getProgrammeCctDate(History history) {
    if (history.template() != null
        && history.template().variables() != null
        && history.template().variables().get(CCT_DATE_FIELD) != null) {
      try {
        return (LocalDate) history.template().variables().get(CCT_DATE_FIELD);
      } catch (Exception e) {
        log.error("Error: unparseable CCT Date in history (should be a LocalDate): '{}'",
            history.template().variables().get(CCT_DATE_FIELD));
      }
    }
    return null;
  }

  /**
   * Get the Programme membership's CCT date, based on the curricula end dates.
   *
   * @param programmeMembership the Programme membership.
   * @return The CCT date, or null if not available.
   */
  public LocalDate getProgrammeCctDate(ProgrammeMembership programmeMembership) {
    return programmeMembership.getCurricula().stream()
        .filter(c -> c.curriculumEligibleForPeriodOfGrace() != null
            && c.curriculumEligibleForPeriodOfGrace())
        .map(Curriculum::curriculumEndDate)
        .filter(Objects::nonNull)
        .max(LocalDate::compareTo)
        .orElse(null);
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
   * Determines whether a programme membership is excluded from POG notifications, either because
   * it has no CCT date, or the CCT date is within the POG notification cutoff period.
   *
   * @param programmeMembership the Programme membership.
   * @return true if the programme membership is excluded from POG notifications.
   */
  public boolean isExcludedPog(ProgrammeMembership programmeMembership) {
    LocalDate cctDate = getProgrammeCctDate(programmeMembership);
    return cctDate == null
        || cctDate.isBefore(LocalDate.now(timezone).plusWeeks(POG_ALL_NOTIFICATION_CUTOFF_WEEKS));
  }

  /**
   * Determine when a programme notification should be scheduled.
   *
   * @param notificationType         The type of notification to schedule.
   * @param programmeMembership      The programme membership to consider.
   * @param notificationsAlreadySent The notifications already sent for this entity.
   * @return The date when the notification should be scheduled, or null if it should be sent
   *     immediately.
   */
  public Date whenScheduleProgrammeNotification(NotificationType notificationType,
      ProgrammeMembership programmeMembership,
      Map<NotificationType, History> notificationsAlreadySent) {
    if (notificationType == PROGRAMME_CREATED) {
      return whenScheduleDeferrableNotification(PROGRAMME_CREATED, programmeMembership,
          notificationsAlreadySent);
    } else {
      Integer daysBeforeStart = getDaysBeforeStartForNotification(notificationType);
      if (programmeMembership.getStartDate().minusDays(daysBeforeStart)
          .isBefore(LocalDate.now(timezone).plusDays(1))) {
        // If the deadline for this notification type is today or in the past, send immediately.
        return null;
      }
      // Otherwise, schedule for the deadline.
      return Date.from(programmeMembership.getStartDate().minusDays(daysBeforeStart)
          .atStartOfDay(timezone).toInstant());
    }
  }

  /**
   * Determine when a programme POG notification should be scheduled.
   *
   * @param notificationType    The type of notification to schedule.
   * @param programmeMembership The programme membership to consider.
   * @return The date when the notification should be scheduled, or null if it should be sent
   *     immediately.
   */
  public Date whenScheduleProgrammePogNotification(
      NotificationType notificationType, ProgrammeMembership programmeMembership) {

    Integer daysBeforeEnd = getDaysBeforeEndForNotification(notificationType);
    LocalDate cctDate = getProgrammeCctDate(programmeMembership);
    if (cctDate.minusDays(daysBeforeEnd)
        .isBefore(LocalDate.now(timezone).plusDays(1))) {
      // If the deadline for this notification type is today or in the past, send immediately.
      return null;
    }
    // Otherwise, schedule for the deadline.
    return Date.from(cctDate.minusDays(daysBeforeEnd).atStartOfDay(timezone).toInstant());
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
    //reminder notifications are only sent if the deadline is not past
    if (NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(notificationType)) {
      Integer daysBeforeStart = getDaysBeforeStartForNotification(notificationType);
      LocalDate deadline = programmeMembership.getStartDate().minusDays(daysBeforeStart);
      return !deadline.isBefore(LocalDate.now(timezone)); //deadline is in the past, do not send
    }
    return true; //send new notifications
  }

  /**
   * Helper function to determine whether a POG notification should be scheduled.
   *
   * @param notificationType         The notification type.
   * @param programmeMembership      The updated programme membership to consider.
   * @param notificationsAlreadySent The notifications already sent for this entity.
   * @return true if it should be scheduled, false otherwise.
   */
  protected boolean shouldSchedulePogNotification(NotificationType notificationType,
      ProgrammeMembership programmeMembership,
      Map<NotificationType, History> notificationsAlreadySent) {

    LocalDate cctDate = getProgrammeCctDate(programmeMembership);

    //only resend extension notifications
    if (notificationsAlreadySent.containsKey(notificationType)) {
      History lastSent = notificationsAlreadySent.get(notificationType);
      LocalDate oldCctDate = getProgrammeCctDate(lastSent);
      boolean isExtension = oldCctDate != null
          && cctDate != null
          && oldCctDate.plusDays(DEFERRAL_IF_MORE_THAN_DAYS)
          .isBefore(cctDate);
      log.info("Programme membership {} is extension: {} (old CCT date {}, new CCT date {})",
          programmeMembership.getTisId(), isExtension, oldCctDate, cctDate);
      return isExtension;
    }

    return notificationType != NotificationType.PROGRAMME_POG_MONTH_12
        || !cctDate.isBefore(LocalDate.now(timezone)
        .plusMonths(POG_12MONTH_NOTIFICATION_CUTOFF_MONTHS));
    // if less than 6 months we'll send the 6-month notification, so don't send 12-month one,
    // otherwise send it.
    // We will add logic to not schedule the 6-month POG notification if less than 12 weeks to CCT
    // when we add that type.
  }
}
