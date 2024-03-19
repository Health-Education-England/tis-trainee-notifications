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

package uk.nhs.tis.trainee.notifications.dto;

import java.time.Instant;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A DTO for historical notification data.
 *
 * @param id           The ID of the notification.
 * @param tisReference The TIS reference info for the entity that prompted the notification.
 * @param type         The type of notification e.g. EMAIL
 * @param subject      The subject of the notification e.g. COJ_CONFIRMATION
 * @param subjectText  The subject text of the notification
 * @param contact      The contact details used to send the notification.
 * @param sentAt       The timestamp that the notification was sent at.
 * @param readAt       The timestamp that the notification was read at.
 * @param status       The status of the notification history e.g. SENT or FAILED.
 * @param statusDetail Any additional detail about the status.
 */
public record HistoryDto(String id, TisReferenceInfo tisReference, MessageType type,
                         NotificationType subject, String subjectText, String contact,
                         Instant sentAt, Instant readAt, NotificationStatus status,
                         String statusDetail) {

}
