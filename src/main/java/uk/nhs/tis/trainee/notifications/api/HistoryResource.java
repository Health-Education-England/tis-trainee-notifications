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

package uk.nhs.tis.trainee.notifications.api;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

/**
 * A controller providing API access to notification history.
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
public class HistoryResource {

  private final HistoryService service;

  /**
   * Create an instance of the history controller.
   *
   * @param service The service used to access notification history.
   */
  public HistoryResource(HistoryService service) {
    this.service = service;
  }

  /**
   * Get the notification history for a given trainee.
   *
   * @param traineeId The ID of the trainee to get the history for.
   * @return The found history, may be empty.
   */
  @GetMapping("/trainee/{traineeId}")
  ResponseEntity<List<HistoryDto>> getTraineeHistory(@PathVariable String traineeId) {
    log.info("Retrieving notification history for trainee {}.", traineeId);
    List<HistoryDto> history = service.findAllForTrainee(traineeId);
    log.info("Found {} notifications for trainee {}.", history.size(), traineeId);
    return ResponseEntity.ok(history);
  }

  /**
   * Get the historic notification with the given ID.
   *
   * @param notificationId The ID of the notification to get.
   * @return The found notification.
   */
  @GetMapping("/message/{notificationId}")
  ResponseEntity<String> getHistoricalMessage(@PathVariable String notificationId) {
    log.info("Rebuilding message for notification {}.", notificationId);
    Optional<String> message = service.rebuildMessage(notificationId);

    return message.map(msg -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(msg))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
