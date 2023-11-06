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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

@SpringBootTest(
    properties = {"embedded.containers.enabled=true", "embedded.containers.mongodb.enabled=true"})
@ActiveProfiles({"mongodb", "test"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class HistoryServiceIntegrationTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private static final ObjectId NOTIFICATION_ID = ObjectId.get();

  private static final String TEMPLATE_NAME = "email/test-template";
  private static final String TEMPLATE_VERSION = "v1.2.3";
  private static final Map<String, Object> TEMPLATE_VARIABLES = Map.of(
      "key1", "value1",
      "key2", "value2");

  private static final Instant SENT_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  @Autowired
  private HistoryService service;

  @Test
  void shouldSaveHistory() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);

    History history = new History(null, FORM_UPDATED, recipientInfo, templateInfo, SENT_AT);
    History savedHistory = service.save(history);

    assertThat("Unexpected ID.", savedHistory.id(), instanceOf(ObjectId.class));
    assertThat("Unexpected type.", savedHistory.type(), is(FORM_UPDATED));
    assertThat("Unexpected sent at.", savedHistory.sentAt(), is(SENT_AT));

    RecipientInfo savedRecipientInfo = savedHistory.recipient();
    assertThat("Unexpected recipient id.", savedRecipientInfo.id(), is(TRAINEE_ID));
    assertThat("Unexpected recipient type.", savedRecipientInfo.type(), is(EMAIL));
    assertThat("Unexpected recipient contact.", savedRecipientInfo.contact(), is(TRAINEE_CONTACT));

    TemplateInfo savedTemplateInfo = savedHistory.template();
    assertThat("Unexpected template name.", savedTemplateInfo.name(), is(TEMPLATE_NAME));
    assertThat("Unexpected template version.", savedTemplateInfo.version(), is(TEMPLATE_VERSION));
    assertThat("Unexpected template variables.", savedTemplateInfo.variables(),
        is(TEMPLATE_VARIABLES));
  }

  @Test
  void shouldNotFindNotificationsWhenTraineeIdNotMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);

    History history = new History(null, FORM_UPDATED, recipientInfo, templateInfo, SENT_AT);
    service.save(history);

    List<HistoryDto> foundHistory = service.findAllForTrainee("notFound");

    assertThat("Unexpected history count.", foundHistory.size(), is(0));
  }

  @Test
  void shouldFindNotificationsWhenTraineeIdMatches() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);

    History history = new History(null, FORM_UPDATED, recipientInfo, templateInfo, SENT_AT);
    History savedHistory = service.save(history);

    List<HistoryDto> foundHistory = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(1));

    HistoryDto historyDto = foundHistory.get(0);
    assertThat("Unexpected history id.", historyDto.id(), is(savedHistory.id().toString()));
    assertThat("Unexpected history type.", historyDto.type(), is(EMAIL));
    assertThat("Unexpected history subject.", historyDto.subject(), is(FORM_UPDATED));
    assertThat("Unexpected history contact.", historyDto.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto.sentAt(), is(SENT_AT));
  }

  @Test
  void shouldSortFoundNotificationsBySentAtWhenMultipleFound() {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(TEMPLATE_NAME, TEMPLATE_VERSION,
        TEMPLATE_VARIABLES);

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    service.save(new History(null, FORM_UPDATED, recipientInfo, templateInfo, now));

    Instant before = SENT_AT.minus(Duration.ofDays(1));
    service.save(new History(null, FORM_UPDATED, recipientInfo, templateInfo, before));

    Instant after = SENT_AT.plus(Duration.ofDays(1));
    service.save(new History(null, FORM_UPDATED, recipientInfo, templateInfo, after));

    List<HistoryDto> foundHistory = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", foundHistory.size(), is(3));

    HistoryDto history1 = foundHistory.get(0);
    assertThat("Unexpected history sent at.", history1.sentAt(), is(after));

    HistoryDto history2 = foundHistory.get(1);
    assertThat("Unexpected history sent at.", history2.sentAt(), is(now));

    HistoryDto history3 = foundHistory.get(2);
    assertThat("Unexpected history sent at.", history3.sentAt(), is(before));
  }

  @Test
  void shouldNotRebuildMessageWhenNotificationNotFound() {
    Optional<String> message = service.rebuildMessage(ObjectId.get().toString());

    assertThat("Unexpected message.", message, is(Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource(
      "uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getEmailTemplateTypeAndVersions")
  void shouldRebuildEmailMessageWhenNotificationFound(NotificationType notificationType,
      String version) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo(notificationType.getTemplateName(), version,
        TEMPLATE_VARIABLES);
    History history = new History(NOTIFICATION_ID, notificationType, recipientInfo, templateInfo,
        Instant.now());
    service.save(history);

    Optional<String> message = service.rebuildMessage(NOTIFICATION_ID.toString());

    assertThat("Unexpected message presence.", message.isPresent(), is(true));

    Document content = Jsoup.parse(message.get());
    Element body = content.body();

    Element emailHeader = body.children().get(0);
    assertThat("Unexpected element tag.", emailHeader.tagName(), is("h1"));
    assertThat("Unexpected email header.", emailHeader.text(), is("Email Message"));

    Element subjectHeader = body.children().get(1);
    assertThat("Unexpected element tag.", subjectHeader.tagName(), is("h2"));
    assertThat("Unexpected subject header.", subjectHeader.text(), is("Subject"));

    Element bodyHeader = body.children().get(2);
    assertThat("Unexpected element tag.", bodyHeader.tagName(), is("h2"));
    assertThat("Unexpected body header.", bodyHeader.text(), is("Content"));
  }
}
