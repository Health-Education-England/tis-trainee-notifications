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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;

/**
 * Mapper for {@link LtftUpdateEvent}.
 */
@Mapper(componentModel = ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LtftEventMapper {

  /**
   * Maps the {@link LtftUpdateEvent} to a new instance of {@link LtftUpdateEvent} with state detail
   * reason text.
   *
   * @param event The LTFT Update Event to map.
   * @return The mapped LTFT Update Event with state reason replaced by the human-readable version.
   */
  @Mapping(target = "stateDetail", qualifiedByName = "mapStateDetail")
  LtftUpdateEvent map(LtftUpdateEvent event);

  /**
   * Maps the {@link LtftUpdateEvent.LftfStatusInfoDetailDto} to a new instance with human-readable
   * reason.
   *
   * @param detail The LTFT Update Event state detail to map.
   * @return The mapped LTFT Update Event state detail with human-readable reason.
   */
  @Named("mapStateDetail")
  default LtftUpdateEvent.LftfStatusInfoDetailDto mapStateDetail(
      LtftUpdateEvent.LftfStatusInfoDetailDto detail) {
    if (detail == null) {
      return null;
    }
    return LtftUpdateEvent.LftfStatusInfoDetailDto.builder()
        .reason(LtftUpdateEvent.getReasonText(detail.reason()))
        .message(detail.message())
        .build();
  }
}
