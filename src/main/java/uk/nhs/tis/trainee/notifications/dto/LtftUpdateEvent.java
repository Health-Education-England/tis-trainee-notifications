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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import uk.nhs.tis.trainee.notifications.model.CctChangeType;
import uk.nhs.tis.trainee.notifications.model.LifecycleState;
import uk.nhs.tis.trainee.notifications.model.LtftPersonalDetailsDto;

/**
 * A LTFT update event.
 *
 * @param id                  The ID of the LTFT form.
 * @param traineeTisId        The trainee's TID ID.
 * @param formRef             The human-readable reference for the form.
 * @param revision            The revision number of the form.
 * @param name                The trainee provided name for the application.
 * @param personalDetails     The trainee's personal details.
 * @param programmeMembership The programme membership linked with the LTFT application.
 * @param declarations        The LTFT declarations.
 * @param discussions         Discussions which took place as part of the LTFT process.
 * @param change              The calculated LTFT change.
 * @param reasons             The reasons for applying for LTFT.
 * @param status              The status of the LTFT application, both current and audit history.
 * @param created             When the LTFT application was first created.
 * @param lastModified        When the LTFT application was last modified.
 */
@Builder
public record LtftUpdateEvent(
    UUID id,
    String traineeTisId,
    String formRef,
    Integer revision,
    String name,
    LtftPersonalDetailsDto personalDetails,
    ProgrammeMembershipDto programmeMembership,
    DeclarationsDto declarations,
    DiscussionsDto discussions,
    CctChangeDto change,
    ReasonsDto reasons,
    StatusDto status,
    Instant created,
    Instant lastModified
) {

  /**
   * The calculated LTFT change.
   *
   * @param id            The ID of the CCT change used to start the application.
   * @param calculationId The ID of the CCT calculation used to start the application.
   * @param type          The type of change.
   * @param wte           The whole time equivalent after the change.
   * @param startDate     The start date of the change.
   * @param endDate       The end date of the change.
   * @param cctDate       The expected CCT date after this change is applied.
   */
  @Builder
  public record CctChangeDto(
      UUID id,
      UUID calculationId,
      CctChangeType type,
      Double wte,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate cctDate) {

  }

  /**
   * A set of declarations that the trainee must provide.
   *
   * @param discussedWithTpd     That the trainee has discussed with their TPD.
   * @param informationIsCorrect That the trainee has given correct information.
   * @param notGuaranteed        That LTFT approval is not guaranteed
   */
  @Builder
  public record DeclarationsDto(
      Boolean discussedWithTpd,
      Boolean informationIsCorrect,
      Boolean notGuaranteed) {

  }

  /**
   * Programme membership data for a calculation.
   *
   * @param id                 The ID of the programme membership.
   * @param name               The name of the programme.
   * @param designatedBodyCode The designated body code for the programme.
   * @param managingDeanery    The managing deanery for the programme.
   * @param startDate          The start date of the programme.
   * @param endDate            The end date of the programme.
   * @param wte                The whole time equivalent of the programme membership.
   */
  @Builder
  public record ProgrammeMembershipDto(
      UUID id,
      String name,
      String designatedBodyCode,
      String managingDeanery,
      LocalDate startDate,
      LocalDate endDate,
      Double wte) {

  }

  /**
   * Reasons for applying for LTFT.
   *
   * @param selected              A list of selected reasons.
   * @param otherDetail           Additional details if "Other" reason was selected.
   * @param supportingInformation Supporting information for the application.
   */
  @Builder
  public record ReasonsDto(
      List<String> selected,
      String otherDetail,
      String supportingInformation) {

  }

  /**
   * The form status.
   *
   * @param current   The information for the current form status.
   * @param submitted When the form was last submitted.
   * @param history   A list of form status history.
   */
  @Builder
  public record StatusDto(

      StatusInfoDto current,
      Instant submitted,
      List<StatusInfoDto> history) {

    /**
     * Form status information.
     *
     * @param state         The lifecycle state of the form.
     * @param detail        Status reason detail.
     * @param assignedAdmin The admin who is assigned to process the form.
     * @param modifiedBy    The Person who made this status change.
     * @param timestamp     The timestamp of the status change.
     * @param revision      The revision number associated with this status change.
     */
    @Builder
    public record StatusInfoDto(

        LifecycleState state,
        LftfStatusInfoDetailDto detail,
        PersonDto assignedAdmin,
        PersonDto modifiedBy,
        Instant timestamp,
        Integer revision
    ) {

    }

    /**
     * A DTO for state change details.
     *
     * @param reason  The reason for the state change.
     * @param message A message associated with the state change.
     */
    @Builder
    public record LftfStatusInfoDetailDto(

        String reason,
        String message) {

    }
  }

  /**
   * Discussions that the trainee has had about their LTFT needs.
   *
   * @param tpdName  The name of their TPD.
   * @param tpdEmail The contact email for their TPD.
   * @param other    Other people they have discussed with e.g. Education Supervisor.
   */
  @Builder
  public record DiscussionsDto(
      String tpdName,
      String tpdEmail,
      List<PersonDto> other) {

  }

  /**
   * Details of a person and their role.
   *
   * @param name  The person's name.
   * @param email The person's contact email.
   * @param role  The person's role, context dependent.
   */
  @Builder
  public record PersonDto(
      String name,
      String email,
      String role) {

  }
}
