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
import static uk.nhs.tis.trainee.notifications.model.NotificationType.GMC_UPDATED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.GmcUpdateEvent;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.MessagingControllerService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * A listener for GMC update events.
 */
@Slf4j
@Component
public class GmcListener {

  public static final String TRAINEE_ID_FIELD = "traineeId";
  public static final String GIVEN_NAME_FIELD = "givenName";
  public static final String FAMILY_NAME_FIELD = "familyName";
  public static final String GMC_NUMBER_FIELD = "gmcNumber";
  public static final String GMC_STATUS_FIELD = "gmcStatus";

  private final EmailService emailService;
  private final String templateVersion;
  private final NotificationService notificationService;
  private final MessagingControllerService messagingControllerService;

  /**
   * Construct a listener for GMC events.
   *
   * @param emailService The service to use for sending emails.
   */
  public GmcListener(EmailService emailService, NotificationService notificationService,
      MessagingControllerService messagingControllerService,
      @Value("${application.template-versions.gmc-updated.email}") String templateVersion) {
    this.emailService = emailService;
    this.templateVersion = templateVersion;
    this.notificationService = notificationService;
    this.messagingControllerService = messagingControllerService;
  }

  /**
   * Handle GMC update events.
   *
   * @param event The GMC update event message.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.gmc-updated}")
  public void handleGmcUpdate(GmcUpdateEvent event) throws MessagingException {
    log.info("Handling GMC update event {}.", event);

    UserDetails userDetails = notificationService.getTraineeDetails(event.traineeId());
    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put(TRAINEE_ID_FIELD, event.traineeId());
    templateVariables.put(GIVEN_NAME_FIELD, userDetails != null ? userDetails.givenName() : null);
    templateVariables.put(FAMILY_NAME_FIELD, userDetails != null ? userDetails.familyName() : null);
    templateVariables.put(GMC_NUMBER_FIELD, event.gmcDetails().gmcNumber());
    templateVariables.put(GMC_STATUS_FIELD, event.gmcDetails().gmcStatus());

    String traineeId = event.traineeId();
    List<Map<String, String>> localOfficeContacts = notificationService
        .getTraineeLocalOfficeContacts(traineeId, GMC_UPDATE);

    if (localOfficeContacts != null && !localOfficeContacts.isEmpty()) {
      boolean canSendMail = messagingControllerService.isMessagingEnabled(MessageType.EMAIL);
      //since some LO's share a contact we need to eliminate possible duplicates:
      Set<String> distinctContacts = localOfficeContacts.stream()
          .map(c -> c.get("contact")).filter(Objects::nonNull).collect(Collectors.toSet());

      for (String loContact : distinctContacts) {
        if (notificationService.isLocalOfficeContactEmail(loContact)) {
          emailService.sendMessage(traineeId, loContact, GMC_UPDATED, templateVersion,
              templateVariables, null, !canSendMail);
          log.info("GMC updated notification {} for trainee {} to {}.",
              (canSendMail ? "sent" : "logged"), traineeId, loContact);
        } else {
          log.info("GMC updated notification skipped for trainee {} to non-email {}.",
              traineeId, loContact);
        }
      }

    } else {
      log.warn("GMC updated notification not processed for trainee {}: no local office contacts.",
          traineeId);
    }
  }
}
