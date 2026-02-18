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
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.dto.HistoryMessageDto;
import uk.nhs.tis.trainee.notifications.mapper.HistoryMapper;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

/**
 * A service providing functionality for notification history.
 */
@Slf4j
@Service
public class HistoryService {

  private static final String LOG_NOTIFICATION_NOT_FOUND = "Notification {} was not found.";
  private static final String LOG_NOTIFICATION_NOT_FOUND_TRAINEE =
      "Notification {} was not found for trainee {}.";

  private static final Map<MessageType, Set<NotificationStatus>> VALID_STATUSES = Map.of(
      EMAIL, Set.of(FAILED, PENDING, SENT),
      IN_APP, Set.of(ARCHIVED, READ, SCHEDULED, UNREAD)
  );
  private static final String STATUS_PROPERTY = "status";
  private static final String SUBJECT_PROPERTY = "subject";
  private static final String SENT_AT_PROPERTY = "sentAt";
  private static final String CONTACT_PROPERTY = "contact";
  private static final String TYPE_PROPERTY = "type";
  private static final String STATUS_FIELD = "status";
  private static final String SUBJECT_FIELD = "subject";
  private static final String SENT_AT_FIELD = "sentAt";
  private static final String CONTACT_FIELD = "recipient.contact";
  private static final String TYPE_FIELD = "type";
  private static final String RECIPIENT_TYPE_FIELD = "recipient.type";
  private static final String RECIPIENT_ID_FIELD = "recipient.id";

  private static final String SELECTOR_SUBJECT = "subject";
  private static final String SELECTOR_CONTENT = "content";

  private final HistoryRepository repository;
  private final TemplateService templateService;
  private final EventBroadcastService eventBroadcastService;
  private final HistoryMapper mapper;
  private final MongoTemplate mongoTemplate;

  /**
   * Create an instance of the history service.
   *
   * @param repository      The repository to perform all database actions.
   * @param templateService The service providing template handling.
   * @param mapper          The mapper between History data types.
   */
  public HistoryService(HistoryRepository repository, TemplateService templateService,
      EventBroadcastService eventBroadcastService, HistoryMapper mapper,
      MongoTemplate mongoTemplate) {
    this.repository = repository;
    this.templateService = templateService;
    this.eventBroadcastService = eventBroadcastService;
    this.mapper = mapper;
    this.mongoTemplate = mongoTemplate;
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
      log.info(LOG_NOTIFICATION_NOT_FOUND, notificationId);
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
      log.info(LOG_NOTIFICATION_NOT_FOUND_TRAINEE, notificationId, traineeId);
      return Optional.empty();
    }

    return updateStatus(optionalHistory.get(), status, null, null);
  }

  /**
   * Update the status of a notification, ensuring no retrograde event-driven changes.
   *
   * @param history   The notification history to update.
   * @param status    The new status.
   * @param detail    The detail of the status.
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
   * List the IDs of all scheduled notifications which are overdue being sent.
   *
   * @return A list of overdue notification IDs, empty if none found.
   */
  public List<ObjectIdWrapper> findAllOverdue() {
    log.debug("Finding all overdue notifications IDs.");
    return repository.findIdByStatusAndSentAtLessThanEqualOrderById(SCHEDULED, Instant.now());
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
   * Return page of all sent historic notifications for the given Trainee.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @return The found notifications, empty if none found.
   */
  public Page<HistoryDto> findAllSentInPageForTrainee(String traineeId,
      Map<String, String> filterParams, Pageable pageable) {
    Query query = buildHistoryFilteredQuery(traineeId, filterParams, pageable);
    List<History> historyList = mongoTemplate.find(query, History.class);
    Page<History> historyPage = PageableExecutionUtils.getPage(historyList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), History.class));

    if (pageable.isPaged()) {
      log.info("Found {} total notifications, returning page {} of {}",
          historyPage.getTotalElements(),
          pageable.getPageNumber(),
          historyPage.getTotalPages());
    } else {
      log.info("Found {} total notifications, returning all results",
          historyPage.getTotalElements());
    }
    return historyPage.map(this::toDto);
  }

  /**
   * Find sent email notification for the given Trainee by reference and type from DB.
   *
   * @param traineeId        The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId            The reference ID of the TisReferenceType.
   * @param notificationType The notification Type of the notification.
   * @return The found notifications, empty if none found.
   */
  public List<History> findAllSentEmailForTraineeByRefAndType(String traineeId,
      TisReferenceType tisReferenceType, String refId, NotificationType notificationType) {

    History.RecipientInfo recipient = new History.RecipientInfo(traineeId, EMAIL, null);
    History.TisReferenceInfo referenceInfo = new History.TisReferenceInfo(tisReferenceType, refId);
    History history = History.builder()
        .recipient(recipient)
        .tisReference(referenceInfo)
        .type(notificationType)
        .status(SENT)
        .build();

    Example<History> example = Example.of(history);

    Sort sort = Sort.by(SENT_AT_FIELD).descending();
    return repository.findAll(example, sort);
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
   * @param traineeId        The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId            The reference ID of the TisReferenceType.
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
   * @param traineeId        The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId            The reference ID of the TisReferenceType.
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
   * @param traineeId        The ID of the trainee to get notifications for.
   * @param tisReferenceType The reference type of the object.
   * @param refId            The reference ID of the TisReferenceType.
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
   * @param id        The object ID of the history to delete.
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
    NotificationStatus status = history.status();
    String subject = rebuildMessage(history, Set.of(SUBJECT_FIELD)).orElse("");

    if (history.recipient().type() == IN_APP && history.sentAt().isAfter(Instant.now())) {
      status = SCHEDULED;
    }

    if (subject.isEmpty()) {
      return mapper.toDto(history, status);
    } else {
      return mapper.toDto(history, subject, status);
    }
  }

  /**
   * Rebuild the full message for a given trainee's notification.
   *
   * @param traineeId      The ID of the trainee.
   * @param notificationId The ID of the notification.
   * @return The rebuilt message, or empty if the notification was not found.
   */
  public Optional<HistoryMessageDto> rebuildMessageFull(String traineeId, String notificationId) {
    Optional<History> optionalHistory = repository.findByIdAndRecipient_Id(
        new ObjectId(notificationId), traineeId);

    if (optionalHistory.isEmpty()) {
      log.info(LOG_NOTIFICATION_NOT_FOUND_TRAINEE, notificationId, traineeId);
      return Optional.empty();
    }

    History history = optionalHistory.get();
    Optional<String> subject = rebuildMessage(history, Set.of(SELECTOR_SUBJECT));
    Optional<String> content = rebuildMessage(history, Set.of(SELECTOR_CONTENT));

    if (subject.orElse("").isBlank() || content.orElse("").isBlank()) {
      log.warn(
          "Subject and/or Content not found for notification {}, will return empty result.",
          notificationId);
      return Optional.empty();
    }

    return Optional.of(new HistoryMessageDto(subject.get(), content.get(), history.sentAt()));
  }

  /**
   * Rebuild the message content for a given trainee's notification.
   *
   * @param traineeId      The ID of the trainee.
   * @param notificationId The ID of the notification.
   * @return The rebuilt message content, or empty if the notification was not found.
   */
  public Optional<String> rebuildMessageContent(String traineeId, String notificationId) {
    Optional<History> optionalHistory = repository.findByIdAndRecipient_Id(
        new ObjectId(notificationId), traineeId);

    if (optionalHistory.isEmpty()) {
      log.info(LOG_NOTIFICATION_NOT_FOUND_TRAINEE, notificationId, traineeId);
      return Optional.empty();
    }

    History history = optionalHistory.get();
    Set<String> selectors =
        history.recipient().type() == IN_APP ? Set.of(SELECTOR_CONTENT) : Set.of();
    return rebuildMessage(history, selectors);
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
      log.info(LOG_NOTIFICATION_NOT_FOUND, notificationId);
      return Optional.empty();
    }

    return rebuildMessage(optionalHistory.get(), Set.of());
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

  /**
   * Move all notifications from one trainee to another. Assumes that fromTraineeId and toTraineeId
   * are valid. The updated notifications are broadcast as events.
   *
   * @param fromTraineeId The trainee ID to move notifications from.
   * @param toTraineeId   The trainee ID to move notifications to.
   */
  public void moveNotifications(String fromTraineeId, String toTraineeId) {
    AtomicReference<Integer> movedCount = new AtomicReference<>(0);
    List<History> histories = findAllHistoryForTrainee(fromTraineeId);

    histories.forEach(h -> {
      log.debug("Moving notification history [{}] from trainee [{}] to trainee [{}]",
          h.id(), fromTraineeId, toTraineeId);
      // note recipient email address is not changed,
      // neither is any other part of the notification (e.g. template.personId)
      History.RecipientInfo newRecipient
          = new History.RecipientInfo(toTraineeId, h.recipient().type(), h.recipient().contact());
      this.save(h.withRecipient(newRecipient));
      movedCount.getAndSet(movedCount.get() + 1);
    });
    log.info("Moved {} notification histories from trainee [{}] to trainee [{}]",
        movedCount, fromTraineeId, toTraineeId);
  }

  /**
   * Build a notification filtered query, which applies notification type filters.
   *
   * @param filterParams The user-supplied filters to apply, unsupported fields will be dropped.
   * @param pageable     The paging and sorting to apply to the query.
   * @return The build query.
   */
  private Query buildHistoryFilteredQuery(String traineeId, Map<String, String> filterParams,
      Pageable pageable) {
    // Translate sort field(s).
    Sort sort = pageable.getSort().isSorted()
        ? Sort.by(pageable.getSort().stream()
        .map(order -> {
          String property = switch (order.getProperty()) {
            case STATUS_PROPERTY -> STATUS_FIELD;
            case SUBJECT_PROPERTY -> TYPE_FIELD;
            case SENT_AT_PROPERTY -> SENT_AT_FIELD;
            case CONTACT_PROPERTY -> CONTACT_FIELD;
            default -> null;
          };
          return property == null ? null : order.withProperty(property);
        })
        .filter(Objects::nonNull)
        .toList())
        : Sort.by(Sort.Order.desc(SENT_AT_FIELD)); // default sort by sentAt descending

    Query query;

    if (pageable.isUnpaged()) {
      query = new Query().with(sort);
    } else {
      // Add ID sort to ensure consistent pagination when values are duplicated.
      sort = sort.and(Sort.by("id"));
      pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
      query = new Query().with(pageable);
    }

    // Restrict results to the user's traineeId.
    query.addCriteria(Criteria.where(RECIPIENT_ID_FIELD).is(traineeId));
    // Restrict results with sentAt before
    query.addCriteria(Criteria.where(SENT_AT_FIELD).lt(Instant.now()));

    // Handle fix-value filters.
    filterParams.entrySet().stream()
        .filter(e -> e.getValue() != null && !e.getValue().isBlank())
        .map(e -> {
          String property = switch (e.getKey()) {
            case TYPE_PROPERTY -> RECIPIENT_TYPE_FIELD;
            case STATUS_PROPERTY -> STATUS_FIELD;
            default -> null;
          };
          return property == null ? null : new AbstractMap.SimpleEntry<>(property, e.getValue());
        })
        .filter(Objects::nonNull)
        .forEach(e -> {
          String key = e.getKey();
          String value = e.getValue();

          if (value.contains(",")) {
            String[] values = value.split(",");
            query.addCriteria(Criteria.where(key).in((Object[]) values));
          } else {
            query.addCriteria(Criteria.where(key).is(value));
          }
        });

    // Handle search filter on multiple fields.
    String keyword = filterParams.get("keyword");
    if (keyword != null && !keyword.isBlank()) {
      String pattern = ".*" + Pattern.quote(keyword) + ".*";
      query.addCriteria(new Criteria().orOperator(
          Criteria.where(STATUS_FIELD).regex(pattern, "i"),
          Criteria.where(TYPE_FIELD).regex(pattern, "i"),
          Criteria.where(SENT_AT_FIELD).regex(pattern, "i"),
          Criteria.where(CONTACT_FIELD).regex(pattern, "i")
      ));
    }

    return query;
  }
}
