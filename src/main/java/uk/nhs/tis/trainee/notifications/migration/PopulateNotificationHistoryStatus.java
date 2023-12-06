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

package uk.nhs.tis.trainee.notifications.migration;

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
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;

/**
 * Populate the status field for all previously sent notifications.
 */
@Slf4j
@ChangeUnit(id = "populateNotificationHistoryStatus", order = "1")
public class PopulateNotificationHistoryStatus {

  private static final String PROFILE_COLLECTION = "History";
  private final MongoTemplate mongoTemplate;


  public PopulateNotificationHistoryStatus(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Populate notification history status with default value.
   */
  @Execution
  public void migrate() {
    Query query = Query.query(Criteria.where("status").exists(false));
    Update update = Update.update("status", NotificationStatus.SENT);
    try {
      UpdateResult result = mongoTemplate.updateMulti(query, update, PROFILE_COLLECTION);
      log.info("Status set on {} historic notifications.", result.getModifiedCount());
    } catch (MongoException e) {
      log.error("Unable to populate historic statuses due to an error: {} ", e.toString());
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'populateNotificationHistoryStatus' migration.");
  }
}
