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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.model.GmcDetails;
import uk.nhs.tis.trainee.notifications.model.GmcUpdateEvent;
import uk.nhs.tis.trainee.notifications.model.LocalOffice;
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

    Set<LocalOffice> localOffices = new HashSet<>();
    localOffices.add(new LocalOffice("email", "name"));
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(localOffices);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    assertThrows(MessagingException.class, () -> listener.handleGmcUpdate(event));
  }

  @Test
  void shouldNotSendEmailIfNullLocalOffice() throws MessagingException {
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(null);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(messagingControllerService, never()).isMessagingEnabled(any());
  }

  @Test
  void shouldNotSendEmailIfNoLocalOffice() throws MessagingException {
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(new HashSet<>());
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(messagingControllerService, never()).isMessagingEnabled(any());
  }

  @Test
  void shouldNotSendEmailIfLocalOfficeHasNoEmail() throws MessagingException {
    Set<LocalOffice> localOffices = new HashSet<>();
    localOffices.add(new LocalOffice(null, "name"));
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(localOffices);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService, never())
        .sendMessage(any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void shouldSendOneEmailIfLocalOfficesHaveSameEmail() throws MessagingException {
    Set<LocalOffice> localOffices = new HashSet<>();
    localOffices.add(new LocalOffice("email", "name"));
    localOffices.add(new LocalOffice("email", "name2"));
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(localOffices);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService)
        .sendMessage(any(), eq("email"), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void shouldSendMultipleEmailIfLocalOfficesHaveDifferentEmail() throws MessagingException {
    Set<LocalOffice> localOffices = new HashSet<>();
    localOffices.add(new LocalOffice("email", "name"));
    localOffices.add(new LocalOffice("email2", "name2"));
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(localOffices);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(true);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verify(emailService)
        .sendMessage(any(), eq("email"), any(), any(), any(), any(), anyBoolean());
    verify(emailService)
        .sendMessage(any(), eq("email2"), any(), any(), any(), any(), anyBoolean());
    verifyNoMoreInteractions(emailService);
  }

  @Test
  void shouldNotSendEmailIfMessagingNotEnabled() throws MessagingException {
    Set<LocalOffice> localOffices = new HashSet<>();
    localOffices.add(new LocalOffice("email", "name"));
    when(notificationService.getTraineeLocalOffices(any())).thenReturn(localOffices);
    when(messagingControllerService.isMessagingEnabled(any())).thenReturn(false);

    GmcUpdateEvent event
        = new GmcUpdateEvent("traineeId", new GmcDetails("1234567", "CONFIRMED"));

    listener.handleGmcUpdate(event);

    verifyNoInteractions(emailService);
  }

}
