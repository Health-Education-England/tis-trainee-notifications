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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EventBroadcastService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BroadcastLtftSubmittedTraineeNotificationHistoryTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @SpyBean
  private MongoTemplate mongoTemplate;

  private BroadcastLtftSubmittedTraineeNotificationHistory migration;

  private EventBroadcastService service;

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(History.class);
    service = mock(EventBroadcastService.class);
    migration = new BroadcastLtftSubmittedTraineeNotificationHistory(mongoTemplate, service);
  }

  @Test
  void shouldBroadcastLtftSubmittedTraineeNotificationHistory() {
    History historyToBroadcast = History.builder()
        .type(NotificationType.LTFT_SUBMITTED_TRAINEE)
        .build();
    historyToBroadcast = mongoTemplate.save(historyToBroadcast);
    History historyToNotBroadcast = History.builder()
        .type(NotificationType.LTFT_SUBMITTED_TPD)
        .build();
    mongoTemplate.save(historyToNotBroadcast);

    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).stream(queryCaptor.capture(), eq(History.class), eq("History"));

    Query query = queryCaptor.getValue();
    Query expectedQuery = Query.query(Criteria.where("type").is(LTFT_SUBMITTED_TRAINEE));
    assertThat("Unexpected query.", query.equals(expectedQuery), is(true));

    verify(service).publishNotificationsEvent(historyToBroadcast);
    verifyNoMoreInteractions(service);
  }

  @Test
  void shouldCatchMongoExceptionNotThrowIt() {
    Query expectedQuery = Query.query(Criteria.where("type").is(LTFT_SUBMITTED_TRAINEE));
    doThrow(new MongoException("exception"))
        .when(mongoTemplate).stream(expectedQuery, History.class, "History");

    Assertions.assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldNotAttemptRollback() {
    Mockito.clearInvocations(mongoTemplate);
    migration.rollback();
    verifyNoInteractions(mongoTemplate);
  }
}