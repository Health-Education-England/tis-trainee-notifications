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

package uk.nhs.tis.trainee.notifications.migration;

import static java.time.ZoneOffset.UTC;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * Resend email notifications which failed and got stuck in SCHEDULED.
 */
@Slf4j
@ChangeUnit(id = "resendAugust2025RotationScheduleFailures", order = "10")
public class ResendAugust2025RotationScheduleFailures {

  private static final long WINDOW = Duration.ofDays(1).getSeconds();

  private final MongoTemplate mongoTemplate;
  private final HistoryService historyService;
  private final NotificationService notificationService;

  /**
   * Construct the migrator.
   *
   * @param mongoTemplate The mongo template to use for accessing failed records.
   */
  public ResendAugust2025RotationScheduleFailures(MongoTemplate mongoTemplate,
      HistoryService historyService, NotificationService notificationService) {
    this.mongoTemplate = mongoTemplate;
    this.historyService = historyService;
    this.notificationService = notificationService;
  }

  /**
   * Resend the failed Google email notifications.
   */
  @Execution
  public void migrate() {
    Query query = new Query()
        .addCriteria(Criteria.where("recipient.type").is(EMAIL))
        .addCriteria(Criteria.where("status").is(SCHEDULED))
        .addCriteria(Criteria.where("sentAt")
            .gte(Instant.parse("2025-05-01T00:00:00Z"))
            .lt(LocalDate.now(UTC).atStartOfDay(UTC).toInstant())
        );

    List<History> missedSchedules = mongoTemplate.find(query, History.class);
    int total = missedSchedules.size();
    log.info("Found {} qualifying missed schedules.", total);
    int current = 1;

    for (History missedSchedule : missedSchedules) {
      log.info("Progress: {}/{}.", current++, total);
      boolean success = scheduleEmail(missedSchedule);

      if (success) {
        historyService.deleteHistoryForTrainee(missedSchedule.id(),
            missedSchedule.recipient().id());
      }
    }
  }

  /**
   * Schedule retrying to send the missed email.
   *
   * @param missedSchedule The missed schedule notification history.
   * @return Whether the notification was successfully scheduled.
   */
  private boolean scheduleEmail(History missedSchedule) {
    Map<String, Object> templateVariables = missedSchedule.template().variables();
    String jobId = "%s-%s".formatted(missedSchedule.type(), missedSchedule.tisReference().id());

    try {
      notificationService.scheduleNotification(jobId, templateVariables, Date.from(Instant.now()),
          WINDOW);
      return true;
    } catch (Exception e) {
      log.error("Failed to schedule notification retry.", e);
      return false;
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'ResendAugust2025RotationScheduleFailures'"
        + " migration.");
  }
}
