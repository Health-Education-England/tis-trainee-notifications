/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.PlacementEvent;
import uk.nhs.tis.trainee.notifications.mapper.PlacementMapper;
import uk.nhs.tis.trainee.notifications.model.Placement;
import uk.nhs.tis.trainee.notifications.service.PlacementService;

/**
 * A listener for Placement events.
 */
@Slf4j
@Component
public class PlacementListener {

  private final PlacementService placementService;
  private final PlacementMapper mapper;

  /**
   * Construct a listener for placement events.
   *
   * @param placementService The placement service.
   */
  public PlacementListener(PlacementService placementService,
                           PlacementMapper mapper) {
    this.placementService = placementService;
    this.mapper = mapper;
  }

  /**
   * Handle Placement update events.
   *
   * @param event The placement event.
   */
  @SqsListener("${application.queues.placement-updated}")
  public void handlePlacementUpdate(PlacementEvent event)
      throws SchedulerException {
    log.info("Handling placement update event {}.", event);
    if (event.recrd() != null && event.recrd().getData() != null) {
      Placement placement = mapper.toEntity(event.recrd().getData());
      placementService.addNotifications(placement);
    } else {
      log.info("Ignoring non placement update event: {}", event);
    }
  }

  /**
   * Handle Placement delete events.
   *
   * @param event The placement event.
   */
  @SqsListener("${application.queues.placement-deleted}")
  public void handlePlacementDelete(PlacementEvent event)
      throws SchedulerException {
    log.info("Handling placement delete event {}.", event);
    if (event.recrd() != null && event.recrd().getData() != null) {
      Placement placement = mapper.toEntity(event.recrd().getData());
      placement.setTisId(event.tisId()); //delete messages used to have empty record data
      placementService.deleteNotifications(placement);
      placementService.deleteScheduledInAppNotifications(placement);
    } else {
      log.info("Ignoring non placement delete event: {}", event);
    }
  }
}
