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

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.dto.AccountEvent;
import uk.nhs.tis.trainee.notifications.mapper.UserAccountMapper;
import uk.nhs.tis.trainee.notifications.model.UserAccount;
import uk.nhs.tis.trainee.notifications.repository.UserAccountRepository;

/**
 * A service for keeping the local copy of User Management accounts in sync.
 */
@Slf4j
@Service
public class UserAccountSyncService {

  private final UserAccountRepository repository;
  private final UserAccountMapper mapper;

  /**
   * Construct a service for syncing user accounts.
   *
   * @param repository The user account repository.
   * @param mapper     The user account mapper.
   */
  public UserAccountSyncService(UserAccountRepository repository, UserAccountMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  /**
   * Insert or fully replace the user account, null event values will clear stored values.
   *
   * @param event The account event containing the latest account details.
   * @return The saved user account.
   */
  public UserAccount upsert(AccountEvent event) {
    if (event.id() == null) {
      throw new IllegalArgumentException("Account event ID must not be null.");
    }

    log.info("Upserting user account {}.", event.id());
    return repository.save(mapper.toEntity(event));
  }

  /**
   * Delete the user account with the given ID, no action is taken if it does not exist.
   *
   * @param id The ID of the user account to delete.
   */
  public void deleteById(UUID id) {
    if (id == null) {
      throw new IllegalArgumentException("Account event ID must not be null.");
    }

    log.info("Deleting user account {}.", id);
    repository.deleteById(id);
  }
}

