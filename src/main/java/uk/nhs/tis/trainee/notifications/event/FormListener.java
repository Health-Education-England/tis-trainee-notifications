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

package uk.nhs.tis.trainee.notifications.event;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

/**
 * A listener for Form update events.
 */
@Slf4j
@Component
public class FormListener {

  private final EmailService emailService;
  private final String templateVersion;

  /**
   * Construct a listener for form events.
   *
   * @param emailService The service to use for sending emails.
   */
  public FormListener(EmailService emailService,
      @Value("${application.template-versions.form-updated.email}") String templateVersion) {
    this.emailService = emailService;
    this.templateVersion = templateVersion;
  }

  /**
   * Handle form update events.
   *
   * @param event The form update event message.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.form-updated}")
  public void handleFormUpdate(FormUpdateEvent event) throws MessagingException {
    log.info("Handling form update event {}.", event);

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("formName", event.formName());
    templateVariables.put("formType", event.formType());
    templateVariables.put("lifecycleState", event.lifecycleState());
    templateVariables.put("eventDate", event.eventDate());

    String traineeId = event.traineeId();
    emailService.sendMessageToExistingUser(traineeId, FORM_UPDATED, templateVersion,
        templateVariables, null);
    log.info("Form updated notification sent for trainee {}.", traineeId);
  }
}
