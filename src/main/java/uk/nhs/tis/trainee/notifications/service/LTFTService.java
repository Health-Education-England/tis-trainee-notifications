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
 */

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_NOTIFICATION_TYPE_FIELD;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
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
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.LTFT;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for LTFTs.
 */

@Slf4j
@Service
public class LTFTService {

  public static final String TIS_ID_FIELD = "tisId";
  public static final String FORM_ID = "formId";
  public static final String LTFT_NAME = "ltftName";
  public static final String LTFT_STATUS = "ltftStatus";
  public static final String DATE_CHANGED_FIELD = "dateChanged";

  private final HistoryService historyService;
  private final InAppService inAppService;
  private final NotificationService notificationService;
  private final ZoneId timezone;
  private final String ltftVersion;

  /**
   * Initialise the ltft service.
   *
   * @param historyService            The history service to use.
   * @param inAppService              The in-app service to use.
   * @param notificationService       The notification service to use.
   * @param ltftVersion               The LTFT version.
   */
  public LTFTService(HistoryService historyService, InAppService inAppService,
      NotificationService notificationService, @Value("${application.timezone}") ZoneId timezone,
      @Value("${application.template-versions.ltft-status-change.in-app}") String ltftVersion) {
    this.historyService = historyService;
    this.inAppService = inAppService;
    this.notificationService = notificationService;
    this.timezone = timezone;
    this.ltftVersion = ltftVersion;
  }

  /**
   * Get a map of notification types and the most recent one that was sent for a given trainee and
   * ltft from the notification history.
   *
   * @param traineeId             The trainee TIS ID.
   * @param ltftId The ltft TIS ID.
   * @return The map of notification types and the notification item most recently sent.
   */
  private Map<NotificationType, History> getLatestNotificationsSent(String traineeId,
      String ltftId) {
    EnumMap<NotificationType, History> notifications = new EnumMap<>(NotificationType.class);
    List<History> correspondence = historyService.findAllHistoryForTrainee(traineeId);

    Set<NotificationType> notificationTypes = new HashSet<>(
        NotificationType.getLtftNotificationTypes());
    notificationTypes.add(LTFT);


    for (NotificationType milestone : notificationTypes) {
      Optional<History> sentItem = correspondence.stream()
          .filter(c -> c.tisReference() != null)
          .filter(c ->
              c.tisReference().type().equals(TisReferenceType.LTFT)
                  && c.type().equals(milestone)
                  && c.tisReference().id().equals(ltftId))
          .max(Comparator.comparing(History::sentAt)); //get most recent sent
      sentItem.ifPresent(history -> notifications.put(milestone, history));
    }
    return notifications;
  }

  /**
   * Set up notifications for an updated ltft.
   *
   * @param ltft The updated ltft.
   * @throws SchedulerException if any one of the notification jobs could not be scheduled.
   */
  public void addNotifications(LTFT ltft)
      throws SchedulerException {

    //first delete any stale notifications
    deleteNotificationsFromScheduler(ltft);
    deleteScheduledNotificationsFromDb(ltft);


    Map<NotificationType, History> notificationsAlreadySent
        = getLatestNotificationsSent(ltft.getPersonId(),
        ltft.getTraineeTisId());

    createDirectLtftNotifications(ltft, notificationsAlreadySent);

  }

  /**
   * Create "direct" notifications, such as email
   *
   * @param ltft                     The updated ltft.
   * @param notificationsAlreadySent Previously sent notifications.
   */
  private void createDirectLtftNotifications(LTFT ltft,
      Map<NotificationType, History> notificationsAlreadySent) throws SchedulerException {

      log.info("Processing notification {} for {}.", LTFT,
          ltft.getTraineeTisId());

      JobDataMap ltftCreatedJobDataMap = new JobDataMap();
    ltftCreatedJobDataMap.put(TEMPLATE_NOTIFICATION_TYPE_FIELD, LTFT);
    ltftCreatedJobDataMap.put(TIS_ID_FIELD, ltft.getTraineeTisId());
    ltftCreatedJobDataMap.put(PERSON_ID_FIELD, ltft.getPersonId());

      String jobId = LTFT + "-" + ltft.getTraineeTisId();

      notificationService.executeNow(jobId, ltftCreatedJobDataMap);
  }

  /**
   * Remove notifications for a ltft from scheduler.
   *
   * @param ltft The ltft.
   * @throws SchedulerException if any one of the notification jobs could not be removed.
   */
  public void deleteNotificationsFromScheduler(LTFT ltft)
      throws SchedulerException {

    for (NotificationType milestone : NotificationType.getLtftNotificationTypes()) {

      String jobId = milestone.toString() + "-" + ltft.getTraineeTisId();
      notificationService.removeNotification(jobId); //remove existing notification if it exists
    }
  }

  /**
   * Remove scheduled notifications for a ltft from DB.
   *
   * @param ltft The ltft.
   */
  public void deleteScheduledNotificationsFromDb(LTFT ltft) {

    List<History> scheduledHistories = historyService
        .findAllScheduledForTrainee(ltft.getPersonId(), TisReferenceType.LTFT,
            ltft.getFormId());

    for (History history : scheduledHistories) {
      historyService.deleteHistoryForTrainee(history.id(), ltft.getPersonId());
    }
  }
}
