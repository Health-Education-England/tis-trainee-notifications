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

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.model.MessageType;

/**
 * A helper service to determine whether messages should be sent or not. In particular, we would
 * generally not want to send messages to real users from staging, if they have data in this
 * environment. Whitelisted users are presumed to be testers who will always receive messages.
 */
@Slf4j
@Component
public class MessageDispatchService {

  private static final String TRAINEE_TIS_ID_FIELD = "traineeTisId";
  private static final String PLACEMENT_ID_FIELD = "placementId";
  private static final String PROGRAMME_MEMBERSHIP_ID_FIELD = "programmeMembershipId";
  protected static final String API_IS_PILOT_2024
      = "/api/placement/ispilot2024/{traineeTisId}/{placementId}";
  protected static final String API_NEW_STARTER
      = "/api/programme-membership/isnewstarter/{traineeTisId}/{programmeMembershipId}";

  private final List<String> notificationsWhitelist;
  private final boolean inAppNotificationsEnabled;
  private final boolean emailNotificationsEnabled;
  private final RestTemplate restTemplate;
  private final String serviceUrl;

  /**
   * Initialise the service with the environmental variables that control message dispatch.
   *
   * @param restTemplate              The REST template.
   * @param notificationsWhitelist    The whitelist of (tester) trainee TIS IDs.
   * @param inAppNotificationsEnabled Whether in-app notification messages should be sent.
   * @param emailNotificationsEnabled Whether email notification messages should be sent.
   */
  public MessageDispatchService(RestTemplate restTemplate,
      @Value("${application.notifications-whitelist}") List<String> notificationsWhitelist,
      @Value("${application.in-app.enabled}") boolean inAppNotificationsEnabled,
      @Value("${application.email.enabled}") boolean emailNotificationsEnabled,
      @Value("${service.trainee.url}") String serviceUrl) {
    this.restTemplate = restTemplate;
    this.notificationsWhitelist = notificationsWhitelist;
    this.inAppNotificationsEnabled = inAppNotificationsEnabled;
    this.emailNotificationsEnabled = emailNotificationsEnabled;
    this.serviceUrl = serviceUrl;
  }

  /**
   * Determine whether a trainee could receive a message of a particular type. Whitelisted
   * recipients will always receive the message. For others, whether they could receive it or not
   * depends on whether messaging is enabled for the specific message type.
   *
   * @param messageType  The type of message.
   * @param traineeTisId The trainee TIS ID of the recipient.
   * @return true if the trainee could receive the message, otherwise false.
   */
  public boolean isValidRecipient(MessageType messageType, String traineeTisId) {

    boolean inWhitelist = notificationsWhitelist.contains(traineeTisId);

    if (inWhitelist) {
      return true;
    } else {
      if (messageType == MessageType.EMAIL && emailNotificationsEnabled) {
        return true;
      }
      return messageType == MessageType.IN_APP && inAppNotificationsEnabled;
    }
  }

  /**
   * TEMPORARY. Identifies if a placement falls within the pilot group 2024.
   *
   * @param traineeTisId The trainee TIS ID whose placement it is.
   * @param placementId  The placement ID.
   * @return true if the placement is in the pilot group, otherwise false.
   */
  public boolean isPlacementInPilot2024(String traineeTisId, String placementId) {
    try {
      return Boolean.TRUE.equals(
          restTemplate.getForObject(serviceUrl + API_IS_PILOT_2024, Boolean.class,
              Map.of(TRAINEE_TIS_ID_FIELD, traineeTisId,
                  PLACEMENT_ID_FIELD, placementId)));
    } catch (RestClientException rce) {
      log.warn("Exception occurred when requesting trainee " + traineeTisId
          + " placement " + placementId + " pilot 2024 status : " + rce);
    }
    return false;
  }

  /**
   * Identifies if a programme membership is considered a 'new starter' programme membership.
   *
   * @param traineeTisId          The trainee TIS ID whose programme membership it is.
   * @param programmeMembershipId The programme membership ID.
   * @return true if the programme membership is a 'new starter' PM, otherwise false.
   */
  public boolean isProgrammeMembershipNewStarter(String traineeTisId,
      String programmeMembershipId) {
    try {
      return Boolean.TRUE.equals(
          restTemplate.getForObject(serviceUrl + API_NEW_STARTER, Boolean.class,
              Map.of(TRAINEE_TIS_ID_FIELD, traineeTisId,
                  PROGRAMME_MEMBERSHIP_ID_FIELD, programmeMembershipId)));
    } catch (RestClientException rce) {
      log.warn("Exception occurred when requesting trainee " + traineeTisId
          + " programme membership " + programmeMembershipId + " new starter status : " + rce);
    }
    return false;
  }
}
