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
import org.mapstruct.factory.Mappers;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.RecordDto;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.LTFT;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;

class LtftMapperTest {

  private static final String TIS_ID = "123";
  private static final String TRAINEE_ID = "456";
  private static final String FORM_ID = "789";
  private static final String LTFT_NAME = "LTFT Example";
  private static final String LTFT_STATUS = "SUBMITTED";
  private static final Instant DATE_CHANGED = Instant.now();

  private LTFTMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(LTFTMapper.class);
  }

  @Test
  void shouldMapRecordDataToLTFT() {
    Map<String, String> recordData = buildRecordData();
    LTFT ltft = mapper.toEntity(recordData);

    assertThat("Unexpected trainee TIS Id.", ltft.getTraineeTisId(), is(TIS_ID));
    assertThat("Unexpected person Id.", ltft.getPersonId(), is(TRAINEE_ID));
    assertThat("Unexpected form Id.", ltft.getFormId(), is(FORM_ID));
    assertThat("Unexpected LTFT name.", ltft.getLtftName(), is(LTFT_NAME));
    assertThat("Unexpected LTFT status.", ltft.getLtftStatus(), is(LTFT_STATUS));
    assertThat("Unexpected date changed.", ltft.getDateChanged(), is(DATE_CHANGED.toString()));
  }

  /**
   * Helper function to construct record data.
   *
   * @return The record data map.
   */
  private Map<String, String> buildRecordData() {
    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("id", TIS_ID);
    dataMap.put("traineeId", TRAINEE_ID);
    dataMap.put("formId", FORM_ID);
    dataMap.put("ltftName", LTFT_NAME);
    dataMap.put("ltftStatus", LTFT_STATUS);
    dataMap.put("dateChanged", DATE_CHANGED.toString());
    return dataMap;
  }
}
