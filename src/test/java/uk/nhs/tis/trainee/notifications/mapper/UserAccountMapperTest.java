/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.AccountEvent;
import uk.nhs.tis.trainee.notifications.model.UserAccount;

class UserAccountMapperTest {

  private UserAccountMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new UserAccountMapperImpl();
  }

  @Test
  void shouldReturnNullWhenEventNull() {
    assertThat("Unexpected entity.", mapper.toEntity(null), nullValue());
  }

  @Test
  void shouldMapAllFieldsWhenEventPopulated() {
    UUID id = UUID.randomUUID();
    AccountEvent event = new AccountEvent(id, "sub", "email", "40", "forename", "surname");

    UserAccount entity = mapper.toEntity(event);

    assertThat("Unexpected ID.", entity.id(), is(id));
    assertThat("Unexpected sub.", entity.sub(), is("sub"));
    assertThat("Unexpected email.", entity.email(), is("email"));
    assertThat("Unexpected trainee ID.", entity.traineeId(), is("40"));
    assertThat("Unexpected forename.", entity.forename(), is("forename"));
    assertThat("Unexpected surname.", entity.surname(), is("surname"));
    assertThat("Unexpected last modified.", entity.lastModified(), nullValue());
  }
}

