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

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.FormUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

/**
 * A listener for Form update events.
 */
@Slf4j
@Component
public class FormListener {

  private static final String FORM_UPDATE_TEMPLATE = "email/form-updated";

  private final EmailService emailService;

  /**
   * Construct a listener for form events.
   *
   * @param emailService The service to use for sending emails.
   */
  public FormListener(EmailService emailService) {
    this.emailService = emailService;
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

    String traineeId = event.traineeId();
    if (traineeId == null) {
      throw new IllegalArgumentException("Unable to send notification as no trainee ID available");
    }

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("formName", event.formName());
    templateVariables.put("formType", event.formType());
    templateVariables.put("lifecycleState", event.lifecycleState());

    emailService.sendMessageToExistingUser(traineeId, FORM_UPDATE_TEMPLATE, templateVariables);
    log.info("Form updated notification sent for trainee {}.", traineeId);
  }
}
