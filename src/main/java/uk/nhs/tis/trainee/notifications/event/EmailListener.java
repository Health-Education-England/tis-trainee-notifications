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

package uk.nhs.tis.trainee.notifications.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent.Bounce;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent.Complaint;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

/**
 * A listener for email events.
 */
@Slf4j
@Component
public class EmailListener {

  private final HistoryService historyService;

  public EmailListener(HistoryService historySrvice) {
    this.historyService = historySrvice;
  }

  /**
   * Handle failure events, such as bounce and complaints.
   *
   * @param event The email event from SES.
   */
  @SqsListener("${application.queues.email-failure}")
  void handleFailure(EmailEvent event) {
    String notificationId = getNotificationId(event);
    log.info("Handling failure for notification {}.", notificationId);

    String reason = switch (event.notificationType()) {
      case "Bounce" -> getReason(event.bounce());
      case "Complaint" -> getReason(event.complaint());
      default -> null;
    };

    if (reason != null) {
      log.info("Updating notification {} with failure detail '{}'", notificationId, reason);
      historyService.updateStatus(notificationId, NotificationStatus.FAILED, reason);
    }
  }

  /**
   * Get the notification ID from the email event.
   *
   * @param event The email event to get the notification ID from.
   * @return The notification ID from the email event headers.
   */
  private String getNotificationId(EmailEvent event) {
    return event.mail().headers().stream()
        .filter(header -> header.name().equals("NotificationId"))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No notification ID found."))
        .value();
  }

  /**
   * Get the reason text for a bounce event.
   *
   * @param bounce The bounce to construct a message for.
   * @return The reason message.
   */
  private String getReason(Bounce bounce) {
    return String.format("Bounce: %s - %s", bounce.bounceType(), bounce.bounceSubType());
  }

  /**
   * Get the reason text for a complaint event.
   *
   * @param complaint The complaint to construct a message for.
   * @return The reason message.
   */
  private String getReason(Complaint complaint) {
    // Check for nulls here as the complaint fields are optional.
    String reason = complaint.complaintSubType() != null ? complaint.complaintSubType()
        : complaint.complaintFeedbackType();
    return String.format("Complaint: %s", reason != null ? reason : "Undetermined");
  }
}
