package uk.nhs.tis.trainee.notifications.model;

import java.time.LocalDate;

/**
 * A record representing a summary of a notification.
 *
 * @param jobName             The name of the job associated with the notification.
 * @param startDate           The start date of the notification.
 * @param tisReferenceInfo    The TIS reference information associated with the notification.
 * @param unnecessaryReminder Indicates if the notification is an unnecessary reminder.
 */
public record NotificationSummary(String jobName, LocalDate startDate,
                                  History.TisReferenceInfo tisReferenceInfo,
                                  boolean unnecessaryReminder) {

    /**
     * Creates a NotificationSummary with unnecessaryReminder set to false.
     *
     * @param jobName         The name of the job.
     * @param startDate       The start date of the notification.
     * @param tisReferenceInfo The TIS reference information.
     */
    public NotificationSummary(String jobName, LocalDate startDate,
        History.TisReferenceInfo tisReferenceInfo) {
      this(jobName, startDate, tisReferenceInfo, false);
    }

  /**
   *  Default constructor for NotificationSummary.
   */
  public NotificationSummary() {
      this(null, null, null, false);
    }
}
