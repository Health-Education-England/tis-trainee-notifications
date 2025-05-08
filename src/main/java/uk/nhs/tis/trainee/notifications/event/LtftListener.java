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

import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.LTFT_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.SUPPORTED_RETURN_TO_TRAINING;
import static uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType.TSS_SUPPORT;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_APPROVED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_SUBMITTED_TPD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.HrefType;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * A listener for LTFT update events.
 */
@Slf4j
@Component
public class LtftListener {

  private static final Set<LocalOfficeContactType> TEMPLATE_CONTACTS = Set.of(LTFT, LTFT_SUPPORT,
      SUPPORTED_RETURN_TO_TRAINING, TSS_SUPPORT);

  private final NotificationService notificationService;
  private final EmailService emailService;
  private final TemplateVersionsProperties templateVersions;
  private final boolean emailNotificationsEnabled;

  /**
   * Construct a listener for LTFT events.
   *
   * @param notificationService       The service for getting contact lists.
   * @param emailService              The service to use for sending emails.
   * @param templateVersions          The configured versions of each template.
   * @param emailNotificationsEnabled Whether email notifications are enabled.
   */
  public LtftListener(NotificationService notificationService, EmailService emailService,
      TemplateVersionsProperties templateVersions,
      @Value("${application.email.enabled}") boolean emailNotificationsEnabled) {
    this.notificationService = notificationService;
    this.emailService = emailService;
    this.templateVersions = templateVersions;
    this.emailNotificationsEnabled = emailNotificationsEnabled;
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

    NotificationType notificationType = switch (event.getState()) {
      case "APPROVED" -> LTFT_APPROVED;
      case "SUBMITTED" -> LTFT_SUBMITTED;
      default -> LTFT_UPDATED;
    };

    String templateVersion = templateVersions.getTemplateVersion(notificationType, EMAIL)
        .orElseThrow(() -> new IllegalArgumentException(
            "No email template available for notification type '%s'".formatted(notificationType)));

    String traineeTisId = event.getTraineeId();
    String managingDeanery = event.getProgrammeMembership() == null ? null
        : event.getProgrammeMembership().managingDeanery();

    Map<String, Object> templateVariables = Map.of(
        "var", event,
        "contacts", getContacts(managingDeanery)
    );
    emailService.sendMessageToExistingUser(traineeTisId, notificationType, templateVersion,
        templateVariables, null);
    log.info("LTFT updated notification sent for trainee {}.", traineeTisId);
  }

  /**
   * Handle LTFT update events for the TPD.
   *
   * @param event The LTFT update event message.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.ltft-updated-tpd}")
  public void handleLtftUpdateTpd(LtftUpdateEvent event) throws MessagingException {
    log.info("Handling LTFT update TPD event {}.", event);

    if (event.getState().equals("SUBMITTED")) {
      NotificationType notificationType = LTFT_SUBMITTED_TPD;

      String traineeTisId = event.getTraineeId();
      UserDetails userDetails = emailService.getRecipientAccount(traineeTisId);

      String templateVersion = templateVersions.getTemplateVersion(notificationType, EMAIL)
          .orElseThrow(() -> new IllegalArgumentException(
              "No email template available for notification type '%s'".formatted(
                  notificationType)));

      Map<String, Object> templateVariables = new HashMap<>(); //this needs to be modifiable
      templateVariables.putIfAbsent("familyName", userDetails.familyName());
      templateVariables.putIfAbsent("givenName", userDetails.givenName());
      templateVariables.put("var", event);
      String managingDeanery = event.getProgrammeMembership() == null ? null
          : event.getProgrammeMembership().managingDeanery();
      templateVariables.put("contacts", getContacts(managingDeanery));

      String tpdEmail = event.getDiscussions() == null ? null : event.getDiscussions().tpdEmail();
      emailService.sendMessage(traineeTisId, tpdEmail, notificationType,
          templateVersion, templateVariables, null, !emailNotificationsEnabled);
      log.info("LTFT submitted notification sent to TPD at email '{}' for trainee {}.",
          tpdEmail, traineeTisId);
    } else {
      log.info("LTFT update TPD event is not a submission, ignoring.");
    }
  }

  /**
   * Get the contacts for the given managing deanery.
   *
   * @param managingDeanery The local office to get the contacts for.
   * @return A map where the key is the contact type and the value is the {@link Contact} details.
   */
  private Map<String, Contact> getContacts(String managingDeanery) {
    List<Map<String, String>> ownerContactList = notificationService.getOwnerContactList(
        managingDeanery);

    return TEMPLATE_CONTACTS.stream()
        .collect(Collectors.toMap(Enum::name, ct -> {
          String contact = notificationService.getOwnerContact(ownerContactList, ct, TSS_SUPPORT,
              "");
          String type = notificationService.getHrefTypeForContact(contact);
          return new Contact(contact, type);
        }));
  }

  /**
   * A representation of a contact.
   *
   * @param contact The contact link.
   * @param type    The {@link HrefType} of the contact, see
   *                {@link NotificationService#getHrefTypeForContact(String)}
   */
  record Contact(String contact, String type) {

  }
}
