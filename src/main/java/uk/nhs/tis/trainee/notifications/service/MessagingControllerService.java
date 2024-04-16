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
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.model.MessageType;

/**
 * A helper service to determine whether messages should be sent or not. In particular, we would
 * generally not want to send messages to real users from staging, if they have data in this
 * environment. Whitelisted users are presumed to be testers who will always receive messages.
 */
@Slf4j
@Component
public class MessagingControllerService {

  private static final String TRAINEE_TIS_ID_FIELD = "traineeTisId";
  private static final String PLACEMENT_ID_FIELD = "placementId";
  private static final String PROGRAMME_MEMBERSHIP_ID_FIELD = "programmeMembershipId";
  protected static final String API_PLACEMENT_IS_PILOT_2024
      = "/api/placement/ispilot2024/{traineeTisId}/{placementId}";
  protected static final String API_PROGRAMME_MEMBERSHIP_IS_PILOT_2024
      = "/api/programme-membership/ispilot2024/{traineeTisId}/{programmeMembershipId}";
  protected static final String API_PROGRAMME_MEMBERSHIP_NEW_STARTER
      = "/api/programme-membership/isnewstarter/{traineeTisId}/{programmeMembershipId}";

  private final List<String> notificationsWhitelist;
  private final boolean inAppNotificationsEnabled;
  private final boolean emailNotificationsEnabled;
  private final RestTemplate restTemplate;
  private final String serviceUrl;

  /**
   * Initialise the service with the environmental variables that control message dispatch.
   *
   * @param notificationsWhitelist The whitelist of (tester) trainee TIS IDs.
   * @param restTemplate              The REST template.
   * @param inAppNotificationsEnabled Whether in-app notification messages should be sent.
   * @param emailNotificationsEnabled Whether email notification messages should be sent.
   */
  public MessagingControllerService(RestTemplate restTemplate,
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
   * Determine whether a trainee could be sent a message of a particular type. Whitelisted
   * recipients will always be sent the message. For others, whether they could be sent it or not
   * depends on whether messaging is enabled for the specific message type.
   *
   * @param messageType  The type of message.
   * @param traineeTisId The trainee TIS ID of the recipient.
   * @return true if the trainee could be sent the message, otherwise false.
   */
  public boolean isValidRecipient(String traineeTisId, MessageType messageType) {

    boolean inWhitelist = notificationsWhitelist.contains(traineeTisId);

    if (inWhitelist) {
      return true;
    } else {
      return switch (messageType) {
        case EMAIL -> emailNotificationsEnabled;
        case IN_APP -> inAppNotificationsEnabled;
      };
    }
  }

  /**
   * Identifies if a placement falls within the pilot group 2024.
   *
   * @param traineeId   The trainee TIS ID whose placement it is.
   * @param placementId The placement ID.
   * @return true if the placement is in the pilot group, otherwise false.
   */
  public boolean isPlacementInPilot2024(String traineeId, String placementId) {
    Boolean isPilot = restTemplate.getForObject(serviceUrl + API_PLACEMENT_IS_PILOT_2024,
        Boolean.class, Map.of(TRAINEE_TIS_ID_FIELD, traineeId, PLACEMENT_ID_FIELD, placementId));
    if (isPilot == null || !isPilot) {
      log.info("Trainee {} placement {} is not in the pilot 2024.", traineeId, placementId);
      return false;
    }
    return true;
  }

  /**
   * Identifies if a programme membership falls within the pilot group 2024.
   *
   * @param traineeId             The trainee TIS ID whose placement it is.
   * @param programmeMembershipId The programme membership ID.
   * @return true if the programme membership is in the pilot group, otherwise false.
   */
  public boolean isProgrammeMembershipInPilot2024(String traineeId, String programmeMembershipId) {
    Boolean isPilot = restTemplate.getForObject(serviceUrl
            + API_PROGRAMME_MEMBERSHIP_IS_PILOT_2024, Boolean.class,
        Map.of(TRAINEE_TIS_ID_FIELD, traineeId,
            PROGRAMME_MEMBERSHIP_ID_FIELD, programmeMembershipId));
    if (isPilot == null || !isPilot) {
      log.info("Trainee {} programme membership {} is not in the pilot 2024.",
          traineeId, programmeMembershipId);
      return false;
    }
    return true;
  }

  /**
   * Identifies if a programme membership is considered a 'new starter' programme membership.
   *
   * @param traineeId             The trainee TIS ID whose programme membership it is.
   * @param programmeMembershipId The programme membership ID.
   * @return true if the programme membership is a 'new starter' PM, otherwise false.
   */
  public boolean isProgrammeMembershipNewStarter(String traineeId, String programmeMembershipId) {
    Boolean isNewStarter = restTemplate.getForObject(
        serviceUrl + API_PROGRAMME_MEMBERSHIP_NEW_STARTER,
        Boolean.class, Map.of(TRAINEE_TIS_ID_FIELD, traineeId,
            PROGRAMME_MEMBERSHIP_ID_FIELD, programmeMembershipId));

    if (isNewStarter == null || !isNewStarter) {
      log.info("Trainee {} programme membership {} is not a new starter.",
          traineeId, programmeMembershipId);
      return false;
    }
    return true;
  }
}
