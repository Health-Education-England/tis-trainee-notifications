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

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class TemplateVersionPropertiesTest {

  @Test
  void testGetTemplateVersion() {
    Map<String, TemplateVersionsProperties.MessageTypeVersions> mockTemplateVersions = Map.of(
        "programme-created", new TemplateVersionsProperties.MessageTypeVersions("1.0.0", "1.1.0")
    );

    TemplateVersionsProperties properties = new TemplateVersionsProperties(mockTemplateVersions);

    NotificationType notificationType = NotificationType.fromTemplateName("programme-created");
    Optional<String> emailVersion = properties.getTemplateVersion(notificationType,
        MessageType.EMAIL);
    assertTrue(emailVersion.isPresent(), "Email version should be present");
    assertEquals("1.0.0", "1.0.0", emailVersion.get());

    Optional<String> inAppVersion = properties.getTemplateVersion(notificationType,
        MessageType.IN_APP);
    assertTrue(inAppVersion.isPresent(), "In-app version should be present");
    assertEquals("1.1.0", "1.1.0", inAppVersion.get());

  }
}
