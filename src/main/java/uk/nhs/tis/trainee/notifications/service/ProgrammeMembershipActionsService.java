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

import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
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

  ProgrammeMembershipActionsService(@Value("${service.actions.url}") String actionsUrl,
      RestTemplate restTemplate, HistoryService historyService) {
    this.historyService = historyService;
    this.actionsUrl = actionsUrl;
    this.restTemplate = restTemplate;
  }

  /**
   * Get the actions for a trainee and programme membership.
   *
   * @param personId    The ID of the trainee.
   * @param programmeId The ID of the programme membership.
   * @return the set of actions for the trainee and programme membership, or an empty set if none
   * found or an error occurs.
   */
  public Set<ActionDto> getActions(String personId, String programmeId) {
    Set<ActionDto> actions;
    try {
      ParameterizedTypeReference<Set<ActionDto>> actionSetType
          = new ParameterizedTypeReference<>() {};
      actions = restTemplate.exchange(
          actionsUrl + API_PROGRAMME_ACTIONS, HttpMethod.GET, null, actionSetType,
          Map.of("personId", personId, "programmeId", programmeId)).getBody();
      if (actions == null) {
        return Set.of();
      }
    } catch (RestClientException rce) {
      log.warn("Exception occurred when requesting programme actions endpoint for trainee {} "
          + "programme {}: {}", personId, programmeId, rce.toString());
      return Set.of();
    }
    return actions;
  }

  /**
   * Add programme action details to the job data map.
   *
   * @param jobDataMap The job data map to populate with programme actions.
   * @param actions    The set of actions associated with the person and programme.
   */
  public void addActionsToJobMap(@Nonnull JobDataMap jobDataMap, Set<ActionDto> actions) {
    NotificationType notificationType = NotificationType.valueOf(
        jobDataMap.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());

    if (NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(notificationType)) {
      addProgrammeReminderDetailsToJobMap(jobDataMap, actions);
    }
  }

  /**
   * Get a summary of the programme notification.
   *
   * @param jobDataMap The job data map containing programme notification details.
   * @return A NotificationSummary object.
   */
  public NotificationSummary getNotificationSummary(@Nonnull JobDataMap jobDataMap) {
    boolean unnecessaryReminder = false;
    String programmeId = jobDataMap.getString(TIS_ID_FIELD);
    String jobName = jobDataMap.getString(PROGRAMME_NAME_FIELD);
    LocalDate startDate = (LocalDate) jobDataMap.get(START_DATE_FIELD);
    NotificationType notificationType
        = NotificationType.valueOf(jobDataMap.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());
    History.TisReferenceInfo tisReferenceInfo = new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP,
        programmeId);
    if (NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(notificationType)
        && !hasIncompleteProgrammeActions(jobDataMap)) {
      unnecessaryReminder = true;
    }
    return new NotificationSummary(jobName, startDate, tisReferenceInfo, unnecessaryReminder);
  }

  /**
   * Add the trainee's programme reminders to the job data map for programme notifications.
   *
   * @param jobDataMap The job data map to populate.
   */
  private void addProgrammeReminderDetailsToJobMap(JobDataMap jobDataMap, Set<ActionDto> actions) {
    String programmeId = jobDataMap.getString(TIS_ID_FIELD);
    String personId = jobDataMap.getString(PERSON_ID_FIELD);
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
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      Boolean isComplete = isProgrammeActionComplete(actionType, actions);
      if (isComplete == null) {
        log.warn("No {} action found for trainee {} programme membership {}: "
            + "listing as 'assumed complete'.", actionType, personId, programmeId);
        isComplete = true; //assume complete if no action found
      }
      jobDataMap.put(actionType.toString(), isComplete);
    }
  }

  /**
   * Check if there are any incomplete programme actions in the job data map
   *
   * @param jobDataMap The job data map to check.
   * @return true if there are any incomplete actions, false otherwise.
   */
  private boolean hasIncompleteProgrammeActions(@Nonnull JobDataMap jobDataMap) {
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      if (!jobDataMap.getBoolean(actionType.toString())) {
        return true; // Found an incomplete action
      }
    }
    return false; // All actions are complete
  }

  /**
   * Get whether the programme action of a given type is complete.
   *
   * @param actionType The action type to check for completion.
   * @param actions    The set of actions to check within.
   *
   * @return true if the action is complete, and false if not. Null if the action type is not found.
   */
  @Nullable
  private Boolean isProgrammeActionComplete(ProgrammeActionType actionType,
      Set<ActionDto> actions) {
    if (actions == null) {
      return null; //no actions found
    }
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
