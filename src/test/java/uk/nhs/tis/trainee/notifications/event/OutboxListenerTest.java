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
 */

package uk.nhs.tis.trainee.notifications.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.service.MessageSendingService;

class OutboxListenerTest {

  private static final ObjectId NOTIFICATION_ID = ObjectId.get();

  private OutboxListener listener;
  private MessageSendingService messageSendingService;

  @BeforeEach
  void setUp() {
    messageSendingService = mock(MessageSendingService.class);
    listener = new OutboxListener(messageSendingService);
  }

  @Test
  void handleOutboxMessages() {
    ObjectIdWrapper objectIdWrapper = new ObjectIdWrapper(NOTIFICATION_ID);
    Message<ObjectIdWrapper> message = new GenericMessage<>(objectIdWrapper);

    listener.handleOutboxMessages(message);

    verify(messageSendingService).sendScheduled(objectIdWrapper);
  }
}
