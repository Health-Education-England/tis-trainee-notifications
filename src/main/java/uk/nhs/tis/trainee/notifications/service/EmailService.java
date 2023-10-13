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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;

/**
 * A service for sending emails.
 */
@Slf4j
@Service
public class EmailService {

  private final UserAccountService userAccountService;
  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final String sender;
  private final URI appDomain;
  private final String timezone;

  EmailService(UserAccountService userAccountService, JavaMailSender mailSender,
      TemplateEngine templateEngine, @Value("${application.email.sender}") String sender,
      @Value("${application.domain}") URI appDomain,
      @Value("${application.timezone}") String timezone) {
    this.userAccountService = userAccountService;
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.sender = sender;
    this.appDomain = appDomain;
    this.timezone = timezone;
  }

  /**
   * Email a user with an existing account, the name and domain variables will be set if not
   * provided.
   *
   * @param traineeId         The trainee ID of the user.
   * @param templateName      The email template to use.
   * @param templateVariables The variables to pass to the template.
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessageToExistingUser(String traineeId, String templateName,
      Map<String, Object> templateVariables)
      throws MessagingException {
    UserAccountDetails userDetails = getRecipientAccount(traineeId);

    if (Strings.isBlank(userDetails.email())) {
      String message = "Could not find email address for user '%s'".formatted(traineeId);
      throw new IllegalArgumentException(message);
    }

    templateVariables = new HashMap<>(templateVariables);
    templateVariables.putIfAbsent("name", userDetails.familyName());
    sendMessage(userDetails.email(), templateName, templateVariables);
  }

  /**
   * Send an email message using a given template, the domain variable will be set if not provided.
   *
   * @param recipient         Where the email should be sent.
   * @param templateName      The email template to use.
   * @param templateVariables The variables to pass to the template.
   * @throws MessagingException When the message could not be sent.
   */
  private void sendMessage(String recipient, String templateName,
      Map<String, Object> templateVariables) throws MessagingException {
    log.info("Sending template {} to {}.", templateName, recipient);

    // Add the application domain for any templates with hyperlinks.
    templateVariables.putIfAbsent("domain", appDomain);

    // Convert UTC timestamps to the Local Office timezone.
    localizeTimestamps(templateVariables);
    Context templateContext = new Context();
    templateContext.setVariables(templateVariables);

    String subject = templateEngine.process(templateName, Set.of("subject"), templateContext);
    String content = templateEngine.process(templateName, Set.of("content"), templateContext);

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
    helper.setTo(recipient);
    helper.setFrom(sender);
    helper.setSubject(subject);
    helper.setText(content, true);

    mailSender.send(helper.getMimeMessage());
    log.info("Sent template {} to {}.", templateName, recipient);
  }

  /**
   * Localize any compatible data types.
   *
   * @param templateVariables The template variables to localize.
   */
  private void localizeTimestamps(Map<String, Object> templateVariables) {
    for (Entry<String, Object> entry : templateVariables.entrySet()) {

      if (entry.getValue() instanceof Instant timestamp) {
        ZonedDateTime localised = ZonedDateTime.ofInstant(timestamp, ZoneId.of(timezone));
        entry.setValue(localised);
      }
    }
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
