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

package uk.nhs.tis.trainee.notifications.mapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.CojSignedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.RecordDto;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;

class ProgrammeMembershipMapperTest {

  private static final String TIS_ID = "123";
  private static final LocalDate START_DATE = LocalDate.now();
  private static final String CURRICULUM_SUB_TYPE = "sub-type";
  private static final String CURRICULUM_SPECIALTY = "specialty";
  private static final Instant COJ_SYNCED_AT = Instant.now();
  private static final String RO_FIRST_NAME = "RO First Name";
  private static final String RO_LAST_NAME = "RO Last Name";

  private ProgrammeMembershipMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ProgrammeMembershipMapperImpl();
  }

  @Test
  void shouldMapProgrammeMembershipEventToProgrammeMembership() {
    ProgrammeMembershipEvent event = buildPmEvent();
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setStartDate(START_DATE);
    Curriculum curriculum = new Curriculum(CURRICULUM_SUB_TYPE, CURRICULUM_SPECIALTY, false);
    programmeMembership.setCurricula(List.of(curriculum));
    ResponsibleOfficer ro = new ResponsibleOfficer("email@email.com", RO_FIRST_NAME,
        RO_LAST_NAME, "1234567", "0141111111111");
    programmeMembership.setResponsibleOfficer(ro);

    ProgrammeMembership returnedPm = mapper.toEntity(event.recrd().getData());

    assertThat("Unexpected Tis Id.", returnedPm.getTisId(), is(TIS_ID));
    assertThat("Unexpected start date.", returnedPm.getStartDate(), is(START_DATE));
    assertThat("Unexpected curricula.", returnedPm.getCurricula(), is(List.of(curriculum)));
    assertThat("Unexpected Conditions of joining.", returnedPm.getConditionsOfJoining(),
        is(new ConditionsOfJoining(COJ_SYNCED_AT)));
    assertThat("Unexpected Responsible Officer.", returnedPm.getResponsibleOfficer(), is(ro));
  }

  /**
   * Helper function to construct a programme membership event.
   *
   * @return The ProgrammeMembershipEvent.
   */
  ProgrammeMembershipEvent buildPmEvent() {
    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("tisId", TIS_ID);
    dataMap.put("startDate", START_DATE.toString());
    dataMap.put("another-pm-property", "some value");
    dataMap.put("curricula", """
        [{
          "curriculumSubType": "%s",
          "curriculumSpecialty": "%s",
          "another-curriculum-property": "some value"
        }]
        """.formatted(CURRICULUM_SUB_TYPE, CURRICULUM_SPECIALTY));
    dataMap.put("conditionsOfJoining", """
        {
          "signedAt": "2023-06-05T20:44:29.943Z",
          "version": "GG9",
          "syncedAt": "%s"
        }
        """.formatted(COJ_SYNCED_AT));
    dataMap.put("responsibleOfficer", """
        {
          "emailAddress": "email@email.com",
          "firstName": "%s",
          "lastName": "%s",
          "gmcNumber": "1234567",
          "phoneNumber": "0141111111111"
        }
        """.formatted(RO_FIRST_NAME, RO_LAST_NAME));
    RecordDto data = new RecordDto();
    data.setData(dataMap);
    return new ProgrammeMembershipEvent(TIS_ID, data);
  }

}
