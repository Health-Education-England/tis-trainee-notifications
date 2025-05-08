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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED_TRAINEE;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ResetLtftSubmittedTraineeHistoryTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @SpyBean
  private MongoTemplate mongoTemplate;

  private ResetLtftSubmittedTraineeHistory migrator;

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(History.class);
    migrator = new ResetLtftSubmittedTraineeHistory(mongoTemplate);
  }

  @Test
  void shouldResetHistoryWithLtftTraineeSubmittedType() {
    History history = History.builder()
        .type(LTFT_SUBMITTED_TRAINEE)
        .build();
    history = mongoTemplate.save(history);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected missing history.", migratedHistory, notNullValue());
    assertThat("Unexpected type.", migratedHistory.type(), is(LTFT_SUBMITTED));
  }

  @Test
  void shouldNotResetHistoryWithoutLtftSubmittedTraineeType() {
    History history = History.builder()
        .type(LTFT_SUBMITTED)
        .build();
    history = mongoTemplate.save(history);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected missing history.", migratedHistory, notNullValue());
    assertThat("Unexpected type.", migratedHistory.type(), is(LTFT_SUBMITTED));
  }

  @Test
  void shouldUseCorrectQueryToFindAndUpdateHistory() {
    migrator.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();

    verify(mongoTemplate).updateMulti(queryCaptor.capture(), updateCaptor.capture(),
        eq(History.class));

    Query expectedQuery = Query.query(Criteria.where("type").is(LTFT_SUBMITTED_TRAINEE));
    Update expectedUpdate = Update.update("type", LTFT_SUBMITTED);

    assertThat("Unexpected query.", queryCaptor.getValue().getQueryObject(),
        is(expectedQuery.getQueryObject()));
    assertThat("Unexpected update.", updateCaptor.getValue().getUpdateObject(),
        is(expectedUpdate.getUpdateObject()));
  }

  @Test
  void shouldCatchMongoExceptionNotThrowIt() {
    Query expectedQuery = Query.query(Criteria.where("type").is(LTFT_SUBMITTED_TRAINEE));
    Update expectedUpdate = Update.update("type", LTFT_SUBMITTED);

    when(mongoTemplate.updateMulti(expectedQuery, expectedUpdate, History.class))
        .thenThrow(new MongoException("exception"));
    Assertions.assertDoesNotThrow(() -> migrator.migrate());
  }

  @Test
  void rollback() {
    Mockito.clearInvocations(mongoTemplate);
    migrator.rollback();
    verifyNoInteractions(mongoTemplate);
  }
}
