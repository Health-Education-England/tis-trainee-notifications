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

package uk.nhs.tis.trainee.notifications.config;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * Configuration properties for managing template versions associated with different
 * notification types.
 */
@ConfigurationProperties("application")
public final class TemplateVersionsProperties {

  private final Map<NotificationType, MessageTypeVersions> templateVersions;

  /**
   * Constructs an instance of TemplateVersionsProperties.
   *
   * @param templateVersions A map where the key is a template name and the value is
   *                         MessageTypeVersions specifying template versions.
   *                         The template names are converted to
   *                         NotificationType.
   */
  @ConstructorBinding
  public TemplateVersionsProperties(Map<String, MessageTypeVersions> templateVersions) {
    this.templateVersions = templateVersions.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(
            e -> NotificationType.fromTemplateName(e.getKey()),
            Entry::getValue)
        );
  }

  /**
   * Retrieves the template version for a given notification type or message type.
   *
   * @param notificationType The type of notification for which to retrieve the template version.
   * @param messageType The type of message (e.g., email, in-app) for which to retrieve the
   *                    template version.
   * @return An Optional containing the template version for email or inapp notification
   */
  public Optional<String> getTemplateVersion(NotificationType notificationType,
      MessageType messageType) {
    MessageTypeVersions versionProperties = templateVersions.get(notificationType);

    String templateVersion = switch (messageType) {
      case EMAIL -> versionProperties.email();
      case IN_APP -> versionProperties.inApp();
    };

    return Optional.ofNullable(templateVersion);
  }

  /**
   * A record that holds template versions for different message types.
   *
   * @param email The version of the email template.
   * @param inApp The version of the in-app template.
   */
  public record MessageTypeVersions(String email, String inApp) {

  }
}
