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

import static uk.nhs.tis.trainee.notifications.model.HrefType.ABSOLUTE_URL;
import static uk.nhs.tis.trainee.notifications.model.HrefType.NON_HREF;
import static uk.nhs.tis.trainee.notifications.model.HrefType.PROTOCOL_EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.Placement;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for Placement.
 */

@Slf4j
@Service
public class PlacementService {

  public static final String TIS_ID_FIELD = "tisId";
  public static final String PERSON_ID_FIELD = "personId";
  public static final String START_DATE_FIELD = "startDate";
  public static final String PLACEMENT_TYPE_FIELD = "placementType";
  public static final String PLACEMENT_SPECIALTY_FIELD = "specialty";
  public static final String NOTIFICATION_TYPE_FIELD = "notificationType";
  public static final String PLACEMENT_OWNER_FIELD = "localOfficeName";
  public static final String PLACEMENT_OWNER_CONTACT_FIELD = "localOfficeContact";
  public static final String CONTACT_TYPE_FIELD = "contactTypeName";
  public static final String CONTACT_FIELD = "contact";
  public static final String CONTACT_HREF_FIELD = "contactHref";

  public static final List<String> PLACEMENT_TYPES_TO_ACT_ON
      = List.of("In post", "In post - Acting up", "In Post - Extension");
  public static final String API_GET_OWNER_CONTACT =
      "/api/local-office-contact-by-lo-name/{localOfficeName}";
  protected static final String DEFAULT_NO_CONTACT_MESSAGE = "your local deanery office";

  private final HistoryService historyService;
  private final NotificationService notificationService;
  private final RestTemplate restTemplate;
  private final String serviceUrl;

  /**
   * Initialise the Placement Service.
   *
   * @param historyService      The history Service to use.
   * @param notificationService The notification Service to use.
   * @param restTemplate        The REST template.
   * @param serviceUrl          The URL for the tis-trainee-reference service to use
   */
  public PlacementService(HistoryService historyService,
      NotificationService notificationService,
      RestTemplate restTemplate,
      @Value("${service.reference.url}") String serviceUrl) {
    this.historyService = historyService;
    this.notificationService = notificationService;
    this.restTemplate = restTemplate;
    this.serviceUrl = serviceUrl;
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
    if (placement.getPlacementType() == null) {
      return true; //should not happen, but some legacy data has no placement type set.
    }
    return (PLACEMENT_TYPES_TO_ACT_ON.stream()
        .noneMatch(placement.getPlacementType()::equalsIgnoreCase));
  }

  /**
   * Get a map of 12 week notifications and the instant they were sent for a given trainee and
   * placement from the notification history.
   *
   * @param traineeId   The trainee TIS ID.
   * @param placementId The placement TIS ID.
   * @return The map of notification types and when they were sent.
   */
  private Map<NotificationType, Instant> getNotificationsSent(String traineeId,
      String placementId) {
    EnumMap<NotificationType, Instant> notifications = new EnumMap<>(NotificationType.class);
    List<HistoryDto> correspondence = historyService.findAllForTrainee(traineeId);

    Optional<HistoryDto> sentItem = correspondence.stream()
        .filter(c -> c.tisReference() != null)
        .filter(c ->
            c.tisReference().type().equals(TisReferenceType.PLACEMENT)
                && c.subject().equals(PLACEMENT_UPDATED_WEEK_12)
                && c.tisReference().id().equals(placementId))
        .findFirst();
    sentItem.ifPresent(
        historyDto -> notifications.put(PLACEMENT_UPDATED_WEEK_12, historyDto.sentAt()));

    return notifications;
  }

  /**
   * Set up notifications for an updated placement.
   *
   * @param placement The updated placement.
   * @throws SchedulerException if any one of the notification jobs could not be scheduled.
   */
  public void addNotifications(Placement placement)
      throws SchedulerException {

    deleteNotifications(placement); //first delete any stale notifications

    boolean isExcluded = isExcluded(placement);
    log.info("Placement {}: excluded {}.", placement.getTisId(), isExcluded);

    if (!isExcluded) {
      Map<NotificationType, Instant> notificationsAlreadySent
          = getNotificationsSent(placement.getPersonId(), placement.getTisId());

      LocalDate startDate = placement.getStartDate();

      boolean shouldSchedule = shouldScheduleNotification(notificationsAlreadySent, startDate);

      if (shouldSchedule) {
        log.info("Scheduling notification {} for {}.",
            PLACEMENT_UPDATED_WEEK_12, placement.getTisId());
        Integer daysBeforeStart = getNotificationDaysBeforeStart(PLACEMENT_UPDATED_WEEK_12);
        Date when = notificationService.getScheduleDate(startDate, daysBeforeStart);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(TIS_ID_FIELD, placement.getTisId());
        jobDataMap.put(PERSON_ID_FIELD, placement.getPersonId());
        jobDataMap.put(START_DATE_FIELD, placement.getStartDate());
        jobDataMap.put(PLACEMENT_TYPE_FIELD, placement.getPlacementType());
        jobDataMap.put(PLACEMENT_SPECIALTY_FIELD, placement.getSpecialty());
        jobDataMap.put(PLACEMENT_OWNER_FIELD, placement.getOwner());

        String contact = getOwnerContact(placement.getOwner(), LocalOfficeContactType.TSS_SUPPORT);
        jobDataMap.put(PLACEMENT_OWNER_CONTACT_FIELD, contact);
        jobDataMap.put(CONTACT_HREF_FIELD, getHrefTypeForContact(contact));

        jobDataMap.put(NOTIFICATION_TYPE_FIELD, PLACEMENT_UPDATED_WEEK_12);
        // Note the status of the trainee will be retrieved when the job is executed, as will
        // their name and email address, not now.

        String jobId = PLACEMENT_UPDATED_WEEK_12 + "-" + placement.getTisId();
        try {
          notificationService.scheduleNotification(jobId, jobDataMap, when);
        } catch (SchedulerException e) {
          log.error("Failed to schedule notification {}: {}", jobId, e.toString());
          throw (e); //to allow message to be requeue-ed
        }
      }
    }
  }

  /**
   * Remove notifications for a placement.
   *
   * @param placement The placement.
   * @throws SchedulerException if any one of the notification jobs could not be removed.
   */
  public void deleteNotifications(Placement placement)
      throws SchedulerException {
    String jobId = PLACEMENT_UPDATED_WEEK_12 + "-" + placement.getTisId();
    notificationService.removeNotification(jobId); //remove existing notification if it exists
  }

  /**
   * Get the number of days in advance of the placement start to send the notification.
   *
   * @param notificationType The notification type.
   * @return The number of days before the placement start for the notification, or null if not a
   *     placement update notification type.
   */
  public Integer getNotificationDaysBeforeStart(NotificationType notificationType) {
    if (notificationType.equals(PLACEMENT_UPDATED_WEEK_12)) {
      return 84;
    } else {
      return null;
    }
  }

  /**
   * Helper function to determine whether a notification should be scheduled.
   *
   * @return true if it should be scheduled, false otherwise.
   */
  private boolean shouldScheduleNotification(
      Map<NotificationType, Instant> notificationsAlreadySent, LocalDate startDate) {

    if (startDate == null || startDate.isBefore(LocalDate.now())) {
      return false;
    }
    //do not resend any notification
    return (!notificationsAlreadySent.containsKey(PLACEMENT_UPDATED_WEEK_12));
  }

  /**
   * Get placement owner contact from Trainee Reference Service.
   *
   * @param localOfficeName The owner name to search for.
   * @return The specific contact type contact of the owner, or null if not found.
   */
  private String getOwnerContact(String localOfficeName, LocalOfficeContactType contactType) {
    if (localOfficeName != null) {
      try {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> ownerContactList
            = restTemplate.getForObject(serviceUrl + API_GET_OWNER_CONTACT,
            List.class, Map.of(PLACEMENT_OWNER_FIELD, localOfficeName));
        if (ownerContactList != null) {
          Optional<Map<String, String>> ownerContact = ownerContactList.stream()
              .filter(c ->
                  c.get(CONTACT_TYPE_FIELD).equalsIgnoreCase(contactType.getContactTypeName()))
              .findFirst();
          return ownerContact.map(oc -> oc.get(CONTACT_FIELD))
              .orElse(DEFAULT_NO_CONTACT_MESSAGE);
        } else {
          log.warn("Null response when requesting reference local-office-contact-by-lo-name '{}'",
              localOfficeName);
        }
      } catch (RestClientException rce) {
        log.warn("Exception occurred when requesting reference local-office-contact-by-lo-name "
            + "endpoint: " + rce);
      }
    }
    //no matched owner, or other problems retrieving contact
    return DEFAULT_NO_CONTACT_MESSAGE;
  }

  /**
   * Return a href type for a contact. It is assumed to be either a URL or an email address. There
   * is minimal checking that it is a validly formatted email address.
   *
   * @param contact The contact string, expected to be either an email address or a URL.
   * @return "email" if it looks like an email address, "url" if it looks like a URL, and "NOT_HREF"
   *     otherwise.
   */
  private String getHrefTypeForContact(String contact) {
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
