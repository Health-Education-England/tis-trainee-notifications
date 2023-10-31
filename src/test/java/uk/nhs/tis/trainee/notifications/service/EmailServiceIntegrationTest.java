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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.ResourceUtils.CLASSPATH_URL_PREFIX;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.ResourceUtils;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

@SpringBootTest(classes = EmailService.class)
@ImportAutoConfiguration(ThymeleafAutoConfiguration.class)
class EmailServiceIntegrationTest {

  private static final String PERSON_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String RECIPIENT = "test@tis.nhs.uk";

  @MockBean
  private JavaMailSender mailSender;

  @MockBean
  private UserAccountService userAccountService;

  @Autowired
  private EmailService service;

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    when(userAccountService.getUserAccountIds(PERSON_ID)).thenReturn(Set.of(USER_ID));
  }

  private static Stream<Arguments> getEmailTemplates() throws IOException {
    File emailRoot = ResourceUtils.getFile(CLASSPATH_URL_PREFIX + "templates/email/");
    List<Arguments> arguments = new ArrayList<>();

    for (NotificationType type : NotificationType.values()) {
      String name = type.getTemplateName();
      Path templateRoot = emailRoot.toPath().resolve(name);
      int parentNameCount = templateRoot.getNameCount();
      List<Arguments> typeArguments = Files.walk(templateRoot)
          .filter(Files::isRegularFile)
          .map(path -> path.subpath(parentNameCount, path.getNameCount()))
          .map(subPath -> subPath.toString().replace(".html", ""))
          .map(version -> Arguments.of(type, version))
          .toList();
      arguments.addAll(typeArguments);
    }

    return arguments.stream();
  }

  @ParameterizedTest
  @MethodSource("getEmailTemplates")
  void shouldIncludeSubjectInTemplates(NotificationType notificationType, String templateVersion)
      throws Exception {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected subject.", message.getSubject(), not(emptyOrNullString()));
  }

  @ParameterizedTest
  @MethodSource("getEmailTemplates")
  void shouldIncludeContentInTemplates(NotificationType notificationType, String templateVersion)
      throws Exception {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    assertThat("Unexpected content.", (String) message.getContent(), not(emptyOrNullString()));
  }

  @ParameterizedTest
  @MethodSource("getEmailTemplates")
  void shouldGreetExistingUsersConsistentlyWhenNameNotAvailable(NotificationType notificationType,
      String templateVersion) throws Exception {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, null));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    Element greeting = body.children().get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Doctor,"));
  }

  @ParameterizedTest
  @MethodSource("getEmailTemplates")
  void shouldGreetExistingUsersConsistentlyWhenNameAvailable(NotificationType notificationType,
      String templateVersion) throws Exception {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, "Gilliam"));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion, Map.of());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    Element greeting = body.children().get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Gilliam,"));
  }

  @ParameterizedTest
  @MethodSource("getEmailTemplates")
  void shouldGreetExistingUsersConsistentlyWhenNameProvided(NotificationType notificationType,
      String templateVersion) throws Exception {
    when(userAccountService.getUserDetails(USER_ID)).thenReturn(
        new UserAccountDetails(RECIPIENT, "Gilliam"));

    service.sendMessageToExistingUser(PERSON_ID, notificationType, templateVersion,
        Map.of("name", "Milliag"));

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage message = messageCaptor.getValue();
    Document content = Jsoup.parse((String) message.getContent());
    Element body = content.body();

    Element greeting = body.children().get(0);
    assertThat("Unexpected element tag.", greeting.tagName(), is("p"));
    assertThat("Unexpected greeting.", greeting.text(), is("Dear Dr Milliag,"));
  }
}
