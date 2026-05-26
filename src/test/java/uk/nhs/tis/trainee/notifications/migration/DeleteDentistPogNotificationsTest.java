/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *  
 */

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_POG_MONTH_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_POG_MONTH_6;

import com.mongodb.MongoException;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;

class DeleteDentistPogNotificationsTest {

  private DeleteDentistPogNotifications migration;

  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    migration = new DeleteDentistPogNotifications(template);
  }

  @Test
  void shouldDeleteNonScheduledDentalPogHistory() {
    when(template.remove(any(), eq(History.class))).thenReturn(DeleteResult.acknowledged(5L));

    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(template).remove(queryCaptor.capture(), eq(History.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();

    // Verify status criteria: not SCHEDULED
    Document statusCriteria = queryObject.get("status", Document.class);
    assertThat("Unexpected status ne value.", statusCriteria.get("$ne"), is(SCHEDULED));

    // Verify type criteria: in PROGRAMME_POG_MONTH_12 or PROGRAMME_POG_MONTH_6
    Document typeCriteria = queryObject.get("type", Document.class);
    List<?> typeValues = typeCriteria.get("$in", List.class);
    assertThat("Unexpected type list size.", typeValues.size(), is(2));
    assertThat("Missing PROGRAMME_POG_MONTH_12 type.",
        typeValues.contains(PROGRAMME_POG_MONTH_12.name()), is(true));
    assertThat("Missing PROGRAMME_POG_MONTH_6 type.",
        typeValues.contains(PROGRAMME_POG_MONTH_6.name()), is(true));

    // Verify programme name criteria is present
    Document programmeCriteria
        = queryObject.get("template.variables.ProgrammeName", Document.class);
    List<?> programmeNames = programmeCriteria.get("$in", List.class);
    assertThat("Unexpected programme name list is empty.", programmeNames.isEmpty(), is(false));
    assertThat("Missing Dental Public Health programme name.",
        programmeNames.contains("Dental Public Health"), is(true));
    assertThat("Missing Orthodontics programme name.",
        programmeNames.contains("Orthodontics"), is(true));
  }

  @Test
  void shouldCatchMongoExceptionNotThrowIt() {
    when(template.remove(any(), eq(History.class))).thenThrow(MongoException.class);
    Assertions.assertDoesNotThrow(() -> migration.migrate());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}


