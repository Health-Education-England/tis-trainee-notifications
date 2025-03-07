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

package uk.nhs.tis.trainee.notifications.model.content;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;
import uk.nhs.tis.trainee.notifications.model.CctChange;
import uk.nhs.tis.trainee.notifications.model.Person;

/**
 * The content of an LTFT application form.
 *
 * @param name                The trainee provided name for the application.
 * @param personalDetails     The trainee's personal details.
 * @param programmeMembership The programme membership linked with the LTFT application.
 * @param declarations        The LTFT declarations.
 * @param discussions         Discussions which took place as part of the LTFT process.
 * @param change              The calculated LTFT change.
 * @param reasons             The reasons for applying for LTFT.
 * @param assignedAdmin       The administration assigned to the LTFT application.
 */
@Builder
public record LtftContent(
    String name,
    PersonalDetails personalDetails,
    ProgrammeMembership programmeMembership,
    Declarations declarations,
    Discussions discussions,
    CctChange change,
    Reasons reasons,
    Person assignedAdmin) {

  /**
   * The trainee's personal details.
   *
   * @param title                   The trainee's title.
   * @param forenames               The trainee's forenames or given name.
   * @param surname                 The trainee's surname or family name.
   * @param email                   The trainee's email address.
   * @param telephoneNumber         The trainee's contact telephone number.
   * @param mobileNumber            The trainee's contact mobile number.
   * @param gmcNumber               The trainee's GMC registration number.
   * @param gdcNumber               The trainee's GDC registration number.
   * @param skilledWorkerVisaHolder Whether the trainee holds a skilled worker visa.
   */
  @Builder
  public record PersonalDetails(
      String title,
      String forenames,
      String surname,
      String email,
      String telephoneNumber,
      String mobileNumber,
      String gmcNumber,
      String gdcNumber,
      Boolean skilledWorkerVisaHolder) {

  }

  /**
   * Programme membership data for a calculation.
   *
   * @param id                 The ID of the programme membership.
   * @param name               The name of the programme.
   * @param designatedBodyCode The designated body code for the programme.
   * @param startDate          The start date of the programme.
   * @param endDate            The end date of the programme.
   * @param wte                The whole time equivalent of the programme membership.
   */
  @Builder
  public record ProgrammeMembership(
      @Indexed
      @Field("id")
      UUID id,
      String name,
      @Indexed
      String designatedBodyCode,
      LocalDate startDate,
      LocalDate endDate,
      Double wte) {

  }

  /**
   * A set of declarations that the trainee must provide.
   *
   * @param discussedWithTpd     That the trainee has discussed with their TPD.
   * @param informationIsCorrect That the trainee has given correct information.
   * @param notGuaranteed        That LTFT approval is not guaranteed
   */
  @Builder
  public record Declarations(
      Boolean discussedWithTpd,
      Boolean informationIsCorrect,
      Boolean notGuaranteed) {

  }

  /**
   * Discussions that the trainee has had about their LTFT needs.
   *
   * @param tpdName  The name of their TPD.
   * @param tpdEmail The contact email for their TPD.
   * @param other    Other people they have discussed with e.g. Education Supervisor.
   */
  @Builder
  public record Discussions(
      String tpdName,
      String tpdEmail,
      List<Person> other) {

  }

  /**
   * Reasons for applying for LTFT.
   *
   * @param selected    A list of selected reasons.
   * @param otherDetail Additional details if required.
   */
  @Builder
  public record Reasons(
      List<String> selected,
      String otherDetail) {

  }
}
