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

package uk.nhs.tis.trainee.notifications.repository;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;

/**
 * A repository of historical notifications.
 */
@Repository
public interface HistoryRepository extends
    MongoRepository<History, ObjectId> {

  /**
   * Get a list of the IDs of all matching history items.
   *
   * @param status The status to filter by.
   * @param sentAt The timestamp to filter results by, must be less than or equal to this value.
   * @return A list of IDs for matching history items.
   */
  @Query(fields = "{_id: 1}")
  List<ObjectIdWrapper> findIdByStatusAndSentAtLessThanEqualOrderById(NotificationStatus status,
      Instant sentAt);

  /**
   * Find all history for the given recipient ID.
   *
   * @param recipientId The ID of the recipient to get the history for.
   * @return The found history, empty if none found.
   */
  List<History> findAllByRecipient_IdOrderBySentAtDesc(String recipientId);

  /**
   * Find a specific history record associated with the given recipient ID.
   *
   * @param id          The ID of the history record.
   * @param recipientId The ID of the recipient to get the history for.
   * @return The found history, empty if none found.
   */
  Optional<History> findByIdAndRecipient_Id(ObjectId id, String recipientId);

  /**
   * Find all history for the given recipient ID and status.
   *
   * @param recipientId The ID of the recipient to get the history for.
   * @param status      The message status.
   * @return The found history, empty if none found.
   */
  List<History> findAllByRecipient_IdAndStatus(String recipientId, String status);

  /**
   * Remove history Notifications by ID and recipientId.
   *
   * @param id          The ID of the history record.
   * @param recipientId The ID of the recipient to get the history for.
   */
  void deleteByIdAndRecipient_Id(ObjectId id, String recipientId);

  /**
   * Update the status of a notification if the new status changed-at is newer than the current one
   * or the current one is null.
   *
   * @param id                 The ID of the history item.
   * @param newStatusChangedAt The timestamp of the new status change.
   * @param newStatus          The new status.
   * @param statusDetail       The new status detail.
   * @return The number of updated documents (zero if no matching object or if new status
   *         update is older than the existing one).
   */
  @Modifying
  @Transactional
  @Query("{ '_id': ?0, "
      + "'$or': [ { 'latestStatusEventAt': null }, { 'latestStatusEventAt': { '$lte': ?1 } } ] }")
  @Update("{ '$set': { 'status': ?2, 'statusDetail': ?3, 'latestStatusEventAt': ?1 } }")
  int updateStatusIfNewer(ObjectId id, Instant newStatusChangedAt,
      NotificationStatus newStatus, String statusDetail);
}
