/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import uk.nhs.tis.trainee.notifications.model.LTFT;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class LtftServiceTest {

  private static final String TIS_ID = "123";
  private static final String PERSON_ID = "abc";
  private static final String LTFT_STATUS = "the status";
  private static final String LTFT_FORM_ID = "formId12";
  private static final ZoneId timezone = ZoneId.of("Europe/London");
  private static final String LTFT_STATUS_CHANGE = "v1.2.3";

  LTFTService service;
  HistoryService historyService;
  InAppService inAppService;
  NotificationService notificationService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    inAppService = mock(InAppService.class);
    notificationService = mock(NotificationService.class);
    service = new LTFTService(historyService, inAppService, notificationService,
        timezone, LTFT_STATUS_CHANGE);
  }

  @Test
  void shouldRemoveStaleNotifications() throws SchedulerException {
    service.addNotifications(getDefaultLtft());

    for (NotificationType milestone : NotificationType.getLtftNotificationTypes()) {
      String jobId = milestone.toString() + "-" + TIS_ID;
      verify(notificationService).removeNotification(jobId);
    }
  }

  @Test
  void shouldCreateDirectLTFTNotifications() throws SchedulerException {
    service.addNotifications(getDefaultLtft());

    ArgumentCaptor<String> jobIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JobDataMap> jobDataCaptor = ArgumentCaptor.forClass(JobDataMap.class);

    verify(notificationService, times(1))
        .executeNow(jobIdCaptor.capture(), jobDataCaptor.capture());

    JobDataMap capturedJobData = jobDataCaptor.getValue();
    assertThat("Unexpected notification type.", capturedJobData.get("notificationType"), is(NotificationType.LTFT));
    assertThat("Unexpected TIS ID.", capturedJobData.get("tisId"), is(TIS_ID));
    assertThat("Unexpected person ID.", capturedJobData.get("personId"), is(PERSON_ID));
  }

  @Test
  void shouldExecuteDirectLTFTNotification() throws SchedulerException {
    LTFT ltft = getDefaultLtft();

    service.addNotifications(ltft);

    ArgumentCaptor<String> jobIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JobDataMap> jobDataCaptor = ArgumentCaptor.forClass(JobDataMap.class);

    verify(notificationService, times(1)).executeNow(jobIdCaptor.capture(), jobDataCaptor.capture());

    assertThat("Unexpected job ID.", jobIdCaptor.getValue(), is("LTFT-" + ltft.getTraineeTisId()));

    JobDataMap capturedJobData = jobDataCaptor.getValue();
    assertThat("Unexpected notification type.", capturedJobData.get("notificationType"), is(NotificationType.LTFT));
    assertThat("Unexpected TIS ID.", capturedJobData.get("tisId"), is(TIS_ID));
    assertThat("Unexpected person ID.", capturedJobData.get("personId"), is(PERSON_ID));
  }




  private LTFT getDefaultLtft() {
    LTFT ltft = new LTFT();
    ltft.setTraineeTisId(TIS_ID);
    ltft.setPersonId(PERSON_ID);
    ltft.setLtftStatus(LTFT_STATUS);
    ltft.setFormId(LTFT_FORM_ID);
    return ltft;
  }
}
