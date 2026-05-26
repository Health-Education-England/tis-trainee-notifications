/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_POG_MONTH_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_POG_MONTH_6;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DeleteDentistPogNotificationsIntegrationTest {

  private static final String DENTAL_PROGRAMME = "Dental Public Health";
  private static final String NON_DENTAL_PROGRAMME = "General Practice";

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @Autowired
  private MongoTemplate mongoTemplate;

  @MockitoBean
  private SqsTemplate sqsTemplate;

  private DeleteDentistPogNotifications migrator;

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(History.class);
    migrator = new DeleteDentistPogNotifications(mongoTemplate);
  }

  @Test
  void shouldDeleteNonScheduledDentalPogHistory() {
    History sentDentalPog12 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_12)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SENT)
        .build());

    History sentDentalPog6 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_6)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SENT)
        .build());

    migrator.migrate();

    assertThat("Expected sent dental POG month 12 to be deleted.",
        mongoTemplate.findById(sentDentalPog12.id(), History.class), nullValue());
    assertThat("Expected sent dental POG month 6 to be deleted.",
        mongoTemplate.findById(sentDentalPog6.id(), History.class), nullValue());
  }

  @Test
  void shouldNotDeleteScheduledDentalPogHistory() {
    History scheduledDentalPog12 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_12)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SCHEDULED)
        .build());

    migrator.migrate();

    assertThat("Expected scheduled dental POG history to be retained.",
        mongoTemplate.findById(scheduledDentalPog12.id(), History.class), notNullValue());
  }

  @Test
  void shouldNotDeleteNonDentalPogHistory() {
    History sentNonDentalPog = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_12)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", NON_DENTAL_PROGRAMME)))
        .status(SENT)
        .build());

    migrator.migrate();

    assertThat("Expected non-dental POG history to be retained.",
        mongoTemplate.findById(sentNonDentalPog.id(), History.class), notNullValue());
  }

  @Test
  void shouldNotDeleteNonPogDentalHistory() {
    History sentDentalNonPog = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_CREATED)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SENT)
        .build());

    migrator.migrate();

    assertThat("Expected non-POG dental history to be retained.",
        mongoTemplate.findById(sentDentalNonPog.id(), History.class), notNullValue());
  }

  @Test
  void shouldDeleteAllMatchingAndRetainNonMatching() {
    // Should be deleted: non-SCHEDULED, dental programme, POG type
    final History toDelete1 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_12)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SENT)
        .build());
    final History toDelete2 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_6)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", "Orthodontics")))
        .status(SENT)
        .build());

    // Should be retained: SCHEDULED
    final History toRetain1 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_12)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SCHEDULED)
        .build());

    // Should be retained: non-dental programme
    final History toRetain2 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_POG_MONTH_12)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", NON_DENTAL_PROGRAMME)))
        .status(SENT)
        .build());

    // Should be retained: non-POG type
    final History toRetain3 = mongoTemplate.insert(History.builder()
        .type(PROGRAMME_CREATED)
        .template(new TemplateInfo(null, null, Map.of("ProgrammeName", DENTAL_PROGRAMME)))
        .status(SENT)
        .build());

    migrator.migrate();

    List<History> remaining = mongoTemplate.findAll(History.class);
    assertThat("Unexpected remaining count.", remaining.size(), is(3));

    assertThat("Expected record to be deleted.",
        mongoTemplate.findById(toDelete1.id(), History.class), nullValue());
    assertThat("Expected record to be deleted.",
        mongoTemplate.findById(toDelete2.id(), History.class), nullValue());
    assertThat("Expected scheduled record to be retained.",
        mongoTemplate.findById(toRetain1.id(), History.class), notNullValue());
    assertThat("Expected non-dental record to be retained.",
        mongoTemplate.findById(toRetain2.id(), History.class), notNullValue());
    assertThat("Expected non-POG record to be retained.",
        mongoTemplate.findById(toRetain3.id(), History.class), notNullValue());
  }
}

