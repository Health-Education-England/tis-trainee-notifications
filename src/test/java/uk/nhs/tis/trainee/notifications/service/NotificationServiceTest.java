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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.NOTIFICATION_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import jakarta.mail.MessagingException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.UserAccountDetails;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class NotificationServiceTest {

  private static final String TEMPLATE_VERSION = "template-version";
  private static final String SERVICE_URL = "the-url";
  private static final String JOB_KEY_STRING = "job-key";
  private static final JobKey JOB_KEY = new JobKey(JOB_KEY_STRING);
  private static final String TIS_ID = "tis-id";
  private static final String PERSON_ID = "person-id";
  private static final String PROGRAMME_NAME = "the programme";
  private static final LocalDate START_DATE = LocalDate.now();
  private static final NotificationType NOTIFICATION_TYPE
      = NotificationType.PROGRAMME_UPDATED_WEEK_8;

  private static final String USER_EMAIL = "email@address";
  private static final String USER_FAMILY_NAME = "family-name";

  private JobDetail jobDetails;

  private NotificationService service;
  private EmailService emailService;
  private RestTemplate restTemplate;
  private JobExecutionContext jobExecutionContext;

  @BeforeEach
  void setUp() {
    jobExecutionContext = mock(JobExecutionContext.class);
    emailService = mock(EmailService.class);
    restTemplate = mock(RestTemplate.class);

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(TIS_ID_FIELD, TIS_ID);
    jobDataMap.put(PERSON_ID_FIELD, PERSON_ID);
    jobDataMap.put(PROGRAMME_NAME_FIELD, PROGRAMME_NAME);
    jobDataMap.put(NOTIFICATION_TYPE_FIELD, NOTIFICATION_TYPE.toString());
    jobDataMap.put(START_DATE_FIELD, START_DATE);
    jobDetails = newJob(NotificationService.class)
        .withIdentity(JOB_KEY)
        .usingJobData(jobDataMap)
        .build();

    service = new NotificationService(emailService, restTemplate, TEMPLATE_VERSION, SERVICE_URL);
  }

  @Test
  void shouldSetNotificationResultWhenSuccessfullyExecuted() {
    UserAccountDetails userAccountDetails = new UserAccountDetails(USER_EMAIL, USER_FAMILY_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);
    when(emailService.getRecipientAccount(PERSON_ID)).thenReturn(userAccountDetails);

    service.execute(jobExecutionContext);

    verify(jobExecutionContext).setResult(any());
  }

  @Test
  void shouldNotSetResultWhenUserDetailsCannotBeFound() {
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);

    when(emailService.getRecipientAccount(any())).thenReturn(null);
    when(restTemplate.getForObject(any(), any(), anyMap())).thenReturn(null);

    service.execute(jobExecutionContext);

    verify(jobExecutionContext, never()).setResult(any());
  }

  @Test
  void shouldNotGetAccountDetailsFromApiWhenAlreadyFoundInCognito() {
    UserAccountDetails userAccountDetails = new UserAccountDetails(USER_EMAIL, USER_FAMILY_NAME);

    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);
    when(emailService.getRecipientAccount(PERSON_ID)).thenReturn(userAccountDetails);

    service.execute(jobExecutionContext);

    verify(emailService).getRecipientAccount(any());
    verify(restTemplate, never()).getForObject(any(), any(), anyMap());
  }

  @Test
  void shouldHandleRestClientExceptions() {
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);

    when(emailService.getRecipientAccount(any())).thenThrow(new IllegalArgumentException("error"));
    when(restTemplate.getForObject(any(), any(), anyMap()))
        .thenThrow(new RestClientException("error"));

    assertDoesNotThrow(() -> service.execute(jobExecutionContext));
  }

  @Test
  void shouldHandleCognitoExceptions() {
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);

    when(emailService.getRecipientAccount(any())).thenThrow(new IllegalArgumentException("error"));

    assertDoesNotThrow(() -> service.execute(jobExecutionContext));
  }

  @Test
  void shouldRethrowEmailServiceExceptions() throws MessagingException {
    UserAccountDetails userAccountDetails = new UserAccountDetails(USER_EMAIL, USER_FAMILY_NAME);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetails);
    when(emailService.getRecipientAccount(PERSON_ID)).thenReturn(userAccountDetails);

    doThrow(new MessagingException("error"))
        .when(emailService).sendMessage(any(), any(), any(), any(), any(), any());

    assertThrows(RuntimeException.class, () -> service.execute(jobExecutionContext));
  }
}
