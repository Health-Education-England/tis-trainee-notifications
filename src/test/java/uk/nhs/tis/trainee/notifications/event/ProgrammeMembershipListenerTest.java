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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import uk.nhs.tis.trainee.notifications.dto.Curriculum;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService;

class ProgrammeMembershipListenerTest {

  private static final String TIS_ID = "123";
  private static final LocalDate START_DATE = LocalDate.MAX;
  private static final Curriculum MEDICAL_CURRICULUM1
      = new Curriculum("MEDICAL_CURRICULUM", "some-specialty");

  private ProgrammeMembershipListener listener;
  private ProgrammeMembershipService programmeMembershipService;

  @BeforeEach
  void setUp() {
    programmeMembershipService = mock(ProgrammeMembershipService.class);
    listener = new ProgrammeMembershipListener(programmeMembershipService);
  }

  @Test
  void shouldScheduleNotifications() throws SchedulerException {
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(TIS_ID, START_DATE, List.of(MEDICAL_CURRICULUM1));

    listener.handleProgrammeMembershipUpdate(event);

    verify(programmeMembershipService).scheduleNotifications(any());
    verifyNoMoreInteractions(programmeMembershipService);
  }
}
