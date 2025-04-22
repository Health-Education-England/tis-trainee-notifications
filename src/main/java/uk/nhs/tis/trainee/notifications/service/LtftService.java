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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.service;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_APPROVED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service providing functionality for contact detail changes.
 */
@Slf4j
@Service
public class LtftService {
  private final EmailService emailService;
  private final NotificationService notificationService;
  private final String ltftUpdatedVersion;
  private final String ltftApprovedVersion;

  /**
   * Construct a service for LTFT events.
   *
   * @param emailService        The service to use for sending emails.
   * @param notificationService The service to use for handling notifications.
   * @param ltftUpdatedVersion  The ltft-updated template version to use.
   * @param ltftApprovedVersion The ltft-approved template version to use.
   */
  public LtftService(EmailService emailService, NotificationService notificationService,
    @Value("${application.template-versions.ltft-updated.email}") String ltftUpdatedVersion,
    @Value("${application.template-versions.ltft-approved.email}") String ltftApprovedVersion) {
    this.emailService = emailService;
    this.notificationService = notificationService;
    this.ltftUpdatedVersion = ltftUpdatedVersion;
    this.ltftApprovedVersion = ltftApprovedVersion;
  }

  /**
   * Respond to an update of LTFT applications.
   *
   * @param event The updated Ltft.
   */
  public void sendLtftUpdatedNotification(LtftUpdateEvent event) throws MessagingException {
    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("ltftName", event.name());
    templateVariables.put("status", event.status().current().state());
    templateVariables.put("eventDate", event.status().current().timestamp());
    templateVariables.put("formRef", event.formRef());

    try {
      String traineeTisId = event.traineeTisId();
      emailService.sendMessageToExistingUser(traineeTisId, LTFT_UPDATED, ltftUpdatedVersion,
          templateVariables, null);
      log.info("LTFT updated notification sent for trainee {}.", traineeTisId);
    } catch (MessagingException e) {
      throw new RuntimeException(e); //to allow the message to be retried
    }
  }

  /**
   * Respond to an approved of Ltft applications.
   *
   * @param event The approved Ltft.
   */
  public void sendLtftApprovedNotification(LtftUpdateEvent event) throws MessagingException {
    Map<String, Object> templateVariables = new HashMap<>(Map.of("var", event));

    String owner = event.programmeMembership().managingDeanery();
    List<Map<String, String>> contactList = notificationService.getOwnerContactList(owner);

    // LO LTTF Support Contact
    String localOfficeContactLtft = notificationService.getOwnerContact(contactList,
        LocalOfficeContactType.LTFT_Support, LocalOfficeContactType.TSS_SUPPORT, "");
    String localOfficeContactTypeLtft =
        notificationService.getHrefTypeForContact(localOfficeContactLtft);
    templateVariables.put("localOfficeContactLtft", localOfficeContactLtft);
    templateVariables.put("localOfficeContactTypeLtft", localOfficeContactTypeLtft);

    // Supported Return to Training Contact
    String localOfficeContactSupportRtt = notificationService.getOwnerContact(contactList,
        LocalOfficeContactType.SUPPORTED_RETURN_TO_TRAINING, LocalOfficeContactType.TSS_SUPPORT, "");
    String localOfficeContactTypeSupportRtt =
        notificationService.getHrefTypeForContact(localOfficeContactLtft);
    templateVariables.put("localOfficeContactSupportRtt", localOfficeContactSupportRtt);
    templateVariables.put("localOfficeContactTypeSupportRtt", localOfficeContactTypeSupportRtt);

    // LTFT Working Policy Contact
    String localOfficeContactWorkingPolicy = notificationService.getOwnerContact(contactList,
        LocalOfficeContactType.LTFT_Working_Policy, LocalOfficeContactType.TSS_SUPPORT, "");
    String localOfficeContactTypeWorkingPolicy =
        notificationService.getHrefTypeForContact(localOfficeContactLtft);
    templateVariables.put("localOfficeContactWorkingPolicy", localOfficeContactWorkingPolicy);
    templateVariables.put("localOfficeContactTypeWorkingPolicy", localOfficeContactTypeWorkingPolicy);

    try {
      String traineeTisId = event.traineeTisId();
      emailService.sendMessageToExistingUser(traineeTisId, LTFT_APPROVED, ltftApprovedVersion,
          templateVariables, null);
      log.info("LTFT approved notification sent for trainee {}.", traineeTisId);
    } catch (MessagingException e) {
      throw new RuntimeException(e); //to allow the message to be retried
    }
  }
}
