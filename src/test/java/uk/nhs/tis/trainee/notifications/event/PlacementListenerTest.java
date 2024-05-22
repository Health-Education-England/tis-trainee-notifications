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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapping;
import org.mockito.ArgumentCaptor;
import org.quartz.SchedulerException;
import uk.nhs.tis.trainee.notifications.dto.PlacementEvent;
import uk.nhs.tis.trainee.notifications.dto.RecordDto;
import uk.nhs.tis.trainee.notifications.mapper.PlacementMapper;
import uk.nhs.tis.trainee.notifications.model.Placement;
import uk.nhs.tis.trainee.notifications.service.PlacementService;

class PlacementListenerTest {

  private static final String TIS_ID = "123";
  private static final String TRAINEE_ID = "abc";
  private static final LocalDate START_DATE = LocalDate.now();

  private PlacementListener listener;
  private PlacementService placementService;
  private PlacementMapper mapper;

  @BeforeEach
  void setUp() {
    placementService = mock(PlacementService.class);
    mapper = mock(PlacementMapper.class);
    listener = new PlacementListener(placementService, mapper);
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordEvents() {
    PlacementEvent event = new PlacementEvent(TIS_ID, null);

    assertDoesNotThrow(() -> listener.handlePlacementUpdate(event));
    assertDoesNotThrow(() -> listener.handlePlacementDelete(event));
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordDataEvents() {
    RecordDto recordDto = new RecordDto();
    PlacementEvent event = new PlacementEvent(TIS_ID, recordDto);

    assertDoesNotThrow(() -> listener.handlePlacementUpdate(event));
    assertDoesNotThrow(() -> listener.handlePlacementDelete(event));
  }

  @Test
  void shouldAddNotifications() throws SchedulerException {
    PlacementEvent event = buildPlacementEvent();

    listener.handlePlacementUpdate(event);

    verify(placementService).addNotifications(any());
  }

  @Test
  void shouldDeleteNotifications() throws SchedulerException {
    PlacementEvent event = buildPlacementEvent();
    Placement placementToDelete = new Placement();

    when(mapper.toEntity(any())).thenReturn(placementToDelete);

    ArgumentCaptor<Placement> placementCaptor = ArgumentCaptor.captor();

    listener.handlePlacementDelete(event);

    verify(placementService).deleteNotifications(placementCaptor.capture());
    verify(placementService).deleteScheduledInAppNotifications(placementCaptor.capture());

    Placement placement = placementCaptor.getValue();
    assertThat("Unexpected placement id", placement.getTisId(), is(TIS_ID));
  }

  /**
   * Helper function to construct a programme membership event.
   *
   * @return The ProgrammeMembershipEvent.
   */
  PlacementEvent buildPlacementEvent() {
    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("id", TIS_ID);
    dataMap.put("startDate", START_DATE.toString());
    dataMap.put("traineeId", TRAINEE_ID);
    RecordDto data = new RecordDto();
    data.setData(dataMap);
    return new PlacementEvent(TIS_ID, data);
  }
}
