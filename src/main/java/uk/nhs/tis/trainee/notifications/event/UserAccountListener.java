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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.event;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.EMAIL_UPDATED_NEW;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.EMAIL_UPDATED_OLD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.WELCOME;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.AccountConfirmedEvent;
import uk.nhs.tis.trainee.notifications.dto.AccountUpdatedEvent;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.service.EmailService;
import uk.nhs.tis.trainee.notifications.service.InAppService;
import uk.nhs.tis.trainee.notifications.service.UserAccountService;

/**
 * A listener for user account events.
 */
@Slf4j
@Component
public class UserAccountListener {

  private final EmailService emailService;
  private final InAppService inAppService;
  private final UserAccountService userAccountService;
  private final URI appDomain;
  private final String emailUpdatedNewVersion;
  private final String emailUpdatedOldVersion;
  private final String welcomeVersion;

  /**
   * Construct a listener for user account events.
   *
   * @param emailService The email service.
   * @param inAppService The in-app service.
   */
  public UserAccountListener(EmailService emailService, InAppService inAppService,
      UserAccountService userAccountService, @Value("${application.domain}") URI appDomain,
      @Value("${application.template-versions.email-updated-new.email}")
      String emailUpdatedNewVersion,
      @Value("${application.template-versions.email-updated-old.email}")
      String emailUpdatedOldVersion,
      @Value("${application.template-versions.welcome.in-app}") String welcomeVersion) {
    this.emailService = emailService;
    this.inAppService = inAppService;
    this.userAccountService = userAccountService;
    this.appDomain = appDomain;
    this.emailUpdatedNewVersion = emailUpdatedNewVersion;
    this.emailUpdatedOldVersion = emailUpdatedOldVersion;
    this.welcomeVersion = welcomeVersion;
  }

  /**
   * Handle account confirmation events.
   *
   * @param event The account confirmation event.
   */
  @SqsListener("${application.queues.account-confirmed}")
  public void handleAccountConfirmation(AccountConfirmedEvent event) {
    log.info("Handling account confirmation event for user {}.", event.userId());
    inAppService.createNotifications(event.traineeId(), null, WELCOME, welcomeVersion, Map.of());
  }

  /**
   * Handle account update events.
   *
   * @param event The account update event.
   */
  @SqsListener("${application.queues.account-updated}")
  public void handleAccountUpdate(AccountUpdatedEvent event) throws MessagingException {
    UUID userId = event.userId();
    log.info("Handling account update event for user {}.", userId);

    UserDetails userDetails = userAccountService.getUserDetailsById(userId.toString());

    Map<String, Object> newEmailVariables = new HashMap<>();
    newEmailVariables.put("familyName", userDetails.familyName());
    newEmailVariables.put("domain", appDomain);
    newEmailVariables.put("newEmail", event.newEmail());

    emailService.sendMessage(event.traineeId(), event.newEmail(), EMAIL_UPDATED_NEW,
        emailUpdatedNewVersion, newEmailVariables, null, false);

    Map<String, Object> oldEmailVariables = new HashMap<>(newEmailVariables);
    oldEmailVariables.put("previousEmail", event.previousEmail());
    emailService.sendMessage(event.traineeId(), event.previousEmail(), EMAIL_UPDATED_OLD,
        emailUpdatedOldVersion, oldEmailVariables, null, false);
  }
}
