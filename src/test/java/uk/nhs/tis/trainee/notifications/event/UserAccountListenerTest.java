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

package uk.nhs.tis.trainee.notifications.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.WELCOME;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.notifications.service.InAppService;

class UserAccountListenerTest {

  private static final String VERSION = "v1.2.3";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "trainee@example.com";

  private UserAccountListener listener;
  private InAppService inAppService;

  @BeforeEach
  void setUp() {
    inAppService = mock(InAppService.class);
    listener = new UserAccountListener(inAppService, VERSION);
  }

  @Test
  void shouldCreateWelcomeNotificationWhenAccountConfirmed() {
    AccountConfirmedEvent event = new AccountConfirmedEvent(USER_ID, TRAINEE_ID, EMAIL);

    listener.handleAccountConfirmation(event);

    verify(inAppService).createNotifications(TRAINEE_ID, null, WELCOME, VERSION, Map.of());
  }
}
