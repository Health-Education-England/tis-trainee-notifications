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

package uk.nhs.tis.trainee.notifications.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.CREDENTIAL_REVOKED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

@WebMvcTest(controllers = HistoryResource.class)
@AutoConfigureMockMvc
class HistoryResourceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT_1 = "test1@tis.nhs.uk";
  private static final String TRAINEE_CONTACT_2 = "test2@tis.nhs.uk";
  private static final String TRAINEE_CONTACT_3 = "test3@tis.nhs.uk";

  private static final String NOTIFICATION_ID = ObjectId.get().toString();

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private HistoryService service;

  @Test
  void shouldReturnEmptyArrayWhenNoTraineeHistoryFound() throws Exception {
    when(service.findAllForTrainee(TRAINEE_ID)).thenReturn(List.of());

    mockMvc.perform(get("/api/history/trainee/{traineeId}", TRAINEE_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReturnTraineeHistoryArrayWhenHistoryFound() throws Exception {
    HistoryDto history1 = new HistoryDto("1", EMAIL, COJ_CONFIRMATION, TRAINEE_CONTACT_1,
        Instant.MIN);
    HistoryDto history2 = new HistoryDto("2", EMAIL, CREDENTIAL_REVOKED, TRAINEE_CONTACT_2,
        Instant.EPOCH);
    HistoryDto history3 = new HistoryDto("3", EMAIL, FORM_UPDATED, TRAINEE_CONTACT_3, Instant.MAX);

    when(service.findAllForTrainee(TRAINEE_ID)).thenReturn(List.of(history1, history2, history3));

    mockMvc.perform(get("/api/history/trainee/{traineeId}", TRAINEE_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].id").value("1"))
        .andExpect(jsonPath("$[0].type").value(EMAIL.toString()))
        .andExpect(jsonPath("$[0].subject").value(COJ_CONFIRMATION.toString()))
        .andExpect(jsonPath("$[0].contact").value(TRAINEE_CONTACT_1))
        .andExpect(jsonPath("$[0].sentAt").value(Instant.MIN.toString()))
        .andExpect(jsonPath("$[1].id").value("2"))
        .andExpect(jsonPath("$[1].type").value(EMAIL.toString()))
        .andExpect(jsonPath("$[1].subject").value(CREDENTIAL_REVOKED.toString()))
        .andExpect(jsonPath("$[1].contact").value(TRAINEE_CONTACT_2))
        .andExpect(jsonPath("$[1].sentAt").value(Instant.EPOCH.toString()))
        .andExpect(jsonPath("$[2].id").value("3"))
        .andExpect(jsonPath("$[2].type").value(EMAIL.toString()))
        .andExpect(jsonPath("$[2].subject").value(FORM_UPDATED.toString()))
        .andExpect(jsonPath("$[2].contact").value(TRAINEE_CONTACT_3))
        .andExpect(jsonPath("$[2].sentAt").value(Instant.MAX.toString()));
  }

  @Test
  void shouldReturnNotFoundWhenNoNotificationMessageFound() throws Exception {
    when(service.rebuildMessage(NOTIFICATION_ID)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/history/message/{notificationId}", NOTIFICATION_ID))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnRebuiltMessageWhenNotificationFound() throws Exception {
    String message = """
        <html>
          <p>Rebuilt message</p>
        </html>""";
    when(service.rebuildMessage(NOTIFICATION_ID)).thenReturn(Optional.of(message));

    mockMvc.perform(get("/api/history/message/{notificationId}", NOTIFICATION_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_HTML))
        .andExpect(content().string(message));
  }
}
