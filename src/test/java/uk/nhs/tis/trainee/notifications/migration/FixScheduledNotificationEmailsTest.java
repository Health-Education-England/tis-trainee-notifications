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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.service.HistoryService;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

class FixScheduledNotificationEmailsTest {

  // IDs must match the entries in the test resource file:
  // src/test/resources/db/migration/fix-scheduled-notification-emails-trainee-ids.txt
  private static final String TRAINEE_ID_1 = "trainee-id-aaa";
  private static final String TRAINEE_ID_2 = "trainee-id-bbb";
  private static final String TRAINEE_ID_3 = "trainee-id-ccc";
  private static final String NEW_EMAIL = "new@example.com";

  private HistoryService historyService;
  private NotificationService notificationService;
  private FixScheduledNotificationEmails migrator;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    notificationService = mock(NotificationService.class);
    migrator = new FixScheduledNotificationEmails(historyService, notificationService);
  }

  @Test
  void shouldUpdateEmailWhenTraineeDetailsFound() {
    UserDetails details = new UserDetails(true, NEW_EMAIL, null, "Smith", "John", null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(details);

    migrator.migrate();

    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_1, NEW_EMAIL);
  }

  @Test
  void shouldSkipWhenTraineeDetailsNotFound() {
    // TRAINEE_ID_1 returns null; TRAINEE_ID_2 has valid details to prove others still process.
    UserDetails validDetails = new UserDetails(true, NEW_EMAIL, null, null, null, null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_2)).thenReturn(validDetails);

    migrator.migrate();

    verify(historyService, never()).updateScheduledNotificationEmail(eq(TRAINEE_ID_1), any());
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_2, NEW_EMAIL);
  }

  @Test
  void shouldSkipWhenTraineeEmailIsNull() {
    // TRAINEE_ID_1 has null email; TRAINEE_ID_2 has valid details to prove others still process.
    UserDetails nullEmailDetails = new UserDetails(true, null, null, "Smith", "John", null);
    UserDetails validDetails = new UserDetails(true, NEW_EMAIL, null, null, null, null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(nullEmailDetails);
    when(notificationService.getTraineeDetails(TRAINEE_ID_2)).thenReturn(validDetails);

    migrator.migrate();

    verify(historyService, never()).updateScheduledNotificationEmail(eq(TRAINEE_ID_1), any());
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_2, NEW_EMAIL);
  }

  @Test
  void shouldSkipWhenTraineeEmailIsBlank() {
    // TRAINEE_ID_1 has blank email; TRAINEE_ID_2 has valid details to prove others still process.
    UserDetails blankEmailDetails = new UserDetails(true, "  ", null, "Smith", "John", null);
    UserDetails validDetails = new UserDetails(true, NEW_EMAIL, null, null, null, null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(blankEmailDetails);
    when(notificationService.getTraineeDetails(TRAINEE_ID_2)).thenReturn(validDetails);

    migrator.migrate();

    verify(historyService, never()).updateScheduledNotificationEmail(eq(TRAINEE_ID_1), any());
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_2, NEW_EMAIL);
  }

  @Test
  void shouldProcessAllTraineesInResourceFile() {
    UserDetails details1 = new UserDetails(true, "email1@example.com", null, null, null, null);
    UserDetails details2 = new UserDetails(true, "email2@example.com", null, null, null, null);
    UserDetails details3 = new UserDetails(true, "email3@example.com", null, null, null, null);
    when(notificationService.getTraineeDetails(TRAINEE_ID_1)).thenReturn(details1);
    when(notificationService.getTraineeDetails(TRAINEE_ID_2)).thenReturn(details2);
    when(notificationService.getTraineeDetails(TRAINEE_ID_3)).thenReturn(details3);

    migrator.migrate();

    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_1, "email1@example.com");
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_2, "email2@example.com");
    verify(historyService).updateScheduledNotificationEmail(TRAINEE_ID_3, "email3@example.com");
  }

  @Test
  void shouldDoNothingWhenResourceStreamIsNull() {
    FixScheduledNotificationEmails migrator = new FixScheduledNotificationEmails(
        historyService, notificationService) {
      @Override
      InputStream getTraineeIdsStream() {
        return null;
      }
    };

    migrator.migrate();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldNotRollback() {
    migrator.rollback();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldLoadTraineeIdsFromResourceFileIgnoringCommentsAndBlanks() {
    List<String> ids = migrator.loadTraineeIds();

    assertThat("Unexpected trainee IDs loaded.",
        ids, contains(TRAINEE_ID_1, TRAINEE_ID_2, TRAINEE_ID_3));
  }

  @Test
  void shouldReturnEmptyListWhenResourceStreamIsNull() {
    FixScheduledNotificationEmails migrator = new FixScheduledNotificationEmails(
        historyService, notificationService) {
      @Override
      InputStream getTraineeIdsStream() {
        return null;
      }
    };

    List<String> ids = migrator.loadTraineeIds();

    assertThat("Expected empty list when resource stream is null.", ids, is(empty()));
  }

  @Test
  void shouldReturnEmptyListWhenResourceStreamThrowsIoException() {
    InputStream faultyStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("Simulated read failure");
      }
    };

    FixScheduledNotificationEmails migrator = new FixScheduledNotificationEmails(
        historyService, notificationService) {
      @Override
      InputStream getTraineeIdsStream() {
        return faultyStream;
      }
    };

    List<String> ids = migrator.loadTraineeIds();

    assertThat("Expected empty list when resource stream throws IOException.", ids, is(empty()));
  }
}
