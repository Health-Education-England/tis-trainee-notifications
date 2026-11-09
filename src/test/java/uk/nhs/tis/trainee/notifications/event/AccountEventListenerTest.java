/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.notifications.dto.AccountEvent;
import uk.nhs.tis.trainee.notifications.service.UserAccountSyncService;

class AccountEventListenerTest {

  private static final UUID ID = UUID.randomUUID();

  private AccountEventListener listener;
  private UserAccountSyncService service;

  @BeforeEach
  void setUp() {
    service = mock(UserAccountSyncService.class);
    listener = new AccountEventListener(service);
  }

  @Test
  void shouldUpsertAccountWhenUpdateEvent() {
    AccountEvent event = new AccountEvent(ID, "sub", "trainee@example.com", "40", "Anthony",
        "Gilliam");

    listener.handleAccountEvent(event, "UPDATE");

    verify(service).upsert(event);
  }

  @Test
  void shouldDeleteAccountWhenDeleteEvent() {
    AccountEvent event = new AccountEvent(ID, null, null, null, null, null);

    listener.handleAccountEvent(event, "DELETE");

    verify(service).deleteById(ID);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"CREATE", "update", "delete", "UNKNOWN"})
  void shouldIgnoreEventWhenEventTypeUnexpected(String eventType) {
    AccountEvent event = new AccountEvent(ID, "sub", "trainee@example.com", "40", "Anthony",
        "Gilliam");

    listener.handleAccountEvent(event, eventType);

    verifyNoInteractions(service);
  }

  @Test
  void shouldPropagateExceptionWhenUpsertFails() {
    AccountEvent event = new AccountEvent(ID, null, null, null, null, null);
    when(service.upsert(any())).thenThrow(new IllegalStateException("expected"));

    assertThrows(IllegalStateException.class, () -> listener.handleAccountEvent(event, "UPDATE"));
  }
}

