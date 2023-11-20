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

import java.time.Instant;
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
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for executing notification scheduling jobs.
 */
@Slf4j
@Component
public class NotificationService implements Job {

  public static final String DUMMY_EMAIL = "TODO get email";
  public static final String API_GET_EMAIL = "/api/trainee-profile/trainee-email/{tisId}";

  private static final String TIS_ID = "tisId";
  private static final String PERSON_ID = "personId";

  private final HistoryService historyService;
  private final EmailService emailService;
  private final RestTemplate restTemplate;

  @Value("${service.trainee.url}")
  private String serviceUrl;

  public NotificationService(HistoryService historyService, EmailService emailService,
      RestTemplate restTemplate) {
    this.historyService = historyService;
    this.emailService = emailService;
    this.restTemplate = restTemplate;
  }

  /**
   * Execute a given notification job.
   *
   * @param jobExecutionContext The job execution context.
   */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    //for now, simply log jobs to demonstrate that scheduling is correct
    String jobKey = jobExecutionContext.getJobDetail().getKey().toString();
    Map<String, String> result = new HashMap<>();
    JobDataMap jobDetails = jobExecutionContext.getJobDetail().getJobDataMap();

    //get email
    //get signed-up

    String personId = jobDetails.getString(PERSON_ID);
    UserAccountDetails userAccountDetails = null; //getCognitoAccountDetails(personId);
    boolean hasSignedUpToTss = (userAccountDetails != null);
    if (userAccountDetails == null) {
      //get from trainee-details API. Problem is, it just gives email address, not family name
      userAccountDetails = getTraineeDetailsAccountDetails(personId);
      if (userAccountDetails == null) {
        //give up, they dont have TSS details at all
        log.info("No TSS details found for tisId {}", personId);
      }
    }

    //get signed COJ
    //get completed FormR A+B

    if (userAccountDetails != null) {
      log.info("Sent {} notification for {} to {}", jobKey, jobDetails.getString(TIS_ID),
          userAccountDetails.email());
      Instant processedOn = Instant.now();
      result.put("status", "sent " + processedOn.toString());
      jobExecutionContext.setResult(result);

      saveNotificationSent(jobDetails, userAccountDetails.email(), processedOn);
    }

    //MVP:
    //get trainee name and job details (ProgrammeMembershipEvent, NotificationType)
    //inject name, programme name and notification type (8-week, 4-week etc.) into basic template
    //send email

    //MVP +1 iteration:
    //use job details to check status of trainee (signed-up, submitted coj, submitted formR)
    //inject these into full template
    //send email
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
    ObjectId objectId = ObjectId.get(); //TODO let DB generate this?
    RecipientInfo recipientInfo
        = new RecipientInfo(jobDetails.getString(TIS_ID), MessageType.EMAIL, email);
    NotificationType notificationType = NotificationType.valueOf(
        jobDetails.get("notificationType").toString());

    TemplateInfo templateInfo
        = new TemplateInfo(notificationType.getTemplateName(), "v1.0.0",
        jobDetails.getWrappedMap());
    TisReferenceInfo tisReference = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP,
        jobDetails.get(TIS_ID).toString());

    History history = new History(objectId, tisReference, notificationType, recipientInfo,
        templateInfo, sentAt);

    return historyService.save(history);
  }

  private UserAccountDetails getCognitoAccountDetails(String personId) {
    try {
       return emailService.getRecipientAccount(personId);
    } catch (IllegalArgumentException e) {
      //no TSS account (or a duplicate account)
      return null;
    }
  }

  private UserAccountDetails getTraineeDetailsAccountDetails(String personId) {
    try {
      return new UserAccountDetails(
        restTemplate.getForObject(serviceUrl + API_GET_EMAIL, String.class, Map.of(TIS_ID, personId)),
          "Family name TODO"); //TODO
    } catch (RestClientException e) {
      //no trainee details
      return null;
    }
  }
}
