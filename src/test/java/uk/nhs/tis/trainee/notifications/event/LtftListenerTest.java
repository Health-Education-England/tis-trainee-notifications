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
import uk.nhs.tis.trainee.notifications.dto.LTFTEvent;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.RecordDto;
import uk.nhs.tis.trainee.notifications.mapper.LTFTMapper;
import uk.nhs.tis.trainee.notifications.mapper.ProgrammeMembershipMapper;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.service.LTFTService;
import uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService;

class LtftListenerTest {

  private static final String TIS_ID = "123";

  private LTFTListener listener;
  private LTFTService ltftService;
  private LTFTMapper mapper;

  @BeforeEach
  void setUp() {
    ltftService = mock(LTFTService.class);
    mapper = mock(LTFTMapper.class);
    listener = new LTFTListener(ltftService, mapper);
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordEvents() {
    LTFTEvent event = new LTFTEvent(TIS_ID, null);

    assertDoesNotThrow(() -> listener.handleLTFTUpdate(event));
    assertDoesNotThrow(() -> listener.handleLTFTUpdate(event));
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordDataEvents() {
    RecordDto recordDto = new RecordDto();
    LTFTEvent event = new LTFTEvent(TIS_ID, recordDto);

    assertDoesNotThrow(() -> listener.handleLTFTUpdate(event));
    assertDoesNotThrow(() -> listener.handleLTFTUpdate(event));
  }
}
