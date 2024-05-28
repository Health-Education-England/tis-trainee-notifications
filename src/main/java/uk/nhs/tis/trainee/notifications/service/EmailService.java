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

import jakarta.annotation.Nullable;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
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
   * @param tisReferenceInfo  The TIS reference information (table and key).
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessageToExistingUser(String traineeId, NotificationType notificationType,
      String templateVersion, Map<String, Object> templateVariables,
      TisReferenceInfo tisReferenceInfo) throws MessagingException {
    if (traineeId == null) {
      throw new IllegalArgumentException("Unable to send notification as no trainee ID available");
    }

    UserDetails userDetails = getRecipientAccount(traineeId);

    if (Strings.isBlank(userDetails.email())) {
      String message = "Could not find email address for user '%s'".formatted(traineeId);
      throw new IllegalArgumentException(message);
    }

    templateVariables = new HashMap<>(templateVariables);
    templateVariables.putIfAbsent("familyName", userDetails.familyName());
    templateVariables.putIfAbsent("givenName", userDetails.givenName());
    sendMessage(traineeId, userDetails.email(), notificationType, templateVersion,
        templateVariables, tisReferenceInfo, false);
  }

  /**
   * Send an email message using a given template, the domain variable will be set if not provided.
   * If no email address is given a history record will still be saved with a failed state for
   * reporting purposes.
   *
   * @param traineeId         The trainee ID of the recipient.
   * @param recipient         Where the email should be sent, should be null when not available.
   * @param notificationType  The type of notification, which will determine the template used.
   * @param templateVersion   The version of the template to be sent.
   * @param templateVariables The variables to pass to the template.
   * @param tisReferenceInfo  The TIS reference information (table and key).
   * @param doNotSendJustLog  Do not actually send the mail, simply log the action.
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessage(String traineeId, @Nullable String recipient,
      NotificationType notificationType,
      String templateVersion, Map<String, Object> templateVariables,
      TisReferenceInfo tisReferenceInfo, boolean doNotSendJustLog) throws MessagingException {
    String templateName = templateService.getTemplatePath(EMAIL, notificationType, templateVersion);
    log.info("Sending template {} to {}.", templateName, recipient);

    if (!doNotSendJustLog) {
      ObjectId notificationId = ObjectId.get();
      NotificationStatus status;
      String statusDetail = null;

      if (recipient != null) {
        MimeMessageHelper helper = buildMessageHelper(recipient, templateName, templateVariables,
            notificationId);
        mailSender.send(helper.getMimeMessage());
        status = NotificationStatus.SENT;
      } else {
        log.info("No email address available for trainee {}, this failure will be recorded.",
            traineeId);
        status = NotificationStatus.FAILED;
        statusDetail = "No email address available.";
      }

      // Store the notification history.
      RecipientInfo recipientInfo = new RecipientInfo(traineeId, EMAIL, recipient);
      TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(),
          templateVersion, templateVariables);
      History history = new History(notificationId, tisReferenceInfo, notificationType,
          recipientInfo, templateInfo, Instant.now(), null, status, statusDetail, null);
      historyService.save(history);

      log.info("Sent template {} to {}.", templateName, recipient);
    } else {
      log.info("For now, just logging mail to '{}' from template '{}' with variables '{}'",
          recipient, templateName, templateVariables);
    }
  }

  /**
   * Resend the message for the given history item and update it with the new details.
   *
   * @param toResend            The history item that should be resent.
   * @param updatedEmailAddress The updated email address to use.
   * @throws MessagingException When the message could not be sent.
   */
  public void resendMessage(History toResend, String updatedEmailAddress)
      throws MessagingException {
    if (toResend.recipient().type() != EMAIL) {
      log.warn("Cannot resend non-email history item {}", toResend);
    } else {
      String templateName = templateService.getTemplatePath(EMAIL, toResend.type(),
          toResend.template().version());
      log.info("Sending template {} to {}.", templateName, updatedEmailAddress);
      ObjectId notificationId = toResend.id();
      Map<String, Object> resendVariables = new HashMap<>(toResend.template().variables());
      resendVariables.putIfAbsent("originallySentOn", toResend.sentAt());

      MimeMessageHelper helper = buildMessageHelper(updatedEmailAddress, templateName,
          resendVariables, notificationId);

      mailSender.send(helper.getMimeMessage());

      //update history entry
      TemplateInfo updatedTemplateInfo = new TemplateInfo(toResend.type().getTemplateName(),
          toResend.template().version(), toResend.template().variables());
      RecipientInfo updatedRecipientInfo = new RecipientInfo(toResend.recipient().id(),
          toResend.recipient().type(), updatedEmailAddress);
      History updatedHistory = new History(notificationId, toResend.tisReference(), toResend.type(),
          updatedRecipientInfo, updatedTemplateInfo, toResend.sentAt(), toResend.readAt(),
          NotificationStatus.SENT, null, Instant.now());
      historyService.save(updatedHistory);

      log.info("Sent template {} to {}.", templateName, updatedEmailAddress);
    }
  }

  /**
   * Build the Mime message helper object with the populated template as content.
   *
   * @param recipient         Where the email should be sent.
   * @param templateName      The versioned template name.
   * @param templateVariables The variables to pass to the template
   * @param notificationId    The notification ID to set in the email header
   * @return The build Mime message helper
   * @throws MessagingException if there is an error populating the helper.
   */
  private MimeMessageHelper buildMessageHelper(String recipient, String templateName,
      Map<String, Object> templateVariables, ObjectId notificationId)
      throws MessagingException {

    // Add the application domain for any templates with hyperlinks.
    templateVariables.putIfAbsent("domain", appDomain);
    templateVariables.putIfAbsent("hashedEmail", createMd5Hash(recipient));

    Context templateContext = templateService.buildContext(templateVariables);
    final String subject = templateService.process(templateName, Set.of("subject"),
        templateContext);
    final String content = templateService.process(templateName, Set.of("content"),
        templateContext);

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    mimeMessage.addHeader("NotificationId", notificationId.toString());

    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
    helper.setTo(recipient);
    helper.setFrom(sender);
    helper.setSubject(subject);
    helper.setText(content, true);

    return helper;
  }

  /**
   * Get the user account for the given trainee email.
   *
   * @param email The trainee email to get the account for.
   * @return The found account.
   * @throws UserNotFoundException if the user account could not be found.
   */
  public UserDetails getRecipientAccountByEmail(String email) throws UserNotFoundException {
    return userAccountService.getUserDetailsByEmail(email);
  }

  /**
   * Get the user account for the given trainee ID.
   *
   * @param traineeId The trainee ID to get the account for.
   * @return The found account.
   */
  public UserDetails getRecipientAccount(String traineeId) {
    Set<String> userAccountIds = userAccountService.getUserAccountIds(traineeId);

    return switch (userAccountIds.size()) {
      case 0 -> throw new IllegalArgumentException("No user account found for the given ID.");
      case 1 -> userAccountService.getUserDetailsById(userAccountIds.iterator().next());
      default ->
          throw new IllegalArgumentException("Multiple user accounts found for the given ID.");
    };
  }

  /**
   * Create an MD5 hash of a given string.
   *
   * @param input The string to hash.
   * @return The MD5 hash, or a fixed default if MD5 is not available or the input is null.
   */
  public String createMd5Hash(final String input) {
    if (input != null) {
      try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] messageDigest = md.digest(input.getBytes());
        return convertToHex(messageDigest);
      } catch (NoSuchAlgorithmException ignored) {
        log.warn("MD5 algorithm not available, default hash will be used.");
      }
    }
    return "0".repeat(32); //default hash
  }

  /**
   * Helper function to convert an array of bytes into a hexadecimal encoded string.
   *
   * @param messageDigest The array of bytes to convert.
   * @return The hex string.
   */
  private String convertToHex(final byte[] messageDigest) {
    BigInteger bigint = new BigInteger(1, messageDigest);
    String hexText = bigint.toString(16);
    while (hexText.length() < 32) {
      hexText = "0".concat(hexText);
    }
    return hexText;
  }
}
