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

package uk.nhs.tis.trainee.notifications.mapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static uk.nhs.tis.trainee.notifications.mapper.HistoryMapper.WELCOME_SUBJECT_TEXT;

import java.time.Duration;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class HistoryMapperTest {

  private HistoryMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new HistoryMapperImpl();
  }

  @ParameterizedTest
  @EnumSource(NotificationStatus.class)
  void shouldUseExistingReadAtWhenPopulated(NotificationStatus status) {
    Instant existingReadAt = Instant.now().minus(Duration.ofDays(1));
    History entity = new History(null, null, null, null, null, null, existingReadAt, null, null);

    Instant readAt = mapper.calculateReadAt(entity, status);
    assertThat("Unexpected readAt timestamp.", readAt, is(existingReadAt));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = Mode.EXCLUDE, names = "READ")
  void shouldNotGenerateReadAtWhenStatusNotReadAndNotPopulated(NotificationStatus status) {
    History entity = new History(null, null, null, null, null, null, null, null, null);

    Instant readAt = mapper.calculateReadAt(entity, status);
    assertThat("Unexpected readAt timestamp.", readAt, nullValue());
  }

  @Test
  void shouldGenerateReadAtWhenStatusReadAndNotPopulated() {
    History entity = new History(null, null, null, null, null, null, null, null, null);

    Instant readAt = mapper.calculateReadAt(entity, NotificationStatus.READ);
    assertThat("Unexpected readAt timestamp.", readAt, notNullValue());

    long diffSeconds = Instant.now().getEpochSecond() - readAt.getEpochSecond();
    assertThat("Unexpected readAt timestamp drift.", diffSeconds, lessThan(10L));
  }

  @Test
  void shouldGenerateSubjectTextWhenWelcomeInAppNotification() {
    ObjectId id = new ObjectId();
    RecipientInfo recipient = new RecipientInfo(null, MessageType.IN_APP, null);
    History entity = new History(id, null, NotificationType.WELCOME, recipient, null, null, null, null, null);

    HistoryDto dto = mapper.toDto(entity);
    assertThat("Unexpected subject text.", dto.subjectText(), is(WELCOME_SUBJECT_TEXT));
  }

  @Test
  void shouldHaveNoSubjectTextWhenNotInAppNotification() {
    ObjectId id = new ObjectId();
    RecipientInfo recipient = new RecipientInfo(null, MessageType.EMAIL, null);
    History entity = new History(id, null, NotificationType.WELCOME, recipient, null, null, null, null, null);

    HistoryDto dto = mapper.toDto(entity);
    assertThat("Unexpected subject text.", dto.subjectText(), is(nullValue()));
  }

  @Test
  void shouldHaveNoSubjectTextWhenUnmappedInAppNotification() {
    ObjectId id = new ObjectId();
    RecipientInfo recipient = new RecipientInfo(null, MessageType.IN_APP, null);
    History entity = new History(id, null, NotificationType.COJ_CONFIRMATION, recipient, null, null, null, null, null);

    HistoryDto dto = mapper.toDto(entity);
    assertThat("Unexpected subject text.", dto.subjectText(), is(nullValue()));
  }
}
