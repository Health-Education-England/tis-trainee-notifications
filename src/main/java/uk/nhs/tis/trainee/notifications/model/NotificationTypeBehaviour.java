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
 *
 */

package uk.nhs.tis.trainee.notifications.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The behaviour configuration for a notification type.
 */
@Getter
@AllArgsConstructor
public class NotificationTypeBehaviour {
  NotificationType notificationType;         // the type of notification
  MessageType messageType;                   // the type of message (e.g., email, in-app)
  TisReferenceType referenceType;            // TIS entity this notification type is associated with
  boolean isActive;                          // if new notifications can be created of this type
  boolean isReminder;                        // if this notification type is a reminder
  AnchorReferenceState anchorReferenceState; // which state to anchor the reminders against
  Integer daysBeforeAnchorToSend;            // days before anchor to send reminder

  //consider: canResend, resendIfDeferral?

}
