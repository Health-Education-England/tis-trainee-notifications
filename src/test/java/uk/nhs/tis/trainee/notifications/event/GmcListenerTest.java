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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.event.GmcListener.FAMILY_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcListener.GIVEN_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcListener.GMC_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcListener.GMC_STATUS_FIELD;
import static uk.nhs.tis.trainee.notifications.event.GmcListener.TRAINEE_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.GMC_UPDATE;

import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.GmcDetails;
import uk.nhs.tis.trainee.notifications.model.GmcUpdateEvent;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContact;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.MessagingControllerService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class GmcListenerTest {

  private static final String VERSION = "v1.2.3";

  private GmcListener listener;
  private EmailService emailService;
  private NotificationService notificationService;
  private MessagingControllerService messagingControllerService;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    notificationService = mock(NotificationService.class);
    messagingControllerService = mock(MessagingControllerService.class);
    listener = new GmcListener(emailService, notificationService, messagingControllerService,
        VERSION);
  }

  @Test
  void shouldThrowExceptionWhenGmcUpdatedAndSendingFails() throws MessagingException {
    doThrow(MessagingException.class).when(emailService)
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());

    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact", "local office"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    assertThrows(MessagingException.class, () -> listener.handleGmcUpdate(event));
  }

  @Test
  void shouldNotSendEmailIfNullLocalOffice() throws MessagingException {
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(null);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(messagingControllerService, never()).isMessagingEnabled(any());
  }

  @Test
  void shouldNotSendEmailIfNoLocalOffice() throws MessagingException {
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(new HashSet<>());

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(messagingControllerService, never()).isMessagingEnabled(any());
  }

  @Test
  void shouldNotSendEmailIfLocalOfficeHasNoEmail() throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact(null, "local office"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService, never())
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void shouldNotSendEmailIfLocalOfficeContactNotEmail() throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("https://url.com", "local office"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(false);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService, never())
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void shouldSendOneEmailIfLocalOfficesHaveSameEmail() throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact", "local office"));
    localOfficeContacts.add(new LocalOfficeContact("contact", "name2"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService)
        .sendMessage(any(), eq("contact"), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void shouldSendMultipleEmailIfLocalOfficesHaveDifferentEmail() throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact", "local office"));
    localOfficeContacts.add(new LocalOfficeContact("contact2", "name2"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService)
        .sendMessage(any(), eq("contact"), any(), any(), any(), any(), anyBoolean());
    verify(emailService)
        .sendMessage(any(), eq("contact2"), any(), any(), any(), any(), anyBoolean());
    verifyNoMoreInteractions(emailService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldLogEmailIfMessagingNotEnabled(boolean isMessagingEnabled) throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact", "local office"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(isMessagingEnabled);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService)
        .sendMessage(any(), eq("contact"), any(), any(), any(), any(), eq(!isMessagingEnabled));
  }

  @Test
  void shouldIncludeUserDetailsInTemplateIfAvailable() throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("contact", "local office"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    UserDetails userDetails
        = new UserDetails(true, "traineeemail", "title", "family", "given", "1111111");
    when(notificationService.getTraineeDetails(any())).thenReturn(userDetails);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    Map<String, Object> expectedTemplateVariables = new HashMap<>();
    expectedTemplateVariables.put(TRAINEE_ID_FIELD, "traineeId");
    expectedTemplateVariables.put(GIVEN_NAME_FIELD, "given");
    expectedTemplateVariables.put(FAMILY_NAME_FIELD, "family");
    expectedTemplateVariables.put(GMC_NUMBER_FIELD, "1234567");
    expectedTemplateVariables.put(GMC_STATUS_FIELD, "CONFIRMED");

    verify(emailService)
        .sendMessage(eq("traineeId"), eq("contact"), eq(NotificationType.GMC_UPDATED), any(),
            eq(expectedTemplateVariables), eq(null), anyBoolean());
  }

  @Test
  void shouldNotIncludeUserDetailsInTemplateIfNotAvailable() throws MessagingException {
    Set<LocalOfficeContact> localOfficeContacts = new HashSet<>();
    localOfficeContacts.add(new LocalOfficeContact("email@contact.com", "local office"));
    when(notificationService.getTraineeLocalOfficeContacts(any(), eq(GMC_UPDATE)))
        .thenReturn(localOfficeContacts);
    when(notificationService.getTraineeDetails(any())).thenReturn(null);
    when(notificationService.isLocalOfficeContactEmail(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    Map<String, Object> expectedTemplateVariables = new HashMap<>();
    expectedTemplateVariables.put(TRAINEE_ID_FIELD, "traineeId");
    expectedTemplateVariables.put(GIVEN_NAME_FIELD, null);
    expectedTemplateVariables.put(FAMILY_NAME_FIELD, null);
    expectedTemplateVariables.put(GMC_NUMBER_FIELD, "1234567");
    expectedTemplateVariables.put(GMC_STATUS_FIELD, "CONFIRMED");

    verify(emailService)
        .sendMessage(eq("traineeId"), eq("email@contact.com"), eq(NotificationType.GMC_UPDATED),
            any(), eq(expectedTemplateVariables), eq(null), anyBoolean());
  }

}
