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

package uk.nhs.tis.trainee.notifications.job;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import io.awspring.cloud.sqs.operations.SendResult.Failed;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.MessageSendingService;

/**
 * A job which sends scheduled emails which are due or overdue.
 */
@Slf4j
@Component
@XRayEnabled
public class ScheduledEmailSender {

  private final HistoryService historyService;
  private final MessageSendingService messageService;

  /**
   * Construct a scheduled email sender.
   *
   * @param historyService A service to retrieve scheduled notifications.
   * @param messageService A service for sending messages.
   */
  public ScheduledEmailSender(HistoryService historyService, MessageSendingService messageService) {
    this.historyService = historyService;
    this.messageService = messageService;
  }

  /**
   * Execute the scheduled job to send all scheduled emails which are due or overdue.
   */
  @Scheduled(cron = "${application.schedules.send-scheduled-emails}")
  @SchedulerLock(name = "ScheduledEmailSender.execute")
  public void execute() {
    log.debug("Checking for overdue emails.");
    List<ObjectIdWrapper> overdueIds = historyService.findAllOverdue();

    if (overdueIds.isEmpty()) {
      log.debug("No overdue emails found.");
      return;
    }

    log.info("{} overdue emails found, queueing for sending.", overdueIds.size());
    Collection<Failed<ObjectIdWrapper>> failures = messageService.sendToOutbox(overdueIds);
    log.info("Queued {} overdue email(s) with {} failure(s).", overdueIds.size(), failures.size());

    if (!failures.isEmpty()) {
      log.error("Failed to queue {} overdue notifications.", failures.size());
    }
    log.debug("Finished queueing overdue emails.");
  }
}
