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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.service.EventBroadcastService.MESSAGE_GROUP_ID_PREFIX;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;
import uk.nhs.tis.trainee.notifications.config.EventNotificationProperties;
import uk.nhs.tis.trainee.notifications.config.EventNotificationProperties.SnsRoute;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class EventBroadcastServiceTest {

  private static final String MESSAGE_ATTRIBUTE = "message-attribute";
  private static final String MESSAGE_ARN = "the-arn";

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private static final String NOTIFICATION_ID = ObjectId.get().toString();
  private static final ObjectId HISTORY_ID = ObjectId.get();

  private static final String TEMPLATE_NAME = "test/template";
  private static final String TEMPLATE_VERSION = "v1.2.3";
  private static final Map<String, Object> TEMPLATE_VARIABLES = Map.of("key1", "value1");

  private static final TisReferenceType TIS_REFERENCE_TYPE = TisReferenceType.PLACEMENT;
  private static final String TIS_REFERENCE_ID = UUID.randomUUID().toString();

  private EventBroadcastService service;

  private ObjectMapper objectMapper;
  private SnsClient snsClient;
  private EventNotificationProperties eventNotificationProperties;

  @BeforeEach
  void setUp() {
    snsClient = mock(SnsClient.class);
    SnsRoute snsRoute = new SnsRoute(MESSAGE_ARN, MESSAGE_ATTRIBUTE);
    eventNotificationProperties = new EventNotificationProperties(snsRoute);
    objectMapper = new ObjectMapper();
    service = new EventBroadcastService(snsClient, eventNotificationProperties);
  }

  @Test
  void shouldNotPublishNotificationEventIfSnsIsNull() {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(null, null);
    Instant sent = Instant.now();
    Instant read = Instant.now().plus(Duration.ofDays(1));
    History history = new History(null, tisReferenceInfo, NotificationType.PROGRAMME_CREATED,
        recipientInfo, templateInfo, sent, read, SENT, null, null);

    eventNotificationProperties = new EventNotificationProperties(null);
    service = new EventBroadcastService(snsClient, eventNotificationProperties);

    service.publishNotificationsEvent(history);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldNotPublishNotificationEventIfEventDtoIsNull() {
    service.publishNotificationsEvent(null);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldNotThrowSnsExceptionsWhenBroadcastingEvent() {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(null, null);
    Instant sent = Instant.now();
    Instant read = Instant.now().plus(Duration.ofDays(1));
    History history = new History(null, tisReferenceInfo, NotificationType.PROGRAMME_CREATED,
        recipientInfo, templateInfo, sent, read, SENT, null, null);

    when(snsClient.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().build());

    assertDoesNotThrow(() -> service.publishNotificationsEvent(history));
  }

  @Test
  void shouldSetMessageGroupIdOnIssuedEventWhenFifoQueue() {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(null, null);
    Instant sent = Instant.now();
    Instant read = Instant.now().plus(Duration.ofDays(1));
    History history = new History(HISTORY_ID, tisReferenceInfo, NotificationType.PROGRAMME_CREATED,
        recipientInfo, templateInfo, sent, read, SENT, null, null);

    SnsRoute fifoSns = new SnsRoute(MESSAGE_ARN + ".fifo", MESSAGE_ATTRIBUTE);
    eventNotificationProperties = new EventNotificationProperties(fifoSns);
    service = new EventBroadcastService(snsClient, eventNotificationProperties);

    service.publishNotificationsEvent(history);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected message group id.", request.messageGroupId(),
        is(MESSAGE_GROUP_ID_PREFIX + "_" + HISTORY_ID));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldNotSetMessageAttributeIfNotRequired() {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(null, null);
    Instant sent = Instant.now();
    Instant read = Instant.now().plus(Duration.ofDays(1));
    History history = new History(HISTORY_ID, tisReferenceInfo, NotificationType.PROGRAMME_CREATED,
        recipientInfo, templateInfo, sent, read, SENT, null, null);

    eventNotificationProperties
        = new EventNotificationProperties(new SnsRoute(MESSAGE_ARN, null));
    service = new EventBroadcastService(snsClient, eventNotificationProperties);

    service.publishNotificationsEvent(history);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertNull(messageAttributes.get("event_type"), "Unexpected message attribute value.");

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldPublishNotificationEvent() throws JsonProcessingException {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    Instant sent = Instant.now();
    Instant read = Instant.now().plus(Duration.ofDays(1));
    History history = new History(HISTORY_ID, tisReferenceInfo, NotificationType.PROGRAMME_CREATED,
        recipientInfo, templateInfo, sent, read, SENT, null, null);

    service.publishNotificationsEvent(history);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected topic ARN.", request.topicArn(), is(MESSAGE_ARN));

    Map<String, Object> message = objectMapper.readValue(request.message(),
        new TypeReference<>() {
        });
    LinkedHashMap<?, ?> id
        = objectMapper.convertValue(message.get("id"), LinkedHashMap.class);
    assertThat("Unexpected message history id.", id.get("timestamp"),
        is(HISTORY_ID.getTimestamp()));
    //TODO: more attribs

    LinkedHashMap<?, ?> tisReference
        = objectMapper.convertValue(message.get("tisReference"), LinkedHashMap.class);
    assertThat("Unexpected message tis reference type.",
        tisReference.get("type"), is(TIS_REFERENCE_TYPE.toString()));
    assertThat("Unexpected message tis reference id.",
        tisReference.get("id"), is(TIS_REFERENCE_ID));

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertThat("Unexpected message attribute value.",
        messageAttributes.get("event_type").stringValue(), is(MESSAGE_ATTRIBUTE));
    assertThat("Unexpected message attribute data type.",
        messageAttributes.get("event_type").dataType(), is("String"));

    verifyNoMoreInteractions(snsClient);
  }
}
