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

package uk.nhs.tis.trainee.notifications.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A representation of a historical notification.
 *
 * @param id           A unique identifier for the notification.
 * @param type         The type of notification sent.
 * @param recipient    The recipient information the notification was sent to.
 * @param template     The template information used to generate the notification.
 * @param sentAt       The timestamp that the notification was sent at.
 * @param readAt       The timestamp that the notification was read at.
 * @param status       The status of the notification history e.g. SENT or FAILED.
 * @param statusDetail Any additional detail about the status.
 */
@Document(collection = "History")
@Builder
public record History(
    @Id
    ObjectId id,
    TisReferenceInfo tisReference,
    NotificationType type,
    RecipientInfo recipient,
    TemplateInfo template,
    Instant sentAt,
    Instant readAt,
    NotificationStatus status,
    String statusDetail,
    Instant lastRetry) {

  /**
   * A representation of a notified recipient.
   *
   * @param id      The identifier of the recipient.
   * @param type    The type of message sent.
   * @param contact The contact details used to send the notification.
   */
  public record RecipientInfo(@Field("id") String id, MessageType type, String contact) {

  }

  /**
   * A representation of the template information used to generate a notification.
   *
   * @param name      The name of the template.
   * @param version   The version of the template.
   * @param variables The variables to process with the template.
   */
  public record TemplateInfo(String name, String version, Map<String, Object> variables) {

  }

  /**
   * A representation of the TIS record that prompted the notification.
   *
   * @param type The TIS reference type for the entity that prompted the notification.
   * @param id   The TIS ID of the entity that prompted the notification.
   */
  public record TisReferenceInfo(TisReferenceType type, String id) {

  }
}
