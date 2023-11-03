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
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.mapper.HistoryMapper;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

/**
 * A service providing functionality for notification history.
 */
@Service
public class HistoryService {

  private final HistoryRepository repository;
  private final HistoryMapper mapper;

  public HistoryService(HistoryRepository repository, HistoryMapper mapper) {
    this.repository = repository;
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
}
