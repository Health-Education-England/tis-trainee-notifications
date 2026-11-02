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
 */

package uk.nhs.tis.trainee.notifications.dto;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftAssignmentDto;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftStatusAssignedDto;

class LtftUpdateEventTest {

  private static final String ADMIN = """
      {"name": "Admin User", "email": "admin@example.com", "role": "ADMIN"}""";
  private static final String REVIEWER = """
      {"name": "Reviewer User", "email": "reviewer@example.com", "role": "REVIEWER"}""";

  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void shouldUnpackCurrentAssignees() throws JsonProcessingException {
    String json = """
        {
          "status": {
            "current": {
              "state": "SUBMITTED",
              "assignedAdmin": %s,
              "assignedReviewer": %s
            }
          }
        }
        """.formatted(ADMIN, REVIEWER);

    LtftUpdateEvent event = mapper.readValue(json, LtftUpdateEvent.class);

    LtftStatusAssignedDto admin = event.getAssignedAdmin();
    assertThat("Unexpected admin name.", admin.name(), is("Admin User"));
    assertThat("Unexpected admin email.", admin.email(), is("admin@example.com"));
    assertThat("Unexpected admin role.", admin.role(), is("ADMIN"));

    LtftStatusAssignedDto reviewer = event.getAssignedReviewer();
    assertThat("Unexpected reviewer name.", reviewer.name(), is("Reviewer User"));
    assertThat("Unexpected reviewer email.", reviewer.email(), is("reviewer@example.com"));
    assertThat("Unexpected reviewer role.", reviewer.role(), is("REVIEWER"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      ", \"history\": null",
      ", \"history\": []",
      ", \"history\": [{\"state\": \"SUBMITTED\"}]",
      ", \"history\": [null, {\"state\": \"SUBMITTED\"}]"
  })
  void shouldNotSetPreviousAssignmentWhenNoPreviousHistory(String history)
      throws JsonProcessingException {
    String json = """
        {
          "status": {
            "current": {
              "state": "SUBMITTED",
              "assignedAdmin": %s
            }
            %s
          }
        }
        """.formatted(ADMIN, history);

    LtftUpdateEvent event = mapper.readValue(json, LtftUpdateEvent.class);

    assertThat("Unexpected previous assignment.", event.getPreviousAssignment(), nullValue());
  }

  @Test
  void shouldSetPreviousAssignmentFromPenultimateHistoryEntry() throws JsonProcessingException {
    String oldAdmin = """
        {"name": "Old Admin", "email": "old-admin@example.com", "role": "ADMIN"}""";
    String oldReviewer = """
        {"name": "Old Reviewer", "email": "old-reviewer@example.com", "role": "REVIEWER"}""";

    String json = """
        {
          "status": {
            "current": {
              "state": "SUBMITTED",
              "assignedAdmin": %1$s,
              "assignedReviewer": %2$s
            },
            "history": [
              {
                "state": "DRAFT"
              },
              {
                "state": "SUBMITTED",
                "assignedAdmin": %3$s,
                "assignedReviewer": %4$s
              },
              {
                "state": "SUBMITTED",
                "assignedAdmin": %1$s,
                "assignedReviewer": %2$s
              }
            ]
          }
        }
        """.formatted(ADMIN, REVIEWER, oldAdmin, oldReviewer);

    LtftUpdateEvent event = mapper.readValue(json, LtftUpdateEvent.class);

    LtftAssignmentDto previous = event.getPreviousAssignment();
    assertThat("Unexpected previous assignment.", previous, notNullValue());

    LtftStatusAssignedDto previousAdmin = previous.assignedAdmin();
    assertThat("Unexpected previous admin name.", previousAdmin.name(), is("Old Admin"));
    assertThat("Unexpected previous admin email.", previousAdmin.email(),
        is("old-admin@example.com"));
    assertThat("Unexpected previous admin role.", previousAdmin.role(), is("ADMIN"));

    LtftStatusAssignedDto previousReviewer = previous.assignedReviewer();
    assertThat("Unexpected previous reviewer name.", previousReviewer.name(),
        is("Old Reviewer"));
    assertThat("Unexpected previous reviewer email.", previousReviewer.email(),
        is("old-reviewer@example.com"));
    assertThat("Unexpected previous reviewer role.", previousReviewer.role(), is("REVIEWER"));
  }

  @Test
  void shouldSetEmptyPreviousAssignmentWhenPreviousEntryHasNoAssignees()
      throws JsonProcessingException {
    String json = """
        {
          "status": {
            "current": {
              "state": "SUBMITTED",
              "assignedAdmin": %1$s
            },
            "history": [
              {
                "state": "SUBMITTED"
              },
              {
                "state": "SUBMITTED",
                "assignedAdmin": %1$s
              }
            ]
          }
        }
        """.formatted(ADMIN);

    LtftUpdateEvent event = mapper.readValue(json, LtftUpdateEvent.class);

    LtftAssignmentDto previous = event.getPreviousAssignment();
    assertThat("Unexpected previous assignment.", previous, notNullValue());
    assertThat("Unexpected previous admin.", previous.assignedAdmin(), nullValue());
    assertThat("Unexpected previous reviewer.", previous.assignedReviewer(), nullValue());
  }
}

