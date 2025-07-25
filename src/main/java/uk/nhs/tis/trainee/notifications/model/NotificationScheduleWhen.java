package uk.nhs.tis.trainee.notifications.model;

import java.util.Date;

/**
 * A record representing the scheduling of a notification.
 *
 * @param unnecessaryReminder Indicates if the notification is an unnecessary reminder.
 * @param scheduleWhen        The date when the notification is scheduled to be sent.
 */
public record NotificationScheduleWhen(boolean unnecessaryReminder, Date scheduleWhen) {
}
