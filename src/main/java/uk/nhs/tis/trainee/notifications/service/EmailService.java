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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A service for sending emails.
 */
@Slf4j
@Service
public class EmailService {

  private final UserAccountService userAccountService;
  private final HistoryService historyService;
  private final JavaMailSender mailSender;
  private final TemplateService templateService;
  private final String sender;
  private final URI appDomain;

  EmailService(UserAccountService userAccountService, HistoryService historyService,
      JavaMailSender mailSender, TemplateService templateService,
      @Value("${application.email.sender}") String sender,
      @Value("${application.domain}") URI appDomain) {
    this.userAccountService = userAccountService;
    this.historyService = historyService;
    this.mailSender = mailSender;
    this.templateService = templateService;
    this.sender = sender;
    this.appDomain = appDomain;
  }

  /**
   * Email a user with an existing account, the name and domain variables will be set if not
   * provided.
   *
   * @param traineeId         The trainee ID of the user.
   * @param notificationType  The type of notification, which will determine the template used.
   * @param templateVersion   The version of the template to be sent.
   * @param templateVariables The variables to pass to the template.
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessageToExistingUser(String traineeId, NotificationType notificationType,
      String templateVersion, Map<String, Object> templateVariables) throws MessagingException {
    if (traineeId == null) {
      throw new IllegalArgumentException("Unable to send notification as no trainee ID available");
    }

    UserAccountDetails userDetails = getRecipientAccount(traineeId);

    if (Strings.isBlank(userDetails.email())) {
      String message = "Could not find email address for user '%s'".formatted(traineeId);
      throw new IllegalArgumentException(message);
    }

    templateVariables = new HashMap<>(templateVariables);
    templateVariables.putIfAbsent("name", userDetails.familyName());
    sendMessage(traineeId, userDetails.email(), notificationType, templateVersion,
        templateVariables);
  }

  /**
   * Send an email message using a given template, the domain variable will be set if not provided.
   *
   * @param traineeId         The trainee ID of the recipient.
   * @param recipient         Where the email should be sent.
   * @param notificationType  The type of notification, which will determine the template used.
   * @param templateVersion   The version of the template to be sent.
   * @param templateVariables The variables to pass to the template.
   * @throws MessagingException When the message could not be sent.
   */
  private void sendMessage(String traineeId, String recipient, NotificationType notificationType,
      String templateVersion, Map<String, Object> templateVariables) throws MessagingException {
    String templateName = templateService.getTemplatePath(EMAIL, notificationType, templateVersion);
    log.info("Sending template {} to {}.", templateName, recipient);

    // Add the application domain for any templates with hyperlinks.
    templateVariables.putIfAbsent("domain", appDomain);

    Context templateContext = templateService.buildContext(templateVariables);
    final String subject = templateService.process(templateName, Set.of("subject"),
        templateContext);
    final String content = templateService.process(templateName, Set.of("content"),
        templateContext);

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    ObjectId notificationId = ObjectId.get();
    mimeMessage.addHeader("NotificationId", notificationId.toString());

    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
    helper.setTo(recipient);
    helper.setFrom(sender);
    helper.setSubject(subject);
    helper.setText(content, true);

    mailSender.send(helper.getMimeMessage());

    // Store the notification history.
    RecipientInfo recipientInfo = new RecipientInfo(traineeId, EMAIL, recipient);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(),
        templateVersion, templateVariables);
    History history = new History(notificationId, null, notificationType, recipientInfo,
        templateInfo, Instant.now(), NotificationStatus.SENT, null);
    historyService.save(history);

    log.info("Sent template {} to {}.", templateName, recipient);
  }

  /**
   * Get the user account for the given trainee ID.
   *
   * @param traineeId The trainee ID to get the account for.
   * @return The found account.
   */
  private UserAccountDetails getRecipientAccount(String traineeId) {
    Set<String> userAccountIds = userAccountService.getUserAccountIds(traineeId);

    return switch (userAccountIds.size()) {
      case 0 -> throw new IllegalArgumentException("No user account found for the given ID.");
      case 1 -> userAccountService.getUserDetails(userAccountIds.iterator().next());
      default ->
          throw new IllegalArgumentException("Multiple user accounts found for the given ID.");
    };
  }
}
