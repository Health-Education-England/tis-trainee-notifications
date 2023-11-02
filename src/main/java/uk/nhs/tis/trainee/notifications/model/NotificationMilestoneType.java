package uk.nhs.tis.trainee.notifications.model;

/**
 * An enum for notification milestones.
 */
public enum NotificationMilestoneType {
  WEEK_8(56),
  WEEK_4(28),
  WEEK_0(0);

  private final int daysBeforeStart;

  /**
   * Create an enumeration of a possible notification milestone type.
   *
   * @param daysBeforeStart The number of days before programme start date for this milestone.
   */
  NotificationMilestoneType(int daysBeforeStart) {
    this.daysBeforeStart = daysBeforeStart;
  }

  /**
   * Get the days before the start date for this notification milestone.
   *
   * @return The number of days before the start.
   */
  public int getDaysBeforeStart() {
    return daysBeforeStart;
  }

  /**
   * Get a string version of the notification milestone name.
   *
   * @return the string.
   */
  @Override
  public String toString() {
    return name().toLowerCase().replace("_", "-");
  }
}
