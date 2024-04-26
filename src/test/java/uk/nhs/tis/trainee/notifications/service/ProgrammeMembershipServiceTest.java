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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.DEFERRAL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.INDEMNITY_INSURANCE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.SPONSORSHIP;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.BLOCK_INDEMNITY_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.COJ_SYNCED_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import uk.nhs.tis.trainee.notifications.dto.CojSignedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.HistoryDto;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.HrefType;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class ProgrammeMembershipServiceTest {

  private static final String MEDICAL_CURRICULUM_1 = "Medical_curriculum";
  private static final String MEDICAL_CURRICULUM_2 = "Medical_spr";
  private static final String EXCLUDE_SPECIALTY_1 = "Public health medicine";
  private static final String EXCLUDE_SPECIALTY_2 = "Foundation";

  private static final String TIS_ID = "123";
  private static final String PERSON_ID = "abc";
  private static final String PROGRAMME_NAME = "the programme";
  private static final String MANAGING_DEANERY = "the local office";
  private static final LocalDate START_DATE = LocalDate.now().plusYears(1);
  //set a year in the future to allow all notifications to be scheduled

  private static final Curriculum IGNORED_CURRICULUM
      = new Curriculum("some-subtype", "some-specialty", false);

  private static final String E_PORTFOLIO_VERSION = "v1.2.3";
  private static final String INDEMNITY_INSURANCE_VERSION = "v2.3.4";
  private static final String LTFT_VERSION = "v3.4.5";
  private static final String DEFERRAL_VERSION = "v4.5.6";
  private static final String SPONSORSHIP_VERSION = "v5.6.7";

  ProgrammeMembershipService service;
  HistoryService historyService;
  InAppService inAppService;
  NotificationService notificationService;

  @BeforeEach
  void setUp() {
    historyService = mock(HistoryService.class);
    inAppService = mock(InAppService.class);
    notificationService = mock(NotificationService.class);
    service = new ProgrammeMembershipService(historyService, inAppService, notificationService,
        ZoneId.of("Europe/London"), DEFERRAL_VERSION, E_PORTFOLIO_VERSION,
        INDEMNITY_INSURANCE_VERSION, LTFT_VERSION, SPONSORSHIP_VERSION);
  }

  @ParameterizedTest
  @ValueSource(strings = {MEDICAL_CURRICULUM_1, MEDICAL_CURRICULUM_2})
  void shouldNotExcludePmWithMedicalSubtypeAndNoExcludedSpecialties(String subtype) {
    Curriculum theCurriculum = new Curriculum(subtype, "some-specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum, IGNORED_CURRICULUM));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(false));
  }

  @Test
  void shouldExcludePmThatHasNoStartDate() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(theCurriculum, IGNORED_CURRICULUM));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldExcludePmThatIsNotFuture() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(LocalDate.now().minusYears(1));
    programmeMembership.setCurricula(List.of(theCurriculum, IGNORED_CURRICULUM));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldExcludePmWithNoMedicalSubtype() {
    List<Curriculum> curricula = List.of(IGNORED_CURRICULUM);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(curricula);

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldExcludePmWithNoCurricula(List<Curriculum> curricula) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(curricula);

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {EXCLUDE_SPECIALTY_1, EXCLUDE_SPECIALTY_2})
  void shouldExcludePmWithExcludedSpecialty(String specialty) {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, specialty, false);
    Curriculum anotherCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum, anotherCurriculum));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldRemoveStaleNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1, false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
      String jobId = milestone.toString() + "-" + TIS_ID;
      verify(notificationService).removeNotification(jobId);
    }
  }

  @Test
  void shouldNotAddNotificationsWhenExcluded() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1, false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    verify(notificationService, never()).scheduleNotification(any(), any());
    verifyNoInteractions(inAppService);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      E_PORTFOLIO | v1.2.3 | true
      E_PORTFOLIO | v1.2.3 | false
      INDEMNITY_INSURANCE | v2.3.4 | true
      INDEMNITY_INSURANCE | v2.3.4 | false
      LTFT | v3.4.5 | true
      LTFT | v3.4.5 | false
      DEFERRAL | v4.5.6 | true
      DEFERRAL | v4.5.6 | false
      SPONSORSHIP | v5.6.7 | true
      SPONSORSHIP | v5.6.7 | false""")
  void shouldAddInAppNotificationsWhenNotExcludedAndMeetsCriteria(NotificationType notificationType,
      String notificationVersion, boolean notifiablePm) throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(programmeMembership,
        MessageType.IN_APP)).thenReturn(notifiablePm);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<TisReferenceInfo> referenceInfoCaptor = ArgumentCaptor.forClass(
        TisReferenceInfo.class);
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Boolean> doNotStoreJustLogCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(inAppService).createNotifications(eq(PERSON_ID), referenceInfoCaptor.capture(),
        eq(notificationType), eq(notificationVersion), variablesCaptor.capture(),
        doNotStoreJustLogCaptor.capture());

    TisReferenceInfo referenceInfo = referenceInfoCaptor.getValue();
    assertThat("Unexpected reference type.", referenceInfo.type(), is(PROGRAMME_MEMBERSHIP));
    assertThat("Unexpected reference id.", referenceInfo.id(), is(TIS_ID));

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected programme name.", variables.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", variables.get(START_DATE_FIELD), is(START_DATE));

    Boolean doNotStoreJustLog = doNotStoreJustLogCaptor.getValue();
    assertThat("Unexpected doNotStoreJustLog value.", doNotStoreJustLog, is(!notifiablePm));
  }

  @Test
  void shouldIncludeBlockFlagInIndemnityInsuranceInAppNotification() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", true);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(INDEMNITY_INSURANCE),
        eq(INDEMNITY_INSURANCE_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected variable count.", variables.size(), is(3));
    assertThat("Unexpected programme name.", variables.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", variables.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected block indemnity flag.", variables.get(BLOCK_INDEMNITY_FIELD), is(true));
  }

  @Test
  void shouldSetBlockIndemnityToTrueWhenAnySpecialtyHasBlockIndemnity() throws SchedulerException {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 1", false),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 2", true),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 3", false)
    ));

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(INDEMNITY_INSURANCE),
        eq(INDEMNITY_INSURANCE_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected block indemnity flag.", variables.get(BLOCK_INDEMNITY_FIELD), is(true));
  }

  @Test
  void shouldSetBlockIndemnityToFalseWhenNoSpecialtyHasBlockIndemnity() throws SchedulerException {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 1", false),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 2", false),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 3", false)
    ));

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(INDEMNITY_INSURANCE),
        eq(INDEMNITY_INSURANCE_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected block indemnity flag.", variables.get(BLOCK_INDEMNITY_FIELD), is(false));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      email@example.com | PROTOCOL_EMAIL
      https://example.com | ABSOLUTE_URL
      not a href | NON_HREF""")
  void shouldIncludeContactDetailsInInAppNotification(String contact, HrefType contactType)
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", true);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));
    programmeMembership.setManagingDeanery(MANAGING_DEANERY);

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(MANAGING_DEANERY)).thenReturn(contactList);
    when(notificationService.getOwnerContact(contactList, LocalOfficeContactType.LTFT,
        LocalOfficeContactType.TSS_SUPPORT, "")).thenReturn(contact);
    when(notificationService.getOwnerContact(contactList, LocalOfficeContactType.DEFERRAL,
        LocalOfficeContactType.TSS_SUPPORT, "")).thenReturn(contact);
    when(notificationService.getOwnerContact(contactList, LocalOfficeContactType.SPONSORSHIP,
        LocalOfficeContactType.TSS_SUPPORT, "")).thenReturn(contact);
    when(notificationService.getHrefTypeForContact(contact)).thenReturn(
        contactType.getHrefTypeName());

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(LTFT),
        eq(LTFT_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DEFERRAL),
        eq(DEFERRAL_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(SPONSORSHIP),
        eq(SPONSORSHIP_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected variable count.", variables.size(), is(4));
    assertThat("Unexpected programme name.", variables.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", variables.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected local office contact.", variables.get(LOCAL_OFFICE_CONTACT_FIELD),
        is(contact));
    assertThat("Unexpected local office contact type.",
        variables.get(LOCAL_OFFICE_CONTACT_TYPE_FIELD), is(contactType.getHrefTypeName()));
  }

  @Test
  void shouldNotAddInAppNotificationsWhenNotMeetsCriteria() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(false);

    service.addNotifications(programmeMembership);

    verifyNoInteractions(inAppService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.INCLUDE, names = {"DEFERRAL",
      "E_PORTFOLIO", "INDEMNITY_INSURANCE", "LTFT", "SPONSORSHIP"})
  void shouldNotAddInAppNotificationsWhenNotUnique(NotificationType notificationType)
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));

    List<HistoryDto> sentNotifications = List.of(
        new HistoryDto("id", new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID), MessageType.IN_APP,
            notificationType, null, null, Instant.MIN, Instant.MAX, UNREAD, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);
    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(programmeMembership,
        MessageType.IN_APP)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    verify(inAppService, never()).createNotifications(any(), any(), eq(notificationType), any(),
        any());
  }

  @Test
  void shouldAddDirectNotificationsWhenNotExcluded() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));

    Date expectedWhen = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));

    when(notificationService.getScheduleDate(LocalDate.now(), 1))
        .thenReturn(expectedWhen);
    service.addNotifications(programmeMembership);

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<JobDataMap> jobDataMapCaptor = ArgumentCaptor.forClass(JobDataMap.class);
    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
    verify(notificationService).scheduleNotification(
        stringCaptor.capture(),
        jobDataMapCaptor.capture()
    );

    //verify the details of the notification added
    String jobId = stringCaptor.getValue();
    String expectedJobId = PROGRAMME_CREATED + "-" + TIS_ID;
    assertThat("Unexpected job id.", jobId, is(expectedJobId));

    JobDataMap jobDataMap = jobDataMapCaptor.getValue();
    assertThat("Unexpected tisId.", jobDataMap.get(TIS_ID_FIELD), is(TIS_ID));
    assertThat("Unexpected personId.", jobDataMap.get(PERSON_ID_FIELD), is(PERSON_ID));
    assertThat("Unexpected programme.", jobDataMap.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", jobDataMap.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected CoJ synced at.", jobDataMap.get(COJ_SYNCED_FIELD),
        is(Instant.MIN));
  }

  @Test
  void shouldNotScheduleSentNotification() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        MessageType.EMAIL,
        PROGRAMME_CREATED, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    Date expectedWhen = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);
    when(notificationService.getScheduleDate(START_DATE, 1))
        .thenReturn(expectedWhen);

    service.addNotifications(programmeMembership);

    verify(notificationService, never()).scheduleNotification(any(), any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE, names = "PROGRAMME_CREATED")
  void shouldIgnoreNonPmCreatedSentNotifications(NotificationType notificationType)
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        MessageType.EMAIL,
        notificationType, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(notificationService).scheduleNotification(any(), any());
  }

  @Test
  void shouldIgnoreNonPmTypeSentNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_ID), //note: placement type
        MessageType.EMAIL,
        PROGRAMME_CREATED, null, //to avoid masking the test condition
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(notificationService).scheduleNotification(any(), any());
  }

  @Test
  void shouldIgnoreOtherPmUpdateSentNotifications() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, "another id"),
        MessageType.EMAIL,
        PROGRAMME_CREATED, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(notificationService).scheduleNotification(any(), any());
  }

//  @Test
//  void shouldScheduleMissedNotification() throws SchedulerException {
//    LocalDate dateToday = LocalDate.now();
//
//    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
//    ProgrammeMembership programmeMembership = new ProgrammeMembership();
//    programmeMembership.setTisId(TIS_ID);
//    programmeMembership.setPersonId(PERSON_ID);
//    programmeMembership.setStartDate(dateToday);
//    programmeMembership.setCurricula(List.of(theCurriculum));
//
//    Date dateLaterThan = Date.from(Instant.now());
//    when(notificationService
//        .getScheduleDate(dateToday, 1))
//        .thenReturn(dateLaterThan);
//    service.addNotifications(programmeMembership);
//
//    //the notification should be scheduled immediately
//    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
//    verify(notificationService).scheduleNotification(
//        any(),
//        any()
//    );
//
//    Date when = dateCaptor.getValue();
//    assertThat("Unexpected start time", when, is(dateLaterThan));
//  }

  @Test
  void shouldNotFailOnHistoryWithoutTisReferenceInfo()
      throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        null,
        MessageType.EMAIL,
        PROGRAMME_CREATED, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    assertDoesNotThrow(() -> service.addNotifications(programmeMembership),
        "Unexpected addNotifications failure");
  }

  @Test
  void shouldIgnoreHistoryWithoutTisReferenceInfo() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    List<HistoryDto> sentNotifications = new ArrayList<>();
    sentNotifications.add(new HistoryDto("id",
        null,
        MessageType.EMAIL,
        PROGRAMME_CREATED, null,
        "email address",
        Instant.MIN, Instant.MAX, SENT, null));

    when(historyService.findAllForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    service.addNotifications(programmeMembership);

    verify(notificationService).scheduleNotification(any(), any());
  }

  @Test
  void shouldRethrowSchedulerExceptions() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    doThrow(new SchedulerException())
        .when(notificationService).scheduleNotification(any(), any());

    assertThrows(SchedulerException.class,
        () -> service.addNotifications(programmeMembership));
  }

  @Test
  void shouldDeleteNotifications() throws SchedulerException {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);

    service.deleteNotifications(programmeMembership);

    for (NotificationType milestone : NotificationType.getProgrammeUpdateNotificationTypes()) {
      String jobId = milestone.toString() + "-" + TIS_ID;
      verify(notificationService).removeNotification(jobId);
    }
  }

  @Test
  void shouldIgnoreMissingConditionsOfJoining() throws SchedulerException {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    ArgumentCaptor<JobDataMap> jobDataMapCaptor = ArgumentCaptor.forClass(JobDataMap.class);
    verify(notificationService).scheduleNotification(any(), jobDataMapCaptor.capture());

    //verify the details of the job scheduled
    JobDataMap jobDataMap = jobDataMapCaptor.getValue();
    assertThat("Unexpected CoJ synced at.", jobDataMap.get(COJ_SYNCED_FIELD),
        is(nullValue()));
  }
}
