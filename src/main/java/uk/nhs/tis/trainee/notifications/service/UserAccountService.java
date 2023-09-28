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

package uk.nhs.tis.trainee.notifications.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;

/**
 * A service providing user account data.
 */
@Slf4j
@Service
public class UserAccountService {

  private static final String USER_ID_CACHE = "UserId";

  private static final String ATTRIBUTE_EMAIL = "email";
  private static final String ATTRIBUTE_FAMILY_NAME = "family_name";

  private final CognitoIdentityProviderClient cognitoClient;
  private final String userPoolId;
  private final Cache cache;

  UserAccountService(CognitoIdentityProviderClient cognitoClient,
      @Value("${application.cognito.user-pool-id}") String userPoolId, CacheManager cacheManager) {
    this.cognitoClient = cognitoClient;
    this.userPoolId = userPoolId;
    cache = cacheManager.getCache(USER_ID_CACHE);
  }

  /**
   * Get all user account IDs associated with the given person ID.
   *
   * @param personId The person ID to get the user IDs for.
   * @return The found user IDs.
   */
  @Cacheable(USER_ID_CACHE)
  public Set<String> getUserAccountIds(String personId) {
    log.info("User account not found in the cache.");
    cacheAllUserAccountIds();
    return cache.get(personId, Set.class);
  }

  /**
   * Retrieve and cache a mapping of all person IDs to user IDs.
   */
  private void cacheAllUserAccountIds() {
    log.info("Caching all user account ids from Cognito.");
    StopWatch cacheTimer = new StopWatch();
    cacheTimer.start();

    ListUsersRequest request = ListUsersRequest.builder()
        .userPoolId(userPoolId)
        .build();
    ListUsersIterable responses = cognitoClient.listUsersPaginator(request);
    responses.users().stream()
        .map(UserType::attributes)
        .map(attributes -> attributes.stream()
            .collect(Collectors.toMap(AttributeType::name, AttributeType::value)))
        .forEach(attr -> {
          String tisId = attr.get("custom:tisId");
          Set<String> ids = cache.get(tisId, Set.class);

          if (ids == null) {
            ids = new HashSet<>();
          }

          ids.add(attr.get("sub"));
          cache.put(tisId, ids);
        });

    cacheTimer.stop();
    log.info("Total time taken to cache all user accounts was: {}s",
        cacheTimer.getTotalTimeSeconds());
  }

  /**
   * Get the user account details for a given user ID.
   *
   * @param userAccountId The user ID to get the details of.
   * @return The found user account details.
   */
  public UserAccountDetails getUserDetails(String userAccountId) {
    log.info("Getting user details for account {}", userAccountId);
    AdminGetUserRequest request = AdminGetUserRequest.builder()
        .userPoolId(userPoolId)
        .username(userAccountId)
        .build();
    AdminGetUserResponse response = cognitoClient.adminGetUser(request);
    Map<String, String> attributes = response.userAttributes().stream()
        .collect(Collectors.toMap(AttributeType::name, AttributeType::value));

    return new UserAccountDetails(attributes.get(ATTRIBUTE_EMAIL),
        attributes.get(ATTRIBUTE_FAMILY_NAME));
  }
}
