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
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A service for executing notification scheduling jobs.
 */
@Slf4j
@Component
public class NotificationService implements Job {

  public static final String API_GET_EMAIL = "/api/trainee-profile/account-details/{tisId}";

  private final EmailService emailService;
  private final RestTemplate restTemplate;
  private final String templateVersion;
  private final String serviceUrl;

  /**
   * Initialise the Notification Service.
   *
   * @param emailService    The Email Service to use.
   * @param restTemplate    The REST template.
   * @param templateVersion The email template version.
   * @param serviceUrl      The URL for the tis-trainee-details service to use for profile
   *                        information.
   */
  public NotificationService(EmailService emailService, RestTemplate restTemplate,
      @Value("${application.template-versions.form-updated.email}") String templateVersion,
      @Value("${service.trainee.url}") String serviceUrl) {
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
      NotificationType notificationType
          = NotificationType.valueOf(jobDetails.get(NOTIFICATION_TYPE_FIELD).toString());
      TisReferenceInfo tisReferenceInfo = new TisReferenceInfo(PROGRAMME_MEMBERSHIP,
          jobDetails.get(TIS_ID_FIELD).toString());
      jobDetails.putIfAbsent("name", userAccountDetails.familyName());

      try {
        emailService.sendMessage(personId, userAccountDetails.email(), notificationType,
            templateVersion, jobDetails.getWrappedMap(), tisReferenceInfo, true);
      } catch (MessagingException e) {
        throw new RuntimeException(e);
      }

      log.info("Sent {} notification for {} ({}, starting {}) to {} using template {}", jobKey,
          jobDetails.getString(TIS_ID_FIELD), programmeName, startDate, userAccountDetails.email(),
          templateVersion);
      Instant processedOn = Instant.now();
      result.put("status", "sent " + processedOn.toString());
      jobExecutionContext.setResult(result);
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
}
