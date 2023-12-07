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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class NotificationServiceTest {

  private static final String JOB_KEY_STRING = "job-key";
  private static final JobKey JOB_KEY = new JobKey(JOB_KEY_STRING);
  private static final String TIS_ID = "tis-id";
  private static final String PERSON_ID = "person-id";
  private static final NotificationType NOTIFICATION_TYPE
      = NotificationType.PROGRAMME_UPDATED_WEEK_8;
  private JobDetail jobDetails;

  private NotificationService service;
  private HistoryService historyService;
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  void setUp() {
    jobExecutionContext = mock(JobExecutionContext.class);
    historyService = mock(HistoryService.class);

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(TIS_ID_FIELD, TIS_ID);
    jobDataMap.put(PERSON_ID_FIELD, PERSON_ID);
    jobDataMap.put("notificationType", NOTIFICATION_TYPE.toString());
    jobDetails = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(jobDataMap)
        .build();

    service = new NotificationService(historyService);
  }

  @Test
  void shouldSetNotificationResultWhenExecuted() {
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);

    service.execute(jobExecutionContext);

    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldSaveNotificationHistoryWhenExecuted() {
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);

    service.execute(jobExecutionContext);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.forClass(History.class);

    verify(historyService).save(historyCaptor.capture());

    History savedHistory = historyCaptor.getValue();

    RecipientInfo expectedRecipient
        = new RecipientInfo(PERSON_ID, MessageType.EMAIL, NotificationService.DUMMY_EMAIL);
    TemplateInfo expectedTemplate
        = new TemplateInfo(NOTIFICATION_TYPE.getTemplateName(), "v1.0.0",
        jobDetails.getJobDataMap().getWrappedMap());
    TisReferenceInfo expectedTisReference
        = new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_ID);

    assertThat("Unexpected recipientInfo.", savedHistory.recipient(), is(expectedRecipient));
    assertThat("Unexpected templateInfo.", savedHistory.template(), is(expectedTemplate));
    assertThat("Unexpected tisReference.", savedHistory.tisReference(),
        is(expectedTisReference));
  }
}
