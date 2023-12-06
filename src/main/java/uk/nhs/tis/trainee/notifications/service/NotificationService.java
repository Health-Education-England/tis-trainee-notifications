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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A service for executing notification scheduling jobs.
 */
@Slf4j
@Component
public class NotificationService implements Job {

  public static final String API_GET_EMAIL = "/api/trainee-profile/account-details/{tisId}";

  private static final String TIS_ID = "tisId";

  private final HistoryService historyService;
  private final EmailService emailService;
  private final RestTemplate restTemplate;
  private final String templateVersion;
  private final String serviceUrl;

  public NotificationService(HistoryService historyService, EmailService emailService,
      RestTemplate restTemplate,
      @Value("${application.template-versions.form-updated.email}") String templateVersion,
      @Value("${service.trainee.url}") String serviceUrl) {
    this.historyService = historyService;
    this.emailService = emailService;
    this.restTemplate = restTemplate;
    this.templateVersion = templateVersion;
    this.serviceUrl = serviceUrl;
  }

  /**
   * Execute a given notification job.
   *
   * @param jobExecutionContext The job execution context.
   */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    String jobKey = jobExecutionContext.getJobDetail().getKey().toString();
    Map<String, String> result = new HashMap<>();
    JobDataMap jobDetails = jobExecutionContext.getJobDetail().getJobDataMap();

    String personId = jobDetails.getString(PERSON_ID_FIELD);

    UserAccountDetails userAccountDetails = getAccountDetails(personId);

    if (userAccountDetails != null) {
      String programmeName = jobDetails.getString(PROGRAMME_NAME_FIELD);
      LocalDate startDate = (LocalDate) jobDetails.get(START_DATE_FIELD);

      jobDetails.putIfAbsent("name", userAccountDetails.familyName());

      log.info("Mock sent {} notification for {} ({}, starting {}) to {} using template {}", jobKey,
          jobDetails.getString(TIS_ID_FIELD), programmeName, startDate, userAccountDetails.email(),
          templateVersion);
      Instant processedOn = Instant.now();
      result.put("status", "sent " + processedOn.toString());
      jobExecutionContext.setResult(result);

      saveNotificationSent(jobDetails, userAccountDetails.email(), processedOn);
    } else {
      log.info("No notification could be sent, no TSS details found for tisId {}", personId);
    }
  }

  /**
   * Get the user account details from Cognito (if they have signed-up to TIS Self-Service), or from
   * the Trainee Details profile if not.
   *
   * @param personId The person ID to search for.
   * @return The user account details, or null if not found.
   */
  private UserAccountDetails getAccountDetails(String personId) {
    try {
      return emailService.getRecipientAccount(personId);
    } catch (IllegalArgumentException e) {
      //no TSS account (or duplicate accounts), so try to get from trainee-details profile.
      try {
        return restTemplate.getForObject(serviceUrl + API_GET_EMAIL, UserAccountDetails.class,
            Map.of(TIS_ID_FIELD, personId));
      } catch (RestClientException rce) {
        //no trainee details profile
        return null;
      }
    }
  }

  /**
   * Save a Programme Updated sent-notification into the history.
   *
   * @param jobDetails The JobDataMap.
   * @param email      The recipient email address.
   * @param sentAt     The instant the email was sent.
   * @return The saved notification history.
   */
  private History saveNotificationSent(JobDataMap jobDetails, String email, Instant sentAt) {
    ObjectId objectId = ObjectId.get();
    RecipientInfo recipientInfo
        = new RecipientInfo(jobDetails.getString(PERSON_ID_FIELD), MessageType.EMAIL, email);
    NotificationType notificationType = NotificationType.valueOf(
        jobDetails.get("notificationType").toString());

    TemplateInfo templateInfo
        = new TemplateInfo(notificationType.getTemplateName(), "v1.0.0",
        jobDetails.getWrappedMap());
    TisReferenceInfo tisReference = new TisReferenceInfo(PROGRAMME_MEMBERSHIP,
        jobDetails.get(TIS_ID).toString());

    History history = new History(objectId, tisReference, notificationType, recipientInfo,
        templateInfo, sentAt, NotificationStatus.SENT, null);

    return historyService.save(history);
  }
}
