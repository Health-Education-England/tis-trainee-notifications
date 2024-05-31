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

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;

class UserAccountServiceTest {

  private static final String USER_POOL_ID = "userPool123";

  private static final String USER_ID_1 = UUID.randomUUID().toString();
  private static final String PERSON_ID_1 = "1";
  private static final String GMC_NUMBER = "111111";

  private static final String USER_ID_2 = UUID.randomUUID().toString();
  private static final String PERSON_ID_2 = "2";
  private static final String EMAIL = "anthony.gilliam@tis.nhs.uk";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String GIVEN_NAME = "Anthony";

  private static final String ATTRIBUTE_PERSON_ID = "custom:tisId";
  private static final String ATTRIBUTE_USER_ID = "sub";

  private UserAccountService service;
  private CognitoIdentityProviderClient cognitoClient;
  private Cache cache;

  @BeforeEach
  void setUp() {
    cognitoClient = mock(CognitoIdentityProviderClient.class);
    cache = mock(Cache.class);

    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("UserId")).thenReturn(cache);

    service = new UserAccountService(cognitoClient, USER_POOL_ID, cacheManager);
  }

  @Test
  void shouldRequestUserAccountIdsFromGivenUserPoolWhenGettingUserAccountIds() {
    // The response is mocked instead of constructed due to embedded pagination handling.
    ListUsersIterable responses = mock(ListUsersIterable.class);

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoClient.listUsersPaginator(requestCaptor.capture())).thenReturn(responses);

    SdkIterable<UserType> users = mock(SdkIterable.class);
    when(responses.users()).thenReturn(users);
    when(users.stream()).thenReturn(Stream.of());

    service.getUserAccountIds(PERSON_ID_1);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected request user pool.", request.userPoolId(), is(USER_POOL_ID));
  }

  @Test
  void shouldCacheAllUserAccountIdsWhenGettingUserAccountIds() {
    // The response is mocked instead of constructed due to embedded pagination handling.
    ListUsersIterable responses = mock(ListUsersIterable.class);
    when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(responses);

    SdkIterable<UserType> users = mock(SdkIterable.class);
    when(responses.users()).thenReturn(users);

    UserType user1 = UserType.builder().attributes(
        AttributeType.builder().name(ATTRIBUTE_PERSON_ID).value(PERSON_ID_1).build(),
        AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_1.toString()).build()
    ).build();
    UserType user2 = UserType.builder().attributes(
        AttributeType.builder().name(ATTRIBUTE_PERSON_ID).value(PERSON_ID_2).build(),
        AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_2.toString()).build()
    ).build();
    when(users.stream()).thenReturn(Stream.of(user1, user2));

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(PERSON_ID_1);

    verify(cache).put(PERSON_ID_1, Set.of(USER_ID_1));
    verify(cache).put(PERSON_ID_2, Set.of(USER_ID_2));
  }

  @Test
  void shouldCacheDuplicateUserAccountIdsWhenGettingUserAccountIds() {
    // The response is mocked instead of constructed due to embedded pagination handling.
    ListUsersIterable responses = mock(ListUsersIterable.class);
    when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(responses);

    SdkIterable<UserType> users = mock(SdkIterable.class);
    when(responses.users()).thenReturn(users);

    UserType user2 = UserType.builder().attributes(
        AttributeType.builder().name(ATTRIBUTE_PERSON_ID).value(PERSON_ID_1).build(),
        AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_2.toString()).build()
    ).build();
    when(users.stream()).thenReturn(Stream.of(user2));

    when(cache.get(PERSON_ID_1, Set.class)).thenReturn(new HashSet<>(Set.of(USER_ID_1)));

    service.getUserAccountIds(PERSON_ID_1);

    verify(cache).put(PERSON_ID_1, Set.of(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetUserAccountIdsFromCache() {
    // The response is mocked instead of constructed due to embedded pagination handling.
    ListUsersIterable responses = mock(ListUsersIterable.class);
    when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(responses);

    SdkIterable<UserType> users = mock(SdkIterable.class);
    when(responses.users()).thenReturn(users);

    when(cache.get(PERSON_ID_1, Set.class)).thenReturn(Set.of(USER_ID_1, USER_ID_2));

    Set<String> userAccountIds = service.getUserAccountIds(PERSON_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(2));
    assertThat("Unexpected user IDs.", userAccountIds, hasItems(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetEmptyUserAccountIdsWhenAccountNotFoundAfterBuildingCache() {
    // The response is mocked instead of constructed due to embedded pagination handling.
    ListUsersIterable responses = mock(ListUsersIterable.class);
    when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(responses);

    SdkIterable<UserType> users = mock(SdkIterable.class);
    when(responses.users()).thenReturn(users);

    when(cache.get(PERSON_ID_1, Set.class)).thenReturn(null);

    Set<String> userAccountIds = service.getUserAccountIds(PERSON_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(0));
  }

  @Test
  void shouldNotImmediatelyRepeatBuildingUserIdCache() {
    // The response is mocked instead of constructed due to embedded pagination handling.
    ListUsersIterable responses = mock(ListUsersIterable.class);
    when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(responses);

    SdkIterable<UserType> users = mock(SdkIterable.class);
    when(responses.users()).thenReturn(users);

    service.getUserAccountIds(PERSON_ID_1);
    service.getUserAccountIds(PERSON_ID_2);

    verify(cognitoClient, times(1)).listUsersPaginator(any(ListUsersRequest.class));
  }

  @Test
  void shouldRequestUserDetailsByEmail() {
    UserType user = UserType.builder().build();
    ListUsersResponse response = ListUsersResponse.builder().users(user).build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoClient.listUsers(requestCaptor.capture())).thenReturn(response);

    service.getUserDetailsByEmail(EMAIL);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected user filter.", request.filter(), is("email=\"" + EMAIL + "\""));
  }

  @Test
  void shouldGetUserDetailsWhenUserFoundByEmail() {
    UserType user = UserType.builder()
        .attributes(
            AttributeType.builder().name("email").value(EMAIL).build(),
            AttributeType.builder().name("family_name").value(FAMILY_NAME).build(),
            AttributeType.builder().name("given_name").value(GIVEN_NAME).build())
        .build();

    ListUsersResponse response = ListUsersResponse.builder().users(user).build();
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(response);

    UserDetails userDetails = service.getUserDetailsByEmail(EMAIL);

    assertThat("Unexpected isRegistered.", userDetails.isRegistered(), is(true));
    assertThat("Unexpected email.", userDetails.email(), is(EMAIL));
    assertThat("Unexpected title.", userDetails.title(), is(nullValue()));
    assertThat("Unexpected family name.", userDetails.familyName(), is(FAMILY_NAME));
    assertThat("Unexpected given name.", userDetails.givenName(), is(GIVEN_NAME));
    assertThat("Unexpected gmc number.", userDetails.gmcNumber(), is(nullValue()));
  }

  @Test
  void shouldGetEmptyUserDetailsWhenUserFoundByEmailWithMissingAttributes() {
    UserType user = UserType.builder().build();
    ListUsersResponse response = ListUsersResponse.builder().users(user).build();
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(response);

    UserDetails userDetails = service.getUserDetailsByEmail(EMAIL);

    assertThat("Unexpected email.", userDetails.email(), nullValue());
    assertThat("Unexpected family name.", userDetails.familyName(), nullValue());
  }

  @Test
  void shouldThrowExceptionGettingUserDetailsWhenUserNotFoundByEmail() {
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(
        ListUsersResponse.builder().build());

    assertThrows(UserNotFoundException.class, () -> service.getUserDetailsByEmail(EMAIL));
  }

  @Test
  void shouldThrowExceptionGettingUserDetailsWhenUsersFoundByEmailIsEmptyCollection() {
    ListUsersResponse listUsersResponse
        = ListUsersResponse.builder().users(new ArrayList<>()).build();
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(
        listUsersResponse);

    assertThrows(UserNotFoundException.class, () -> service.getUserDetailsByEmail(EMAIL));
  }

  @Test
  void shouldRequestUserDetailsById() {
    UserType user = UserType.builder().build();
    ListUsersResponse response = ListUsersResponse.builder().users(user).build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoClient.listUsers(requestCaptor.capture())).thenReturn(response);

    service.getUserDetailsById(USER_ID_1);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected user filter.", request.filter(), is("sub=\"" + USER_ID_1 + "\""));
  }

  @Test
  void shouldGetUserDetailsWhenUserFoundById() {
    UserType user = UserType.builder()
        .attributes(
            AttributeType.builder().name("email").value(EMAIL).build(),
            AttributeType.builder().name("family_name").value(FAMILY_NAME).build(),
            AttributeType.builder().name("given_name").value(GIVEN_NAME).build())
        .build();

    ListUsersResponse response = ListUsersResponse.builder().users(user).build();
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(response);

    UserDetails userDetails = service.getUserDetailsById(USER_ID_1);

    assertThat("Unexpected isRegistered.", userDetails.isRegistered(), is(true));
    assertThat("Unexpected email.", userDetails.email(), is(EMAIL));
    assertThat("Unexpected title.", userDetails.title(), is(nullValue()));
    assertThat("Unexpected family name.", userDetails.familyName(), is(FAMILY_NAME));
    assertThat("Unexpected given name.", userDetails.givenName(), is(GIVEN_NAME));
    assertThat("Unexpected gmc number.", userDetails.gmcNumber(), is(nullValue()));
  }

  @Test
  void shouldGetEmptyUserDetailsWhenUserFoundByIdWithMissingAttributes() {
    UserType user = UserType.builder().build();
    ListUsersResponse response = ListUsersResponse.builder().users(user).build();
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(response);

    UserDetails userDetails = service.getUserDetailsById(USER_ID_1);

    assertThat("Unexpected email.", userDetails.email(), nullValue());
    assertThat("Unexpected family name.", userDetails.familyName(), nullValue());
  }

  @Test
  void shouldThrowExceptionGettingUserDetailsWhenUserNotFoundById() {
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(
        ListUsersResponse.builder().build());

    assertThrows(UserNotFoundException.class, () -> service.getUserDetailsById(USER_ID_1));
  }
}
