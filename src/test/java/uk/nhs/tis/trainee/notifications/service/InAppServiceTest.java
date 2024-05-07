/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PLACEMENT;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class InAppServiceTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String VERSION = "V1.2.3";

  private InAppService service;
  private HistoryService historyService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    service = new InAppService(historyService);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldCreateNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    verify(historyService).save(any());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotStoreNotificationIfOnlyLogged(NotificationType notificationType) {
    String referenceId = UUID.randomUUID().toString();
    TisReferenceInfo referenceInfo = new TisReferenceInfo(PLACEMENT, referenceId);
    service.createNotifications(TRAINEE_ID, referenceInfo, notificationType, VERSION, Map.of(),
        true);

    verify(historyService, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotSetIdWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected history ID.", history.id(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetTisReferenceWhenCreatingNotification(NotificationType notificationType) {
    String referenceId = UUID.randomUUID().toString();
    TisReferenceInfo referenceInfo = new TisReferenceInfo(PLACEMENT, referenceId);
    service.createNotifications(TRAINEE_ID, referenceInfo, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected TIS reference info.", history.tisReference(), notNullValue());
    assertThat("Unexpected TIS reference type.", history.tisReference().type(), is(PLACEMENT));
    assertThat("Unexpected TIS reference id.", history.tisReference().id(), is(referenceId));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetNotificationTypeWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected notification type.", history.type(), is(notificationType));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetRecipientInfoWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    RecipientInfo recipient = history.recipient();
    assertThat("Unexpected recipient ID.", recipient.id(), is(TRAINEE_ID));
    assertThat("Unexpected recipient type.", recipient.type(), is(IN_APP));
    assertThat("Unexpected recipient contact.", recipient.contact(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetTemplateInfoWhenCreatingNotification(NotificationType notificationType) {
    Map<String, Object> variables = Map.of(
        "field1", "value1",
        "field2", 2,
        "field3", Instant.EPOCH
    );
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, variables);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    TemplateInfo template = history.template();
    assertThat("Unexpected template name.", template.name(),
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", template.version(), is(VERSION));

    Map<String, Object> savedVariables = template.variables();
    assertThat("Unexpected template variable count.", savedVariables.size(), is(3));
    assertThat("Unexpected template variable.", savedVariables.get("field1"), is("value1"));
    assertThat("Unexpected template variable.", savedVariables.get("field2"), is(2));
    assertThat("Unexpected template variable.", savedVariables.get("field3"), is(Instant.EPOCH));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetSentAtWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    long diffSeconds = Instant.now().getEpochSecond() - history.sentAt().getEpochSecond();
    assertThat("Unexpected sentAt time difference.", diffSeconds, lessThan(10L));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldNotSetReadAtWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected readAt timestamp.", history.readAt(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetStatusWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected status.", history.status(), is(UNREAD));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetStatusDetailWhenCreatingNotification(NotificationType notificationType) {
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of());

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected status detail.", history.statusDetail(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSetSentAtTimeWhenCreatingNotification(NotificationType notificationType) {
    Instant timeNow = Instant.now();
    service.createNotifications(TRAINEE_ID, null, notificationType, VERSION, Map.of(),
        false, timeNow);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.captor();
    verify(historyService).save(historyCaptor.capture());

    History history = historyCaptor.getValue();
    assertThat("Unexpected sent at.", history.sentAt(), is(timeNow));
  }
}
