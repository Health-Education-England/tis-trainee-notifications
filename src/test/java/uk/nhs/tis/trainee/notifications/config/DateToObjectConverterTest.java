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

package uk.nhs.tis.trainee.notifications.config;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.config.MongoConfiguration.DateToObjectConverter;

class DateToObjectConverterTest {

  private DateToObjectConverter converter;

  @BeforeEach
  void setUp() {
    converter = new DateToObjectConverter();
  }


  @Test
  void shouldConvertToLocalDateWhenDateIsNotNull() {
    Instant now = Instant.now();
    Date date = Date.from(now);

    Object convertedDate = converter.convert(date);

    assertThat("Unexpected converted type.", convertedDate, instanceOf(LocalDate.class));

    LocalDate localDate = (LocalDate) convertedDate;
    assertThat("Unexpected local date.", localDate,
        is(LocalDate.from(now.atZone(ZoneId.of("UTC")))));
  }

  @Test
  void shouldConvertToNullWhenDateIsNull() {
    Object convertedDate = converter.convert(null);

    assertThat("Unexpected converted type.", convertedDate, nullValue());
  }

  @Test
  void shouldConvertToMinimumLocalDateWhenDateIsMinimum() {
    Date date = new Date(Long.MIN_VALUE);

    Object convertedDate = converter.convert(date);

    assertThat("Unexpected converted type.", convertedDate, instanceOf(LocalDate.class));

    LocalDate localDate = (LocalDate) convertedDate;
    assertThat("Unexpected local date.", localDate, is(LocalDate.MIN));
  }

  @Test
  void shouldConvertToMaximumLocalDateWhenDateIsMaximum() {
    Date date = new Date(Long.MAX_VALUE);

    Object convertedDate = converter.convert(date);

    assertThat("Unexpected converted type.", convertedDate, instanceOf(LocalDate.class));

    LocalDate localDate = (LocalDate) convertedDate;
    assertThat("Unexpected local date.", localDate, is(LocalDate.MAX));
  }

  @Test
  void shouldReturnOriginalDateWhenCanNotGetTime() {
    Date date = mock(Date.class);
    when(date.getTime()).thenThrow(RuntimeException.class);

    Object convertedDate = converter.convert(date);

    assertThat("Unexpected converted type.", convertedDate, instanceOf(Date.class));
    assertThat("Unexpected date instance.", convertedDate, sameInstance(date));
  }
}
