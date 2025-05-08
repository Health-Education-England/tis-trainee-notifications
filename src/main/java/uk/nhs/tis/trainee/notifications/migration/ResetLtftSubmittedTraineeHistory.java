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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.migration;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED_TRAINEE;

import com.mongodb.MongoException;
import com.mongodb.client.result.UpdateResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.tis.trainee.notifications.model.History;

/**
 * Broadcast existing LTFT_SUBMITTED_TRAINEE notification history records.
 */
@Slf4j
@ChangeUnit(id = "resetLtftSubmittedTraineeHistory", order = "8")
public class ResetLtftSubmittedTraineeHistory {

  private final MongoTemplate mongoTemplate;

  public ResetLtftSubmittedTraineeHistory(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Reset notification History items to use LTFT_SUBMITTED.
   */
  @Execution
  public void migrate() {
    Query query = Query.query(Criteria.where("type").is(LTFT_SUBMITTED_TRAINEE));
    Update update = Update.update("type", LTFT_SUBMITTED);
    try {
      UpdateResult result = mongoTemplate.updateMulti(query, update, History.class);
      log.info("LTFT_UPDATED type set on {} historic notifications.", result.getModifiedCount());
    } catch (MongoException e) {
      log.error("Unable to populate historic types due to an error: {} ", e.toString());
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for"
        + " 'resetLtftSubmittedTraineeHistory' migration.");
  }
}