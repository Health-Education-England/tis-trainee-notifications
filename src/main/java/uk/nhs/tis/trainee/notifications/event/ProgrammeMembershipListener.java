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
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.RegistrationService;

/**
 * A listener for Conditions of Joining events.
 */
@Slf4j
@Component
public class ProgrammeMembershipListener {


  private final EmailService emailService;
  private final RegistrationService registrationService;

  /**
   * Construct a listener for programme membership events.
   *
   * @param emailService The service to use for sending emails.
   */
  public ProgrammeMembershipListener(EmailService emailService,
      RegistrationService registrationService) {
    this.emailService = emailService;
    this.registrationService = registrationService;
  }

  /**
   * Handle Programme membership received events.
   *
   * @param event The program membership event.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.programme-membership}")
  public void handleProgrammeMembershipUpdate(ProgrammeMembershipEvent event)
      throws MessagingException, SchedulerException {
    log.info("Handling programme membership update event {}.", event);
    registrationService.registerUser();

    Map<String, Object> templateVariables = new HashMap<>();

    if (event.conditionsOfJoining() != null) {
      templateVariables.put("syncedAt", event.conditionsOfJoining().syncedAt());
    }

    String traineeId = event.personId();
    //emailService.sendMessageToExistingUser(traineeId, CONFIRMATION_TEMPLATE, templateVariables);
    log.info("Reminders updated for trainee {}.", traineeId);
  }
}
