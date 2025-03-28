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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.ARCHIVED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.PENDING;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.READ;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.mapper.HistoryMapper;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

/**
 * A service providing functionality for notification history.
 */
@Slf4j
@Service
public class HistoryService {

  private static final Map<MessageType, Set<NotificationStatus>> VALID_STATUSES = Map.of(
      EMAIL, Set.of(FAILED, PENDING, SENT),
      IN_APP, Set.of(ARCHIVED, READ, SCHEDULED, UNREAD)
  );

  private final HistoryRepository repository;
  private final TemplateService templateService;
  private final EventBroadcastService eventBroadcastService;
  private final HistoryMapper mapper;

  /**
   * Create an instance of the history service.
   *
   * @param repository      The repository to perform all database actions.
   * @param templateService The service providing template handling.
   * @param mapper          The mapper between History data types.
   */
  public HistoryService(HistoryRepository repository, TemplateService templateService,
      EventBroadcastService eventBroadcastService, HistoryMapper mapper) {
    this.repository = repository;
    this.templateService = templateService;
    this.eventBroadcastService = eventBroadcastService;
    this.mapper = mapper;
  }

  /**
   * Save a notification history.
   *
   * @param history The notification to save in history.
   * @return The saved notification history.
   */
  public History save(History history) {
    History savedHistory = repository.save(history);
    eventBroadcastService.publishNotificationsEvent(savedHistory);
    return savedHistory;
  }

  /**
   * Update the status of a notification.
   *
   * @param notificationId The notification to update the status of.
   * @param status         The new status.
   * @param detail         The detail of the status.
   * @param timestamp      The timestamp of the status update, if it is event-driven.
   * @return The updated notification history, or empty if not found.
   */
  public Optional<HistoryDto> updateStatus(String notificationId, NotificationStatus status,
      String detail, Instant timestamp) {
    Optional<History> optionalHistory = repository.findById(new ObjectId(notificationId));

    if (optionalHistory.isEmpty()) {
      log.info("Notification {} was not found.", notificationId);
      return Optional.empty();
    }

    return updateStatus(optionalHistory.get(), status, detail, timestamp);
  }

  /**
   * Update the status of a notification.
   *
   * @param traineeId      The ID of the trainee.
   * @param notificationId The notification to update the status of.
   * @param status         The new status.
   * @return The (possibly) updated notification history, or empty if not found.
   */
  public Optional<HistoryDto> updateStatus(String traineeId, String notificationId,
      NotificationStatus status) {
    Optional<History> optionalHistory = repository.findByIdAndRecipient_Id(
        new ObjectId(notificationId), traineeId);

    if (optionalHistory.isEmpty()) {
      log.info("Notification {} was not found for trainee {}.", notificationId, traineeId);
      return Optional.empty();
    }

    return updateStatus(optionalHistory.get(), status, null, null);
  }

  /**
   * Update the status of a notification, ensuring no retrograde event-driven changes.
   *
   * @param history The notification history to update.
   * @param status  The new status.
   * @param detail  The detail of the status.
   * @param timestamp The timestamp of the status update, if it is event-driven.
   * @return The updated notification history, or empty if not found.
   */
  private Optional<HistoryDto> updateStatus(History history, NotificationStatus status,
      String detail, Instant timestamp) {

    // Validate the status is valid for the notification type.
    MessageType type = history.recipient().type();
    boolean valid = VALID_STATUSES.get(type).contains(status);

    if (!valid) {
      String message = String.format(
          "Invalid combination of type %s and status %s for notification %s.", type, status,
          history.id());
      throw new IllegalArgumentException(message);
    }

    if (timestamp != null) {
      //only update the status if the event timestamp is after the existing latestStatusEventAt
      int updatedHistoryCount
          = repository.updateStatusIfNewer(history.id(), timestamp, status, detail);

      Optional<History> updatedHistory = repository.findById(history.id());
      if (updatedHistoryCount > 0) {
        eventBroadcastService.publishNotificationsEvent(updatedHistory.orElse(null));
      } else {
        log.info("Notification {} was not updated as the event timestamp {} was not newer than {}.",
            history.id(), timestamp, history.latestStatusEventAt());
      }
      return updatedHistory.map(this::toDto);
    } else {
      //without an event timestamp, we simply update the notification status
      history = mapper.updateStatus(history, status, detail);
      history = repository.save(history);
      eventBroadcastService.publishNotificationsEvent(history);
    }
    return Optional.of(toDto(history));
  }

  /**
   * Find all historic notifications for the given Trainee.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @return The found notifications, empty if none found.
   */
  public List<HistoryDto> findAllForTrainee(String traineeId) {
    List<History> history = repository.findAllByRecipient_IdOrderBySentAtDesc(traineeId);

    return history.stream()
        .map(this::toDto)
        .toList();
  }

  /**
   * Find all historic notifications for the given Trainee as full history items.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @return The found notifications, empty if none found.
   */
  public List<History> findAllHistoryForTrainee(String traineeId) {
    return repository.findAllByRecipient_IdOrderBySentAtDesc(traineeId);
  }

  /**
   * Find all sent historic notifications for the given Trainee.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @return The found notifications, empty if none found.
   */
  public List<HistoryDto> findAllSentForTrainee(String traineeId) {
    List<History> history = repository.findAllByRecipient_IdOrderBySentAtDesc(traineeId);

    return history.stream()
        .dropWhile(h -> h.sentAt().isAfter(Instant.now()))
        .map(this::toDto)
        .toList();
  }

  /**
   * Final all historic notifications for a given Trainee with a Failed status.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @return The found notifications, empty if none found.
   */
  public List<History> findAllFailedForTrainee(String traineeId) {
    return repository.findAllByRecipient_IdAndStatus(traineeId,
        NotificationStatus.FAILED.name());
  }

  /**
   * Find all scheduled notifications for the given Trainee from DB.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId The reference ID of the TisReferenceType.
   * @return The found notifications, empty if none found.
   */
  public List<History> findAllScheduledForTrainee(
      String traineeId, TisReferenceType tisReferenceType, String refId) {
    List<History> history = repository.findAllByRecipient_IdOrderBySentAtDesc(traineeId);

    return history.stream()
        // In-app notifications scheduled by future send date,
        // while email notification scheduled by SCHEDULED status
        .filter(h -> (h.sentAt().isAfter(Instant.now())) || (h.status().equals(SCHEDULED)))
        .filter(h -> h.tisReference().id().equals(refId)
            && h.tisReference().type().equals(tisReferenceType))
        .toList();
  }

  /**
   * Find scheduled email notification for the given Trainee by reference and type from DB.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId The reference ID of the TisReferenceType.
   * @param notificationType The notification Type of the notification.
   * @return The found notifications, empty if none found.
   */
  public List<History> findAllScheduledEmailForTraineeByRefAndType(String traineeId,
      TisReferenceType tisReferenceType, String refId, NotificationType notificationType) {
    List<History> history = repository.findAllByRecipient_IdOrderBySentAtDesc(traineeId);

    return history.stream()
        .filter(h -> h.recipient().type().equals(EMAIL))
        .filter(h -> h.status().equals(SCHEDULED))
        .filter(h -> h.tisReference().id().equals(refId)
            && h.tisReference().type().equals(tisReferenceType))
        .filter(h -> h.type().equals(notificationType))
        .toList();
  }

  /**
   * Find latest scheduled email notification for the given Trainee by reference and type from DB.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId The reference ID of the TisReferenceType.
   * @param notificationType The notification Type of the notification.
   * @return The found notifications, empty if none found.
   */
  public History findScheduledEmailForTraineeByRefAndType(String traineeId,
      TisReferenceType tisReferenceType, String refId, NotificationType notificationType) {
    List<History> history = findAllScheduledEmailForTraineeByRefAndType(traineeId,
        tisReferenceType, refId, notificationType);

    return history.stream()
        .findFirst()
        .orElse(null);
  }

  /**
   * Delete notification history by history ID and trainee ID.
   *
   * @param id The object ID of the history to delete.
   * @param traineeId The ID of the trainee to get notifications for.
   */
  public void deleteHistoryForTrainee(ObjectId id, String traineeId) {
    repository.deleteByIdAndRecipient_Id(id, traineeId);
    eventBroadcastService.publishNotificationsDeleteEvent(id);
    log.info("Removed notification history {} for {}", id, traineeId);
  }


  /**
   * Convert a history entity to an equivalent DTO, handles in-app subject text.
   *
   * @param history The history entity to map.
   * @return The mapped HistoryDto.
   */
  private HistoryDto toDto(History history) {
    String subject = null;
    NotificationStatus status = history.status();

    if (history.recipient().type() == IN_APP) {
      subject = rebuildMessage(history, Set.of("subject")).orElse("");

      if (history.sentAt().isAfter(Instant.now())) {
        status = SCHEDULED;
      }
    }

    if (subject == null || subject.isEmpty()) {
      return mapper.toDto(history, status);
    } else {
      return mapper.toDto(history, subject, status);
    }
  }

  /**
   * Rebuild the message for a given notification.
   *
   * @param notificationId The ID of the notification.
   * @return The rebuilt message, or empty if the notification was not found.
   */
  public Optional<String> rebuildMessage(String notificationId) {
    Optional<History> optionalHistory = repository.findById(new ObjectId(notificationId));

    if (optionalHistory.isEmpty()) {
      log.info("Notification {} was not found.", notificationId);
      return Optional.empty();
    }

    return rebuildMessage(optionalHistory.get(), Set.of());
  }

  /**
   * Rebuild the message for a given trainee's notification.
   *
   * @param traineeId      The ID of the trainee.
   * @param notificationId The ID of the notification.
   * @return The rebuilt message, or empty if the notification was not found.
   */
  public Optional<String> rebuildMessage(String traineeId, String notificationId) {
    Optional<History> optionalHistory = repository.findByIdAndRecipient_Id(
        new ObjectId(notificationId), traineeId);

    if (optionalHistory.isEmpty()) {
      log.info("Notification {} was not found for trainee {}.", notificationId, traineeId);
      return Optional.empty();
    }

    History history = optionalHistory.get();
    Set<String> selectors = history.recipient().type() == IN_APP ? Set.of("content") : Set.of();
    return rebuildMessage(history, selectors);
  }

  /**
   * Rebuild the message for a given notification history.
   *
   * @param history   The historical notification.
   * @param selectors The template selectors to use.
   * @return The rebuilt message, or empty if the notification was not found.
   */
  private Optional<String> rebuildMessage(History history, Set<String> selectors) {
    MessageType messageType = history.recipient().type();
    TemplateInfo templateInfo = history.template();

    String templatePath = templateService.getTemplatePath(messageType, templateInfo.name(),
        templateInfo.version());
    String message = templateService.process(templatePath, selectors, templateInfo.variables());
    return Optional.of(message);
  }
}
