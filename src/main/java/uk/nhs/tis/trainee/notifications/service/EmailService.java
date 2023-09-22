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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * A service for sending emails.
 */
@Slf4j
@Service
public class EmailService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final String sender;

  EmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
      @Value("${application.email.sender}") String sender) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.sender = sender;
  }

  /***
   * Send an email message using a given template.
   *
   * @param recipient Where the email should be sent.
   * @param subject The email subject line.
   * @param templateName The email template to use.
   * @param templateVariables The variables to pass to the template.
   * @throws MessagingException When the message could not be sent.
   */
  public void sendMessage(String recipient, String subject, String templateName,
      Map<String, Object> templateVariables) throws MessagingException {
    Context templateContext = new Context();
    templateContext.setVariables(templateVariables);
    String text = templateEngine.process(templateName, templateContext);

    log.info(text);

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
    helper.setTo(recipient);
    helper.setFrom(sender);
    helper.setSubject(subject);
    helper.setText(text, true);

    mailSender.send(helper.getMimeMessage());
  }
}
