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

import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import org.mapstruct.ReportingPolicy;
import uk.nhs.tis.trainee.notifications.model.Placement;

/**
 * A mapper to map between TIS Data and Placement Data.
 */
@Mapper(componentModel = ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlacementMapper {

  /**
   * Map a record data map to a Placement.
   *
   * @param recordData The map to convert.
   * @return The mapped Placement.
   */
  @Mapping(target = "tisId", source = "recordData.id")
  @Mapping(target = "personId", source = "recordData.traineeId")
  @Mapping(target = "startDate", source = "recordData.dateFrom")
  @Mapping(target = "placementType", source = "recordData.placementType")
  @Mapping(target = "owner", source = "recordData.owner")
  @Mapping(target = "site", expression = "java(calculateSite(recordData))")
  Placement toEntity(Map<String, String> recordData);

  /**
   * Determine the site name to use, with 'site known as' preferred.
   *
   * @param recordData The record data map.
   * @return The site name.
   */
  default String calculateSite(Map<String, String> recordData) {
    return ((recordData.get("siteKnownAs") != null && !recordData.get("siteKnownAs").isBlank())
        ? recordData.get("siteKnownAs")
        : recordData.get("site"));
  }
}


