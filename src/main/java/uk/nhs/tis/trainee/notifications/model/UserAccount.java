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

package uk.nhs.tis.trainee.notifications.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A local copy of a User Management account, populated from account events.
 *
 * <p>Indexes on {@code sub}, {@code email} and {@code traineeId} are created by
 * {@link uk.nhs.tis.trainee.notifications.config.MongoCollectionConfiguration}. None are unique,
 * a trainee may have multiple accounts and an email may briefly be held by two accounts.
 *
 * @param id           The User Management account ID.
 * @param sub          The Cognito subject, not guaranteed to be a UUID.
 * @param email        The account's email address.
 * @param traineeId    The linked trainee's TIS ID.
 * @param forename     The account holder's forename.
 * @param surname      The account holder's surname.
 * @param lastModified When the account was last modified.
 */
@Document(collection = "UserAccount")
@Builder
public record UserAccount(
    @Id
    UUID id,
    String sub,
    String email,
    String traineeId,
    String forename,
    String surname,
    @LastModifiedDate
    Instant lastModified) {

}

