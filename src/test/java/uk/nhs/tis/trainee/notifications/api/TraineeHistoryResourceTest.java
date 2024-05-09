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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.FAILED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.READ;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.COJ_CONFIRMATION;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.CREDENTIAL_REVOKED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.FORM_UPDATED;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.tis.trainee.notifications.TestJwtUtil;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;
import uk.nhs.tis.trainee.notifications.service.HistoryService;

@WebMvcTest(controllers = TraineeHistoryResource.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraineeHistoryResourceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_CONTACT_1 = "test1@tis.nhs.uk";
  private static final String TRAINEE_CONTACT_2 = "test2@tis.nhs.uk";
  private static final String TRAINEE_CONTACT_3 = "test3@tis.nhs.uk";
  private static final String TIS_REFERENCE_ID = UUID.randomUUID().toString();

  private static final String NOTIFICATION_ID = ObjectId.get().toString();

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private HistoryService service;

  @Test
  void shouldReturnBadRequestWhenGettingTraineeHistoryWithoutToken() throws Exception {
    mockMvc.perform(get("/api/history/trainee"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadRequestWhenGettingTraineeHistoryWithInvalidToken() throws Exception {
    mockMvc.perform(get("/api/history/trainee")
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("[]")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadRequestWhenGettingTraineeHistoryWithNoIdInToken() throws Exception {
    mockMvc.perform(get("/api/history/trainee")
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnEmptyArrayWhenNoTraineeHistoryFound() throws Exception {
    when(service.findAllForTrainee(TRAINEE_ID)).thenReturn(List.of());

    mockMvc.perform(get("/api/history/trainee")
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(TRAINEE_ID)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReturnTraineeHistoryArrayWhenHistoryFound() throws Exception {
    TisReferenceInfo tisReferenceProgramme
        = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_REFERENCE_ID);
    TisReferenceInfo tisReferenceForm
        = new TisReferenceInfo(TisReferenceType.FORMR_PARTA, TIS_REFERENCE_ID);
    HistoryDto history1 = new HistoryDto("1", tisReferenceProgramme, EMAIL, COJ_CONFIRMATION,
        null, TRAINEE_CONTACT_1, Instant.MIN, Instant.MAX, SENT, null);
    HistoryDto history2 = new HistoryDto("2", tisReferenceProgramme, EMAIL, CREDENTIAL_REVOKED,
        null, TRAINEE_CONTACT_2, Instant.EPOCH, Instant.EPOCH, FAILED, null);
    HistoryDto history3 = new HistoryDto("3", tisReferenceForm, EMAIL, FORM_UPDATED,
        null, TRAINEE_CONTACT_3, Instant.MAX, Instant.MIN, FAILED, "Additional detail");

    when(service.findAllSentForTrainee(TRAINEE_ID)).thenReturn(
        List.of(history1, history2, history3));

    mockMvc.perform(get("/api/history/trainee")
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(TRAINEE_ID)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].id").value("1"))
        .andExpect(jsonPath("$[0].tisReference.id").value(TIS_REFERENCE_ID))
        .andExpect(jsonPath("$[0].tisReference.type")
            .value(TisReferenceType.PROGRAMME_MEMBERSHIP.toString()))
        .andExpect(jsonPath("$[0].type").value(EMAIL.toString()))
        .andExpect(jsonPath("$[0].subject").value(COJ_CONFIRMATION.toString()))
        .andExpect(jsonPath("$[0].contact").value(TRAINEE_CONTACT_1))
        .andExpect(jsonPath("$[0].sentAt").value(Instant.MIN.toString()))
        .andExpect(jsonPath("$[0].readAt").value(Instant.MAX.toString()))
        .andExpect(jsonPath("$[0].status").value(SENT.toString()))
        .andExpect(jsonPath("$[0].statusDetail").doesNotExist())
        .andExpect(jsonPath("$[1].id").value("2"))
        .andExpect(jsonPath("$[1].tisReference.id").value(TIS_REFERENCE_ID))
        .andExpect(jsonPath("$[1].tisReference.type")
            .value(TisReferenceType.PROGRAMME_MEMBERSHIP.toString()))
        .andExpect(jsonPath("$[1].type").value(EMAIL.toString()))
        .andExpect(jsonPath("$[1].subject").value(CREDENTIAL_REVOKED.toString()))
        .andExpect(jsonPath("$[1].contact").value(TRAINEE_CONTACT_2))
        .andExpect(jsonPath("$[1].sentAt").value(Instant.EPOCH.toString()))
        .andExpect(jsonPath("$[1].readAt").value(Instant.EPOCH.toString()))
        .andExpect(jsonPath("$[1].status").value(FAILED.toString()))
        .andExpect(jsonPath("$[1].statusDetail").doesNotExist())
        .andExpect(jsonPath("$[2].id").value("3"))
        .andExpect(jsonPath("$[2].tisReference.id").value(TIS_REFERENCE_ID))
        .andExpect(jsonPath("$[2].tisReference.type")
            .value(TisReferenceType.FORMR_PARTA.toString()))
        .andExpect(jsonPath("$[2].type").value(EMAIL.toString()))
        .andExpect(jsonPath("$[2].subject").value(FORM_UPDATED.toString()))
        .andExpect(jsonPath("$[2].contact").value(TRAINEE_CONTACT_3))
        .andExpect(jsonPath("$[2].sentAt").value(Instant.MAX.toString()))
        .andExpect(jsonPath("$[2].readAt").value(Instant.MIN.toString()))
        .andExpect(jsonPath("$[2].status").value(FAILED.toString()))
        .andExpect(jsonPath("$[2].statusDetail").value("Additional detail"));
  }

  @Test
  void shouldReturnBadRequestWhenGettingNotificationMessageWithoutToken() throws Exception {
    mockMvc.perform(get("/api/history/trainee/message/{notificationId}", NOTIFICATION_ID))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadRequestWhenGettingNotificationMessageWithInvalidToken() throws Exception {
    mockMvc.perform(get("/api/history/trainee/message/{notificationId}", NOTIFICATION_ID)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("[]")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadRequestWhenGettingNotificationMessageWithNoIdInToken() throws Exception {
    mockMvc.perform(get("/api/history/trainee/message/{notificationId}", NOTIFICATION_ID)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnNotFoundWhenNoNotificationMessageFound() throws Exception {
    when(service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/history/trainee/message/{notificationId}", NOTIFICATION_ID)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(TRAINEE_ID)))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnRebuiltMessageWhenNotificationFound() throws Exception {
    String message = """
        <html>
          <p>Rebuilt message</p>
        </html>""";
    when(service.rebuildMessage(TRAINEE_ID, NOTIFICATION_ID)).thenReturn(Optional.of(message));

    mockMvc.perform(get("/api/history/trainee/message/{notificationId}", NOTIFICATION_ID)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(TRAINEE_ID)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_HTML))
        .andExpect(content().string(message));
  }

  @ParameterizedTest
  @ValueSource(strings = {"archive", "mark-read", "mark-unread"})
  void shouldReturnBadRequestWhenUpdatingStatusWithoutToken(String path) throws Exception {
    mockMvc.perform(
            put("/api/history/trainee/notification/{notificationId}/{path}", NOTIFICATION_ID, path))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"archive", "mark-read", "mark-unread"})
  void shouldReturnBadRequestWhenUpdatingStatusWithInvalidToken(String path) throws Exception {
    mockMvc.perform(
            put("/api/history/trainee/notification/{notificationId}/{path}", NOTIFICATION_ID, path)
                .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("[]")))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"archive", "mark-read", "mark-unread"})
  void shouldReturnBadRequestWhenUpdatingStatusWithNoIdInToken(String path) throws Exception {
    mockMvc.perform(
            put("/api/history/trainee/notification/{notificationId}/{path}", NOTIFICATION_ID, path)
                .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}")))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"archive", "mark-read", "mark-unread"})
  void shouldReturnNotFoundWhenUpdatingStatusAndNotificationNotFound(String path) throws Exception {
    when(service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, READ)).thenReturn(Optional.empty());

    mockMvc.perform(
            put("/api/history/trainee/notification/{notificationId}/{path}", NOTIFICATION_ID, path)
                .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(TRAINEE_ID)))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      archive     | ARCHIVED
      mark-read   | READ
      mark-unread | UNREAD
      """)
  void shouldReturnUpdatedStatusWhenNotificationFound(String path, NotificationStatus status)
      throws Exception {
    TisReferenceInfo tisReference
        = new TisReferenceInfo(TisReferenceType.FORMR_PARTA, TIS_REFERENCE_ID);
    HistoryDto history = new HistoryDto("1", tisReference, IN_APP, FORM_UPDATED, null,
        TRAINEE_CONTACT_1, Instant.MIN, Instant.MAX, status, "Additional detail");
    when(service.updateStatus(TRAINEE_ID, NOTIFICATION_ID, status)).thenReturn(
        Optional.of(history));

    mockMvc.perform(
            put("/api/history/trainee/notification/{notificationId}/{path}", NOTIFICATION_ID, path)
                .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(TRAINEE_ID)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.tisReference.id").value(TIS_REFERENCE_ID))
        .andExpect(jsonPath("$.tisReference.type").value(TisReferenceType.FORMR_PARTA.toString()))
        .andExpect(jsonPath("$.type").value(IN_APP.toString()))
        .andExpect(jsonPath("$.subject").value(FORM_UPDATED.toString()))
        .andExpect(jsonPath("$.contact").value(TRAINEE_CONTACT_1))
        .andExpect(jsonPath("$.sentAt").value(Instant.MIN.toString()))
        .andExpect(jsonPath("$.readAt").value(Instant.MAX.toString()))
        .andExpect(jsonPath("$.status").value(status.toString()))
        .andExpect(jsonPath("$.statusDetail").value("Additional detail"));
  }
}
