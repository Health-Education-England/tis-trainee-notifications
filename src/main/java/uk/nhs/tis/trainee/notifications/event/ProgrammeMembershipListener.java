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

package uk.nhs.tis.trainee.notifications.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.mapper.ProgrammeMembershipMapper;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService;

/**
 * A listener for Programme Membership events.
 */
@Slf4j
@Component
public class ProgrammeMembershipListener {

  private final ProgrammeMembershipService programmeMembershipService;
  private final ProgrammeMembershipMapper mapper;

  /**
   * Construct a listener for programme membership events.
   *
   * @param programmeMembershipService The programme membership service.
   */
  public ProgrammeMembershipListener(ProgrammeMembershipService programmeMembershipService,
      ProgrammeMembershipMapper mapper) {
    this.programmeMembershipService = programmeMembershipService;
    this.mapper = mapper;
  }

  /**
   * Handle Programme membership update events.
   *
   * @param event The program membership event.
   */
  @SqsListener("${application.queues.programme-membership-updated}")
  public void handleProgrammeMembershipUpdate(ProgrammeMembershipEvent event)
      throws SchedulerException {
    log.info("Handling programme membership update event {}.", event);
    if (event.recrd() != null && event.recrd().getData() != null) {
      ProgrammeMembership programmeMembership = mapper.toEntity(event.recrd().getData());
      programmeMembershipService.addNotifications(programmeMembership);
    } else {
      log.info("Ignoring non programme membership update event: {}", event);
    }
  }

  /**
   * Handle Programme membership delete events.
   *
   * @param event The program membership event.
   */
  @SqsListener("${application.queues.programme-membership-deleted}")
  public void handleProgrammeMembershipDelete(ProgrammeMembershipEvent event)
      throws SchedulerException {
    log.info("Handling programme membership delete event {}.", event);
    if (event.recrd() != null && event.recrd().getData() != null) {
      ProgrammeMembership programmeMembership = new ProgrammeMembership();
      programmeMembership.setTisId(event.tisId()); //delete messages have empty recrd data
      programmeMembershipService.deleteNotifications(programmeMembership);
    } else {
      log.info("Ignoring non programme membership delete event: {}", event);
    }
  }
}
