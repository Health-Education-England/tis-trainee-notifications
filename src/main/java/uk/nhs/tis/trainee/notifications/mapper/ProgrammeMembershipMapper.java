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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import org.mapstruct.ReportingPolicy;
import uk.nhs.tis.trainee.notifications.dto.CojSignedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;

/**
 * A mapper to map between TIS Data and Programme Membership Data.
 */
@Mapper(componentModel = ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProgrammeMembershipMapper {

  /**
   * Map a record data map to a ProgrammeMembership.
   *
   * @param recordData The map to convert.
   * @return The mapped ProgrammeMembership.
   */
  @Mapping(target = "curricula", source = "recordData.curricula")
  @Mapping(target = "tisId", source = "recordData.tisId")
  @Mapping(target = "startDate", source = "recordData.startDate")
  ProgrammeMembership toEntity(Map<String, String> recordData);

  /**
   * Map a serialized list of curricula.
   *
   * @param curriculumString The serialized curricula.
   * @return The list of Curriculum records.
   * @throws JsonProcessingException if curricula are malformed.
   */
  default List<Curriculum> toCurricula(String curriculumString) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return objectMapper.readValue(curriculumString,
        new TypeReference<>() {
        });
  }

  /**
   * Map a serialized Conditions of Joining.
   *
   * @param cojString The serialized Conditions of Joining.
   * @return The Conditions of Joining record.
   * @throws JsonProcessingException if the CoJ is malformed.
   */
  default ConditionsOfJoining toConditionsOfJoining(String cojString)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return objectMapper.readValue(cojString,
        new TypeReference<>() {
        });
  }

  /**
   * Map a serialized Responsible Officer.
   *
   * @param roString The serialized Responsible Officer.
   * @return The Responsible Officer record.
   * @throws JsonProcessingException if the RO is malformed.
   */
  default ResponsibleOfficer toResponsibleOfficer(String roString)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return objectMapper.readValue(roString,
        new TypeReference<>() {
        });
  }
}


