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

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.service.EventBroadcastService;

class BroadcastNotificationHistoryTest {

  private BroadcastNotificationHistory migration;

  private MongoTemplate template;
  private EventBroadcastService service;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    service = mock(EventBroadcastService.class);
    migration = new BroadcastNotificationHistory(template, service);
  }

  @Test
  void shouldBroadcastNotificationHistory() {
    History history = new History(null, null, null, null,
        null, null, null, null, null, null);
    when(template.stream(any(), eq(History.class), eq("History"))).thenReturn(Stream.of(history));

    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(template).stream(queryCaptor.capture(), eq(History.class), eq("History"));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected query.", query.equals(new Query()), is(true));

    verify(service).publishNotificationsEvent(history);
    verifyNoMoreInteractions(service);
  }

  @Test
  void shouldCatchMongoExceptionNotThrowIt() {
    when(template.stream(any(), eq(History.class))).thenThrow(new MongoException("exception"));
    Assertions.assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
