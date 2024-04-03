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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.model.MessageType;

class MessageDispatchServiceTest {

  private static final String SERVICE_URL = "the-url";
  private static final String WHITELIST_1 = "123";
  private static final String WHITELIST_2 = "456";
  private static final List<String> WHITELISTED = List.of(WHITELIST_1, WHITELIST_2);

  private RestTemplate restTemplate;
  private MessageDispatchService service;

  @BeforeEach
  void setUp() {
    restTemplate = mock(RestTemplate.class);
    service = new MessageDispatchService(restTemplate, WHITELISTED, false, false, SERVICE_URL);
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void whitelistedShouldBeValidRecipients(MessageType messageType) {
    assertThat("Unexpected isValidRecipient().",
        service.isValidRecipient(messageType, WHITELIST_1), is(true));
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void nonWhitelistedShouldNotBeValidRecipientsIfNotificationsNotEnabled(MessageType messageType) {
    assertThat("Unexpected isValidRecipient().",
        service.isValidRecipient(messageType, "some other id"), is(false));
  }

  @Test
  void nonWhitelistedShouldBeValidRecipientsIfNotificationsEnabled() {
    service = new MessageDispatchService(restTemplate, WHITELISTED, true, false, SERVICE_URL);
    assertThat("Unexpected isValidRecipient().",
        service.isValidRecipient(MessageType.IN_APP, "some other id"), is(true));

    service = new MessageDispatchService(restTemplate, WHITELISTED, false, true, SERVICE_URL);
    assertThat("Unexpected isValidRecipient().",
        service.isValidRecipient(MessageType.EMAIL, "some other id"), is(true));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void isPlacementInPilot2024ShouldReturnApiResult(boolean apiResult) {
    when(restTemplate
        .getForObject("the-url/api/placement/ispilot2024/{traineeTisId}/{placementId}",
            Boolean.class, Map.of("traineeTisId", "123",
                "placementId", "abc"))).thenReturn(apiResult);

    assertThat("Unexpected isPlacementInPilot2024() result.",
        service.isPlacementInPilot2024("123", "abc"), is(apiResult));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void isProgrammeMembershipNewStarterShouldReturnApiResult(boolean apiResult) {
    when(restTemplate
        .getForObject(
            "the-url/api/programme-membership/isnewstarter/{traineeTisId}/{programmeMembershipId}",
            Boolean.class, Map.of("traineeTisId", "123",
                "programmeMembershipId", "abc"))).thenReturn(apiResult);

    assertThat("Unexpected isProgrammeMembershipNewStarter() result.",
        service.isProgrammeMembershipNewStarter("123", "abc"), is(apiResult));
  }

  @Test
  void ifApiErrorPlacementInPilot2024ShouldReturnFalse() {
    doThrow(new RestClientException("error"))
        .when(restTemplate)
            .getForObject("the-url/api/placement/ispilot2024/{traineeTisId}/{placementId}",
                Boolean.class, Map.of("traineeTisId", "123",
                    "placementId", "abc"));
    assertThat("Unexpected isPlacementInPilot2024() result.",
        service.isPlacementInPilot2024("123", "abc"), is(false));
  }

  @Test
  void ifApiErrorProgrammeMembershipNewStarterShouldReturnFalse() {
    doThrow(new RestClientException("error"))
        .when(restTemplate)
            .getForObject(
                "the-url/api/programme-membership/isnewstarter/{traineeTisId}/{programmeMembershipId}",
                Boolean.class, Map.of("traineeTisId", "123",
                    "programmeMembershipId", "abc"));
    assertThat("Unexpected isProgrammeMembershipNewStarter() result.",
        service.isProgrammeMembershipNewStarter("123", "abc"), is(false));
  }
}
