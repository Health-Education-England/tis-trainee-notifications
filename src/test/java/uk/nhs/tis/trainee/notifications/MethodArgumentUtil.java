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

package uk.nhs.tis.trainee.notifications;

import static org.springframework.util.ResourceUtils.CLASSPATH_URL_PREFIX;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.util.ResourceUtils;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

/**
 * A utility for building arguments to be used with ParameterizedTest MethodSources.
 */
public class MethodArgumentUtil {

  /**
   * Get all versions of each email template for each {@link NotificationType}.
   *
   * @return A stream of NotificationType and versions, indexed in that order.
   * @throws IOException If the template files could not be traversed.
   */
  public static Stream<Arguments> getEmailTemplateTypeAndVersions() throws IOException {
    File emailRoot = ResourceUtils.getFile(CLASSPATH_URL_PREFIX + "templates/email/");
    List<Arguments> arguments = new ArrayList<>();

    for (NotificationType type : NotificationType.values()) {
      String name = type.getTemplateName();
      Path templateRoot = emailRoot.toPath().resolve(name);

      try (Stream<Path> paths = Files.walk(templateRoot)) {
        int parentNameCount = templateRoot.getNameCount();
        List<Arguments> typeArguments = paths
            .filter(Files::isRegularFile)
            .map(path -> path.subpath(parentNameCount, path.getNameCount()))
            .map(subPath -> subPath.toString().replace(".html", ""))
            .map(version -> Arguments.of(type, version))
            .toList();
        arguments.addAll(typeArguments);
      } catch (NoSuchFileException e) {
        // Not all notification types have an email template.
      }
    }

    return arguments.stream();
  }

  /**
   * Get all versions of each in-app template for each {@link NotificationType}.
   *
   * @return A stream of NotificationType and versions, indexed in that order.
   * @throws IOException If the template files could not be traversed.
   */
  public static Stream<Arguments> getInAppTemplateTypeAndVersions() throws IOException {
    File inAppRoot = ResourceUtils.getFile(CLASSPATH_URL_PREFIX + "templates/in-app/");
    List<Arguments> arguments = new ArrayList<>();

    for (NotificationType type : NotificationType.values()) {
      String name = type.getTemplateName();
      Path templateRoot = inAppRoot.toPath().resolve(name);

      try (Stream<Path> paths = Files.walk(templateRoot)) {
        int parentNameCount = templateRoot.getNameCount();
        List<Arguments> typeArguments = paths
            .filter(Files::isRegularFile)
            .map(path -> path.subpath(parentNameCount, path.getNameCount()))
            .map(subPath -> subPath.toString().replace(".html", ""))
            .map(version -> Arguments.of(type, version))
            .toList();
        arguments.addAll(typeArguments);
      } catch (NoSuchFileException e) {
        // Not all notification types have an email template.
      }
    }

    return arguments.stream();
  }

  /**
   * Get all combinations for {@link MessageType} and {@link NotificationType}.
   *
   * @return A stream of MessageType and NotificationType combinations, indexed in that order.
   */
  public static Stream<Arguments> getTemplateCombinations() {
    return Arrays.stream(MessageType.values())
        .flatMap(mt -> Arrays.stream(NotificationType.values()).map(nt -> Arguments.of(mt, nt)));
  }

  /**
   * Get all non-ProgrammeMembershipUpdate Notification Types.
   *
   * @return A stream of NotificationType.
   */
  public static Stream<Arguments> getNonProgrammeUpdateNotificationTypes() {
    return Arrays.stream(NotificationType.values())
        .filter(n -> !NotificationType.getProgrammeUpdateNotificationTypes().contains(n))
        .map(Arguments::of);
  }
}
