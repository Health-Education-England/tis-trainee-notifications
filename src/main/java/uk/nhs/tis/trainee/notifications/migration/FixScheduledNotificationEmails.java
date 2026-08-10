/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * Fix scheduled email notifications that refer to outdated email addresses.
 *
 * <p>A known set of 1,094 trainee IDs (loaded from a resource file) had their scheduled
 * notification email addresses fall out of sync before the contact-details update flow was fixed.
 * For each trainee, the current email is fetched live from the trainee-profile service and used
 * to correct any mismatched scheduled notifications. The resource file contains only IDs — not
 * snapshot email values — so the migration is safe to re-run in any environment at any time.
 */
@Slf4j
@ChangeUnit(id = "fixScheduledNotificationEmails", order = "13")
public class FixScheduledNotificationEmails {

  static final String TRAINEE_IDS_RESOURCE =
      "db/migration/fix-scheduled-notification-emails-trainee-ids.txt";

  private final HistoryService historyService;
  private final NotificationService notificationService;

  /**
   * Construct the migrator.
   *
   * @param historyService      The history service for updating notification emails.
   * @param notificationService The notification service for retrieving trainee details.
   */
  public FixScheduledNotificationEmails(HistoryService historyService,
      NotificationService notificationService) {
    this.historyService = historyService;
    this.notificationService = notificationService;
  }

  /**
   * Fix scheduled email notifications with outdated email addresses.
   */
  @Execution
  public void migrate() {
    List<String> traineeIds = loadTraineeIds();
    log.info("Loaded {} trainee ID(s) to process.", traineeIds.size());

    int updated = 0;
    int skipped = 0;

    for (String traineeId : traineeIds) {
      UserDetails traineeDetails = notificationService.getTraineeDetails(traineeId);

      if (traineeDetails == null || traineeDetails.email() == null
          || traineeDetails.email().isBlank()) {
        log.warn("Could not retrieve current email for trainee {}, skipping.", traineeId);
        skipped++;
        continue;
      }

      historyService.updateScheduledNotificationEmail(traineeId, traineeDetails.email());
      updated++;
    }

    log.info("Processed {} trainee(s): {} processed, {} skipped.",
        traineeIds.size(), updated, skipped);
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'fixScheduledNotificationEmails' migration.");
  }

  /**
   * Load the list of affected trainee IDs from the classpath resource file.
   *
   * @return The list of trainee IDs; empty if the resource cannot be read.
   */
  List<String> loadTraineeIds() {
    InputStream stream = getTraineeIdsStream();

    if (stream == null) {
      log.error("Trainee ID resource file not found: {}", TRAINEE_IDS_RESOURCE);
      return List.of();
    }

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList();
    } catch (IOException | UncheckedIOException e) {
      log.error("Failed to read trainee ID resource file: {}", TRAINEE_IDS_RESOURCE, e);
      return List.of();
    }
  }

  /**
   * Open an input stream for the trainee IDs resource file.
   *
   * @return The stream, or {@code null} if the resource is not found on the classpath.
   */
  InputStream getTraineeIdsStream() {
    return getClass().getClassLoader().getResourceAsStream(TRAINEE_IDS_RESOURCE);
  }
}

