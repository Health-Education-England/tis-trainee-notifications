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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.DELETED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;
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
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class EventBroadcastServiceTest {

  private static final String MESSAGE_ATTRIBUTE = "message-attribute";
  private static final String MESSAGE_ARN = "the-arn";

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";
  private static final MessageType MESSAGE_TYPE = MessageType.EMAIL;

  private static final NotificationType NOTIFICATION_TYPE = PROGRAMME_CREATED;
  private static final ObjectId HISTORY_ID = ObjectId.get();

  private static final String TEMPLATE_NAME = "test/template";
  private static final String TEMPLATE_VERSION = "v1.2.3";
  private static final Map<String, Object> TEMPLATE_VARIABLES = Map.of("key1", "value1");

  private static final Instant SENT_AT = Instant.MIN;
  private static final Instant READ_AT = Instant.now();
  private static final Instant LAST_RETRY = Instant.now().minus(Duration.ofDays(1));

  private static final NotificationStatus NOTIFICATION_STATUS = SENT;
  private static final String NOTIFICATION_STATUS_DETAIL = "some detail";

  private static final TisReferenceType TIS_REFERENCE_TYPE = PLACEMENT;
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
    History history = buildDummyHistory();

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
    History history = buildDummyHistory();

    when(snsClient.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().build());

    assertDoesNotThrow(() -> service.publishNotificationsEvent(history));
  }

  @Test
  void shouldSetMessageGroupIdOnIssuedEventWhenFifoQueue() {
    History history = buildDummyHistory();

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
    History history = buildDummyHistory();

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
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, MESSAGE_TYPE, TRAINEE_CONTACT);
    TemplateInfo templateInfo
        = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION, TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);
    History history = new History(HISTORY_ID, tisReferenceInfo, NOTIFICATION_TYPE,
        recipientInfo, templateInfo, SENT_AT, READ_AT, NOTIFICATION_STATUS,
        NOTIFICATION_STATUS_DETAIL, LAST_RETRY);

    service.publishNotificationsEvent(history);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected topic ARN.", request.topicArn(), is(MESSAGE_ARN));

    Map<String, Object> message = objectMapper.readValue(request.message(),
        new TypeReference<>() {
        });
    assertThat("Unexpected message id.", message.get("id"), is(HISTORY_ID.toString()));

    LinkedHashMap<?, ?> tisReference
        = objectMapper.convertValue(message.get("tisReference"), LinkedHashMap.class);
    assertThat("Unexpected message tis reference type.",
        tisReference.get("type"), is(TIS_REFERENCE_TYPE.toString()));
    assertThat("Unexpected message tis reference id.",
        tisReference.get("id"), is(TIS_REFERENCE_ID));

    assertThat("Unexpected message notification type.", message.get("type"),
        is(NOTIFICATION_TYPE.toString()));

    LinkedHashMap<?, ?> recipient
        = objectMapper.convertValue(message.get("recipient"), LinkedHashMap.class);
    assertThat("Unexpected message recipient id.",
        recipient.get("id"), is(TRAINEE_ID));
    assertThat("Unexpected message recipient message type.",
        recipient.get("type"), is(MESSAGE_TYPE.toString()));
    assertThat("Unexpected message recipient contact.",
        recipient.get("contact"), is(TRAINEE_CONTACT));

    LinkedHashMap<?, ?> template
        = objectMapper.convertValue(message.get("template"), LinkedHashMap.class);
    assertThat("Unexpected template name.",
        template.get("name"), is(TEMPLATE_NAME));
    assertThat("Unexpected template version.",
        template.get("version"), is(TEMPLATE_VERSION));
    LinkedHashMap<?, ?> templateVariables
        = objectMapper.convertValue(template.get("variables"), LinkedHashMap.class);
    assertThat("Unexpected template variables.",
        templateVariables.get("key1"), is("value1"));

    assertThat("Unexpected message sent at.", message.get("sentAt"),
        is(SENT_AT.toString()));

    assertThat("Unexpected message read at.", message.get("readAt"),
        is(READ_AT.toString()));

    assertThat("Unexpected message notification status.", message.get("status"),
        is(NOTIFICATION_STATUS.toString()));

    assertThat("Unexpected message notification status detail.", message.get("statusDetail"),
        is(NOTIFICATION_STATUS_DETAIL.toString()));

    assertThat("Unexpected message last retry.", message.get("lastRetry"),
        is(LAST_RETRY.toString()));

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertThat("Unexpected message attribute value.",
        messageAttributes.get("event_type").stringValue(), is(MESSAGE_ATTRIBUTE));
    assertThat("Unexpected message attribute data type.",
        messageAttributes.get("event_type").dataType(), is("String"));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldPublishDeleteNotificationEvent() throws JsonProcessingException {
    service.publishNotificationsDeleteEvent(HISTORY_ID);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected topic ARN.", request.topicArn(), is(MESSAGE_ARN));

    Map<String, Object> message = objectMapper.readValue(request.message(),
        new TypeReference<>() {
        });
    assertThat("Unexpected message id.", message.get("id"),
        is(HISTORY_ID.toString()));
    assertThat("Unexpected message notification status.", message.get("status"),
        is(DELETED.toString()));
    assertThat("Unexpected message sent at.", message.get("sentAt"),
        is(notNullValue()));

    assertThat("Unexpected message TIS reference.", message.get("tisReference"),
        is(nullValue()));
    assertThat("Unexpected message notification type.", message.get("type"),
        is(nullValue()));
    assertThat("Unexpected message recipient.", message.get("recipient"),
        is(nullValue()));
    assertThat("Unexpected message template.", message.get("template"),
        is(nullValue()));
    assertThat("Unexpected message read at.", message.get("readAt"),
        is(nullValue()));
    assertThat("Unexpected message notification status detail.", message.get("statusDetail"),
        is(nullValue()));
    assertThat("Unexpected message last retry.", message.get("lastRetry"),
        is(nullValue()));
  }

  /**
   * Return a largely empty history for test purposes.
   *
   * @return the History entity.
   */
  private History buildDummyHistory() {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(null, null);
    return new History(HISTORY_ID, tisReferenceInfo, NOTIFICATION_TYPE,
        recipientInfo, templateInfo, SENT_AT, READ_AT, NOTIFICATION_STATUS, null, null);
  }
}
