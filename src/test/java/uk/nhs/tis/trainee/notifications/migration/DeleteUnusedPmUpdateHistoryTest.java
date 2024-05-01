/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class DeleteUnusedPmUpdateHistoryTest {

  private DeleteUnusedPmUpdateHistory migration;

  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    migration = new DeleteUnusedPmUpdateHistory(template);
  }

  @Test
  void shouldDeleteUnusedPmUpdateHistory() {
    int expectedDeletes = NotificationType.getProgrammeUpdateNotificationTypes().size();
    when(template.remove(any(), eq(History.class))).thenReturn(DeleteResult.acknowledged(1L));
    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(template, times(expectedDeletes)).remove(queryCaptor.capture(), eq(History.class));

    List<Query> queries = queryCaptor.getAllValues();
    for (Query query : queries) {
      Document queryObject = query.getQueryObject();
      NotificationType notification = queryObject.get("type", NotificationType.class);
      assertThat("Unexpected query type.",
          NotificationType.getProgrammeUpdateNotificationTypes().contains(notification), is(true));
    }
  }

  @Test
  void shouldCatchMongoExceptionNotThrowIt() {
    when(template.remove(any(), eq(History.class))).thenThrow(new MongoException("exception"));
    Assertions.assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
