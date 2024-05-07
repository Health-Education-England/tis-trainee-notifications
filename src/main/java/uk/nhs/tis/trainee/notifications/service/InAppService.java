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

package uk.nhs.tis.trainee.notifications.service;

import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;

import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A service for managing in-app notifications.
 */
@Slf4j
@Service
public class InAppService {

  private final HistoryService historyService;

  public InAppService(HistoryService historyService) {
    this.historyService = historyService;
  }

  /**
   * Create an in-app notification, or simply log it.
   *
   * @param traineeId         The trainee ID to associate the notification with.
   * @param tisReference      The TIS reference of the associated object.
   * @param notificationType  The type of notification.
   * @param templateVersion   The version of the template to use.
   * @param templateVariables The variables to insert in to the template.
   * @param doNotStoreJustLog Do not store the notification, just log it.
   * @param sendAt            The date and time the notification is displayed / to be displayed.
   */
  public void createNotifications(String traineeId, TisReferenceInfo tisReference,
      NotificationType notificationType, String templateVersion,
      Map<String, Object> templateVariables, boolean doNotStoreJustLog, Instant sendAt) {
    log.info("Creating in-app {} notification for trainee {}.", notificationType, traineeId);
    RecipientInfo recipient = new RecipientInfo(traineeId, IN_APP, null);
    TemplateInfo template = new TemplateInfo(notificationType.getTemplateName(), templateVersion,
        templateVariables);

    History history = new History(null, tisReference, notificationType, recipient, template,
        sendAt, null, UNREAD, null, null);
    if (!doNotStoreJustLog) {
      historyService.save(history);
    } else {
      log.info("Just logging in-app notification with contents: {}", history);
    }
  }

  /**
   * Create an in-app notification, or simply log it.
   *
   * @param traineeId         The trainee ID to associate the notification with.
   * @param tisReference      The TIS reference of the associated object.
   * @param notificationType  The type of notification.
   * @param templateVersion   The version of the template to use.
   * @param templateVariables The variables to insert in to the template.
   * @param doNotStoreJustLog Do not store the notification, just log it.
   */
  public void createNotifications(String traineeId, TisReferenceInfo tisReference,
      NotificationType notificationType, String templateVersion,
      Map<String, Object> templateVariables, boolean doNotStoreJustLog) {
    createNotifications(traineeId, tisReference, notificationType, templateVersion,
        templateVariables, doNotStoreJustLog, Instant.now());
  }

  /**
   * Create an in-app notification.
   *
   * @param traineeId         The trainee ID to associate the notification with.
   * @param tisReference      The TIS reference of the associated object.
   * @param notificationType  The type of notification.
   * @param templateVersion   The version of the template to use.
   * @param templateVariables The variables to insert in to the template.
   */
  public void createNotifications(String traineeId, TisReferenceInfo tisReference,
      NotificationType notificationType, String templateVersion,
      Map<String, Object> templateVariables) {
    createNotifications(traineeId, tisReference, notificationType, templateVersion,
        templateVariables, false);
  }
}
