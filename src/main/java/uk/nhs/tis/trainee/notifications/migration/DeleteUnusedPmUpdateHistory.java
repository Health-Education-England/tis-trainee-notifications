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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */


package uk.nhs.tis.trainee.notifications.migration;

import com.mongodb.MongoException;
import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * Delete unused programme-membership-update history records.
 */
@Slf4j
@ChangeUnit(id = "deleteUnusedPmUpdateHistory", order = "2")
public class DeleteUnusedPmUpdateHistory {

  private final MongoTemplate mongoTemplate;

  public DeleteUnusedPmUpdateHistory(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Remove Programme Updated History items.
   */
  @Execution
  public void migrate() {
    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
      var isProgrammeUpdateType = Criteria.where("type").is(milestone);
      var isProgrammeUpdateQuery = Query.query(isProgrammeUpdateType);
      try {
        DeleteResult result = mongoTemplate.remove(isProgrammeUpdateQuery, History.class);
        log.info("Unused PM Update history {}: {} deleted", milestone, result.getDeletedCount());
      } catch (MongoException me) {
        log.error("Unable to delete unused PM Update history {} due to an error: {} ",
            milestone, me.toString());
      }
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'deleteUnusedPmUpdateHistory' migration.");
  }
}
