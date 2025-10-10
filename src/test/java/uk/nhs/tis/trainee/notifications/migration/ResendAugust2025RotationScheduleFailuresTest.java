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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.tis.trainee.notifications.matcher.DateCloseTo;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class ResendAugust2025RotationScheduleFailuresTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_EMAIL = "trainee@example.com";
  private static final String REFERENCE_ID = UUID.randomUUID().toString();

  private MongoTemplate mongoTemplate;

  private HistoryService historyService;

  private NotificationService notificationService;

  private ResendAugust2025RotationScheduleFailures migrator;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    historyService = mock(HistoryService.class);
    notificationService = mock(NotificationService.class);
    migrator = new ResendAugust2025RotationScheduleFailures(mongoTemplate,
        historyService, notificationService);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldMigrateScheduledEmails(NotificationType type) {
    ObjectId historyId = ObjectId.get();
    History failure = History.builder()
        .id(historyId)
        .type(type)
        .tisReference(new TisReferenceInfo(PLACEMENT, REFERENCE_ID))
        .recipient(new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_EMAIL))
        .template(new TemplateInfo("template-name", "v1.2.3", Map.of(
            "key1", "value1",
            "key2", "value2"
        )))
        .build();
    when(mongoTemplate.find(any(), eq(History.class))).thenReturn(List.of(failure));

    migrator.migrate();

    String jobId = type + "-" + REFERENCE_ID;

    ArgumentCaptor<Map<String, Object>> jobDataCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Date> whenCaptor = ArgumentCaptor.captor();
    verify(notificationService).scheduleNotification(eq(jobId), jobDataCaptor.capture(),
        whenCaptor.capture(), eq(86400L));

    Map<String, Object> jobData = jobDataCaptor.getValue();
    assertThat("Unexpected job data count.", jobData.keySet(), hasSize(2));
    assertThat("Unexpected job data.", jobData.get("key1"), is("value1"));
    assertThat("Unexpected job data.", jobData.get("key2"), is("value2"));

    assertThat("Unexpected send date.", whenCaptor.getValue(),
        DateCloseTo.closeTo(Instant.now().getEpochSecond(), 1));

    verify(historyService).deleteHistoryForTrainee(historyId, TRAINEE_ID);
  }

  @Test
  void rollback() {
    Mockito.clearInvocations(mongoTemplate);
    migrator.rollback();

    verifyNoInteractions(mongoTemplate);
    verifyNoInteractions(historyService);
  }
}
