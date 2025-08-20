/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.tis.trainee.notifications.model.History;

class FailHistoricMissedSchedulesTest {

  private FailHistoricMissedSchedules migrator;

  private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);

    Environment env = mock(Environment.class);
    when(env.getProperty("application.timezone")).thenReturn("UTC");
    migrator = new FailHistoricMissedSchedules(mongoTemplate, env);
  }

  @Test
  void shouldThrowExceptionWhenUnacknowledged() {
    when(mongoTemplate.updateMulti(any(), any(), eq(History.class))).thenReturn(
        UpdateResult.unacknowledged()
    );

    RuntimeException exception = assertThrows(RuntimeException.class, () -> migrator.migrate());
    assertThat("Unexpected exception.", exception.getMessage(),
        is("Failed to update notifications."));
  }

  @Test
  void shouldThrowExceptionWhenPartialFailure() {
    when(mongoTemplate.updateMulti(any(), any(), eq(History.class))).thenReturn(
        UpdateResult.acknowledged(10L, 8L, null)
    );

    RuntimeException exception = assertThrows(RuntimeException.class, () -> migrator.migrate());
    assertThat("Unexpected exception.", exception.getMessage(),
        is("Failed to update 2 notifications."));
  }

  @Test
  void rollback() {
    clearInvocations(mongoTemplate);
    migrator.rollback();
    verifyNoInteractions(mongoTemplate);
  }
}
