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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.TriesToReturnSelf;
import org.quartz.JobDataMap;
import org.springframework.messaging.support.GenericMessage;

class MessageSendingServiceTest {

  private static final String OUTBOX_QUEUE = "outbox.fifo";
  private static final UUID MESSAGE_ID = UUID.randomUUID();

  private MessageSendingService service;

  private SqsTemplate sqsTemplate;

  @BeforeEach
  void setUp() {
    sqsTemplate = mock(SqsTemplate.class);
    service = new MessageSendingService(sqsTemplate, OUTBOX_QUEUE);
  }

  @Test
  void shouldSetSendOptionsWhenSendingJobToOutbox() {
    ArgumentCaptor<Consumer<SqsSendOptions<JobDataMap>>> consumerCaptor = ArgumentCaptor.captor();
    SendResult<JobDataMap> sendResult = new SendResult<>(MESSAGE_ID, OUTBOX_QUEUE,
        new GenericMessage<>(new JobDataMap()), Map.of());
    when(sqsTemplate.send(consumerCaptor.capture())).thenReturn(sendResult);

    JobDataMap jobData = new JobDataMap(Map.of(
        "key1", "value1",
        "key2", "value2",
        "personId", "person123"
    ));

    service.sendJobToOutbox("job123", jobData);

    Consumer<SqsSendOptions<JobDataMap>> consumer = consumerCaptor.getValue();
    SqsSendOptions<JobDataMap> sendOptions = mock(SqsSendOptions.class, new TriesToReturnSelf());
    consumer.accept(sendOptions);

    verify(sendOptions).queue(OUTBOX_QUEUE);
    verify(sendOptions).payload(jobData);
    verify(sendOptions).messageDeduplicationId("job123");
    verify(sendOptions).messageGroupId("person123");
    verifyNoMoreInteractions(sendOptions);
  }

  @Test
  void shouldReturnQueuedResultWhenSentJobToOutbox() {
    SendResult<Object> sendResult = new SendResult<>(MESSAGE_ID, OUTBOX_QUEUE,
        new GenericMessage<>(new Object()), Map.of());
    when(sqsTemplate.send(any())).thenReturn(sendResult);

    Map<String, String> result = service.sendJobToOutbox("job123", new JobDataMap());

    assertThat("Unexpected result count.", result.keySet(), hasSize(1));
    assertThat("Unexpected result status.", result.get("status"), is("queued " + MESSAGE_ID));
  }
}
