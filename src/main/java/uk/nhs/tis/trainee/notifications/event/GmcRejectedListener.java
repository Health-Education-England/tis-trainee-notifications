/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.event;

import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.GMC_UPDATE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.GMC_REJECTED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.GmcRejectedEvent;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * A listener for GMC update-rejected events.
 */
@Slf4j
@Component
public class GmcRejectedListener {

  public static final String TRAINEE_ID_FIELD = "traineeId";
  public static final String GIVEN_NAME_FIELD = "givenName";
  public static final String FAMILY_NAME_FIELD = "familyName";
  public static final String GMC_NUMBER_FIELD = "gmcNumber";
  public static final String GMC_STATUS_FIELD = "gmcStatus";
  public static final String TIS_TRIGGER_FIELD = "tisTrigger";
  public static final String TIS_TRIGGER_DETAIL_FIELD = "tisTriggerDetail";

  private final String templateVersion;
  private final NotificationService notificationService;

  /**
   * Construct a listener for GMC rejected events.
   *
   * @param notificationService The notification service to use.
   * @param templateVersion     The template version to use.
   */
  public GmcRejectedListener(NotificationService notificationService,
      @Value("${application.template-versions.gmc-rejected.email}") String templateVersion) {
    this.templateVersion = templateVersion;
    this.notificationService = notificationService;
  }

  /**
   * Handle GMC rejected events.
   *
   * @param event The GMC rejected event message, which contains the reset GMC details.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.gmc-rejected}")
  public void handleGmcRejected(GmcRejectedEvent event) throws MessagingException {
    log.info("Handling GMC rejected event {}.", event);

    UserDetails userDetails = notificationService.getTraineeDetails(event.traineeId());
    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put(TRAINEE_ID_FIELD, event.traineeId());
    templateVariables.put(TIS_TRIGGER_FIELD, event.tisTrigger());
    templateVariables.put(TIS_TRIGGER_DETAIL_FIELD, event.tisTriggerDetail());
    templateVariables.put(GIVEN_NAME_FIELD, userDetails != null ? userDetails.givenName() : null);
    templateVariables.put(FAMILY_NAME_FIELD, userDetails != null ? userDetails.familyName() : null);
    templateVariables.put(GMC_NUMBER_FIELD, event.update().gmcDetails().gmcNumber());
    templateVariables.put(GMC_STATUS_FIELD, event.update().gmcDetails().gmcStatus());

    String traineeId = event.traineeId();
    String traineeEmail = userDetails != null ? userDetails.email() : null;
    notificationService.sendLocalOfficeMail(traineeId, GMC_UPDATE, templateVariables,
        templateVersion, GMC_REJECTED, traineeEmail);
  }
}
