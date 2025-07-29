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

import static io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes.SQS_AWS_TRACE_HEADER;
import static java.util.stream.Collectors.toList;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SendResult.Batch;
import io.awspring.cloud.sqs.operations.SendResult.Failed;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;

/**
 * A service for sending SQS and SNS messages.
 */
@Slf4j
@Service
@XRayEnabled
public class MessageSendingService {

  private static final int MAX_BATCH_SIZE = 10;

  private final SqsTemplate sqsTemplate;

  private final String outboxQueue;

  /**
   * Construct a message sending service.
   *
   * @param sqsTemplate The SQS template to use for sending SQS messages.
   * @param outboxQueue The queue name/url of the outbox queue.
   */
  public MessageSendingService(SqsTemplate sqsTemplate,
      @Value("${application.queues.outbox}") String outboxQueue) {
    this.sqsTemplate = sqsTemplate;
    this.outboxQueue = outboxQueue;
  }

  /**
   * Send the given notifications to the outbox.
   *
   * @param notificationIds The IDs of the notifications to send to the outbox.
   * @return A collection of failures.
   */
  public Collection<Failed<ObjectIdWrapper>> sendToOutbox(List<ObjectIdWrapper> notificationIds) {
    Batch<ObjectIdWrapper> result = sendMany(outboxQueue, notificationIds);

    log.debug("Sent {} notification(s) to the outbox.", result.successful().size());

    if (!result.failed().isEmpty()) {
      log.warn("Failed to send {} notification(s) to the outbox.", result.failed().size());
    }

    return result.failed();
  }

  /**
   * Send many message to the given queue.
   *
   * @param queue   The queue name or URL to send to.
   * @param content A list of content objects.
   * @param <T>     The type of the content.
   * @return The batch send result.
   */
  private <T> Batch<T> sendMany(String queue, List<T> content) {
    if (content.isEmpty()) {
      return new Batch<>(Set.of(), Set.of());
    }

    Map<String, Object> headers = new HashMap<>();

    if (AWSXRay.getTraceEntity() != null) {
      TraceHeader traceHeader = TraceHeader.fromEntity(AWSXRay.getTraceEntity());
      log.debug("Trace header '{}' found and will be reused.", traceHeader);
      headers.put(SQS_AWS_TRACE_HEADER, traceHeader);
    }

    int total = content.size();
    int batchCount = (int) Math.ceil((double) total / MAX_BATCH_SIZE);
    List<SendResult<T>> successful = new ArrayList<>();
    List<Failed<T>> failed = new ArrayList<>();

    for (int batchNumber = 1; batchNumber <= batchCount; batchNumber++) {
      int batchStart = (batchNumber - 1) * MAX_BATCH_SIZE;
      int batchEnd = batchNumber == batchCount ? total : batchStart + MAX_BATCH_SIZE;

      List<T> batch = content.subList(batchStart, batchEnd);

      List<Message<T>> messages = batch.stream()
          .map(payload -> new GenericMessage<>(payload, headers))
          .collect(toList());

      Batch<T> result = sqsTemplate.sendMany(queue, messages);
      successful.addAll(result.successful());
      failed.addAll(result.failed());
    }

    return new Batch<>(successful, failed);
  }
}
