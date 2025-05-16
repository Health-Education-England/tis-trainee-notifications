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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;

class LtftEventMapperTest {

  private LtftEventMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new LtftEventMapperImpl();
  }

  @Test
  void shouldUseNullWhenStateDetailNull() {
    LtftUpdateEvent event = LtftUpdateEvent.builder().build();

    LtftUpdateEvent eventMapped = mapper.map(event);

    assertThat("Unexpected state detail.", eventMapped.getStateDetail(),
        is(nullValue()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldUseReasonWhenReasonKnownAsMissing(String reason) {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .stateDetail(LtftUpdateEvent.LftfStatusInfoDetailDto.builder()
            .reason(reason)
            .message("the message")
            .build())
        .build();

    LtftUpdateEvent eventMapped = mapper.map(event);

    assertThat("Unexpected unmapped reason.", eventMapped.getStateDetail().reason(),
        is(reason));
  }

  @ParameterizedTest
  @ValueSource(strings = {"other", "changePercentage", "changeStartDate", "changeOfCircs"})
  void shouldMapReasonsToText(String reason) {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .stateDetail(LtftUpdateEvent.LftfStatusInfoDetailDto.builder()
            .reason(reason)
            .message("the message")
            .build())
        .build();

    LtftUpdateEvent eventMapped = mapper.map(event);

    assertThat("Unexpected unmapped reason.", eventMapped.getStateDetail().reason(),
        not(reason));
  }

}
