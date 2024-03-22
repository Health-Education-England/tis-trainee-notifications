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

package uk.nhs.tis.trainee.notifications.mapper;

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.READ;

import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A mapper to convert between notification history data types.
 */
@Mapper(componentModel = ComponentModel.SPRING)
public interface HistoryMapper {

  String WELCOME_SUBJECT_TEXT = "Welcome to TIS Self-Service";

  /**
   * Convert history entities to history DTOs.
   *
   * @param entities The history entities to convert.
   * @return The converted history DTOs.
   */
  List<HistoryDto> toDtos(List<History> entities);

  /**
   * Convert a history entity to a history DTO.
   *
   * @param entity The history entity to convert.
   * @return The converted history DTOs.
   */
  @Mapping(target = "id", expression = "java(entity.id().toString())")
  @Mapping(target = "type", source = "recipient.type")
  @Mapping(target = "tisReference")
  @Mapping(target = "subject", source = "type")
  @Mapping(target = "subjectText",
      expression = "java(getSubjectText(entity.type(), entity.recipient().type()))")
  @Mapping(target = "contact", source = "recipient.contact")
  @Mapping(target = "sentAt")
  @Mapping(target = "readAt")
  @Mapping(target = "status")
  @Mapping(target = "statusDetail")
  HistoryDto toDto(History entity);

  /**
   * Update the status of the given history entity.
   *
   * @param entity The history to update the status of.
   * @param status The new status.
   * @param detail Any relevant status detail.
   * @return The updated history entity.
   */
  @Mapping(target = "status", source = "status")
  @Mapping(target = "statusDetail", source = "detail")
  @Mapping(target = "readAt", expression = "java(calculateReadAt(entity, status))")
  History updateStatus(History entity, NotificationStatus status, String detail);

  /**
   * Calculate the readAt field based on the entity and status.
   *
   * @param entity The entity to calculate the readAt for.
   * @param status The notification status.
   * @return The readAt value to use for the entity.
   */
  default Instant calculateReadAt(History entity, NotificationStatus status) {
    return entity.readAt() == null && status.equals(READ) ? Instant.now() : entity.readAt();
  }

  /**
   * Get the subject text from the NotificationType for in-app messages. Note this is hardcoded, not
   * extracted from the applicable template subject fragment.
   *
   * @param notificationType The notification type.
   * @param messageType      The message type.
   * @return The corresponding subject text.
   */
  default String getSubjectText(NotificationType notificationType, MessageType messageType) {
    if (messageType != MessageType.IN_APP) {
      return ""; //these are ignored
    }
    return switch (notificationType) {
      case WELCOME -> WELCOME_SUBJECT_TEXT;
      default -> "";
    };
  }
}
