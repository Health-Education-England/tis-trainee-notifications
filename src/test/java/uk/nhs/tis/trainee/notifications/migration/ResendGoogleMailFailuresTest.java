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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.number.IsCloseTo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class ResendGoogleMailFailuresTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_EMAIL = "trainee@example.com";
  private static final String REFERENCE_ID = UUID.randomUUID().toString();

  private MongoTemplate mongoTemplate;

  private EmailService emailService;

  private HistoryService historyService;

  private NotificationService notificationService;

  private ResendGoogleMailFailures migrator;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    emailService = mock(EmailService.class);
    historyService = mock(HistoryService.class);
    notificationService = mock(NotificationService.class);
    migrator = new ResendGoogleMailFailures(mongoTemplate, emailService, historyService,
        notificationService);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotInterruptMigrationForExceptions(NotificationType type)
      throws MessagingException, SchedulerException {
    ObjectId historyId = ObjectId.get();
    History failure = History.builder()
        .id(historyId)
        .type(type)
        .tisReference(new TisReferenceInfo(PLACEMENT, REFERENCE_ID))
        .recipient(new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_EMAIL))
        .template(new TemplateInfo("template-name", "v1.2.3", Map.of()))
        .build();
    when(mongoTemplate.find(any(), eq(History.class))).thenReturn(List.of(failure));

    doThrow(MessagingException.class).when(emailService).resendMessage(any(), any());
    doThrow(SchedulerException.class).when(notificationService)
        .scheduleNotification(any(), any(), any(), anyLong());

    assertDoesNotThrow(() -> migrator.migrate());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotRemoveNotificationsWhenResendFails(NotificationType type)
      throws MessagingException, SchedulerException {
    ObjectId historyId = ObjectId.get();
    History failure = History.builder()
        .id(historyId)
        .type(type)
        .tisReference(new TisReferenceInfo(PLACEMENT, REFERENCE_ID))
        .recipient(new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_EMAIL))
        .template(new TemplateInfo("template-name", "v1.2.3", Map.of()))
        .build();
    when(mongoTemplate.find(any(), eq(History.class))).thenReturn(List.of(failure));

    doThrow(MessagingException.class).when(emailService).resendMessage(any(), any());
    doThrow(SchedulerException.class).when(notificationService)
        .scheduleNotification(any(), any(), any(), anyLong());

    migrator.migrate();

    verifyNoInteractions(historyService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {
      "COJ_CONFIRMATION", "EMAIL_UPDATED_NEW", "FORM_UPDATED", "LTFT_SUBMITTED"})
  void shouldMigrateInstantEmails(NotificationType type) throws MessagingException {
    ObjectId historyId = ObjectId.get();
    History failure = History.builder()
        .id(historyId)
        .type(type)
        .recipient(new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_EMAIL))
        .build();
    when(mongoTemplate.find(any(), eq(History.class))).thenReturn(List.of(failure));

    migrator.migrate();

    verify(emailService).resendMessage(failure, TRAINEE_EMAIL);
    verify(historyService).deleteHistoryForTrainee(historyId, TRAINEE_ID);
    verifyNoInteractions(notificationService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {
      "PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldMigrateScheduledEmails(NotificationType type) throws SchedulerException {
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
    verify(notificationService).removeNotification(jobId);

    ArgumentCaptor<JobDataMap> jobDataCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Date> whenCaptor = ArgumentCaptor.captor();
    verify(notificationService).scheduleNotification(eq(jobId), jobDataCaptor.capture(),
        whenCaptor.capture(), eq(86400L));

    JobDataMap jobData = jobDataCaptor.getValue();
    assertThat("Unexpected job data count.", jobData.keySet(), hasSize(2));
    assertThat("Unexpected job data.", jobData.get("key1"), is("value1"));
    assertThat("Unexpected job data.", jobData.get("key2"), is("value2"));

    assertThat("Unexpected send date.", whenCaptor.getValue(),
        DateCloseTo.closeTo(Instant.now().getEpochSecond(), 1));

    verify(historyService).deleteHistoryForTrainee(historyId, TRAINEE_ID);
    verifyNoInteractions(emailService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EXCLUDE, names = {
      "COJ_CONFIRMATION", "EMAIL_UPDATED_NEW", "FORM_UPDATED", "LTFT_SUBMITTED",
      "PLACEMENT_UPDATED_WEEK_12", "PROGRAMME_CREATED", "PROGRAMME_DAY_ONE"})
  void shouldNotMigrateUnsupportedTypes(NotificationType type) {
    History failure = History.builder()
        .id(ObjectId.get())
        .type(type)
        .build();
    when(mongoTemplate.find(any(), eq(History.class))).thenReturn(List.of(failure));

    Mockito.clearInvocations(mongoTemplate);
    migrator.migrate();

    verifyNoInteractions(emailService);
    verifyNoInteractions(notificationService);
    verifyNoInteractions(historyService);
  }

  @Test
  void rollback() {
    Mockito.clearInvocations(mongoTemplate);
    migrator.rollback();

    verifyNoInteractions(mongoTemplate);
    verifyNoInteractions(emailService);
    verifyNoInteractions(notificationService);
    verifyNoInteractions(historyService);
  }

  /**
   * A custom matcher which wraps {@link IsCloseTo} to allow easier use with Java Dates.
   */
  private static class DateCloseTo extends TypeSafeMatcher<Date> {

    private final IsCloseTo isCloseTo;

    /**
     * Construct a matcher that matches when a date value is equal, within the error range.
     *
     * @param value The expected value of matching doubles after conversion.
     * @param error The delta (+/-) within which matches will be allowed.
     */
    DateCloseTo(double value, double error) {
      isCloseTo = new IsCloseTo(value, error);
    }

    @Override
    protected boolean matchesSafely(Date date) {
      return isCloseTo.matchesSafely((double) date.toInstant().getEpochSecond());
    }

    @Override
    public void describeTo(Description description) {
      isCloseTo.describeTo(description);
    }

    /**
     * Get an instance of the closeTo matcher.
     *
     * @param operand The expected value of matching doubles after conversion.
     * @param error   The delta (+/-) within which matches will be allowed.
     * @return the matcher instance.
     */
    public static Matcher<Date> closeTo(double operand, double error) {
      return new DateCloseTo(operand, error);
    }
  }
}
