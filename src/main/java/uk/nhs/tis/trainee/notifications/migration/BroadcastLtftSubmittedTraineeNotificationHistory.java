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

import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED_TRAINEE;

import com.mongodb.MongoException;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.service.EventBroadcastService;

/**
 * Broadcast existing LTFT_SUBMITTED_TRAINEE notification history records.
 */
@Slf4j
@ChangeUnit(id = "broadcastLtftSubmittedTraineeNotificationHistory", order = "8")
public class BroadcastLtftSubmittedTraineeNotificationHistory {

  private final MongoTemplate mongoTemplate;
  private final EventBroadcastService eventBroadcastService;

  public BroadcastLtftSubmittedTraineeNotificationHistory(MongoTemplate mongoTemplate,
      EventBroadcastService eventBroadcastService) {
    this.mongoTemplate = mongoTemplate;
    this.eventBroadcastService = eventBroadcastService;
  }

  /**
   * Broadcast notification History items.
   */
  @Execution
  public void migrate() {
    Query query = Query.query(Criteria.where("type").is(LTFT_SUBMITTED_TRAINEE));
    try {
      mongoTemplate.stream(query, History.class, "History")
          .forEach(eventBroadcastService::publishNotificationsEvent);
    } catch (MongoException me) {
      log.error("Unable to broadcast LTFT Submitted Trainee history due to an error: {}",
          me.toString());
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for"
        + " 'broadcastLtftSubmittedTraineeNotificationHistory' migration.");
  }
}