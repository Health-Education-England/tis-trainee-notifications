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

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.AccountEvent;
import uk.nhs.tis.trainee.notifications.mapper.UserAccountMapperImpl;
import uk.nhs.tis.trainee.notifications.model.UserAccount;
import uk.nhs.tis.trainee.notifications.repository.UserAccountRepository;

class UserAccountSyncServiceTest {

  private static final UUID ID = UUID.randomUUID();
  private static final String SUB = "cognito-sub";
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String TRAINEE_ID = "40";
  private static final String FORENAME = "Anthony";
  private static final String SURNAME = "Gilliam";

  private UserAccountSyncService service;
  private UserAccountRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(UserAccountRepository.class);
    service = new UserAccountSyncService(repository, new UserAccountMapperImpl());
  }

  @Test
  void shouldSaveAllFieldsWhenUpserting() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    AccountEvent event = new AccountEvent(ID, SUB, EMAIL, TRAINEE_ID, FORENAME, SURNAME);

    service.upsert(event);

    ArgumentCaptor<UserAccount> captor = ArgumentCaptor.captor();
    verify(repository).save(captor.capture());

    UserAccount account = captor.getValue();
    assertThat("Unexpected ID.", account.id(), is(ID));
    assertThat("Unexpected sub.", account.sub(), is(SUB));
    assertThat("Unexpected email.", account.email(), is(EMAIL));
    assertThat("Unexpected trainee ID.", account.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected forename.", account.forename(), is(FORENAME));
    assertThat("Unexpected surname.", account.surname(), is(SURNAME));
    assertThat("Unexpected last modified.", account.lastModified(), nullValue());
  }

  @Test
  void shouldSaveNullFieldsWhenUpsertingWithNullValues() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    AccountEvent event = new AccountEvent(ID, SUB, null, null, null, null);

    service.upsert(event);

    ArgumentCaptor<UserAccount> captor = ArgumentCaptor.captor();
    verify(repository).save(captor.capture());

    UserAccount account = captor.getValue();
    assertThat("Unexpected ID.", account.id(), is(ID));
    assertThat("Unexpected sub.", account.sub(), is(SUB));
    assertThat("Unexpected email.", account.email(), nullValue());
    assertThat("Unexpected trainee ID.", account.traineeId(), nullValue());
    assertThat("Unexpected forename.", account.forename(), nullValue());
    assertThat("Unexpected surname.", account.surname(), nullValue());
  }

  @Test
  void shouldReturnSavedAccountWhenUpserting() {
    UserAccount saved = UserAccount.builder().id(ID).build();
    when(repository.save(any())).thenReturn(saved);

    UserAccount result = service.upsert(new AccountEvent(ID, null, null, null, null, null));

    assertThat("Unexpected saved account.", result, is(saved));
  }

  @Test
  void shouldThrowExceptionWhenUpsertingWithoutId() {
    AccountEvent event = new AccountEvent(null, SUB, EMAIL, TRAINEE_ID, FORENAME, SURNAME);

    assertThrows(IllegalArgumentException.class, () -> service.upsert(event));

    verifyNoInteractions(repository);
  }

  @Test
  void shouldDeleteAccountById() {
    service.deleteById(ID);

    verify(repository).deleteById(ID);
  }

  @Test
  void shouldThrowExceptionWhenDeletingWithoutId() {
    assertThrows(IllegalArgumentException.class, () -> service.deleteById(null));

    verifyNoInteractions(repository);
  }
}

