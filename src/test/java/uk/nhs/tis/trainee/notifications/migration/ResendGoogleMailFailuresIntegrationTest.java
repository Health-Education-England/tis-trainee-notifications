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
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.WELCOME;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ResendGoogleMailFailuresIntegrationTest {

  private static final String LOG_MESSAGE = "Found 2 qualifying failure(s).";

  private static final Logger log = (Logger) LoggerFactory.getLogger(
      ResendGoogleMailFailures.class);

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @SpyBean
  private MongoTemplate mongoTemplate;

  private ResendGoogleMailFailures migrator;
  private List<ILoggingEvent> logsList;

  @BeforeEach
  void setUp() {
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    log.addAppender(listAppender);
    logsList = listAppender.list;

    mongoTemplate.dropCollection(History.class);
    migrator = new ResendGoogleMailFailures(mongoTemplate, null, null, null);
  }

  @ParameterizedTest
  @EnumSource(value = MessageType.class, mode = EXCLUDE, names = "EMAIL")
  void shouldFindByRecipientType(MessageType type) {
    createFailure(null, null, null, null, null);
    createFailure(EMAIL, null, null, null, null);
    createFailure(type, null, null, null, null);

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  @Test
  void shouldFindByRecipientContact() {
    createFailure(null, null, null, null, null);
    createFailure(null, ".@gmail.com", null, null, null);
    createFailure(null, ".@GMAIL.COM", null, null, null);
    createFailure(null, ".@googlemail.com", null, null, null);
    createFailure(null, ".@GOOGLEMAIL.COM", null, null, null);
    createFailure(null, ".@example.com", null, null, null);
    createFailure(null, ".@EXAMPLE.COM", null, null, null);

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is("Found 5 qualifying failure(s)."));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = EXCLUDE, names = "FAILED")
  void shouldFindByStatus(NotificationStatus status) {
    createFailure(null, null, null, null, null);
    createFailure(null, null, FAILED, null, null);
    createFailure(null, null, status, null, null);

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  @Test
  void shouldFindByStatusDetail() {
    createFailure(null, null, null, null, null);
    createFailure(null, null, null, "Bounce: Transient - General", null);
    createFailure(null, null, null, "Some other status detail", null);

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  @Test
  void shouldFindBySentAt() {
    createFailure(null, null, null, null, Instant.parse("2025-05-13T23:59:59Z"));
    createFailure(null, null, null, null, Instant.parse("2025-05-14T00:00:00Z"));
    createFailure(null, null, null, null, Instant.parse("2025-05-15T23:59:59Z"));
    createFailure(null, null, null, null, Instant.parse("2025-05-16T00:00:00Z"));

    migrator.migrate();

    String formattedMessage = logsList.get(0).getFormattedMessage();
    assertThat("Unexpected log message.", formattedMessage, is(LOG_MESSAGE));
  }

  /**
   * Create and persist a failure, uses valid defaults for null parameters.
   *
   * @param type         The message type.
   * @param contact      The contact email address.
   * @param status       The history status.
   * @param statusDetail The status detail text.
   * @param sentAt       The sent-at timestamp.
   */
  private void createFailure(@Nullable MessageType type, @Nullable String contact,
      @Nullable NotificationStatus status, @Nullable String statusDetail,
      @Nullable Instant sentAt) {
    History history = History.builder()
        .id(ObjectId.get())
        .type(WELCOME)
        .recipient(new RecipientInfo(UUID.randomUUID().toString(), type != null ? type : EMAIL,
            contact != null ? contact : "_@gmail.com"))
        .status(status != null ? status : FAILED)
        .statusDetail(statusDetail != null ? statusDetail : "Bounce: Transient - General")
        .sentAt(sentAt != null ? sentAt : Instant.parse("2025-05-14T12:00:00Z"))
        .build();
    mongoTemplate.save(history);
  }
}
