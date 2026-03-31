/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import uk.nhs.tis.trainee.notifications.model.Placement;

class PlacementMapperTest {

  private PlacementMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new PlacementMapperImpl();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldUseSiteWhenSiteKnownAsMissing(String siteKnownAs) {
    Map<String, String> recordData = new HashMap<>();
    recordData.put("siteKnownAs", siteKnownAs);
    recordData.put("site", "the site");

    String site = mapper.calculateSite(recordData);

    assertThat("Unexpected site.", site, is("the site"));
  }

  @Test
  void shouldUseSiteKnownAsWhenItExists() {
    Map<String, String> recordData = new HashMap<>();
    recordData.put("siteKnownAs", "the siteKnownAs");
    recordData.put("site", "the site");

    String site = mapper.calculateSite(recordData);

    assertThat("Unexpected site.", site, is("the siteKnownAs"));
  }

  @Test
  void shouldMapPlacementFromRecordData() {
    Map<String, String> recordData = new HashMap<>();
    recordData.put("id", "the id");
    recordData.put("traineeId", "the traineeId");
    recordData.put("dateFrom", "2024-01-01");
    recordData.put("placementType", "the placementType");
    recordData.put("gradeAbbreviation", "the gradeAbbreviation");
    recordData.put("owner", "the owner");
    recordData.put("siteKnownAs", "the siteKnownAs");

    Placement placement = mapper.toEntity(recordData);

    assertThat("Unexpected TIS ID.", placement.getTisId(), is("the id"));
    assertThat("Unexpected person ID.", placement.getPersonId(), is("the traineeId"));
    assertThat("Unexpected start date.", placement.getStartDate(), is(LocalDate.of(2024, 1, 1)));
    assertThat("Unexpected placement type.", placement.getPlacementType(),
        is("the placementType"));
    assertThat("Unexpected grade abbreviation.", placement.getGradeAbbreviation(),
        is("the gradeAbbreviation"));
    assertThat("Unexpected owner.", placement.getOwner(), is("the owner"));
    assertThat("Unexpected site.", placement.getSite(), is("the siteKnownAs"));
  }
}
