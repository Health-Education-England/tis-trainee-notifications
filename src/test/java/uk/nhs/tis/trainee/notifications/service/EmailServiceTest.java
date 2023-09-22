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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

class EmailServiceTest {

  private static final String RECIPIENT = "anthony.gilliam@tis.nhs.uk";
  private static final String SENDER = "sender@test.email";

  private EmailService service;
  private JavaMailSender mailSender;
  private TemplateEngine templateEngine;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    templateEngine = mock(TemplateEngine.class);
    when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("");

    service = new EmailService(mailSender, templateEngine, SENDER);
  }

  @Test
  void shouldSendMessageToRecipient() throws MessagingException {
    service.sendMessage(RECIPIENT, "", "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Address[] toRecipients = message.getRecipients(RecipientType.TO);
    assertThat("Unexpected recipient count.", toRecipients.length, is(1));
    assertThat("Unexpected recipient.", toRecipients[0].toString(), is(RECIPIENT));

    Address[] allRecipients = message.getAllRecipients();
    assertThat("Unexpected recipient count.", allRecipients.length, is(1));
  }

  @Test
  void shouldSendMessageFromSender() throws MessagingException {
    service.sendMessage(RECIPIENT, "", "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Address[] senders = message.getFrom();
    assertThat("Unexpected sender count.", senders.length, is(1));
    assertThat("Unexpected sender.", senders[0].toString(), is(SENDER));
  }

  @Test
  void shouldSendMessageWithSubject() throws MessagingException {
    String subject = "Test Notification";

    service.sendMessage(RECIPIENT, subject, "", Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), is(subject));
  }

  @Test
  void shouldSendMessageWithBody() throws MessagingException, IOException {
    String body = """
        <!DOCTYPE html>
        <html>
          <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
          </head>
          <body>
            <div>Test message body</div>
          </body>
        </html>
        """;
    when(templateEngine.process(any(String.class), any())).thenReturn(body);

    service.sendMessage(RECIPIENT, "", "email/test.html", Map.of("key1", "value1"));

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected content.", message.getContent(), is(body));

    DataHandler dataHandler = message.getDataHandler();
    assertThat("Unexpected content type.", dataHandler.getContentType(),
        is("text/html;charset=UTF-8"));
  }

  @Test
  void shouldProcessTheTemplateWhenSendingMessage() throws MessagingException, IOException {
    String templateName = "email/test.html";

    service.sendMessage(RECIPIENT, "", templateName, Map.of("key1", "value1"));

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine).process(eq(templateName), contextCaptor.capture());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected template variable count.", context.getVariableNames().size(), is(1));
    assertThat("Unexpected template variable.", context.getVariable("key1"), is("value1"));
  }
}
