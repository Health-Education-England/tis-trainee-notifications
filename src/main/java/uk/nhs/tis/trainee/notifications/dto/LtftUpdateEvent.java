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

package uk.nhs.tis.trainee.notifications.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * A LTFT update event.
 */
@Data
@Builder
public class LtftUpdateEvent {

  @JsonAlias("traineeTisId")
  private String traineeId;
  private String formRef;
  private String formName;
  private PersonalDetails personalDetails;
  private ProgrammeMembershipDto programmeMembership;
  private DiscussionsDto discussions;
  private ChangeDto change;
  private String state;
  private Instant timestamp;
  private LftfStatusInfoDetailDto stateDetail;
  private LtftStatusModifiedByDto modifiedBy;

  /**
   * A trainee's personal details.
   *
   * @param gmcNumber The trainee's GMC registration number.
   */
  public record PersonalDetails(String gmcNumber) {

  }

  /**
   * An LTFT change.
   *
   * @param startDate The start date of the LTFT change.
   * @param wte       The whole time equivalent being requested.
   * @param cctDate   The CCT/end of programme date..
   */
  public record ChangeDto(LocalDate startDate, Double wte, LocalDate cctDate) {

  }

  /**
   * Discussions that the trainee has had about their LTFT needs.
   *
   * @param tpdName  The name of their TPD.
   * @param tpdEmail The contact email for their TPD.
   */
  @Builder
  public record DiscussionsDto(String tpdName, String tpdEmail) {

  }

  /**
   * A DTO for state change details.
   *
   * @param reason  The reason for the state change.
   * @param message A message associated with the state change.
   */
  @Builder
  public record LftfStatusInfoDetailDto(String reason, String message) {

  }

  /**
   * A DTO for the person who modified the LTFT status.
   *
   * @param name The name of the person who modified the status.
   * @param role The role of the person who modified the status.
   */
  @Builder
  public record LtftStatusModifiedByDto(String name, String role) {

  }

  /**
   * Unpack the current status to set the state, timestamp and detail.
   *
   * @param status The value of the status property.
   */
  @JsonProperty("status")
  private void unpackCurrentStatus(Map<String, Object> status) {
    Map<String, Object> current = (Map<String, Object>) status.get("current");
    state = (String) current.get("state");
    String timestampString = (String) current.get("timestamp");
    timestamp = timestampString == null || timestampString.isBlank() ? null
        : Instant.parse(timestampString);
    Map<String, String> detail = (Map<String, String>) current.get("detail");
    stateDetail = detail == null ? null
        : LftfStatusInfoDetailDto.builder()
            .reason(detail.get("reason"))
            .message(detail.get("message"))
            .build();
    Map<String, String> modifiedByMap = (Map<String, String>) current.get("modifiedBy");
    modifiedBy = modifiedByMap == null ? null
        : LtftStatusModifiedByDto.builder()
            .name(modifiedByMap.get("name"))
            .role(modifiedByMap.get("role"))
            .build();
  }

  /**
   * Get the reason text for the LTFT update.
   *
   * @param reason The reason for the LTFT update.
   * @return The reason text, or the reason if no mapping exists.
   */
  public static String getReasonText(String reason) {
    if (reason == null) {
      return null;
    }
    return switch (reason) {
      case "other" -> "other reason";
      case "changePercentage" -> "Change WTE percentage";
      case "changeStartDate" -> "Change start date";
      case "changeOfCircs" -> "Change of circumstances";
      default -> reason;
    };
  }
}
