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

import java.util.List;
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
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

/**
 * A service providing functionality for notification history.
 */
@Slf4j
@Service
public class HistoryService {

  private final HistoryRepository repository;
  private final TemplateService templateService;
  private final HistoryMapper mapper;

  /**
   * Create an instance of the history service.
   *
   * @param repository      The repository to perform all database actions.
   * @param templateService The service providing template handling.
   * @param mapper          The mapper between History data types.
   */
  public HistoryService(HistoryRepository repository, TemplateService templateService,
      HistoryMapper mapper) {
    this.repository = repository;
    this.templateService = templateService;
    this.mapper = mapper;
  }

  /**
   * Save a notification history.
   *
   * @param history The notification to save in history.
   * @return The saved notification history.
   */
  public History save(History history) {
    return repository.save(history);
  }

  /**
   * Find all historic notifications for the given Trainee.
   *
   * @param traineeId The ID of the trainee to get notifications for.
   * @return The found notifications, empty if none found.
   */
  public List<HistoryDto> findAllForTrainee(String traineeId) {
    List<History> history = repository.findAllByRecipient_IdOrderBySentAtDesc(traineeId);
    return mapper.toDtos(history);
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

    History history = optionalHistory.get();
    MessageType messageType = history.recipient().type();
    TemplateInfo templateInfo = history.template();

    String templatePath = templateService.getTemplatePath(messageType, templateInfo.name(),
        templateInfo.version());
    String message = templateService.process(templatePath, Set.of(), templateInfo.variables());
    return Optional.of(message);
  }
}
