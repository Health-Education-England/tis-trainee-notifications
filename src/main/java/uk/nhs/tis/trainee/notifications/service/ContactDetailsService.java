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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.service;

import jakarta.mail.MessagingException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.model.ContactDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.MessageType;

/**
 * A service providing functionality for contact detail changes.
 */
@Slf4j
@Service
public class ContactDetailsService {

  private final HistoryService historyService;
  private final EmailService emailService;

  public ContactDetailsService(HistoryService historyService, EmailService emailService) {
    this.historyService = historyService;
    this.emailService = emailService;
  }

  /**
   * Respond to an update of trainee contact details.
   *
   * @param contactDetails The updated contact details.
   */
  public void updateContactDetails(ContactDetails contactDetails) {
    String traineeId = contactDetails.getTisId();
    if (contactDetails.getEmail() == null) {
      log.info("The latest email for trainee {} is null, contact details will not be updated.",
          traineeId);
      return;
    }

    List<History> failedMessages
        = historyService.findAllFailedForTrainee(traineeId);
    List<History> onesToResend = failedMessages.stream()
        .filter(h -> h.recipient().type() == MessageType.EMAIL)
        .filter(h -> h.recipient().contact() == null || !h.recipient().contact()
            .equalsIgnoreCase(contactDetails.getEmail()))
        .toList();
    log.info("There are {} failed emails to retry for trainee {} with updated email address",
        onesToResend.size(), traineeId);
    onesToResend.forEach(failed -> {
      log.info("Resending failed message {} (originally sent to trainee {} using address {}) "
              + "to updated address {}", failed.id(), failed.recipient().id(),
          failed.recipient().contact(), contactDetails.getEmail());

      try {
        emailService.resendMessage(failed, contactDetails.getEmail());
      } catch (MessagingException e) {
        throw new RuntimeException(e); //to allow the message to be retried
      }
    });
  }
}
