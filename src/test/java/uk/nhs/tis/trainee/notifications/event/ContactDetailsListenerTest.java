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

package uk.nhs.tis.trainee.notifications.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.ContactDetailsEvent;
import uk.nhs.tis.trainee.notifications.dto.RecordDto;
import uk.nhs.tis.trainee.notifications.mapper.ContactDetailsMapper;
import uk.nhs.tis.trainee.notifications.service.ContactDetailsService;

class ContactDetailsListenerTest {

  private static final String TIS_ID = "123";
  private static final String EMAIL = "email@address";

  private ContactDetailsListener listener;
  private ContactDetailsService contactDetailsService;
  private ContactDetailsMapper mapper;

  @BeforeEach
  void setUp() {
    contactDetailsService = mock(ContactDetailsService.class);
    mapper = mock(ContactDetailsMapper.class);
    listener = new ContactDetailsListener(contactDetailsService, mapper);
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordEvents() {
    //ensure events with COJ_RECEIVED structure (and any other) are ignored, and not requeued
    ContactDetailsEvent event = new ContactDetailsEvent(TIS_ID, null);

    assertDoesNotThrow(() -> listener.handleUpdate(event));
  }

  @Test
  void shouldNotThrowAnExceptionOnNonRecordDataEvents() {
    //ensure events without record data are ignored, and not requeued
    RecordDto recordDto = new RecordDto();
    ContactDetailsEvent event = new ContactDetailsEvent(TIS_ID, recordDto);

    assertDoesNotThrow(() -> listener.handleUpdate(event));
  }

  @Test
  void shouldUpdateContactDetails() {
    ContactDetailsEvent event = buildContactDetailsEvent();

    listener.handleUpdate(event);

    verify(contactDetailsService).updateContactDetails(any());
  }

  /**
   * Helper function to construct a contact details event.
   *
   * @return The ProgrammeMembershipEvent.
   */
  ContactDetailsEvent buildContactDetailsEvent() {
    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("tisId", TIS_ID);
    dataMap.put("email", EMAIL);

    RecordDto data = new RecordDto();
    data.setData(dataMap);
    return new ContactDetailsEvent(TIS_ID, data);
  }
}
