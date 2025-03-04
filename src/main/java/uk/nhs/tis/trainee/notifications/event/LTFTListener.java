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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.LTFTEvent;
import uk.nhs.tis.trainee.notifications.mapper.LTFTMapper;
import uk.nhs.tis.trainee.notifications.model.LTFT;
import uk.nhs.tis.trainee.notifications.service.LTFTService;

/**
 * A listener for LTFT events.
 */
@Slf4j
@Component
public class LTFTListener {

  private final LTFTService ltftService;
  private final LTFTMapper mapper;

  /**
   * Construct a listener for LTFT events.
   *
   * @param ltftService The LTFT service.
   */
  public LTFTListener(LTFTService ltftService,
      LTFTMapper mapper) {
    this.ltftService = ltftService;
    this.mapper = mapper;
  }

  /**
   * Handle LTFT update events.
   *
   * @param event The LTFT event.
   */
  @SqsListener("${application.queues.ltft-updated}")
  public void handleLTFTUpdate(LTFTEvent event)
      throws SchedulerException {
    log.info("Handling LTFT update event {}.", event);
    if (event.recrd() != null && event.recrd().getData() != null) {
      LTFT ltft = mapper.toEntity(event.recrd().getData());
      ltftService.addNotifications(ltft);
    } else {
      log.info("Ignoring non LTFT update event: {}", event);
    }
  }

}
