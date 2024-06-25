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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;

@SpringBootTest(properties = {"embedded.containers.enabled=true", "embedded.mongodb.enabled=true"})
@ActiveProfiles({"mongodb", "test"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PopulateNotificationHistoryStatusIntegrationTest {

  @SpyBean
  private MongoTemplate mongoTemplate;

  private PopulateNotificationHistoryStatus migrator;

  @BeforeEach
  void setUp() {
    migrator = new PopulateNotificationHistoryStatus(mongoTemplate);
  }

  @Test
  void shouldMigrateHistoryWithEmptyStatus() {
    History history = History.builder()
        .type(COJ_CONFIRMATION)
        .sentAt(Instant.now())
        .readAt(Instant.now().plus(Duration.ofDays(1)))
        .build();
    history = mongoTemplate.save(history);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected status.", migratedHistory, notNullValue());
    assertThat("Unexpected status.", migratedHistory.status(), is(SENT));
  }

  @Test
  void shouldNotMigrateHistoryWithPopulatedStatus() {
    History history = History.builder()
        .type(COJ_CONFIRMATION)
        .sentAt(Instant.now())
        .readAt(Instant.now().plus(Duration.ofDays(1)))
        .status(FAILED)
        .build();
    history = mongoTemplate.save(history);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected status.", migratedHistory, notNullValue());
    assertThat("Unexpected status.", migratedHistory.status(), is(FAILED));
  }

  @Test
  void rollback() {
    Mockito.clearInvocations(mongoTemplate);
    migrator.rollback();
    verifyNoInteractions(mongoTemplate);
  }
}
