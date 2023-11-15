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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class NotificationServiceTest {

  private static final String JOB_KEY_STRING = "job-key";
  private static final JobKey JOB_KEY = new JobKey(JOB_KEY_STRING);
  private static final String TIS_ID_KEY = "tisId";
  private static final String TIS_ID_VALUE = "tis-id";
  private static final NotificationType NOTIFICATION_TYPE
      = NotificationType.PROGRAMME_UPDATED_WEEK_8;
  private JobDetail jobDetail;

  private NotificationService service;
  private HistoryService historyService;
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  void setUp() {
    jobExecutionContext = mock(JobExecutionContext.class);
    historyService = mock(HistoryService.class);

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(TIS_ID_KEY, TIS_ID_VALUE);
    jobDataMap.put("notificationType", NOTIFICATION_TYPE.toString());
    jobDetail = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(jobDataMap)
        .build();

    service = new NotificationService(historyService);
  }

  @Test
  void shouldSetNotificationResultWhenExecuted() {
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);

    service.execute(jobExecutionContext);

    verify(jobExecutionContext).setResult(any());
  }
}
