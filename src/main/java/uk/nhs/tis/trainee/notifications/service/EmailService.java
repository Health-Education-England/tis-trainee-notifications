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

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import jakarta.annotation.Nullable;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.notifications.dto.StoredFile;
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
  private final S3Template s3Template;
  private final String sender;
  private final URI appDomain;

  EmailService(UserAccountService userAccountService, HistoryService historyService,
      JavaMailSender mailSender, TemplateService templateService, S3Template s3Template,
      @Value("${application.email.sender}") String sender,
      @Value("${application.domain}") URI appDomain) {
    this.userAccountService = userAccountService;
    this.historyService = historyService;
    this.mailSender = mailSender;
    this.templateService = templateService;
    this.s3Template = s3Template;
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
    sendMessageToExistingUser(traineeId, notificationType, templateVersion, templateVariables,
        tisReferenceInfo, null);
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
   * @param attachment        A published PDF to include as an attachment.
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessageToExistingUser(String traineeId, NotificationType notificationType,
      String templateVersion, Map<String, Object> templateVariables,
      TisReferenceInfo tisReferenceInfo, StoredFile attachment) throws MessagingException {
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
        templateVariables, tisReferenceInfo, attachment, false);
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
    sendMessage(traineeId, recipient, notificationType, templateVersion, templateVariables,
        tisReferenceInfo, null, doNotSendJustLog);
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
   * @param attachment        A published PDF to include as an attachment.
   * @param doNotSendJustLog  Do not actually send the mail, simply log the action.
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessage(String traineeId, @Nullable String recipient,
      NotificationType notificationType,
      String templateVersion, Map<String, Object> templateVariables,
      TisReferenceInfo tisReferenceInfo, StoredFile attachment, boolean doNotSendJustLog)
      throws MessagingException {
    String templateName = templateService.getTemplatePath(EMAIL, notificationType, templateVersion);
    log.info("Processing send job template {} to {}.", templateName, recipient);

    if (!doNotSendJustLog || true) {
      ObjectId notificationId = ObjectId.get();
      NotificationStatus status;
      String statusDetail = null;

      // find scheduled history from DB
      History latestScheduledHistory = null;
      if (tisReferenceInfo != null) {
        latestScheduledHistory = historyService.findScheduledEmailForTraineeByRefAndType(
            traineeId, tisReferenceInfo.type(), tisReferenceInfo.id(), notificationType);
        if (latestScheduledHistory != null) {
          notificationId = latestScheduledHistory.id();
        }
      }

      List<StoredFile> attachments = attachment == null ? null : List.of(attachment);

      if (recipient != null) {
        MimeMessageHelper helper = buildMessageHelper(recipient, templateName, templateVariables,
            notificationId, attachments);
        //mailSender.send(helper.getMimeMessage());
        log.info(helper.getMimeMessage().getSubject());
        try {
          log.info(helper.getMimeMessage().getContent().toString());
        } catch (IOException e) {
          //throw new RuntimeException(e);
        }
        log.info("Sent template {} to {}.", templateName, recipient);
        status = NotificationStatus.PENDING;
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
          recipientInfo, templateInfo, attachments, Instant.now(), null, status, statusDetail,
          null);
      historyService.save(history);

      log.info("Finish processing send job {} to {}.", templateName, recipient);
    } else {
      log.info("Send job is ignored. "
              + "For now, just logging mail to '{}' from template '{}' with variables '{}'",
          recipient, templateName, templateVariables);
    }

    // Delete SCHEDULED history after the notification is sent or ignored
    if (tisReferenceInfo != null) {
      List<History> allScheduledHistories =
          historyService.findAllScheduledEmailForTraineeByRefAndType(
          traineeId, tisReferenceInfo.type(), tisReferenceInfo.id(), notificationType);
      for (History history : allScheduledHistories) {
        historyService.deleteHistoryForTrainee(history.id(), traineeId);
      }
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
      log.info("Re-sending template {} to {}.", templateName, updatedEmailAddress);
      ObjectId notificationId = toResend.id();
      Map<String, Object> resendVariables = new HashMap<>(toResend.template().variables());
      resendVariables.putIfAbsent("originallySentOn", toResend.sentAt());

      MimeMessageHelper helper = buildMessageHelper(updatedEmailAddress, templateName,
          resendVariables, notificationId, toResend.attachments());

      mailSender.send(helper.getMimeMessage());

      //update history entry
      TemplateInfo updatedTemplateInfo = new TemplateInfo(toResend.type().getTemplateName(),
          toResend.template().version(), toResend.template().variables());
      RecipientInfo updatedRecipientInfo = new RecipientInfo(toResend.recipient().id(),
          toResend.recipient().type(), updatedEmailAddress);
      History updatedHistory = new History(notificationId, toResend.tisReference(), toResend.type(),
          updatedRecipientInfo, updatedTemplateInfo, toResend.attachments(), toResend.sentAt(),
          toResend.readAt(), NotificationStatus.PENDING, null, Instant.now());
      historyService.save(updatedHistory);

      log.info("Re-sent template {} to {}.", templateName, updatedEmailAddress);
    }
  }

  /**
   * Build the Mime message helper object with the populated template as content.
   *
   * @param recipient         Where the email should be sent.
   * @param templateName      The versioned template name.
   * @param templateVariables The variables to pass to the template.
   * @param notificationId    The notification ID to set in the email header.
   * @param attachments       Optional stored files to include as attachments.
   * @return The build Mime message helper.
   * @throws MessagingException if there is an error populating the helper.
   */
  private MimeMessageHelper buildMessageHelper(String recipient, String templateName,
      Map<String, Object> templateVariables, ObjectId notificationId, List<StoredFile> attachments)
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

    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, attachments != null,
        StandardCharsets.UTF_8.name());
    helper.setTo(recipient);
    helper.setFrom(sender);
    helper.setSubject(subject);
    helper.setText(content, true);

    if (attachments != null) {
      for (StoredFile attachment : attachments) {
        S3Resource resource = s3Template.download(attachment.bucket(), attachment.key());

        try {
          helper.addAttachment(Objects.requireNonNull(resource.getFilename()),
              new ByteArrayResource(resource.getContentAsByteArray()), resource.contentType());
        } catch (IOException e) {
          String message = String.format("Unable to read file '%s:%s'.", attachment.bucket(),
              attachment.key());
          throw new MessagingException(message, e);
        }
      }
    }

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
    return new UserDetails(true, email, "Mr", "Test", "Testy", "1234567");
    //return userAccountService.getUserDetailsByEmail(email);
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
      case 0 -> throw new IllegalArgumentException(
          "No account found for trainee %s.".formatted(traineeId));
      case 1 -> userAccountService.getUserDetailsById(userAccountIds.iterator().next());
      default -> throw new IllegalArgumentException(
          "%s accounts found for trainee %s. Found: [%s]".formatted(userAccountIds.size(),
              traineeId, String.join(", ", userAccountIds)));
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
