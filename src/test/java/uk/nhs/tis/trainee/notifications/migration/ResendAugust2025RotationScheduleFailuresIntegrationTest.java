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
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.Mockito.mock;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.WELCOME;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ResendAugust2025RotationScheduleFailuresIntegrationTest {

  private static final String LOG_MESSAGE = "Found 2 qualifying missed schedules.";

  private static final Logger log = (Logger) LoggerFactory.getLogger(
      ResendAugust2025RotationScheduleFailures.class);

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @MockitoSpyBean
  private MongoTemplate mongoTemplate;

  @MockitoBean
  private SqsTemplate sqsTemplate;

  private ResendAugust2025RotationScheduleFailures migrator;
  private List<ILoggingEvent> logsList;

  @BeforeEach
  void setUp() {
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    log.addAppender(listAppender);
    logsList = listAppender.list;

    mongoTemplate.dropCollection(History.class);
    migrator = new ResendAugust2025RotationScheduleFailures(mongoTemplate,
        mock(HistoryService.class), mock(NotificationService.class));
  }

  @ParameterizedTest
  @EnumSource(value = MessageType.class, mode = EXCLUDE, names = "EMAIL")
  void shouldFindByRecipientType(MessageType type) {
    createMissedSchedule(null, null, null);
    createMissedSchedule(EMAIL, null, null);
    createMissedSchedule(type, null, null);

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = EXCLUDE, names = "SCHEDULED")
  void shouldFindByStatus(NotificationStatus status) {
    createMissedSchedule(null, null, null);
    createMissedSchedule(null, SCHEDULED, null);
    createMissedSchedule(null, status, null);

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  @Test
  void shouldFindBySentAt() {
    createMissedSchedule(null, null, Instant.parse("2025-04-30T23:59:59Z"));
    createMissedSchedule(null, null, Instant.parse("2025-05-01T00:00:00Z"));
    createMissedSchedule(null, null, Instant.now()
        .truncatedTo(ChronoUnit.DAYS).minusMillis(1));
    createMissedSchedule(null, null, Instant.now().truncatedTo(ChronoUnit.DAYS));

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  /**
   * Create and persist a missed schedule, uses valid defaults for null parameters.
   *
   * @param type   The message type.
   * @param status The history status.
   * @param sentAt The sent-at timestamp.
   */
  private void createMissedSchedule(@Nullable MessageType type, @Nullable NotificationStatus status,
      @Nullable Instant sentAt) {
    History history = History.builder()
        .id(ObjectId.get())
        .type(WELCOME)
        .recipient(new RecipientInfo(UUID.randomUUID().toString(), type != null ? type : EMAIL,
            "trainee@example.com"))
        .status(status != null ? status : SCHEDULED)
        .sentAt(sentAt != null ? sentAt : Instant.parse("2025-05-01T12:00:00Z"))
        .template(new TemplateInfo("template", "v1.2.3", Map.of()))
        .tisReference(new TisReferenceInfo(TisReferenceType.PLACEMENT, "123"))
        .build();
    mongoTemplate.save(history);
  }
}
