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

import static uk.nhs.tis.trainee.notifications.model.NotificationType.CREDENTIAL_REVOKED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.CredentialEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

/**
 * A listener for credential events.
 */
@Slf4j
@Component
public class CredentialListener {

  private final EmailService emailService;
  private final String templateVersion;

  /**
   * Construct a listener for credential events.
   *
   * @param emailService The service to use for sending emails.
   */
  public CredentialListener(EmailService emailService,
      @Value("${application.template-versions.credential-revoked.email}") String templateVersion) {
    this.emailService = emailService;
    this.templateVersion = templateVersion;
  }

  /**
   * Handle credential revocation events.
   *
   * @param event The credential event.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.credential-revoked}")
  public void handleCredentialRevoked(CredentialEvent event) throws MessagingException {
    log.info("Handling credential revoked event {}.", event);

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("credentialType", event.credentialType());
    templateVariables.put("issuedAt", event.issuedAt());

    String traineeId = event.traineeId();
    emailService.sendMessageToExistingUser(traineeId, CREDENTIAL_REVOKED, templateVersion,
        templateVariables, null);
    log.info("Credential revocation notification sent for trainee {}.", traineeId);
  }
}
