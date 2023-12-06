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

package uk.nhs.tis.trainee.notifications.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent.Bounce;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent.Complaint;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent.Mail;
import uk.nhs.tis.trainee.notifications.dto.EmailEvent.Mail.MailHeader;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

class EmailListenerTest {

  private static final String NOTIFICATION_ID = "40";

  private EmailListener listener;
  private HistoryService historyService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    listener = new EmailListener(historyService);
  }

  @Test
  void shouldThrowExceptionHandlingFailureWhenNoNotificationId() {
    Mail mail = new Mail(List.of());
    EmailEvent event = new EmailEvent("bounce", mail, null, null);

    assertThrows(IllegalArgumentException.class, () -> listener.handleFailure(event));
  }

  @Test
  void shouldHandleFailureWhenBounceEvent() {
    Mail mail = new Mail(List.of(new MailHeader("NotificationId", NOTIFICATION_ID)));
    Bounce bounce = new Bounce("type1", "type2");
    EmailEvent event = new EmailEvent("Bounce", mail, bounce, null);

    listener.handleFailure(event);

    verify(historyService).updateStatus(NOTIFICATION_ID, FAILED, "Bounce: type1 - type2");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      type1 | type2 | Complaint: type1
      null  | type2 | Complaint: type2
      null  | null  | Complaint: Undetermined
      """)
  void shouldHandleFailureWhenComplaintEvent(String subType, String feedbackType, String message) {
    Mail mail = new Mail(List.of(new MailHeader("NotificationId", NOTIFICATION_ID)));
    Complaint complaint = new Complaint(subType, feedbackType);
    EmailEvent event = new EmailEvent("Complaint", mail, null, complaint);

    listener.handleFailure(event);

    verify(historyService).updateStatus(NOTIFICATION_ID, FAILED, message);
  }

  @Test
  void shouldNotHandleFailureWhenDeliveryEvent() {
    Mail mail = new Mail(List.of(new MailHeader("NotificationId", NOTIFICATION_ID)));
    EmailEvent event = new EmailEvent("Delivery", mail, null, null);

    listener.handleFailure(event);

    verifyNoInteractions(historyService);
  }
}
