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

package uk.nhs.tis.trainee.notifications.api;

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.ARCHIVED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.READ;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.notifications.api.util.AuthTokenUtil;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

/**
 * A controller providing trainees access to their notification history.
 */
@Slf4j
@RestController
@RequestMapping("/api/history/trainee")
public class TraineeHistoryResource {

  private final HistoryService service;

  /**
   * Create an instance of the trainee history controller.
   *
   * @param service The service used to access notification history.
   */
  public TraineeHistoryResource(HistoryService service) {
    this.service = service;
  }


  /**
   * Get the notification history for the authorized trainee.
   *
   * @param token The authorization token from the request header.
   * @return The found history, may be empty.
   */
  @GetMapping
  ResponseEntity<List<HistoryDto>> getTraineeHistory(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Retrieving notification history for the authorized trainee.");
    String traineeId = getTraineeId(token);

    if (traineeId == null) {
      return ResponseEntity.badRequest().build();
    }

    log.info("Retrieving notification history for trainee {}.", traineeId);
    List<HistoryDto> history = service.findAllSentForTrainee(traineeId);
    log.info("Found {} notifications for trainee {}.", history.size(), traineeId);
    return ResponseEntity.ok(history);
  }

  /**
   * Get the historic notification with the given ID.
   *
   * @param notificationId The ID of the notification to get.
   * @param token          The authorization token from the request header.
   * @return The found notification.
   */
  @GetMapping("/message/{notificationId}")
  ResponseEntity<String> getHistoricalMessage(@PathVariable String notificationId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Retrieving notification message for the authorized trainee.");
    String traineeId = getTraineeId(token);

    if (traineeId == null) {
      return ResponseEntity.badRequest().build();
    }

    log.info("Rebuilding message for notification {}.", notificationId);
    Optional<String> message = service.rebuildMessage(traineeId, notificationId);

    return message.map(msg -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(msg))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Update the status of a notification to set it to "ARCHIVED".
   *
   * @param notificationId The ID of the notification to mark as archived.
   * @param token          The authorization token from the request header.
   * @return The updated notification history.
   */
  @PutMapping("/notification/{notificationId}/archive")
  ResponseEntity<HistoryDto> archiveNotification(@PathVariable String notificationId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Archiving notification for the authorized trainee.");
    String traineeId = getTraineeId(token);

    if (traineeId == null) {
      return ResponseEntity.badRequest().build();
    }

    log.info("Archiving notification {}.", notificationId);
    Optional<HistoryDto> history = service.updateStatus(traineeId, notificationId, ARCHIVED);
    return ResponseEntity.of(history);
  }

  /**
   * Update the status of a notification to set it to "READ".
   *
   * @param notificationId The ID of the notification to mark as read.
   * @param token          The authorization token from the request header.
   * @return The updated notification history.
   */
  @PutMapping("/notification/{notificationId}/mark-read")
  ResponseEntity<HistoryDto> markNotificationAsRead(@PathVariable String notificationId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Marking notification as read for the authorized trainee.");
    String traineeId = getTraineeId(token);

    if (traineeId == null) {
      return ResponseEntity.badRequest().build();
    }

    log.info("Marking notification {} as read.", notificationId);
    Optional<HistoryDto> history = service.updateStatus(traineeId, notificationId, READ);
    return ResponseEntity.of(history);
  }

  /**
   * Update the status of a notification to set it to "UNREAD".
   *
   * @param notificationId The ID of the notification to mark as unread.
   * @param token          The authorization token from the request header.
   * @return The updated notification history.
   */
  @PutMapping("/notification/{notificationId}/mark-unread")
  ResponseEntity<HistoryDto> markNotificationAsUnread(@PathVariable String notificationId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Marking notification as unread for the authorized trainee.");
    String traineeId = getTraineeId(token);

    if (traineeId == null) {
      return ResponseEntity.badRequest().build();
    }

    log.info("Marking notification {} as unread.", notificationId);
    Optional<HistoryDto> history = service.updateStatus(traineeId, notificationId, UNREAD);
    return ResponseEntity.of(history);
  }

  /**
   * Get the trainee ID from the given authorization token.
   *
   * @param token The authorization token from the request header.
   * @return The token's trainee ID or null if not available.
   */
  private String getTraineeId(String token) {
    String traineeId = null;

    try {
      traineeId = AuthTokenUtil.getTraineeTisId(token);

      if (traineeId == null) {
        log.warn("The trainee ID was not found in the token.");
      }
    } catch (IOException e) {
      log.warn("Unable to read the trainee ID from the token.", e);
    }

    return traineeId;
  }
}
