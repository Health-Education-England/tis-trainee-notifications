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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import io.awspring.cloud.sqs.operations.SendResult.Batch;
import io.awspring.cloud.sqs.operations.SendResult.Failed;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

class MessageSendingServiceTest {

  private static final String OUTBOX_QUEUE = "http://outbox.example.com";
  private static final ObjectId NOTIFICATION_ID = ObjectId.get();

  private MessageSendingService service;
  private HistoryRepository repository;
  private NotificationService notificationService;
  private SqsTemplate sqsTemplate;

  @BeforeEach
  void setUp() {
    repository = mock(HistoryRepository.class);
    notificationService = mock(NotificationService.class);
    sqsTemplate = mock(SqsTemplate.class);
    service = new MessageSendingService(repository, notificationService, sqsTemplate, OUTBOX_QUEUE);
  }

  @Test
  void shouldThrowErrorSendingScheduledWhenNotFound() {
    when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

    ObjectIdWrapper notificationIdWrapper = new ObjectIdWrapper(NOTIFICATION_ID);
    assertThrows(MessagingException.class, () -> service.sendScheduled(notificationIdWrapper));

    verifyNoInteractions(notificationService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = EXCLUDE, names = "SCHEDULED")
  void shouldSkipSendingScheduledWhenStateNotScheduled(NotificationStatus status) {
    Map<String, Object> templateVariables = Map.of(
        "key1", "value1",
        "key2", true,
        "key3", 123
    );
    when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(
        History.builder()
            .status(status)
            .template(new TemplateInfo("template", "v1.2.3", templateVariables))
            .build()
    ));

    ObjectIdWrapper notificationIdWrapper = new ObjectIdWrapper(NOTIFICATION_ID);
    service.sendScheduled(notificationIdWrapper);

    verifyNoInteractions(notificationService);
  }

  @Test
  void shouldSendScheduledWithTemplateVariables() {
    Map<String, Object> templateVariables = Map.of(
        "key1", "value1",
        "key2", true,
        "key3", 123
    );
    when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(
        History.builder()
            .status(SCHEDULED)
            .template(new TemplateInfo("template", "v1.2.3", templateVariables))
            .build()
    ));

    when(notificationService.executeNow(any(), any())).thenReturn(
        Map.of("status", "sent " + Instant.now()));

    ObjectIdWrapper notificationIdWrapper = new ObjectIdWrapper(NOTIFICATION_ID);
    service.sendScheduled(notificationIdWrapper);

    ArgumentCaptor<Map<String, Object>> jobDataCaptor = ArgumentCaptor.captor();
    verify(notificationService).executeNow(eq("OUTBOX_" + NOTIFICATION_ID),
        jobDataCaptor.capture());

    Map<String, Object> jobData = jobDataCaptor.getValue();
    assertThat("Unexpected job data count.", jobData.keySet(), hasSize(3));
    assertThat("Unexpected job data.", jobData.get("key1"), is("value1"));
    assertThat("Unexpected job data.", jobData.get("key2"), is(true));
    assertThat("Unexpected job data.", jobData.get("key3"), is(123));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "failed")
  void shouldThrowErrorWhenSendScheduledFails(String failedStatus) {
    Map<String, Object> templateVariables = Map.of(
        "key1", "value1",
        "key2", true,
        "key3", 123
    );
    when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(
        History.builder()
            .status(SCHEDULED)
            .template(new TemplateInfo("template", "v1.2.3", templateVariables))
            .build()
    ));

    Map<String, String> result = new HashMap<>();
    result.put("status", failedStatus);
    when(notificationService.executeNow(any(), any())).thenReturn(result);

    ObjectIdWrapper notificationIdWrapper = new ObjectIdWrapper(NOTIFICATION_ID);
    assertThrows(MessagingException.class, () -> service.sendScheduled(notificationIdWrapper));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      0  | 0
      1  | 1
      9  | 1
      10 | 1
      11 | 2
      20 | 2
      25 | 3
      """)
  void shouldSendToOutboxInBatches(int idCount, int batchCount) {
    when(sqsTemplate.sendMany(any(), any())).thenReturn(new Batch<>(List.of(), List.of()));

    List<ObjectIdWrapper> ids = IntStream.range(0, idCount)
        .mapToObj(i -> new ObjectIdWrapper(ObjectId.get()))
        .toList();

    service.sendToOutbox(ids);

    ArgumentCaptor<List<Message<ObjectIdWrapper>>> batchCaptor = ArgumentCaptor.captor();
    verify(sqsTemplate, times(batchCount)).sendMany(eq(OUTBOX_QUEUE), batchCaptor.capture());

    List<List<Message<ObjectIdWrapper>>> batches = batchCaptor.getAllValues();
    assertThat("Unexpected batch count.", batches, hasSize(batchCount));

    int remainder = idCount % 10;

    for (var iterator = batches.listIterator(); iterator.hasNext(); ) {
      List<Message<ObjectIdWrapper>> batch = iterator.next();

      if (iterator.hasNext() || remainder == 0) {
        assertThat("Unexpected batch size.", batch, hasSize(10));
      } else {
        assertThat("Unexpected batch size.", batch, hasSize(remainder));
      }
    }
  }

  @Test
  void shouldCombineFailuresWhenBatchSendingToOutbox() {
    Failed<ObjectIdWrapper> fail1 = new Failed<>("fail1", null, null, null);
    Failed<ObjectIdWrapper> fail2 = new Failed<>("fail1", null, null, null);

    when(sqsTemplate.sendMany(any(), (List<Message<ObjectIdWrapper>>) any())).thenReturn(
        new Batch<>(List.of(), List.of(fail1)),
        new Batch<>(List.of(), List.of(fail2))
    );

    List<ObjectIdWrapper> ids = IntStream.range(0, 11)
        .mapToObj(i -> new ObjectIdWrapper(ObjectId.get()))
        .toList();

    Collection<Failed<ObjectIdWrapper>> failures = service.sendToOutbox(ids);

    assertThat("Unexpected failure size.", failures, hasSize(2));
    assertThat("Unexpected failures.", failures, hasItems(fail1, fail2));
  }

  @Test
  void shouldPopulateTraceHeaderWhenTracingSendingToOutbox() {
    AWSXRayRecorder recorder = spy(AWSXRayRecorder.class);
    AWSXRay.setGlobalRecorder(recorder);
    Segment segment = recorder.beginSegment("testSegment");
    when(recorder.getTraceEntity()).thenReturn(segment);

    when(sqsTemplate.sendMany(any(), any())).thenReturn(new Batch<>(List.of(), List.of()));

    ObjectIdWrapper id = new ObjectIdWrapper(ObjectId.get());
    service.sendToOutbox(List.of(id));

    ArgumentCaptor<List<Message<ObjectIdWrapper>>> batchCaptor = ArgumentCaptor.captor();
    verify(sqsTemplate).sendMany(eq(OUTBOX_QUEUE), batchCaptor.capture());

    List<Message<ObjectIdWrapper>> batch = batchCaptor.getValue();
    Message<ObjectIdWrapper> message = batch.get(0);
    MessageHeaders messageHeaders = message.getHeaders();
    assertThat("Unexpected message header.", messageHeaders, hasKey(SQS_AWS_TRACE_HEADER));
    assertThat("Unexpected trace id.", messageHeaders.get(SQS_AWS_TRACE_HEADER),
        instanceOf(String.class));
  }

  @Test
  void shouldNotPopulateTraceHeaderWhenNotTracingSendingToOutbox() {
    AWSXRay.setGlobalRecorder(mock(AWSXRayRecorder.class));

    when(sqsTemplate.sendMany(any(), any())).thenReturn(new Batch<>(List.of(), List.of()));

    ObjectIdWrapper id = new ObjectIdWrapper(ObjectId.get());
    service.sendToOutbox(List.of(id));

    ArgumentCaptor<List<Message<ObjectIdWrapper>>> batchCaptor = ArgumentCaptor.captor();
    verify(sqsTemplate).sendMany(eq(OUTBOX_QUEUE), batchCaptor.capture());

    List<Message<ObjectIdWrapper>> batch = batchCaptor.getValue();
    Message<ObjectIdWrapper> message = batch.get(0);
    MessageHeaders messageHeaders = message.getHeaders();
    assertThat("Unexpected message header.", messageHeaders, not(hasKey(SQS_AWS_TRACE_HEADER)));
  }
}
