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

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;

import com.mongodb.MongoException;
import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;

/**
 * Delete orphan SCHEDULED history from DB.
 */
@Slf4j
@ChangeUnit(id = "deleteOrphanScheduledHistory", order = "7")
public class DeleteOrphanScheduledHistory {

  private final MongoTemplate mongoTemplate;

  /**
   * Constructor.
   */
  public DeleteOrphanScheduledHistory(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Delete past SCHEDULED history from DB.
   */
  @Execution
  public void migrate() {
    CriteriaDefinition isScheduled = Criteria.where("status").is(SCHEDULED);
    CriteriaDefinition isPast = Criteria.where("sentAt").lt(new Date());
    Query query = Query.query(isScheduled);
    query.addCriteria(isPast);

    try {
      DeleteResult result = mongoTemplate.remove(query, History.class);
      log.info("{} Orphan SCHEDULED history deleted", result.getDeletedCount());
    } catch (MongoException me) {
      log.error("Unable to delete orphan SCHEDULED history due to an error: {} ",
          me.toString());
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'DeleteOrphanScheduledHistory' migration.");
  }
}
