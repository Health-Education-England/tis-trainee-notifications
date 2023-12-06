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

import java.util.List;

/**
 * A representation of the Amazon SES email notification event.
 *
 * @param notificationType The notification type.
 * @param mail             The mail details.
 * @param bounce           The bounce details, if this is a bounce event.
 * @param complaint        The complaint details, if this is a complain event.
 */
public record EmailEvent(String notificationType, Mail mail, Bounce bounce, Complaint complaint) {

  /**
   * A representation of a bounce event data from Amazon SES.
   *
   * @param bounceType    The type of bounce, e.g. Permanent or Transient.
   * @param bounceSubType The subtype of the bounce, e.g. General or MailboxFull
   */
  public record Bounce(String bounceType, String bounceSubType) {

  }

  /**
   * A representation of a complaint event from Amazon SES.
   *
   * @param complaintSubType      The complaint sub type, either null or OnAccountSuppressionList.
   * @param complaintFeedbackType The type of feedback from an ISP complaint, may be null.
   */
  public record Complaint(String complaintSubType, String complaintFeedbackType) {

  }

  /**
   * A representation of the mail details included in an Amazon SES event.
   *
   * @param headers The headers sent with the email.
   */
  public record Mail(List<MailHeader> headers) {

    /**
     * An email header.
     *
     * @param name  The name of the header.
     * @param value The value of the header.
     */
    public record MailHeader(String name, String value) {

    }
  }
}
