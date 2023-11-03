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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.mapper.HistoryMapperImpl;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.repository.HistoryRepository;

class HistoryServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT = "test@tis.nhs.uk";

  private HistoryService service;
  private HistoryRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(HistoryRepository.class);
    service = new HistoryService(repository, new HistoryMapperImpl());
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldSaveHistory(NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(null, null, null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, Map.of());
    Instant now = Instant.now();
    History history = new History(null, notificationType, recipientInfo,
        templateInfo, now);

    ObjectId id = new ObjectId();
    when(repository.save(history)).then(inv -> {
      History saving = inv.getArgument(0);
      assertThat("Unexpected ID.", saving.id(), nullValue());
      return new History(id, saving.type(), saving.recipient(), saving.template(),
          saving.sentAt());
    });

    History savedHistory = service.save(history);

    assertThat("Unexpected ID.", savedHistory.id(), is(id));
    assertThat("Unexpected type.", savedHistory.type(), is(notificationType));
    assertThat("Unexpected recipient.", savedHistory.recipient(), sameInstance(recipientInfo));
    assertThat("Unexpected template.", savedHistory.template(), sameInstance(templateInfo));
    assertThat("Unexpected sent at.", savedHistory.sentAt(), is(now));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldFindNoneForTraineeWhenNotificationsNotExist(NotificationType notificationType) {
    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(List.of());

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(0));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void shouldFindAllForTraineeWhenNotificationsExist(NotificationType notificationType) {
    RecipientInfo recipientInfo = new RecipientInfo(TRAINEE_ID, EMAIL, TRAINEE_CONTACT);
    TemplateInfo templateInfo = new TemplateInfo("test/template/", "v1.2.3", Map.of());

    ObjectId id1 = ObjectId.get();
    History history1 = new History(id1, notificationType, recipientInfo, templateInfo, Instant.MIN);

    ObjectId id2 = ObjectId.get();
    History history2 = new History(id2, notificationType, recipientInfo, templateInfo, Instant.MAX);

    when(repository.findAllByRecipient_IdOrderBySentAtDesc(TRAINEE_ID)).thenReturn(
        List.of(history1, history2));

    List<HistoryDto> historyDtos = service.findAllForTrainee(TRAINEE_ID);

    assertThat("Unexpected history count.", historyDtos.size(), is(2));

    HistoryDto historyDto1 = historyDtos.get(0);
    assertThat("Unexpected history id.", historyDto1.id(), is(id1.toString()));
    assertThat("Unexpected history type.", historyDto1.type(), is(EMAIL));
    assertThat("Unexpected history subject.", historyDto1.subject(), is(notificationType));
    assertThat("Unexpected history contact.", historyDto1.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto1.sentAt(), is(Instant.MIN));

    HistoryDto historyDto2 = historyDtos.get(1);
    assertThat("Unexpected history id.", historyDto2.id(), is(id2.toString()));
    assertThat("Unexpected history type.", historyDto2.type(), is(EMAIL));
    assertThat("Unexpected history subject.", historyDto2.subject(), is(notificationType));
    assertThat("Unexpected history contact.", historyDto2.contact(), is(TRAINEE_CONTACT));
    assertThat("Unexpected history sent at.", historyDto2.sentAt(), is(Instant.MAX));
  }
}
