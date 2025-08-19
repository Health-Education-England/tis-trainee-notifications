/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationSummary;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeActionType;

/**
 * A helper component for managing programme actions.
 */
@Slf4j
@Component
public class ProgrammeMembershipActionsService {
  public static final String TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD = "welcomeSendDate";
  public static final String TEMPLATE_NOTIFICATION_TYPE_FIELD = "notificationType";

  public static final String API_PROGRAMME_ACTIONS = "/api/action/{personId}/{programmeId}";

  private final String actionsUrl;
  private final RestTemplate restTemplate;
  private final HistoryService historyService;

  private String personId;
  private String programmeId;
  private String jobName;
  private LocalDate startDate;
  private NotificationType notificationType;
  private Set<ActionDto> actions;

  ProgrammeMembershipActionsService(@Value("${service.actions.url}") String actionsUrl,
      RestTemplate restTemplate, HistoryService historyService) {
    this.historyService = historyService;
    this.actionsUrl = actionsUrl;
    this.restTemplate = restTemplate;
  }

  /**
   * Add programme action details to the job data map.
   *
   * @param personId    The ID of the person associated with the programme.
   * @param jobDataMap The job data map to populate with programme actions.
   */
  public void addActionsToJobMap(@Nonnull String personId, @Nonnull JobDataMap jobDataMap) {
    this.personId = personId;
    this.programmeId = jobDataMap.getString(TIS_ID_FIELD);
    this.jobName = jobDataMap.getString(PROGRAMME_NAME_FIELD);
    this.startDate = (LocalDate) jobDataMap.get(START_DATE_FIELD);
    this.notificationType = NotificationType.valueOf(
        jobDataMap.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());

    if (NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(notificationType)) {
      addProgrammeReminderDetailsToJobMap(jobDataMap);
    }
  }

  /**
   * Get a summary of the programme notification.
   *
   * @return A NotificationSummary object.
   */
  public NotificationSummary getNotificationSummary() {
    boolean unnecessaryReminder = false;
    History.TisReferenceInfo tisReferenceInfo = new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP,
        programmeId);
    if (NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(notificationType)
        && !hasIncompleteProgrammeActions()) {
      unnecessaryReminder = true;
    }
    return new NotificationSummary(jobName, startDate, tisReferenceInfo, unnecessaryReminder);
  }

  /**
   * Add the trainee's programme reminders to the job data map for programme notifications.
   *
   * @param jobDataMap The job data map to populate.
   */
  private void addProgrammeReminderDetailsToJobMap(JobDataMap jobDataMap) {
    List<History> welcomeNotification = historyService.findAllSentEmailForTraineeByRefAndType(
        personId, PROGRAMME_MEMBERSHIP, programmeId, PROGRAMME_CREATED);
    if (welcomeNotification == null || welcomeNotification.isEmpty()) {
      log.warn("Welcome notification for trainee {} and programme membership {} is missing or "
              + "not SENT, so it is not added to the job data map.",
          personId, programmeId);
    } else {
      if (welcomeNotification.size() > 1) {
        log.warn("Multiple welcome notifications found for trainee {} and programme membership {}. "
            + "Using the first one.", personId, programmeId);
      }
      jobDataMap.putIfAbsent(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD,
          (welcomeNotification.get(0).lastRetry() != null
              ? welcomeNotification.get(0).lastRetry()
              : welcomeNotification.get(0).sentAt()));
    }
    setActions();
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      Boolean isComplete = isProgrammeActionComplete(actionType);
      if (isComplete == null) {
        log.warn("No {} action found for trainee {} programme membership {}: "
            + "listing as 'assumed complete'.", actionType, personId, programmeId);
        isComplete = true; //assume complete if no action found
      }
      jobDataMap.put(actionType.toString(), isComplete);
    }
  }

  /**
   * Set the actions for a trainee and programme membership.
   *
   */
  private void setActions() {
    try {
      ParameterizedTypeReference<Set<ActionDto>> actionSetType
          = new ParameterizedTypeReference<>() {};
      actions = restTemplate.exchange(
          actionsUrl + API_PROGRAMME_ACTIONS, HttpMethod.GET, null, actionSetType,
          Map.of("personId", personId, "programmeId", programmeId)).getBody();
      if (actions == null) {
        actions = Set.of();
      }
    } catch (RestClientException rce) {
      log.warn("Exception occurred when requesting programme actions endpoint for trainee {} "
          + "programme {}: {}", personId, programmeId, rce.toString());
      actions = Set.of();
    }
  }

  /**
   * Check if there are any incomplete programme actions.
   *
   * @return true if there are any incomplete actions, false otherwise.
   */
  private boolean hasIncompleteProgrammeActions() {
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      Boolean isComplete = isProgrammeActionComplete(actionType);
      if (isComplete != null && !isComplete) {
        return true; // Found an incomplete action
      }
    }
    return false; // All actions are complete
  }

  /**
   * Get whether the programme action of a given type is complete.
   *
   * @param actionType The action type to check for completion.
   *
   * @return true if the action is complete, and false if not. Null if the action type is not found.
   */
  @Nullable
  private Boolean isProgrammeActionComplete(ProgrammeActionType actionType) {
    Optional<ActionDto> actionOfType = actions.stream()
        .filter(action -> action.type().equalsIgnoreCase(actionType.toString()))
        .findFirst();
    if (actionOfType.isEmpty()) {
      return null; //no action of this type found
    } else {
      return actionOfType.get().completed() != null;
    }
  }
}
