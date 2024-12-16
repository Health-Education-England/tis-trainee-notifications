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
 * TODO: javadocs
 */
@ConfigurationProperties("application")
public final class TemplateVersionsProperties {

  private final Map<NotificationType, MessageTypeVersions> templateVersions;

  /**
   * TODO: javadocs
   *
   * @param templateVersions
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
   * TODO: javadocs
   *
   * @param notificationType
   * @param messageType
   * @return
   */
  public Optional<String> getTemplateVersion(NotificationType notificationType,
      MessageType messageType) {
    MessageTypeVersions versionProperties = templateVersions.get(notificationType);

    return Optional.of(switch (messageType) {
      case EMAIL -> versionProperties.email();
      case IN_APP -> versionProperties.inApp();
    });
  }

  /**
   * TODO: javadocs
   *
   * @param email
   * @param inApp
   */
  public record MessageTypeVersions(String email, String inApp) {

  }
}
