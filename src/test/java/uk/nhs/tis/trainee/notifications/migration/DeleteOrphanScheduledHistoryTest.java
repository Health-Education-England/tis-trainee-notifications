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

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;

import com.mongodb.MongoException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DeleteOrphanScheduledHistoryTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @SpyBean
  private MongoTemplate mongoTemplate;

  private DeleteOrphanScheduledHistory migrator;

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(History.class);
    migrator = new DeleteOrphanScheduledHistory(mongoTemplate);
  }

  @Test
  void shouldDeletePastScheduledHistory() {
    Instant past = Instant.now().minus(Duration.ofDays(1));
    Instant future = Instant.now().plus(Duration.ofDays(1));

    History pastHistory = History.builder()
        .sentAt(past)
        .status(SCHEDULED)
        .build();
    pastHistory = mongoTemplate.save(pastHistory);

    History futureHistory = History.builder()
        .sentAt(future)
        .status(SCHEDULED)
        .build();
    futureHistory = mongoTemplate.save(futureHistory);

    History nonScheduledHistory = History.builder()
        .sentAt(future)
        .status(SENT)
        .build();
    nonScheduledHistory = mongoTemplate.save(nonScheduledHistory);

    migrator.migrate();

    List<History> migratedHistories = mongoTemplate.findAll(History.class);

    assertThat("Unexpected count.", migratedHistories.size(), is(2));
    assertThat("Unexpected existence of past scheduled history.",
        mongoTemplate.findById(pastHistory.id(), History.class), nullValue());
    assertThat("Unexpected existence of future history.",
        mongoTemplate.findById(futureHistory.id(), History.class), notNullValue());
    assertThat("Unexpected existence of non-scheduled history.",
        mongoTemplate.findById(nonScheduledHistory.id(), History.class), notNullValue());
  }

  @Test
  void shouldNotFailMigration() {
    doThrow(new MongoException("error")).when(mongoTemplate)
        .remove(any(), eq(History.class));

    Assertions.assertDoesNotThrow(() -> migrator.migrate());
  }

  @Test
  void rollback() {
    Mockito.clearInvocations(mongoTemplate);
    migrator.rollback();
    verifyNoInteractions(mongoTemplate);
  }
}
