/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED_ASSIGNMENT;

import jakarta.mail.MessagingException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftAssignmentDto;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftStatusAssignedDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A service for handling LTFT notification logic.
 */
@Slf4j
@Service
public class LtftService {

  public static final String ASSIGNMENT_COOLDOWN_KEY_PREFIX = "ltft-assignment-cooldown:";
  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_REVIEWER = "REVIEWER";

  private final EmailService emailService;
  private final HistoryService historyService;
  private final TemplateVersionsProperties templateVersions;
  private final RedisTemplate<String, String> redisTemplate;
  private final boolean emailNotificationsEnabled;
  private final Duration assignmentCooldown;

  /**
   * Construct the LTFT service.
   *
   * @param emailService              The service to use for sending emails.
   * @param historyService            The service for storing notification history.
   * @param templateVersions          The configured versions of each template.
   * @param redisTemplate             The Redis template for managing cooldowns.
   * @param emailNotificationsEnabled Whether email notifications are enabled.
   * @param assignmentCooldown        The cooldown duration between assignment notifications.
   */
  public LtftService(EmailService emailService, HistoryService historyService,
      TemplateVersionsProperties templateVersions,
      RedisTemplate<String, String> redisTemplate,
      @Value("${application.email.enabled}") boolean emailNotificationsEnabled,
      @Value("${application.ltft.assignment-cooldown:PT15M}") Duration assignmentCooldown) {
    this.emailService = emailService;
    this.historyService = historyService;
    this.templateVersions = templateVersions;
    this.redisTemplate = redisTemplate;
    this.emailNotificationsEnabled = emailNotificationsEnabled;
    this.assignmentCooldown = assignmentCooldown;
  }

  /**
   * Handle an LTFT assignment notification. Sends an email to the assigned LO admin, throttled by a
   * 15-minute cooldown per admin email address using Redis. If the cooldown is active, the
   * notification is logged as SKIPPED in the database for reporting purposes.
   *
   * @param event The LTFT update event containing assignment information.
   * @throws MessagingException If the email could not be sent.
   */
  public void handleAssignmentNotification(LtftUpdateEvent event) throws MessagingException {
    LtftAssignmentDto previous = event.getPreviousAssignment();
    boolean previousKnown = previous != null;

    LtftStatusAssignedDto admin = event.getAssignedAdmin();
    LtftStatusAssignedDto previousAdmin = previousKnown ? previous.assignedAdmin() : null;
    boolean adminChanged = isNewAssignee(admin, previousAdmin, previousKnown);

    LtftStatusAssignedDto reviewer = event.getAssignedReviewer();
    LtftStatusAssignedDto previousReviewer = previousKnown ? previous.assignedReviewer() : null;
    boolean reviewerChanged = isNewAssignee(reviewer, previousReviewer, previousKnown);

    if (!adminChanged && !reviewerChanged) {
      log.info("LTFT assignment event has no newly assigned admin or reviewer with an email, "
          + "ignoring. (FormId = {})", event.getFormId());
      return;
    }

    if (adminChanged) {
      notifyAssignee(event, admin, ROLE_ADMIN);
    }

    if (reviewerChanged) {
      notifyAssignee(event, reviewer, ROLE_REVIEWER);
    }
  }

  /**
   * Check whether the assignee has been newly assigned.
   *
   * @param current       The current assignee.
   * @param previous      The previous assignee.
   * @param previousKnown Whether the previous assignment is known.
   * @return true if there is a current assignee with an email, and it differs from the previous (or
   *     the previous is unknown).
   */
  private boolean isNewAssignee(LtftStatusAssignedDto current, LtftStatusAssignedDto previous,
      boolean previousKnown) {
    if (current == null || current.email() == null) {
      return false;
    }

    if (!previousKnown || previous == null || previous.email() == null) {
      return true;
    }

    return !current.email().equalsIgnoreCase(previous.email());
  }

  /**
   * Notify an assignee that they have been assigned an LTFT application.
   *
   * @param event       The LTFT update event.
   * @param assignee    The assignee to notify.
   * @param defaultRole The role to log if the assignee has no role.
   * @throws MessagingException If the email could not be sent.
   */
  private void notifyAssignee(LtftUpdateEvent event, LtftStatusAssignedDto assignee,
      String defaultRole) throws MessagingException {
    String role = assignee.role() == null ? defaultRole : assignee.role();

    if (event.getModifiedBy() != null && assignee.name() != null
        && assignee.name().equalsIgnoreCase(event.getModifiedBy().name())) {
      log.info("LTFT assignment event modified by the assigned {} '{}', ignoring. (FormId = {})",
          role, assignee.name(), event.getFormId());
      return;
    }

    String assigneeEmail = assignee.email();
    String cooldownKey = ASSIGNMENT_COOLDOWN_KEY_PREFIX + assigneeEmail;

    String templateVersion = templateVersions
        .getTemplateVersion(LTFT_UPDATED_ASSIGNMENT, EMAIL)
        .orElseThrow(() -> new IllegalArgumentException(
            "No email template available for notification type '%s'"
                .formatted(LTFT_UPDATED_ASSIGNMENT)));

    String traineeTisId = event.getTraineeId();
    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("var", event);
    templateVariables.put("name", assignee.name());

    TisReferenceInfo tisReferenceInfo =
        new TisReferenceInfo(TisReferenceType.LTFT, event.getFormId());

    // Check Redis cooldown: if the key exists, the assignee was recently notified.
    Boolean notNotifiedRecently = redisTemplate.opsForValue()
        .setIfAbsent(cooldownKey, "1", assignmentCooldown);

    if (Boolean.TRUE.equals(notNotifiedRecently)) {
      // No recent notification — send the email.
      emailService.sendMessage(traineeTisId, assigneeEmail, LTFT_UPDATED_ASSIGNMENT,
          templateVersion, templateVariables, tisReferenceInfo, !emailNotificationsEnabled);
      log.info("LTFT assignment notification sent to {} '{}' for form {}.",
          role, assigneeEmail, event.getFormId());
    } else {
      // Cooldown active — log as SKIPPED in history for reporting.
      RecipientInfo recipientInfo = new RecipientInfo(traineeTisId, EMAIL, assigneeEmail);
      TemplateInfo templateInfo = new TemplateInfo(
          LTFT_UPDATED_ASSIGNMENT.getTemplateName(), templateVersion, templateVariables);
      History skippedHistory = new History(ObjectId.get(), tisReferenceInfo,
          LTFT_UPDATED_ASSIGNMENT, recipientInfo, templateInfo, null,
          Instant.now(), null, NotificationStatus.SKIPPED,
          "Throttled: %s recently notified of an LTFT assignment.".formatted(
              role.toLowerCase()), null);
      historyService.save(skippedHistory);
      log.info("LTFT assignment notification for {} '{}' skipped due to cooldown, "
          + "logged as SKIPPED for form {}.", role, assigneeEmail, event.getFormId());
    }
  }
}

