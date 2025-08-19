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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FailHistoricMissedSchedulesIntegrationTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @Autowired
  private MongoTemplate mongoTemplate;

  @MockBean
  private SqsTemplate sqsTemplate;

  private FailHistoricMissedSchedules migrator;

  @Value("${application.timezone}")
  private String timezone;

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(History.class);

    Environment env = mock(Environment.class);
    when(env.getProperty("application.timezone")).thenReturn(timezone);

    migrator = new FailHistoricMissedSchedules(mongoTemplate, env);
  }

  @Test
  void migrate() {
    final History preRetryable = mongoTemplate.insert(History.builder()
        .status(SCHEDULED)
        .statusDetail("scheduled")
        .sentAt(Instant.parse("2025-05-01T00:00:00Z"))
        .build()
    );

    final History preNonRetryable = mongoTemplate.insert(History.builder()
        .status(SCHEDULED)
        .statusDetail("scheduled")
        .sentAt(Instant.parse("2025-04-29T23:59:59Z"))
        .build()
    );

    migrator.migrate();

    History postRetryable = mongoTemplate.findById(preRetryable.id(), History.class);
    assertThat("Unexpected status.", postRetryable.status(), is(SCHEDULED));
    assertThat("Unexpected status detail.", postRetryable.statusDetail(), is("scheduled"));

    History postNonRetryable = mongoTemplate.findById(preNonRetryable.id(), History.class);
    assertThat("Unexpected status.", postNonRetryable.status(), is(FAILED));
    assertThat("Unexpected status detail.", postNonRetryable.statusDetail(),
        is("Missed Schedule: Programme already started"));
  }
}
