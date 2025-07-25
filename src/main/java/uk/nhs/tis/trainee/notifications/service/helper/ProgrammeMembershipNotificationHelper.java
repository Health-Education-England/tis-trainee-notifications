package uk.nhs.tis.trainee.notifications.service.helper;

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationSummary;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeActionType;
import uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService;

/**
 * A helper component for managing programme notifications.
 */
@Slf4j
@Component
public class ProgrammeMembershipNotificationHelper {
  public static final String TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD = "welcomeSendDate";
  public static final String TEMPLATE_NOTIFICATION_TYPE_FIELD = "notificationType";

  private static final String API_PROGRAMME_ACTIONS = "/api/action/{personId}/{programmeId}";

  private final String actionsUrl;
  private final RestTemplate restTemplate;

  ProgrammeMembershipNotificationHelper(@Value("${service.actions.url}") String actionsUrl,
      RestTemplate restTemplate) {
    this.actionsUrl = actionsUrl;
    this.restTemplate = restTemplate;
  }

  /**
   * Get the actions for a trainee and programme membership.
   *
   * @param personId    The person to get actions for.
   * @param programmeId The programme membership to get actions for.
   *
   * @return A list of actions for the person and programme membership.
   */
  private List<ActionDto> getTraineeProgrammeActions(String personId, String programmeId) {
    try {
      return restTemplate.getForObject(actionsUrl + API_PROGRAMME_ACTIONS, List.class,
          Map.of("personId", personId, "programmeId", programmeId));
    } catch (RestClientException rce) {
      log.warn("Exception occurred when requesting programme actions endpoint for trainee {} "
          + "programme {}: {}", personId, programmeId, rce.toString());
      return List.of();
    }
  }

  /**
   * Get whether the programme action of a given type is complete.
   *
   * @param actions    The list of actions to check.
   * @param actionType The action type to check for completion.
   *
   * @return true if the action is complete, and false if not. Null if the action type is not found.
   */
  private Boolean isProgrammeActionComplete(List<ActionDto> actions,
      ProgrammeActionType actionType) {
    List<ActionDto> actionsOfType = actions.stream()
        .filter(action -> action.type().equalsIgnoreCase(actionType.toString()))
        .toList();
    if (actionsOfType.isEmpty()) {
      return null; //no action of this type found
    } else {
      return actionsOfType.stream().anyMatch(action -> action.completed() != null);
    }
  }

  /**
   * Add the trainee's programme actions to the job data map for programme notifications.
   *
   * @param jobDataMap  The job data map to populate.
   * @param personId    The person to get actions for.
   * @param programmeId The programme membership to get actions for.
   */
  public void addProgrammeReminderDetailsToJobMap(JobDataMap jobDataMap,
      String personId, String programmeId) {
    List<ActionDto> actions = getTraineeProgrammeActions(personId, programmeId);
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      Boolean isComplete = isProgrammeActionComplete(actions, actionType);
      if (isComplete == null) {
        log.warn("No {} action found for trainee {} programme membership {}: "
            + "listing as 'assumed complete'.", actionType, personId, programmeId);
        isComplete = true; //assume complete if no action found
      }
      jobDataMap.put(actionType.toString(), isComplete);
    }
  }

  /**
   * Check if there are any incomplete programme actions in the job data map.
   *
   * @param jobDataMap The job data map containing programme action completion statuses.
   *
   * @return true if there are any incomplete actions, false otherwise.
   */
  public boolean hasIncompleteProgrammeActions(JobDataMap jobDataMap) {
    for (ProgrammeActionType actionType : ProgrammeActionType.values()) {
      Boolean isComplete = (Boolean) jobDataMap.get(actionType.toString());
      if (isComplete != null && !isComplete) {
        return true; // Found an incomplete action
      }
    }
    return false; // All actions are complete
  }

  /**
   * Add the welcome notification sent date to the job data map.
   *
   * @param jobDataMap          The job data map to populate.
   * @param welcomeNotification The welcome notification history to check.
   */
  public void addWelcomeSentDateToJobMap(JobDataMap jobDataMap, History welcomeNotification) {

    if (welcomeNotification == null
        || welcomeNotification.status() == null
        || !welcomeNotification.status().equals(SENT)) {
      log.warn("Welcome notification for trainee {} and programme membership {} is missing or "
              + "not SENT, so it is not added to the job data map.",
          jobDataMap.get("personId"), jobDataMap.get("tisId"));
    } else {
      jobDataMap.putIfAbsent(TEMPLATE_WELCOME_NOTIFICATION_DATE_FIELD,
          (welcomeNotification.lastRetry() != null
              ? welcomeNotification.lastRetry()
              : welcomeNotification.sentAt()));
    }
  }

  /**
   * Get a summary of the notification based on the job data map.
   *
   * @param jobDataMap          The job data map containing notification details.
   * @param unnecessaryReminder Indicates if the notification is an unnecessary reminder.
   * @return A NotificationSummary object.
   */
  public NotificationSummary getNotificationSummary(JobDataMap jobDataMap,
      boolean unnecessaryReminder) {
    String jobName = jobDataMap.getString(ProgrammeMembershipService.PROGRAMME_NAME_FIELD);
    LocalDate startDate = (LocalDate) jobDataMap.get(ProgrammeMembershipService.START_DATE_FIELD);
    History.TisReferenceInfo tisReferenceInfo = new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP,
        jobDataMap.get(ProgrammeMembershipService.TIS_ID_FIELD).toString());
    NotificationType notificationType
        = NotificationType.valueOf(jobDataMap.get(TEMPLATE_NOTIFICATION_TYPE_FIELD).toString());
    if (NotificationType.getReminderProgrammeUpdateNotificationTypes().contains(notificationType)
        && !hasIncompleteProgrammeActions(jobDataMap)) {
      unnecessaryReminder = true;
    }
    return new NotificationSummary(jobName, startDate, tisReferenceInfo, unnecessaryReminder);
  }
}
