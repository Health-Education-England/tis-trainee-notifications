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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;

import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * A service for sending messages to a queue.
 */
@Slf4j
@Service
public class MessageSendingService {

  private final SqsTemplate template;
  private final String outboxQueue;

  /**
   * Construct an instance of the message sending service.
   *
   * @param template    The SQS template to use for sending messages.
   * @param outboxQueue The name of the outbox queue.
   */
  public MessageSendingService(SqsTemplate template,
      @Value("${application.queues.outbox}") String outboxQueue) {
    this.template = template;
    this.outboxQueue = outboxQueue;
  }

  /**
   * Queue a job for execution.
   *
   * @param jobKey     The descriptive job identifier.
   * @param jobDetails The job details.
   * @return the result map with status details if successful.
   */
  public Map<String, String> sendJobToOutbox(String jobKey, JobDataMap jobDetails) {
    log.info("Sending job {} to the outbox.", jobKey);
    SendResult<Object> result = template.send(to -> to.queue(outboxQueue)
        .payload(jobDetails)
        .messageDeduplicationId(jobKey)
        .messageGroupId(jobDetails.getString(PERSON_ID_FIELD))
    );

    return Map.of("status", "queued " + result.messageId());
  }
}
