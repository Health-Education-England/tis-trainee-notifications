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

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.AccountEvent;
import uk.nhs.tis.trainee.notifications.service.UserAccountSyncService;

/**
 * A listener for User Management account events, used to maintain a local copy of user accounts.
 */
@Slf4j
@Component
public class AccountEventListener {

  private static final String EVENT_TYPE_HEADER = "event_type";

  private final UserAccountSyncService service;

  /**
   * Construct a listener for account events.
   *
   * @param service The service to use for syncing user accounts.
   */
  public AccountEventListener(UserAccountSyncService service) {
    this.service = service;
  }

  /**
   * Handle account events, upserting or deleting the account depending on the event type.
   *
   * @param event     The account event.
   * @param eventType The type of the event, UPDATE or DELETE.
   */
  @SqsListener("${application.queues.user-details}")
  public void handleAccountEvent(AccountEvent event,
      @Header(value = EVENT_TYPE_HEADER, required = false) String eventType) {
    log.info("Handling account event of type '{}' for account {}.", eventType, event.id());

    switch (eventType == null ? "" : eventType) {
      case "UPDATE" -> service.upsert(event);
      case "DELETE" -> service.deleteById(event.id());
      default -> log.warn("Unexpected account event type '{}', ignoring.", eventType);
    }
  }
}

