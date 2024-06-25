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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.DELETED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest.Builder;
import software.amazon.awssdk.services.sns.model.SnsException;
import uk.nhs.tis.trainee.notifications.config.EventNotificationProperties;
import uk.nhs.tis.trainee.notifications.config.EventNotificationProperties.SnsRoute;
import uk.nhs.tis.trainee.notifications.config.ObjectIdSerializerModule;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;

/**
 * A service for broadcasting form events to SNS.
 */
@Slf4j
@Service
public class EventBroadcastService {

  public static final String MESSAGE_GROUP_ID_PREFIX = "notifications_event";

  private final SnsClient snsClient;

  private final EventNotificationProperties eventNotificationProperties;

  EventBroadcastService(SnsClient snsClient,
      EventNotificationProperties eventNotificationProperties) {
    this.snsClient = snsClient;
    this.eventNotificationProperties = eventNotificationProperties;
  }

  /**
   * Publish a notification history event to SNS.
   *
   * @param history The history event to publish.
   */
  public void publishNotificationsEvent(History history) {

    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new ObjectIdSerializerModule());

    PublishRequest request = null;
    SnsRoute snsTopic = eventNotificationProperties.notificationsEvent();

    if (snsTopic != null && history != null) {
      JsonNode eventJson = objectMapper.valueToTree(history);
      request = buildSnsRequest(eventJson, snsTopic, history.id());
    }

    if (request != null) {
      try {
        snsClient.publish(request);
        log.info("Broadcast event sent to SNS for notification event {}.", history.id());
      } catch (SnsException e) {
        String message = String.format(
            "Failed to broadcast event to SNS topic '%s' for notification event '%s'",
            snsTopic, history.id());
        log.error(message, e);
      }
    }
  }

  /**
   * Publish a blank record with NotificationStatus DELETED for a deleted history item.
   *
   * @param id The History id.
   */
  public void publishNotificationsDeleteEvent(ObjectId id) {
    Instant sentAt = Instant.now();
    History history = History.builder()
        .id(id)
        .sentAt(sentAt)
        .status(DELETED)
        .build();
    publishNotificationsEvent(history);
  }

  /**
   * Build an SNS publish request.
   *
   * @param eventJson The SNS message contents.
   * @param snsTopic  The SNS topic to send the message to.
   * @param id        The event id.
   * @return the built request.
   */
  private PublishRequest buildSnsRequest(JsonNode eventJson, SnsRoute snsTopic,
      ObjectId id) {
    Builder request = PublishRequest.builder()
        .message(eventJson.toString())
        .topicArn(snsTopic.arn());

    if (snsTopic.messageAttribute() != null) {
      MessageAttributeValue messageAttributeValue = MessageAttributeValue.builder()
          .dataType("String")
          .stringValue(snsTopic.messageAttribute())
          .build();
      request.messageAttributes(Map.of("event_type", messageAttributeValue));
    }

    if (snsTopic.arn().endsWith(".fifo")) {
      // Create a message group to ensure FIFO per unique object.
      String messageGroup = String.format("%s_%s", MESSAGE_GROUP_ID_PREFIX, id);
      request.messageGroupId(messageGroup);
    }

    return request.build();
  }
}
