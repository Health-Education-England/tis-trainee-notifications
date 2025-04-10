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

package uk.nhs.tis.trainee.notifications.event;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

/**
 * A listener for LTFT update events.
 */
@Slf4j
@Component
public class LtftListener {
  private final EmailService emailService;

  private final Map<String, String> templateVersions;

  /**
   * Construct a listener for LTFT events.
   *
   * @param emailService The service to use for sending emails.
   * @param submittedTemplateVersion The email template version for submitted status.
   * @param updatedTemplateVersion The email template version for updated status.
   */
  public LtftListener(
      EmailService emailService,
      @Value("${application.template-versions.ltft-submitted.email}") String submittedTemplateVersion,
      @Value("${application.template-versions.ltft-updated.email}") String updatedTemplateVersion) {
    this.emailService = emailService;
    this.templateVersions = Map.of(
        "SUBMITTED", submittedTemplateVersion,
        "UNSUBMITTED", updatedTemplateVersion,
        "APPROVED", updatedTemplateVersion,
        "WITHDRAWN", updatedTemplateVersion
    );
  }

  /**
   * Handle LTFT update events.
   *
   * @param event The LTFT update event message.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.ltft-updated}")
  public void handleLtftUpdate(LtftUpdateEvent event) throws MessagingException {
    log.info("Handling LTFT update event {}.", event);

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("ltftName", event.content().name());
    templateVariables.put("status", event.status().current().state());
    templateVariables.put("eventDate", event.status().current().timestamp());
    templateVariables.put("formRef", event.formRef());

    String traineeTisId = event.traineeTisId();
    String currentState = event.status().current().state();

    String templateVersion = templateVersions.get(currentState);
    if (templateVersion == null) {
      throw new IllegalStateException("No template version configured for LTFT state: " + currentState);
    }

    emailService.sendMessageToExistingUser(traineeTisId, LTFT_UPDATED, templateVersion,
        templateVariables, null);

    log.info("LTFT {} notification sent for trainee {}.",
        "SUBMITTED".equalsIgnoreCase(currentState) ? "submitted" : "updated",
        traineeTisId);
  }
}
