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

package uk.nhs.tis.trainee.notifications.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A service providing consistent template functionality.
 */
@Service
public class TemplateService {

  private final TemplateEngine templateEngine;
  private final String timezone;

  public TemplateService(TemplateEngine templateEngine,
      @Value("${application.timezone}") String timezone) {
    this.templateEngine = templateEngine;
    this.timezone = timezone;
  }

  /**
   * Build a template context from the given variables, some type conversion may occur.
   *
   * @param variables The variables to insert in to the context.
   * @return The built template context.
   */
  public Context buildContext(Map<String, Object> variables) {
    // Convert UTC timestamps to the Local Office timezone.
    Map<String, Object> localizedTemplateVariables = localizeTimestamps(variables);
    Context context = new Context();
    context.setVariables(localizedTemplateVariables);

    return context;
  }

  /**
   * Localize any compatible data types.
   *
   * @param variables The template variables to localize.
   * @return The variables with any timestamps localized.
   */
  private Map<String, Object> localizeTimestamps(Map<String, Object> variables) {
    Map<String, Object> localizedTemplateVariables = new HashMap<>(variables);

    for (Entry<String, Object> entry : localizedTemplateVariables.entrySet()) {
      Object value = entry.getValue();
      ZonedDateTime localized = null;

      if (value instanceof Instant instant) {
        localized = ZonedDateTime.ofInstant(instant, ZoneId.of(timezone));
      } else if (value instanceof Date date) {
        localized = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of(timezone));
      }

      if (localized != null) {
        entry.setValue(localized);
      }
    }

    return localizedTemplateVariables;
  }

  /**
   * Get the template for the given message type, notification type and version.
   *
   * @param messageType      The message type.
   * @param notificationType The notification type.
   * @param version          The template version.
   * @return The full template name.
   */
  public String getTemplatePath(MessageType messageType, NotificationType notificationType,
      String version) {
    return getTemplatePath(messageType, notificationType.getTemplateName(), version);
  }

  /**
   * Get the template for the given message type, notification type and version.
   *
   * @param messageType      The message type.
   * @param notificationType The notification type.
   * @param version          The template version.
   * @return The full template name.
   */
  public String getTemplatePath(MessageType messageType, String notificationType, String version) {
    return messageType.getTemplatePath() + "/" + notificationType + "/" + version;
  }

  /**
   * Process the given template, applying the selector and variables provided.
   *
   * @param template  The name of the template to use.
   * @param selectors The selectors within the template.
   * @param variables The variables for placeholder replacement.
   * @return The processed template.
   */
  public String process(String template, Set<String> selectors, Map<String, Object> variables) {
    return process(template, selectors, buildContext(variables));
  }

  /**
   * Process the given template, applying the selector and variables provided.
   *
   * @param template  The name of the template to use.
   * @param selectors The selectors within the template.
   * @param context   The context to apply to the template.
   * @return The processed template.
   */
  public String process(String template, Set<String> selectors, Context context) {
    return templateEngine.process(template, selectors, context);
  }
}
