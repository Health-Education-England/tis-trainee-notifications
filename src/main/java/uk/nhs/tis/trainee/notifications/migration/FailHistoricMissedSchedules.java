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

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;

import com.mongodb.client.result.UpdateResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.tis.trainee.notifications.model.History;

/**
 * Flag historic missed schedules as failed to avoid sending as overdue.
 */
@Slf4j
@ChangeUnit(id = "failHistoricMissedSchedules", order = "11")
public class FailHistoricMissedSchedules {

  private final MongoTemplate mongoTemplate;
  private final ZoneId timezone;

  /**
   * Construct the migrator.
   *
   * @param mongoTemplate The mongo template to use for accessing failed records.
   */
  public FailHistoricMissedSchedules(MongoTemplate mongoTemplate, Environment env) {
    this.mongoTemplate = mongoTemplate;

    String timezoneProperty = env.getProperty("application.timezone");
    assert timezoneProperty != null;
    this.timezone = ZoneId.of(timezoneProperty);
  }

  /**
   * Flag historic missed schedules as failed.
   */
  @Execution
  public void migrate() {
    Query query = new Query()
        .addCriteria(Criteria.where("status").is(SCHEDULED))
        .addCriteria(Criteria.where("sentAt")
            // Consider anything prior to 2025-05-01 as too late to re-attempt.
            .lt(LocalDate.of(2025, 5, 1)
                .atStartOfDay(timezone)
                .toInstant())
        );

    Update updateDefinition = new Update()
        .set("status", FAILED)
        // This is true for all prod data in this state.
        .set("statusDetail", "Missed Schedule: Programme already started");

    UpdateResult updateResult = mongoTemplate.updateMulti(query, updateDefinition, History.class);

    if (!updateResult.wasAcknowledged()) {
      throw new RuntimeException("Failed to update notifications.");
    }

    long matches = updateResult.getMatchedCount();
    long updates = updateResult.getModifiedCount();
    log.info("Found and updated {} notifications.", updates);

    if (matches != updates) {
      String message = "Failed to update %d notifications.".formatted(matches - updates);
      throw new RuntimeException(message);
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'FailHistoricMissedSchedules' migration.");
  }
}
