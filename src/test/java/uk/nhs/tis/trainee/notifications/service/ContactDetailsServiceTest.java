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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.model.ContactDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class ContactDetailsServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private static final String TEMPLATE_NAME = "test/template";
  private static final String TEMPLATE_VERSION = "v1.2.3";
  private static final Map<String, Object> TEMPLATE_VARIABLES = Map.of("key1", "value1");

  private static final TisReferenceType TIS_REFERENCE_TYPE = TisReferenceType.PROGRAMME_MEMBERSHIP;
  private static final String TIS_REFERENCE_ID = UUID.randomUUID().toString();

  ContactDetailsService service;
  HistoryService historyService;
  EmailService emailService;
  NotificationService notificationService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    emailService = mock(EmailService.class);
    notificationService = mock(NotificationService.class);
    service = new ContactDetailsService(historyService, emailService);
  }

  @Test
  void shouldNotResendFailedEmailsWhenLatestEmailIsNull() {
    ContactDetails updatedContactDetails = new ContactDetails();
    updatedContactDetails.setTisId(TRAINEE_ID);
    service.updateContactDetails(updatedContactDetails);

    verifyNoInteractions(emailService);
  }

  @Test
  void shouldResendFailedEmailTypeMessagesWithDifferentEmailOnly() throws MessagingException {
    History historySameEmail = buildHistory(TRAINEE_CONTACT, EMAIL);
    History historyOtherEmail = buildHistory("different email", EMAIL);
    History historyNullEmail = buildHistory(null, EMAIL);
    History historySameInApp = buildHistory(TRAINEE_CONTACT, IN_APP);
    History historyOtherInApp = buildHistory("different email", IN_APP);
    History historyNullInApp = buildHistory(null, IN_APP);

    when(historyService.findAllFailedForTrainee(TRAINEE_ID)).thenReturn(
        List.of(historySameEmail, historyOtherEmail, historyNullEmail, historySameInApp,
            historyOtherInApp, historyNullInApp));

    ContactDetails updatedContactDetails = new ContactDetails();
    updatedContactDetails.setEmail(TRAINEE_CONTACT);
    updatedContactDetails.setTisId(TRAINEE_ID);

    service.updateContactDetails(updatedContactDetails);

    verify(emailService).resendMessage(historyOtherEmail, TRAINEE_CONTACT);
    verify(emailService).resendMessage(historyNullEmail, TRAINEE_CONTACT);
    verifyNoMoreInteractions(emailService);
  }

  @Test
  void shouldRethrowMessagingExceptions() throws MessagingException {
    History historyOtherEmail = buildHistory("different email", EMAIL);
    MessagingException messagingException = new MessagingException("error");

    when(historyService.findAllFailedForTrainee(TRAINEE_ID)).thenReturn(
        List.of(historyOtherEmail));
    doThrow(messagingException).when(emailService).resendMessage(any(), any());

    ContactDetails updatedContactDetails = new ContactDetails();
    updatedContactDetails.setEmail(TRAINEE_CONTACT);
    updatedContactDetails.setTisId(TRAINEE_ID);

    Exception exception = assertThrows(RuntimeException.class,
        () -> service.updateContactDetails(updatedContactDetails));

    String exceptionMessage = exception.getMessage();
    String expectedMessage = messagingException.getClass().getCanonicalName() + ": "
        + messagingException.getMessage();
    assertThat("Unexpected exception message", exceptionMessage, is(expectedMessage));
  }

  /**
   * Helper function to build a standard history object with variable contact email and message
   * type.
   *
   * @param contact     The contact email to use.
   * @param messageType The message type to use.
   * @return The history object.
   */
  private History buildHistory(String contact, MessageType messageType) {
    RecipientInfo recipient = new RecipientInfo(TRAINEE_ID, messageType, contact);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);
    TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(TIS_REFERENCE_TYPE, TIS_REFERENCE_ID);

    ObjectId id1 = ObjectId.get();
    return new History(id1, tisReferenceInfo, PROGRAMME_CREATED, recipient,
        templateInfo, Instant.MIN, Instant.MAX, FAILED, null, null);
  }
}
