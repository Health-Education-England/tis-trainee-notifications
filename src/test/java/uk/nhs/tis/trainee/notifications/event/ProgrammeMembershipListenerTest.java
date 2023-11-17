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

package uk.nhs.tis.trainee.notifications.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.RecordDto;
import uk.nhs.tis.trainee.notifications.mapper.ProgrammeMembershipMapper;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService;

class ProgrammeMembershipListenerTest {

  private static final String TIS_ID = "123";
  private static final LocalDate START_DATE = LocalDate.now();

  private ProgrammeMembershipListener listener;
  private ProgrammeMembershipService programmeMembershipService;
  private ProgrammeMembershipMapper mapper;

  @BeforeEach
  void setUp() {
    programmeMembershipService = mock(ProgrammeMembershipService.class);
    mapper = mock(ProgrammeMembershipMapper.class);
    listener = new ProgrammeMembershipListener(programmeMembershipService, mapper);
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordEvents() {
    //ensure events with COJ_RECEIVED structure (and any other) are ignored, and not requeued
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TIS_ID, null);

    assertDoesNotThrow(() -> listener.handleProgrammeMembershipUpdate(event));
    assertDoesNotThrow(() -> listener.handleProgrammeMembershipDelete(event));
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordDataEvents() {
    //ensure events without record data are ignored, and not requeued
    RecordDto recordDto = new RecordDto();
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TIS_ID, recordDto);

    assertDoesNotThrow(() -> listener.handleProgrammeMembershipUpdate(event));
    assertDoesNotThrow(() -> listener.handleProgrammeMembershipDelete(event));
  }

  @Test
  void shouldAddNotifications() throws SchedulerException {
    ProgrammeMembershipEvent event = buildPmEvent();

    listener.handleProgrammeMembershipUpdate(event);

    verify(programmeMembershipService).addNotifications(any());
  }

  @Test
  void shouldDeleteNotifications() throws SchedulerException {
    Map<String, String> dataMap = new HashMap<>();
    RecordDto data = new RecordDto();
    data.setData(dataMap);
    ProgrammeMembershipEvent event = new ProgrammeMembershipEvent(TIS_ID, data);

    ProgrammeMembership expectedProgrammeMembership = new ProgrammeMembership();
    expectedProgrammeMembership.setTisId(TIS_ID);

    listener.handleProgrammeMembershipDelete(event);

    verify(programmeMembershipService).deleteNotifications(expectedProgrammeMembership);
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
    dataMap.put("curricula",
        "[{\"curriculumSubType\": \"some type\", \"curriculumSpecialty\": \"some specialty\"}]");
    RecordDto data = new RecordDto();
    data.setData(dataMap);
    return new ProgrammeMembershipEvent(TIS_ID, data);
  }
}
