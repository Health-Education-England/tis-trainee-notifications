package uk.nhs.tis.trainee.notifications.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.mongodb.core.mapping.Field;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

/**
 * A DTO for receiving Action data.
 *
 * @param id               The ID of the action.
 * @param type             The type of action.
 * @param traineeId        The ID of the trainee who the action is for.
 * @param tisReferenceInfo The TIS core object associated with the action.
 * @param availableFrom    When the action is available to complete.
 * @param dueBy            When the action is due to be completed by.
 * @param completed        When the action was completed, null if not completed.
 */
public record ActionDto(
    String id,
    String type,
    String traineeId,
    TisReferenceInfo tisReferenceInfo,
    LocalDate availableFrom,
    LocalDate dueBy,
    Instant completed) {

  /**
   * A representation of the TIS record that prompted the action.
   *
   * @param id   The TIS ID of the entity that prompted the action.
   * @param type The TIS reference type for the entity that prompted the action.
   */
  public record TisReferenceInfo(@Field("id") String id, TisReferenceType type) {

  }
}
