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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class FixScheduledNotificationEmailsTest {

  private static final String TRAINEE_ID_1 = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_2 = UUID.randomUUID().toString();
  private static final String NEW_EMAIL = "new@example.com";

  private HistoryService historyService;
  private NotificationService notificationService;

  /**
   * A subclass that overrides trainee ID loading so tests are not tied to file I/O.
   */
  private static class TestableMigrator extends FixScheduledNotificationEmails {

    private final List<String> traineeIds;

    TestableMigrator(HistoryService historyService, NotificationService notificationService,
        List<String> traineeIds) {
      super(historyService, notificationService);
      this.traineeIds = traineeIds;
    }

    @Override
    List<String> loadTraineeIds() {
      return traineeIds;
    }
  }

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    notificationService = mock(NotificationService.class);
  }

  @Test
  void shouldUpdateEmailWhenTraineeDetailsFound() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService, List.of(TRAINEE_ID_1));

    UserDetails details = new UserDetails(true, NEW_EMAIL, null, "Smith", "John", null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(details);

    migrator.migrate();

    verify(notificationService).getTraineeDetails(TRAINEE_ID_1);
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_1, NEW_EMAIL);
  }

  @Test
  void shouldSkipWhenTraineeDetailsNotFound() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService, List.of(TRAINEE_ID_1));

    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(null);

    migrator.migrate();

    verify(notificationService).getTraineeDetails(TRAINEE_ID_1);
    verify(historyService, never()).updateScheduledNotificationEmail(any(), any());
  }

  @Test
  void shouldSkipWhenTraineeEmailIsNull() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService, List.of(TRAINEE_ID_1));

    UserDetails details = new UserDetails(true, null, null, "Smith", "John", null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(details);

    migrator.migrate();

    verify(historyService, never()).updateScheduledNotificationEmail(any(), any());
  }

  @Test
  void shouldSkipWhenTraineeEmailIsBlank() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService, List.of(TRAINEE_ID_1));

    UserDetails details = new UserDetails(true, "  ", null, "Smith", "John", null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(details);

    migrator.migrate();

    verify(historyService, never()).updateScheduledNotificationEmail(any(), any());
  }

  @Test
  void shouldProcessMultipleTrainees() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService,
            List.of(TRAINEE_ID_1, TRAINEE_ID_2));

    UserDetails details1 = new UserDetails(true, "email1@example.com", null, null, null, null);
    UserDetails details2 = new UserDetails(true, "email2@example.com", null, null, null, null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(details1);
    when(notificationService.getTraineeDetails(TRAINEE_ID_2)).thenReturn(details2);

    migrator.migrate();

    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_1, "email1@example.com");
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_2, "email2@example.com");
  }

  @Test
  void shouldDoNothingWhenTraineeIdListIsEmpty() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService, List.of());

    migrator.migrate();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldNotRollback() {
    FixScheduledNotificationEmails migrator =
        new TestableMigrator(historyService, notificationService, List.of());
    Mockito.clearInvocations(historyService, notificationService);

    migrator.rollback();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldLoadTraineeIdsFromResourceFileIgnoringCommentsAndBlanks() {
    FixScheduledNotificationEmails migrator =
        new FixScheduledNotificationEmails(historyService, notificationService);

    List<String> ids = migrator.loadTraineeIds();

    assertThat("Unexpected trainee IDs loaded.",
        ids, contains("trainee-id-aaa", "trainee-id-bbb", "trainee-id-ccc"));
  }

  @Test
  void shouldReturnEmptyListWhenResourceFileNotFound() {
    FixScheduledNotificationEmails migrator = new FixScheduledNotificationEmails(
        historyService, notificationService) {
      @Override
      List<String> loadTraineeIds() {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/does-not-exist.txt");
        return stream != null ? List.of("should-not-reach") : List.of();
      }
    };

    List<String> ids = migrator.loadTraineeIds();

    assertThat("Expected empty list when resource is missing.", ids, is(empty()));
  }
}

