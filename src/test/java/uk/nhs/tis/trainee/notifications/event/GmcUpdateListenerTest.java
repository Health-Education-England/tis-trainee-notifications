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

package uk.nhs.tis.trainee.notifications.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.event.GmcUpdateListener.FAMILY_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcUpdateListener.GIVEN_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcUpdateListener.GMC_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcUpdateListener.GMC_STATUS_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcUpdateListener.TRAINEE_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.GMC_UPDATE;

import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.GmcDetails;
import uk.nhs.tis.trainee.notifications.model.GmcUpdateEvent;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class GmcUpdateListenerTest {

  private static final String VERSION = "v1.2.3";

  private GmcUpdateListener listener;
  private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    notificationService = mock(NotificationService.class);
    listener = new GmcUpdateListener(notificationService, VERSION);
  }

  @Test
  void shouldThrowExceptionWhenGmcUpdatedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(notificationService)
        .sendLocalOfficeMail(any(), any(), any(), any(), any());

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    assertThrows(MessagingException.class, () -> listener.handleGmcUpdate(event));
  }

  @Test
  void shouldIncludeUserDetailsInTemplateIfAvailable() throws MessagingException {
    UserDetails userDetails
        = new UserDetails(true, "traineeemail", "title", "family", "given", "1111111");
    when(notificationService.getTraineeDetails(any())).thenReturn(userDetails);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    Map<String, Object> expectedTemplateVariables = new HashMap<>();
    expectedTemplateVariables.put(TRAINEE_ID_FIELD, "traineeId");
    expectedTemplateVariables.put(GIVEN_NAME_FIELD, "given");
    expectedTemplateVariables.put(FAMILY_NAME_FIELD, "family");
    expectedTemplateVariables.put(GMC_NUMBER_FIELD, "1234567");
    expectedTemplateVariables.put(GMC_STATUS_FIELD, "CONFIRMED");

    verify(notificationService).sendLocalOfficeMail(eq("traineeId"), eq(GMC_UPDATE),
        eq(expectedTemplateVariables), any(), eq(NotificationType.GMC_UPDATED));
  }

  @Test
  void shouldNotIncludeUserDetailsInTemplateIfNotAvailable() throws MessagingException {
    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    Map<String, Object> expectedTemplateVariables = new HashMap<>();
    expectedTemplateVariables.put(TRAINEE_ID_FIELD, "traineeId");
    expectedTemplateVariables.put(GIVEN_NAME_FIELD, null);
    expectedTemplateVariables.put(FAMILY_NAME_FIELD, null);
    expectedTemplateVariables.put(GMC_NUMBER_FIELD, "1234567");
    expectedTemplateVariables.put(GMC_STATUS_FIELD, "CONFIRMED");

    verify(notificationService).sendLocalOfficeMail(eq("traineeId"), eq(GMC_UPDATE),
        eq(expectedTemplateVariables), any(), eq(NotificationType.GMC_UPDATED));
  }

}
