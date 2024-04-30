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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PLACEMENT_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.PLACEMENT_SITE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.PLACEMENT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.TIS_ID_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.Placement;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class PlacementServiceTest {

  private static final String SERVICE_URL =
      "http://localhost:0000/reference/api/local-office-contact/{owner}";
  private static final String IN_POST = "In Post";
  private static final String In_POST_ACTING_UP = "In Post - Acting up";
  private static final String IN_POST_EXTENSION = "In Post - Extension";
  private static final String EXCLUDED_PLACEMENT_TYPE = "RANDOM TYPE";

  private static final String TIS_ID = "123";
  private static final String OWNER = "North West";
  private static final String OWNER_CONTACT = "north.west@nhs.net";
  private static final String PERSON_ID = "abc";
  private static final String SITE = "site known as";
  private static final LocalDate START_DATE = LocalDate.now().plusYears(1);
  //set a year in the future to allow all notifications to be scheduled
  private static final String PLACEMENT_INFO_VERSION = "v1.2.3";
  HistoryService historyService;
  PlacementService service;
  NotificationService notificationService;
  InAppService inAppService;
  RestTemplate restTemplate;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    notificationService = mock(NotificationService.class);
    inAppService = mock(InAppService.class);
    restTemplate = mock(RestTemplate.class);
    service = new PlacementService(historyService, notificationService, inAppService,
        ZoneId.of("Europe/London"), PLACEMENT_INFO_VERSION);
  }

  @ParameterizedTest
  @ValueSource(strings = {IN_POST, In_POST_ACTING_UP, IN_POST_EXTENSION})
  void shouldNotExcludePlacementWithInPostPlacementType(String placementType) {
    Placement placement = new Placement();
    placement.setPlacementType(placementType);
    placement.setStartDate(START_DATE);

    boolean isExcluded = service.isExcluded(placement);

    assertThat("Unexpected excluded value.", isExcluded, is(false));
  }

  @Test
  void shouldExcludePlacementWithNoStartDate() {
    Placement placement = new Placement();
    placement.setPlacementType(IN_POST);

    boolean isExcluded = service.isExcluded(placement);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldExcludePlacementThatIsNotFuture() {
    Placement placement = new Placement();
    placement.setPlacementType(IN_POST);
    placement.setStartDate(LocalDate.now().minusYears(1));

    boolean isExcluded = service.isExcluded(placement);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldExcludePlacementNotWithInPostPlacementType() {
    Placement placement = new Placement();
    placement.setPlacementType(EXCLUDED_PLACEMENT_TYPE);
    placement.setStartDate(START_DATE);

    boolean isExcluded = service.isExcluded(placement);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldExcludePlacementWithoutPlacementType() {
    Placement placement = new Placement();
    placement.setStartDate(START_DATE);

    boolean isExcluded = service.isExcluded(placement);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldRemoveStaleNotifications() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<Map<String, String>> localOfficeContacts = new ArrayList<>();
    localOfficeContacts.add(Map.of("contact", OWNER_CONTACT));
    when(restTemplate.getForObject(SERVICE_URL, List.class,
        Map.of(TEMPLATE_OWNER_FIELD, "North West"))).thenReturn(localOfficeContacts);

    service.addNotifications(placement);

    String jobId = PLACEMENT_UPDATED_WEEK_12 + "-" + TIS_ID;
    verify(notificationService).removeNotification(jobId);
  }

  @Test
  void shouldNotAddNotificationsIfExcluded() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(EXCLUDED_PLACEMENT_TYPE);

    service.addNotifications(placement);

    verify(notificationService, never()).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldNotAddNotificationsIfNoStartDate() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    service.addNotifications(placement);

    verify(notificationService, never()).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldNotAddNotificationsIfPastStartDate() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);
    placement.setStartDate(LocalDate.MIN);

    service.addNotifications(placement);

    verify(notificationService, never()).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldAddNotificationsIfNotExcluded() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);
    placement.setSite(SITE);

    NotificationType milestone = PLACEMENT_UPDATED_WEEK_12;
    LocalDate expectedDate = START_DATE
        .minusDays(service.getNotificationDaysBeforeStart(milestone));
    Date expectedWhen = Date.from(expectedDate
        .atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());

    when(notificationService
        .getScheduleDate(START_DATE, service.getNotificationDaysBeforeStart(milestone)))
        .thenReturn(expectedWhen);
    service.addNotifications(placement);

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<JobDataMap> jobDataMapCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.captor();
    verify(notificationService).scheduleNotification(
        stringCaptor.capture(),
        jobDataMapCaptor.capture(),
        dateCaptor.capture()
    );

    //verify the details of the last notification added
    String jobId = stringCaptor.getValue();
    String expectedJobId = milestone + "-" + TIS_ID;
    assertThat("Unexpected job id.", jobId, is(expectedJobId));

    JobDataMap jobDataMap = jobDataMapCaptor.getValue();
    assertThat("Unexpected tisId.", jobDataMap.get(TIS_ID_FIELD), is(TIS_ID));
    assertThat("Unexpected personId.", jobDataMap.get(PERSON_ID_FIELD), is(PERSON_ID));
    assertThat("Unexpected start date.", jobDataMap.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected placement type.", jobDataMap.get(PLACEMENT_TYPE_FIELD),
        is(IN_POST));
    assertThat("Unexpected placement owner.", jobDataMap.get(TEMPLATE_OWNER_FIELD),
        is(OWNER));
    assertThat("Unexpected placement site.", jobDataMap.get(PLACEMENT_SITE_FIELD),
        is(SITE));

    Date when = dateCaptor.getValue();
    assertThat("Unexpected start time", when, is(expectedWhen));
  }

  @Test
  void shouldIgnoreNonPlacementSentNotifications() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PROGRAMME_MEMBERSHIP, TIS_ID),
        MessageType.EMAIL,
        PLACEMENT_UPDATED_WEEK_12, null, //to avoid masking the test condition
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(placement);

    verify(notificationService)
        .scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldIgnoreOtherTypesOfPlacementUpdateSentNotifications() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PLACEMENT, "another id"),
        MessageType.EMAIL,
        NotificationType.PROGRAMME_UPDATED_WEEK_8, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(placement);

    verify(notificationService).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldIgnoreDifferentPlacementUpdateSentNotifications() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PLACEMENT, "another id"),
        MessageType.EMAIL,
        PLACEMENT_UPDATED_WEEK_12, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(placement);

    verify(notificationService).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldNotResendPlacementNotification() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_ID),
        MessageType.EMAIL,
        PLACEMENT_UPDATED_WEEK_12, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(placement);

    verify(notificationService, never()).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldScheduleMissedNotification() throws SchedulerException {
    LocalDate dateToday = LocalDate.now();

    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(dateToday);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    Date expectedLaterThan = Date.from(Instant.now());
    when(notificationService
        .getScheduleDate(dateToday, 84))
        .thenReturn(expectedLaterThan);
    service.addNotifications(placement);

    //the zero-day notification should be scheduled, but no other missed notifications
    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.captor();
    verify(notificationService).scheduleNotification(
        any(),
        any(),
        dateCaptor.capture()
    );

    Date when = dateCaptor.getValue();
    assertThat("Unexpected start time", when, is(expectedLaterThan));
  }

  @Test
  void shouldNotFailOnHistoryWithoutTisReferenceInfo()
      throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        null,
        MessageType.EMAIL,
        PLACEMENT_UPDATED_WEEK_12, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(placement);

    assertDoesNotThrow(() -> service.addNotifications(placement),
        "Unexpected addNotifications failure");
  }

  @Test
  void shouldIgnoreHistoryWithoutTisReferenceInfo() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        null,
        MessageType.EMAIL,
        PLACEMENT_UPDATED_WEEK_12, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(placement);

    verify(notificationService).scheduleNotification(any(), any(), any());
  }

  @Test
  void shouldRethrowSchedulerExceptions() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);
    placement.setPersonId(PERSON_ID);
    placement.setStartDate(START_DATE);
    placement.setOwner(OWNER);
    placement.setPlacementType(IN_POST);

    doThrow(new SchedulerException())
        .when(notificationService).scheduleNotification(any(), any(), any());

    assertThrows(SchedulerException.class,
        () -> service.addNotifications(placement));
  }

  @Test
  void shouldDeleteNotifications() throws SchedulerException {
    Placement placement = new Placement();
    placement.setTisId(TIS_ID);

    service.deleteNotifications(placement);

    String jobId = PLACEMENT_UPDATED_WEEK_12 + "-" + TIS_ID;
    verify(notificationService).removeNotification(jobId);
  }

  @Test
  void shouldHandleUnknownNotificationTypesForNotificationDays() {
    assertThat("Unexpected days before start.",
        service.getNotificationDaysBeforeStart(NotificationType.COJ_CONFIRMATION), nullValue());
  }
}
