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

  /**
   * A trainee's personal details.
   *
   * @param gmcNumber The trainee's GMC registration number.
   */
  public record PersonalDetails(String gmcNumber) {

  }

  /**
   * Discussions that the trainee has had about their LTFT needs.
   *
   * @param tpdName  The name of their TPD.
   * @param tpdEmail The contact email for their TPD.
   */
  public record DiscussionsDto(
      String tpdName,
      String tpdEmail) {

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
   * Unpack the current status to set the state and timestamp.
   *
   * @param status The value of the status property.
   */
  @JsonProperty("status")
  private void unpackCurrentStatus(Map<String, Object> status) {
    Map<String, String> current = (Map<String, String>) status.get("current");
    state = current.get("state");
    String timestampString = current.get("timestamp");
    timestamp = timestampString == null || timestampString.isBlank() ? null
        : Instant.parse(timestampString);
  }
}
