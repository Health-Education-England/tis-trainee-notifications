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

package uk.nhs.tis.trainee.notifications.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

/**
 * A listener for Conditions of Joining events.
 */
@Slf4j
@Component
public class ConditionsOfJoiningListener {

  private static final String CONFIRMATION_SUBJECT = "We've received your Conditions of Joining";
  private static final String CONFIRMATION_TEMPLATE = "email/coj-confirmation";

  private final UserAccountService userAccountService;
  private final EmailService emailService;
  private final URI appDomain;
  private final String timezone;

  /**
   * Construct a listener for conditions of joining events.
   *
   * @param emailService The service to use for sending emails.
   * @param appDomain    The application domain to link to.
   * @param timezone     The timezone to base event times on.
   */
  public ConditionsOfJoiningListener(UserAccountService userAccountService,
      EmailService emailService,
      @Value("${application.domain}") URI appDomain,
      @Value("${application.timezone}") String timezone) {
    this.userAccountService = userAccountService;
    this.emailService = emailService;
    this.appDomain = appDomain;
    this.timezone = timezone;
  }

  /**
   * Handle Conditions of Joining received events.
   *
   * @param event The program membership event.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.coj-received}")
  public void handleConditionsOfJoiningReceived(ProgrammeMembershipEvent event)
      throws MessagingException {
    log.info("Handling COJ received event {}.", event);
    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("domain", appDomain);
    templateVariables.put("managingDeanery", event.managingDeanery());

    // Convert the UTC timestamp to the receiver's timezone.
    if (event.conditionsOfJoining() != null && event.conditionsOfJoining().syncedAt() != null) {
      ZonedDateTime syncedAt = ZonedDateTime.ofInstant(event.conditionsOfJoining().syncedAt(),
          ZoneId.of(timezone));
      templateVariables.put("syncedAt", syncedAt);
    }

    // Get the doctor's name.
    String traineeId = event.personId();
    String destination = null;
    if (traineeId != null) {
      Set<String> userAccountIds = userAccountService.getUserAccountIds(traineeId);

      switch (userAccountIds.size()) {
        case 0 -> throw new IllegalArgumentException("No user account found for the given ID.");
        case 1 -> {
          UserAccountDetails userDetails = userAccountService.getUserDetails(
              userAccountIds.iterator().next());
          String familyName = userDetails.familyName();
          String name = Strings.isBlank(familyName) ? "Doctor" : "Dr " + familyName;
          templateVariables.put("name", name);
          destination = userDetails.email();
        }
        default ->
            throw new IllegalArgumentException("Multiple user accounts found for the given ID.");
      }
    }

    if (destination != null) {
      emailService.sendMessage(destination, CONFIRMATION_SUBJECT,
          CONFIRMATION_TEMPLATE, templateVariables);
      log.info("COJ received notification sent to {}.", destination);
    } else {
      String message = "Could not find email address for user '%s'".formatted(traineeId);
      throw new IllegalArgumentException(message);
    }
  }
}
