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

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.EMAIL_UPDATED_NEW;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import jakarta.mail.MessagingException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * Resend email notifications that failed on the 14th and 15th May due to Google blocking us.
 */
@Slf4j
@ChangeUnit(id = "resendGoogleMailFailures", order = "9")
public class ResendGoogleMailFailures {

  private static final long WINDOW = Duration.ofDays(1).getSeconds();

  private static final Set<NotificationType> INSTANT_NOTIFICATIONS = Set.of(
      COJ_CONFIRMATION,
      EMAIL_UPDATED_NEW,
      FORM_UPDATED,
      LTFT_SUBMITTED
  );

  private static final Set<NotificationType> SCHEDULE_NOTIFICATIONS = Set.of(
      PLACEMENT_UPDATED_WEEK_12,
      PROGRAMME_CREATED,
      PROGRAMME_DAY_ONE
  );

  private final MongoTemplate mongoTemplate;
  private final EmailService emailService;
  private final HistoryService historyService;
  private final NotificationService notificationService;

  /**
   * Construct the migrator.
   *
   * @param mongoTemplate The mongo template to use for accessing failed records.
   */
  public ResendGoogleMailFailures(MongoTemplate mongoTemplate, EmailService emailService,
      HistoryService historyService,
      NotificationService notificationService) {
    this.mongoTemplate = mongoTemplate;
    this.emailService = emailService;
    this.historyService = historyService;
    this.notificationService = notificationService;
  }

  /**
   * Resend the failed Google email notifications.
   */
  @Execution
  public void migrate() {
    Query query = new Query()
        .addCriteria(Criteria.where("recipient.type").is(EMAIL))
        .addCriteria(Criteria.where("recipient.contact")
            .regex(Pattern.compile("^.+@(gmail|googlemail)\\.com$", CASE_INSENSITIVE)))
        .addCriteria(Criteria.where("status").is(FAILED))
        .addCriteria(Criteria.where("statusDetail").is("Bounce: Transient - General"))
        .addCriteria(Criteria.where("sentAt")
            .gte(LocalDate.of(2025, 5, 14))
            .lt(LocalDate.of(2025, 5, 16))
        );

    List<History> failures = mongoTemplate.find(query, History.class);
    int total = failures.size();
    log.info("Found {} qualifying failure(s).", total);
    int current = 1;

    for (History failure : failures) {
      log.info("Progress: {}/{}.", current++, total);
      boolean success;

      NotificationType type = failure.type();
      if (INSTANT_NOTIFICATIONS.contains(type)) {
        success = sendEmail(failure);
      } else if (SCHEDULE_NOTIFICATIONS.contains(type)) {
        success = scheduleEmail(failure);
      } else {
        log.warn("Skipping unexpected failed notification type {}.", type);
        continue;
      }

      if (success) {
        historyService.deleteHistoryForTrainee(failure.id(), failure.recipient().id());
      }
    }
  }

  /**
   * Retry sending the failed email.
   *
   * @param failure The failed notification history.
   * @return Whether the notification was successfully sent.
   */
  private boolean sendEmail(History failure) {
    try {
      emailService.resendMessage(failure, failure.recipient().contact());
      return true;
    } catch (MessagingException e) {
      log.error("Failed to send notification retry.", e);
      return false;
    }
  }

  /**
   * Schedule retrying to send the failed email.
   *
   * @param failure The failed notification history.
   * @return Whether the notification was successfully scheduled.
   */
  private boolean scheduleEmail(History failure) {
    Map<String, Object> templateVariables = failure.template().variables();
    JobDataMap jobData = new JobDataMap(templateVariables);
    String jobId = "%s-%s".formatted(failure.type(), failure.tisReference().id());

    try {
      notificationService.removeNotification(jobId);
      notificationService.scheduleNotification(jobId, jobData, Date.from(Instant.now()), WINDOW);
      return true;
    } catch (SchedulerException e) {
      log.error("Failed to schedule notification retry.", e);
      return false;
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'ResendGoogleMailFailures' migration.");
  }
}
