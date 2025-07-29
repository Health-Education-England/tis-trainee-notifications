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

package uk.nhs.tis.trainee.notifications.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.model.ObjectIdWrapper;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.MessageSendingService;

class ScheduledEmailSenderTest {

  private ScheduledEmailSender job;

  private HistoryService historyService;
  private MessageSendingService messageService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    messageService = mock(MessageSendingService.class);

    job = new ScheduledEmailSender(historyService, messageService);
  }

  @Test
  void shouldNotSendMessagesWhenNoOverdueNotifications() {
    when(historyService.findAllOverdue()).thenReturn(List.of());

    job.execute();

    verifyNoInteractions(messageService);
  }

  @Test
  void shouldSendMessagesWhenOverdueNotifications() {
    ObjectId id = ObjectId.get();
    when(historyService.findAllOverdue()).thenReturn(List.of(
        new ObjectIdWrapper(id)
    ));

    job.execute();

    ArgumentCaptor<List<ObjectIdWrapper>> idWrappersCaptor = ArgumentCaptor.captor();
    verify(messageService).sendToOutbox(idWrappersCaptor.capture());

    List<ObjectIdWrapper> idWrappers = idWrappersCaptor.getValue();
    assertThat("Unexpected ID count.", idWrappers, hasSize(1));
    assertThat("Unexpected ID count.", idWrappers.get(0).id(), is(id));
  }
}
