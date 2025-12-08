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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.MessageType.EMAIL;
import static uk.nhs.tis.trainee.notifications.model.MessageType.IN_APP;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.DEFERRAL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.INDEMNITY_INSURANCE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.SPONSORSHIP;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.ONE_DAY_IN_SECONDS;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.PlacementService.GMC_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.BLOCK_INDEMNITY_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.COJ_SYNCED_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DEFERRAL_IF_MORE_THAN_DAYS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DESIGNATED_BODY_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.RO_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent.ConditionsOfJoining;
import uk.nhs.tis.trainee.notifications.dto.UserDetails;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.RecipientInfo;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;
import uk.nhs.tis.trainee.notifications.model.History.TisReferenceInfo;
import uk.nhs.tis.trainee.notifications.model.HrefType;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;
import uk.nhs.tis.trainee.notifications.model.TisReferenceType;

class ProgrammeMembershipServiceTest {

  private static final String MEDICAL_CURRICULUM_1 = "Medical_curriculum";
  private static final String MEDICAL_CURRICULUM_2 = "Medical_spr";
  private static final String EXCLUDE_SPECIALTY_1 = "Public health medicine";
  private static final String EXCLUDE_SPECIALTY_2 = "Foundation";

  private static final String TIS_ID = "123";
  private static final String PERSON_ID = "abc";
  private static final String PROGRAMME_NAME = "the programme";
  private static final String PROGRAMME_NUMBER = "the programme number";
  private static final String MANAGING_DEANERY = "the local office";
  private static final LocalDate START_DATE = LocalDate.now().plusYears(1);
  private static final LocalDate CURRICULUM_END_DATE = LocalDate.now().plusYears(2);
  //set a year in the future to allow all notifications to be scheduled
  private static final ZoneId timezone = ZoneId.of("Europe/London");

  private static final Curriculum IGNORED_CURRICULUM
      = new Curriculum("some-subtype", "some-specialty", false,
      CURRICULUM_END_DATE, false);

  private static final String E_PORTFOLIO_VERSION = "v1.2.3";
  private static final String INDEMNITY_INSURANCE_VERSION = "v2.3.4";
  private static final String LTFT_VERSION = "v3.4.5";
  private static final String DEFERRAL_VERSION = "v4.5.6";
  private static final String SPONSORSHIP_VERSION = "v5.6.7";
  private static final String DAY_ONE_VERSION = "v6.7.8";
  private static final String USER_EMAIL = "email@address";
  private static final String USER_TITLE = "title";
  private static final String USER_FAMILY_NAME = "family-name";
  private static final String USER_GIVEN_NAME = "given-name";
  private static final String USER_GMC = "111111";
  private static final ObjectId HISTORY_ID_1 = ObjectId.get();
  private static final ObjectId HISTORY_ID_2 = ObjectId.get();
  private static final String DESIGNATED_BODY = "deisgnatedBody";
  private static final String RO_FIRST_NAME = "RO First Name";
  private static final String RO_LAST_NAME = "RO Last Name";

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
        timezone, DAY_ONE_VERSION, DEFERRAL_VERSION, E_PORTFOLIO_VERSION,
        INDEMNITY_INSURANCE_VERSION, LTFT_VERSION, SPONSORSHIP_VERSION);
  }

  @ParameterizedTest
  @ValueSource(strings = {MEDICAL_CURRICULUM_1, MEDICAL_CURRICULUM_2})
  void shouldNotExcludePmWithMedicalSubtypeAndNoExcludedSpecialties(String subtype) {
    Curriculum theCurriculum = new Curriculum(subtype, "some-specialty", false,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum, IGNORED_CURRICULUM));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(false));
  }

  @Test
  void shouldExcludePmThatHasNoStartDate() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty", false,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(theCurriculum, IGNORED_CURRICULUM));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test
  void shouldExcludePmThatIsNotFuture() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty", false,
        CURRICULUM_END_DATE, null);
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

  @Test
  void shouldExcludePmWithNullSubtype() {
    Curriculum theCurriculum = new Curriculum(null, "some-specialty", false,
        CURRICULUM_END_DATE, null);
    List<Curriculum> curricula = List.of(theCurriculum);
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
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, specialty, false,
        CURRICULUM_END_DATE, null);
    Curriculum anotherCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty", false,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum, anotherCurriculum));

    boolean isExcluded = service.isExcluded(programmeMembership);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @Test()
  void shouldThrowExceptionIfPmHasNullSpecialty() {
    Curriculum nullCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, null, false,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(nullCurriculum, nullCurriculum));

    assertThrows(NullPointerException.class, () -> service.isExcluded(programmeMembership));
  }

  @Test
  void shouldRemoveStaleNotifications() {
    History history = History.builder()
        .id(HISTORY_ID_1)
        .tisReference(new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID))
        .status(SCHEDULED)
        .build();

    when(historyService.findAllScheduledForTrainee(PERSON_ID, PROGRAMME_MEMBERSHIP, TIS_ID))
        .thenReturn(List.of(history));

    service.addNotifications(getDefaultProgrammeMembership());

    verify(historyService).deleteHistoryForTrainee(HISTORY_ID_1, PERSON_ID);
  }

  @Test
  void shouldNotAddNotificationsWhenExcluded() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, EXCLUDE_SPECIALTY_1, false,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));

    service.addNotifications(programmeMembership);

    verify(notificationService, never()).scheduleNotification(any(), any(), any(), anyLong());
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
      SPONSORSHIP | v5.6.7 | false
      DAY_ONE | v6.7.8 | true
      DAY_ONE | v6.7.8 | false""")
  void shouldAddInAppNotificationsWhenNotExcludedAndMeetsCriteria(NotificationType notificationType,
      String notificationVersion, boolean notifiablePm) {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    UserDetails userAccountDetails =
        new UserDetails(
            null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, USER_GMC);

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(programmeMembership,
        MessageType.IN_APP)).thenReturn(notifiablePm);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");
    when(notificationService.getTraineeDetails(PERSON_ID)).thenReturn(userAccountDetails);

    service.addNotifications(programmeMembership);

    ArgumentCaptor<TisReferenceInfo> referenceInfoCaptor = ArgumentCaptor.forClass(
        TisReferenceInfo.class);
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Boolean> doNotStoreJustLogCaptor = ArgumentCaptor.captor();
    if (notificationType.equals(DAY_ONE)) {
      verify(inAppService).createNotifications(eq(PERSON_ID), referenceInfoCaptor.capture(),
          eq(notificationType), eq(notificationVersion), variablesCaptor.capture(),
          doNotStoreJustLogCaptor.capture(), eq(START_DATE.atStartOfDay(timezone).toInstant()));
    } else {
      verify(inAppService).createNotifications(eq(PERSON_ID), referenceInfoCaptor.capture(),
          eq(notificationType), eq(notificationVersion), variablesCaptor.capture(),
          doNotStoreJustLogCaptor.capture());
    }

    TisReferenceInfo referenceInfo = referenceInfoCaptor.getValue();
    assertThat("Unexpected reference type.", referenceInfo.type(), is(PROGRAMME_MEMBERSHIP));
    assertThat("Unexpected reference id.", referenceInfo.id(), is(TIS_ID));

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected programme name.", variables.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected programme number.", variables.get(PROGRAMME_NUMBER_FIELD),
        is(PROGRAMME_NUMBER));
    assertThat("Unexpected start date.", variables.get(START_DATE_FIELD), is(START_DATE));
    if (notificationType.equals(LTFT) || notificationType.equals(DEFERRAL)
        || notificationType.equals(SPONSORSHIP)) {
      assertThat("Unexpected GMC number.", variables.get(GMC_NUMBER_FIELD), is(USER_GMC));
    }

    Boolean doNotStoreJustLog = doNotStoreJustLogCaptor.getValue();
    assertThat("Unexpected doNotStoreJustLog value.", doNotStoreJustLog, is(!notifiablePm));
  }

  @Test
  void shouldIncludeBlockFlagInIndemnityInsuranceInAppNotification() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", true,
        CURRICULUM_END_DATE, null);
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

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(INDEMNITY_INSURANCE),
        eq(INDEMNITY_INSURANCE_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected variable count.", variables.size(), is(6));
    assertThat("Unexpected programme name.", variables.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", variables.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected block indemnity flag.", variables.get(BLOCK_INDEMNITY_FIELD), is(true));
  }

  @Test
  void shouldSetBlockIndemnityToTrueWhenAnySpecialtyHasBlockIndemnity() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 1", false, CURRICULUM_END_DATE, null),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 2", true, CURRICULUM_END_DATE, null),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 3", false, CURRICULUM_END_DATE, null)
    ));

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");
    when(notificationService.getTraineeDetails(PERSON_ID)).thenReturn(null);

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(INDEMNITY_INSURANCE),
        eq(INDEMNITY_INSURANCE_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected block indemnity flag.", variables.get(BLOCK_INDEMNITY_FIELD), is(true));
  }

  @Test
  void shouldSetBlockIndemnityToFalseWhenNoSpecialtyHasBlockIndemnity() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 1", false, CURRICULUM_END_DATE, null),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 2", false, CURRICULUM_END_DATE, null),
        new Curriculum(MEDICAL_CURRICULUM_1, "specialty 3", false, CURRICULUM_END_DATE, null)
    ));

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
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
  void shouldIncludeContactDetailsInInAppNotification(String contact, HrefType contactType) {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

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

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(LTFT),
        eq(LTFT_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DEFERRAL),
        eq(DEFERRAL_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(SPONSORSHIP),
        eq(SPONSORSHIP_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected variable count.", variables.size(), is(8));
    assertThat("Unexpected programme name.", variables.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", variables.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected responsible officer.", variables.get(RO_NAME_FIELD),
        is(RO_FIRST_NAME + " " + RO_LAST_NAME));
    assertThat("Unexpected designated body.", variables.get(DESIGNATED_BODY_FIELD),
        is(DESIGNATED_BODY));
    assertThat("Unexpected local office contact.", variables.get(LOCAL_OFFICE_CONTACT_FIELD),
        is(contact));
    assertThat("Unexpected local office contact type.",
        variables.get(LOCAL_OFFICE_CONTACT_TYPE_FIELD), is(contactType.getHrefTypeName()));
  }

  @Test
  void shouldAddUnknownGmcInInAppNotificationWhenTraineeDetailsNull() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", true,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));
    programmeMembership.setManagingDeanery(MANAGING_DEANERY);

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);

    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");
    when(notificationService.getTraineeDetails(PERSON_ID)).thenReturn(null);

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(LTFT),
        eq(LTFT_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DEFERRAL),
        eq(DEFERRAL_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(SPONSORSHIP),
        eq(SPONSORSHIP_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected GMC number.", variables.get(GMC_NUMBER_FIELD), is("unknown"));
  }

  @Test
  void shouldAddUnknownGmcInInAppNotificationWhenMissingGmcInTraineeDetails() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", true,
        CURRICULUM_END_DATE, null);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));
    programmeMembership.setManagingDeanery(MANAGING_DEANERY);

    UserDetails userAccountDetails =
        new UserDetails(
            null, USER_EMAIL, USER_TITLE, USER_FAMILY_NAME, USER_GIVEN_NAME, null);

    when(notificationService.meetsCriteria(programmeMembership, true, true)).thenReturn(true);

    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");
    when(notificationService.getTraineeDetails(PERSON_ID)).thenReturn(null);
    when(notificationService.getTraineeDetails(PERSON_ID)).thenReturn(userAccountDetails);

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(LTFT),
        eq(LTFT_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DEFERRAL),
        eq(DEFERRAL_VERSION), variablesCaptor.capture(), anyBoolean());
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(SPONSORSHIP),
        eq(SPONSORSHIP_VERSION), variablesCaptor.capture(), anyBoolean());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected GMC number.", variables.get(GMC_NUMBER_FIELD), is("unknown"));
  }

  @Test
  void shouldNotAddInAppNotificationsWhenNotMeetsCriteria() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(false);

    service.addNotifications(programmeMembership);

    verifyNoInteractions(inAppService);
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.INCLUDE, names = {"DEFERRAL",
      "E_PORTFOLIO", "INDEMNITY_INSURANCE", "LTFT", "SPONSORSHIP", "DAY_ONE"})
  void shouldNotAddInAppNotificationsWhenNotUnique(NotificationType notificationType) {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.IN_APP, null);
    List<History> sentNotifications = List.of(
        new History(ObjectId.get(), new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
            notificationType, recipientInfo, null, null, Instant.MIN, Instant.MAX, UNREAD, null,
            null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);
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
  void shouldReturnEmptyRoNameWhenRoIsMissing() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setResponsibleOfficer(null);

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DAY_ONE), eq(DAY_ONE_VERSION),
        variablesCaptor.capture(), anyBoolean(), eq(START_DATE.atStartOfDay(timezone).toInstant()));

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected responsible officer.", variables.get(RO_NAME_FIELD),
        is(""));
  }

  @Test
  void shouldReturnEmptyRoNameWhenRoFirstAndLastNameAreMissing() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    ResponsibleOfficer theRo = new ResponsibleOfficer("roEmail", "", null,
        "roGmc", "roPhone");
    programmeMembership.setResponsibleOfficer(theRo);

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DAY_ONE), eq(DAY_ONE_VERSION),
        variablesCaptor.capture(), anyBoolean(), eq(START_DATE.atStartOfDay(timezone).toInstant()));

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected responsible officer.", variables.get(RO_NAME_FIELD),
        is(""));
  }

  @Test
  void shouldReturnTrimRoFirstNameWhenRoLastNameIsMissing() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    ResponsibleOfficer theRo = new ResponsibleOfficer("roEmail", RO_FIRST_NAME, "",
        "roGmc", "roPhone");
    programmeMembership.setResponsibleOfficer(theRo);

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DAY_ONE), eq(DAY_ONE_VERSION),
        variablesCaptor.capture(), anyBoolean(), eq(START_DATE.atStartOfDay(timezone).toInstant()));

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected responsible officer.", variables.get(RO_NAME_FIELD),
        is(RO_FIRST_NAME));
  }

  @Test
  void shouldReturnTrimRoLastNameWhenRoFirstNameIsMissing() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    ResponsibleOfficer theRo = new ResponsibleOfficer("roEmail", null, RO_LAST_NAME,
        "roGmc", "roPhone");
    programmeMembership.setResponsibleOfficer(theRo);

    when(notificationService.meetsCriteria(programmeMembership, true,
        true)).thenReturn(true);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("");

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.captor();
    verify(inAppService).createNotifications(eq(PERSON_ID), any(), eq(DAY_ONE), eq(DAY_ONE_VERSION),
        variablesCaptor.capture(), anyBoolean(), eq(START_DATE.atStartOfDay(timezone).toInstant()));

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat("Unexpected responsible officer.", variables.get(RO_NAME_FIELD),
        is(RO_LAST_NAME));
  }

  @Test
  void shouldAddDirectNotificationsWhenNotExcluded() {
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(new ArrayList<>());

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    service.addNotifications(programmeMembership);

    // PROGRAMME_CREATED
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Map<String, Object>> jobDataMapCaptor = ArgumentCaptor.captor();
    verify(notificationService).executeNow(
        stringCaptor.capture(),
        jobDataMapCaptor.capture());

    String jobId = stringCaptor.getValue();
    String expectedJobId = PROGRAMME_CREATED + "-" + TIS_ID;
    assertThat("Unexpected job id.", jobId, is(expectedJobId));

    Map<String, Object> jobDataMap = jobDataMapCaptor.getValue();
    assertThat("Unexpected variable count.", jobDataMap.size(), is(10));
    assertThat("Unexpected tisId.", jobDataMap.get(TIS_ID_FIELD), is(TIS_ID));
    assertThat("Unexpected personId.", jobDataMap.get(PERSON_ID_FIELD), is(PERSON_ID));
    assertThat("Unexpected programme.", jobDataMap.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", jobDataMap.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected CoJ synced at.", jobDataMap.get(COJ_SYNCED_FIELD),
        is(Instant.MIN));

    // PROGRAMME_DAY_ONE
    ArgumentCaptor<String> dayOneStringCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Map<String, Object>> dayOneJobDataMapCaptor = ArgumentCaptor.captor();
    verify(notificationService).scheduleNotification(
        dayOneStringCaptor.capture(),
        dayOneJobDataMapCaptor.capture(),
        eq(Date.from(START_DATE.atStartOfDay(timezone).toInstant())),
        eq(ONE_DAY_IN_SECONDS));

    String dayOneJobId = dayOneStringCaptor.getValue();
    String dayOneExpectedJobId = PROGRAMME_DAY_ONE + "-" + TIS_ID;
    assertThat("Unexpected job id.", dayOneJobId, is(dayOneExpectedJobId));

    Map<String, Object> dayOneJobDataMap = dayOneJobDataMapCaptor.getValue();
    assertThat("Unexpected variable count.", dayOneJobDataMap.size(), is(10));
    assertThat("Unexpected tisId.", dayOneJobDataMap.get(TIS_ID_FIELD), is(TIS_ID));
    assertThat("Unexpected personId.", dayOneJobDataMap.get(PERSON_ID_FIELD), is(PERSON_ID));
    assertThat("Unexpected programme.", dayOneJobDataMap.get(PROGRAMME_NAME_FIELD),
        is(PROGRAMME_NAME));
    assertThat("Unexpected start date.", dayOneJobDataMap.get(START_DATE_FIELD), is(START_DATE));
    assertThat("Unexpected ro name.", dayOneJobDataMap.get(RO_NAME_FIELD),
        is(RO_FIRST_NAME + " " + RO_LAST_NAME));
    assertThat("Unexpected designated body.", dayOneJobDataMap.get(DESIGNATED_BODY_FIELD),
        is(DESIGNATED_BODY));
  }

  @Test
  void shouldSendDayOneEmailNowWhenTodayIsStartDay() {
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(new ArrayList<>());

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(LocalDate.now());

    service.addNotifications(programmeMembership);

    verify(notificationService).executeNow(eq(PROGRAMME_DAY_ONE + "-" + TIS_ID), any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_UPDATED_WEEK_12",
      "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldSendReminderEmailNowWhenTodayIsDueDate(NotificationType reminderNotification) {
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(new ArrayList<>());

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    int offsetDays = service.getDaysBeforeStartForNotification(reminderNotification);
    LocalDate reminderDate = LocalDate.now().plusDays(offsetDays);
    programmeMembership.setStartDate(reminderDate);

    service.addNotifications(programmeMembership);

    verify(notificationService).executeNow(eq(reminderNotification + "-" + TIS_ID), any());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_UPDATED_WEEK_12",
      "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldNotSendOrScheduleReminderEmailWhenOverdue(NotificationType reminderNotification) {
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(new ArrayList<>());

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    int offsetDays = service.getDaysBeforeStartForNotification(reminderNotification);
    LocalDate overdueDate = LocalDate.now().plusDays(offsetDays - 1);
    programmeMembership.setStartDate(overdueDate);

    service.addNotifications(programmeMembership);

    verify(notificationService, never())
        .executeNow(eq(reminderNotification + "-" + TIS_ID), any());
    verify(notificationService, never())
        .scheduleNotification(eq(reminderNotification + "-" + TIS_ID), any(), any(), anyLong());
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, names = {"PROGRAMME_UPDATED_WEEK_12",
      "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldScheduleReminderEmailWhenDueInFuture(NotificationType reminderNotification) {
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(new ArrayList<>());

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    int offsetDays = service.getDaysBeforeStartForNotification(reminderNotification);
    LocalDate overdueDate = LocalDate.now().plusDays(offsetDays + 1);
    programmeMembership.setStartDate(overdueDate);

    service.addNotifications(programmeMembership);

    verify(notificationService, never())
        .executeNow(eq(reminderNotification + "-" + TIS_ID), any());
    verify(notificationService)
        .scheduleNotification(eq(reminderNotification + "-" + TIS_ID), any(), any(), anyLong());
  }

  @Test
  void shouldNotResendSentNotificationIfNotDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, START_DATE));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType notificationType
        : NotificationType.getActiveProgrammeUpdateNotificationTypes()) {

      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          notificationType, recipientInfo,
          templateInfo, null,
          Instant.MIN, Instant.MAX, SENT, null, null));
    }

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    verify(notificationService, never()).scheduleNotification(any(), any(), any(), anyLong());
    verify(notificationService, never()).executeNow(any(), any());
  }

  @Test
  void shouldNotResendInAppNotificationsIfNotDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, START_DATE));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType inAppType : NotificationType.getProgrammeInAppNotificationTypes()) {
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          templateInfo, null,
          Instant.MIN, Instant.MAX, SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(any())).thenReturn(contactList);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("contact");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("href");
    when(notificationService.meetsCriteria(any(), anyBoolean(), anyBoolean())).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(any(), any())).thenReturn(true);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean());
    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  void shouldResendSentNotificationImmediatelyIfDeferralLeadTimeNotInFuture() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was 90 days before START_DATE, and welcome was sent >90 days ago
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));
    List<History> sentNotifications = new ArrayList<>();
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null));
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    String programmeCreatedExpectedJobId = PROGRAMME_CREATED + "-" + TIS_ID;
    verify(notificationService).executeNow(eq(programmeCreatedExpectedJobId), any());

    String dayOneExpectedJobId = PROGRAMME_DAY_ONE + "-" + TIS_ID;
    verify(notificationService)
        .scheduleNotification(eq(dayOneExpectedJobId), any(), any(), eq(ONE_DAY_IN_SECONDS));
  }

  @Test
  void shouldResendInAppNotificationsImmediatelyIfDeferralLeadTimeNotInFuture() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was 90 days before START_DATE, and welcome was sent >90 days ago
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType inAppType : NotificationType.getProgrammeInAppNotificationTypes()) {
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          templateInfo, null,
          Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(any())).thenReturn(contactList);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("contact");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("href");
    when(notificationService.meetsCriteria(any(), anyBoolean(), anyBoolean())).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(any(), any())).thenReturn(true);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    int expectedInAppNotifications
        = NotificationType.getProgrammeInAppNotificationTypes().size();
    verify(inAppService, times(expectedInAppNotifications))
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean());
    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  void shouldResendSentNotificationImmediatelyIfDeferralAndHistoryMissingSentAt() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent >90 days ago
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));
    List<History> sentNotifications = new ArrayList<>();
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo,
        null, null, null,
        SENT, null, null));
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_DAY_ONE, recipientInfo,
        templateInfo,
        null, null, null,
        SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    String programmeCreatedExpectedJobId = PROGRAMME_CREATED + "-" + TIS_ID;
    verify(notificationService).executeNow(eq(programmeCreatedExpectedJobId), any());

    String dayOneExpectedJobId = PROGRAMME_DAY_ONE + "-" + TIS_ID;
    verify(notificationService)
        .scheduleNotification(eq(dayOneExpectedJobId), any(), any(), eq(ONE_DAY_IN_SECONDS));
  }

  @Test
  void shouldResendInAppNotificationsImmediatelyIfDeferralAndHistoryMissingSentAt() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent >90 days ago
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));
    List<History> sentNotifications = new ArrayList<>();

    for (NotificationType inAppType : NotificationType.getProgrammeInAppNotificationTypes()) {
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          templateInfo,
          null, null, null,
          SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(any())).thenReturn(contactList);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("contact");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("href");
    when(notificationService.meetsCriteria(any(), anyBoolean(), anyBoolean())).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(any(), any())).thenReturn(true);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    int expectedInAppNotifications
        = NotificationType.getProgrammeInAppNotificationTypes().size();
    verify(inAppService, times(expectedInAppNotifications))
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean());
    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  void shouldScheduleSentNotificationIfDeferralLeadTimeInFuture() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent <90 days ago
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate originalSentAt = LocalDate.now().minusDays(50);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));
    List<History> sentNotifications = new ArrayList<>();
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    LocalDate expectedWhen = LocalDate.now().plusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1 - 50);
    Date expectedWhenDate = Date.from(
        expectedWhen.atStartOfDay(timezone).toInstant());
    verify(notificationService)
        .scheduleNotification(any(), any(), eq(expectedWhenDate), eq(ONE_DAY_IN_SECONDS));
    verify(notificationService, never()).executeNow(any(), any());
  }

  @Test
  void shouldScheduleInAppNotificationsIfDeferralLeadTimeInFuture() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent <90 days ago
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate originalSentAt = LocalDate.now().minusDays(50);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType inAppType : NotificationType.getProgrammeInAppNotificationTypes()) {
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          templateInfo, null,
          Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(any())).thenReturn(contactList);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("contact");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("href");
    when(notificationService.meetsCriteria(any(), anyBoolean(), anyBoolean())).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(any(), any())).thenReturn(true);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    LocalDate expectedWhen = LocalDate.now().plusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1 - 50);
    Date expectedWhenDate = Date.from(
        expectedWhen.atStartOfDay(timezone).toInstant());
    int expectedInAppNotifications
        = NotificationType.getProgrammeInAppNotificationTypes().size();
    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean());
    verify(inAppService, times(expectedInAppNotifications))
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean(),
            eq(expectedWhenDate.toInstant()));
  }

  @Test
  void shouldScheduleSentNotificationIfMultipleHistoryAndLatestIsDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //most recent start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent <90 days ago
    LocalDate mostRecentStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate mostRecentSentAt = LocalDate.now().minusDays(50);
    LocalDate previousStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS - 1);
    LocalDate previousSentAt = LocalDate.now().minusDays(150);
    TemplateInfo mostRecentTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, mostRecentStartDate));
    TemplateInfo previousTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, previousStartDate));
    List<History> sentNotifications = new ArrayList<>();
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        mostRecentTemplateInfo, null,
        Instant.from(mostRecentSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null));
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        previousTemplateInfo, null,
        Instant.from(previousSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    LocalDate expectedWhen = LocalDate.now().plusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1 - 50);
    Date expectedWhenDate = Date.from(
        expectedWhen.atStartOfDay(timezone).toInstant());
    verify(notificationService)
        .scheduleNotification(any(), any(), eq(expectedWhenDate), eq(ONE_DAY_IN_SECONDS));
    verify(notificationService, never()).executeNow(any(), any());
  }

  @Test
  void shouldScheduleInAppNotificationsIfMultipleHistoryAndLatestIsDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //most recent start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent <90 days ago
    LocalDate mostRecentStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate mostRecentSentAt = LocalDate.now().minusDays(50);
    LocalDate previousStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS - 1);
    LocalDate previousSentAt = LocalDate.now().minusDays(150);
    TemplateInfo mostRecentTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, mostRecentStartDate));
    TemplateInfo previousTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, previousStartDate));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType inAppType : NotificationType.getProgrammeInAppNotificationTypes()) {
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          mostRecentTemplateInfo, null,
          Instant.from(mostRecentSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          previousTemplateInfo, null,
          Instant.from(previousSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(any())).thenReturn(contactList);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("contact");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("href");
    when(notificationService.meetsCriteria(any(), anyBoolean(), anyBoolean())).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(any(), any())).thenReturn(true);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    LocalDate expectedWhen = LocalDate.now().plusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1 - 50);
    Date expectedWhenDate = Date.from(
        expectedWhen.atStartOfDay(timezone).toInstant());
    int expectedInAppNotifications
        = NotificationType.getProgrammeInAppNotificationTypes().size();
    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean());
    verify(inAppService, times(expectedInAppNotifications))
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean(),
            eq(expectedWhenDate.toInstant()));
  }

  @Test
  void shouldNotScheduleSentNotificationIfMultipleHistoryAndLatestIsNotDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //most recent start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent <90 days ago
    LocalDate mostRecentStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS - 1);
    LocalDate mostRecentSentAt = LocalDate.now().minusDays(50);
    LocalDate previousStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate previousSentAt = LocalDate.now().minusDays(150);
    TemplateInfo mostRecentTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, mostRecentStartDate));
    TemplateInfo previousTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, previousStartDate));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType notificationType
        : NotificationType.getActiveProgrammeUpdateNotificationTypes()) {

      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          notificationType, recipientInfo,
          mostRecentTemplateInfo, null,
          Instant.from(mostRecentSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          notificationType, recipientInfo,
          previousTemplateInfo, null,
          Instant.from(previousSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false,
        CURRICULUM_END_DATE, false); //exclude POG notification
    programmeMembership.setCurricula(List.of(theCurriculum));
    service.addNotifications(programmeMembership);

    verify(notificationService, never()).scheduleNotification(any(), any(), any(), anyLong());
    verify(notificationService, never()).executeNow(any(), any());
  }

  @Test
  void shouldNotScheduleInAppNotificationsIfMultipleHistoryAndLatestIsNotDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //most recent start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and welcome was sent <90 days ago
    LocalDate mostRecentStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS - 1);
    LocalDate mostRecentSentAt = LocalDate.now().minusDays(50);
    LocalDate previousStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate previousSentAt = LocalDate.now().minusDays(150);
    TemplateInfo mostRecentTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, mostRecentStartDate));
    TemplateInfo previousTemplateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, previousStartDate));
    List<History> sentNotifications = new ArrayList<>();
    for (NotificationType inAppType : NotificationType.getProgrammeInAppNotificationTypes()) {
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          mostRecentTemplateInfo, null,
          Instant.from(mostRecentSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
      sentNotifications.add(new History(ObjectId.get(),
          new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
          inAppType, recipientInfo,
          previousTemplateInfo, null,
          Instant.from(previousSentAt.atStartOfDay(timezone)), Instant.MAX,
          SENT, null, null));
    }
    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, LocalOfficeContactType.LTFT.getContactTypeName()));
    when(notificationService.getOwnerContactList(any())).thenReturn(contactList);
    when(notificationService.getOwnerContact(any(), any(), any(), any())).thenReturn("contact");
    when(notificationService.getHrefTypeForContact(any())).thenReturn("href");
    when(notificationService.meetsCriteria(any(), anyBoolean(), anyBoolean())).thenReturn(true);
    when(notificationService.programmeMembershipIsNotifiable(any(), any())).thenReturn(true);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    service.addNotifications(programmeMembership);

    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean());
    verify(inAppService, never())
        .createNotifications(any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  void shouldNotScheduleNotificationIfNewStartDateIsNull() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and we don't know updated programme start date
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));

    History sentNotification = new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(null);

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfStartDateChangeNotDeferral() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    //original start date was <= DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS);
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));

    History sentNotification = new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfHistoryStartDateCorrupt() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, "not a date"));

    History sentNotification = new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(timezone)), Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfHistoryTemplateMissing() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");

    History sentNotification = new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        null, null,
        Instant.MIN, Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfHistoryTemplateVariablesMissing() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");

    TemplateInfo templateInfo = new TemplateInfo(null, null, null);
    History sentNotification = new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.MIN, Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfHistoryTemplateVariablesStartDateMissing() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");

    TemplateInfo templateInfo = new TemplateInfo(null, null,
        Map.of("some field", "some field value"));
    History sentNotification = new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.MIN, Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PROGRAMME_CREATED", "PROGRAMME_DAY_ONE", "PROGRAMME_UPDATED_WEEK_12",
          "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2"})
  void shouldIgnoreNonPmCreatedSentNotifications(NotificationType notificationType) {
    List<History> sentNotifications = new ArrayList<>();
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        notificationType, recipientInfo,
        null, null,
        Instant.MIN, Instant.MAX, SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    service.addNotifications(programmeMembership);

    verify(notificationService).executeNow(any(), any());
  }

  @Test
  void shouldIgnoreNonPmTypeSentNotifications() {
    List<History> sentNotifications = new ArrayList<>();
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(TisReferenceType.PLACEMENT, TIS_ID), //note: placement type
        PROGRAMME_CREATED, recipientInfo, //to avoid masking the test condition
        null, null,
        Instant.MIN, Instant.MAX, SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    service.addNotifications(programmeMembership);

    verify(notificationService).executeNow(any(), any());
  }

  @Test
  void shouldIgnoreOtherPmUpdateSentNotifications() {
    List<History> sentNotifications = new ArrayList<>();
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    sentNotifications.add(new History(ObjectId.get(),
        new TisReferenceInfo(PROGRAMME_MEMBERSHIP, "another id"),
        PROGRAMME_CREATED, recipientInfo,
        null, null,
        Instant.MIN, Instant.MAX, SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    service.addNotifications(programmeMembership);

    verify(notificationService).executeNow(any(), any());
  }

  @Test
  void shouldNotFailOnHistoryWithoutTisReferenceInfo() {
    List<History> sentNotifications = new ArrayList<>();
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    sentNotifications.add(new History(ObjectId.get(),
        null,
        PROGRAMME_CREATED, recipientInfo,
        null, null,
        Instant.MIN, Instant.MAX, SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    assertDoesNotThrow(() -> service.addNotifications(programmeMembership),
        "Unexpected addNotifications failure");
  }

  @Test
  void shouldIgnoreHistoryWithoutTisReferenceInfo() {
    RecipientInfo recipientInfo = new RecipientInfo("id", MessageType.EMAIL, "test@email.com");
    List<History> sentNotifications = new ArrayList<>();
    sentNotifications.add(new History(ObjectId.get(),
        null,
        PROGRAMME_CREATED,
        recipientInfo, null, null,
        Instant.MIN, Instant.MAX, SENT, null, null));

    when(historyService.findAllHistoryForTrainee(PERSON_ID)).thenReturn(sentNotifications);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    service.addNotifications(programmeMembership);

    verify(notificationService).executeNow(any(), any());
  }

  @Test
  void shouldDeleteScheduledNotifications() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    RecipientInfo recipientInfo1 = new RecipientInfo(PERSON_ID, IN_APP, null);
    History history1 = History.builder()
        .id(HISTORY_ID_1)
        .tisReference(new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID))
        .recipient(recipientInfo1)
        .status(UNREAD)
        .build();

    RecipientInfo recipientInfo2 = new RecipientInfo(PERSON_ID, EMAIL, null);
    History history2 = History.builder()
        .id(HISTORY_ID_2)
        .tisReference(new TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID))
        .recipient(recipientInfo2)
        .status(SCHEDULED)
        .build();

    when(historyService.findAllScheduledForTrainee(PERSON_ID, PROGRAMME_MEMBERSHIP, TIS_ID))
        .thenReturn(List.of(history1, history2));

    service.deleteScheduledNotificationsFromDb(programmeMembership);

    verify(historyService).deleteHistoryForTrainee(HISTORY_ID_1, PERSON_ID);
    verify(historyService).deleteHistoryForTrainee(HISTORY_ID_2, PERSON_ID);
  }

  @Test
  void shouldNotDeleteWhenNoScheduledNotifications() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    when(historyService.findAllScheduledForTrainee(PERSON_ID, PROGRAMME_MEMBERSHIP, TIS_ID))
        .thenReturn(List.of());

    service.deleteScheduledNotificationsFromDb(programmeMembership);

    verify(historyService, never()).deleteHistoryForTrainee(any(), any());
  }

  @Test
  void shouldIgnoreMissingConditionsOfJoining() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setConditionsOfJoining(null);

    service.addNotifications(programmeMembership);

    ArgumentCaptor<Map<String, Object>> jobDataMapCaptor = ArgumentCaptor.captor();
    verify(notificationService).executeNow(any(), jobDataMapCaptor.capture());

    //verify the details of the job scheduled
    Map<String, Object> jobDataMap = jobDataMapCaptor.getValue();
    assertThat("Unexpected CoJ synced at.", jobDataMap.get(COJ_SYNCED_FIELD),
        is(nullValue()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PROGRAMME_DAY_ONE | 0
      PROGRAMME_UPDATED_WEEK_12 | 84
      PROGRAMME_UPDATED_WEEK_8 | 56
      PROGRAMME_UPDATED_WEEK_4 | 28
      PROGRAMME_UPDATED_WEEK_2 | 14
      PROGRAMME_UPDATED_WEEK_1 | 7
      PROGRAMME_UPDATED_WEEK_0 | 0
      """)
  void shouldCalculateDaysBeforeProgrammeStartDateForMilestoneNotifications(
      NotificationType notificationType, int expectedDaysBeforeStart) {
    int daysBeforeStartDate = service.getDaysBeforeStartForNotification(notificationType);

    assertThat("Unexpected days before start date for " + notificationType,
        daysBeforeStartDate, is(expectedDaysBeforeStart));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = Mode.EXCLUDE,
      names = {"PROGRAMME_DAY_ONE", "PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_8",
          "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2", "PROGRAMME_UPDATED_WEEK_1",
          "PROGRAMME_UPDATED_WEEK_0"})
  void shouldReturnNullForNonProgrammeMilestoneNotifications(NotificationType notificationType) {
    Integer daysBeforeStartDate = service.getDaysBeforeStartForNotification(notificationType);

    assertThat("Expected null for non-programme notification type " + notificationType,
        daysBeforeStartDate, is(nullValue()));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "2020-01-01")
  void shouldExcludePogWhenCctDateIsNullOrPast(String cctDateString) {
    LocalDate cctDate = cctDateString != null ? LocalDate.parse(cctDateString) : null;
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            cctDate, true)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected exclusion when CCT date is null or past.", isExcludedPog, is(true));
  }

  @Test
  void shouldNotExcludePogWhenCctDateIsToday() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(timezone), true)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when CCT date is today.", isExcludedPog, is(false));
  }

  @Test
  void shouldNotExcludePogWhenCctDateIsInFuture() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(timezone).plusDays(10), true)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when CCT date is in the future.",
        isExcludedPog, is(false));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  void shouldExcludePogWhenCurriculumEligibleForPeriodOfGraceIsNullOrFalse(Boolean eligibleForPog) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(timezone).plusDays(10), eligibleForPog)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected exclusion when curriculumEligibleForPeriodOfGrace is null or false.",
        isExcludedPog, is(true));
  }

  @Test
  void shouldIgnoreCurriculaNotEligibleForPeriodOfGrace() {
    LocalDate now = LocalDate.now(timezone);
    Curriculum eligibleCurriculum = new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
        now.plusDays(5), true);
    Curriculum notEligibleCurriculum = new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
        now.minusDays(5), false); //would be excluded

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(eligibleCurriculum, notEligibleCurriculum));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when one eligible curriculum.",
        isExcludedPog, is(false));
  }

  /**
   * Helper function to set up a default non-excluded programme membership.
   *
   * @return the default programme membership.
   */
  private ProgrammeMembership getDefaultProgrammeMembership() {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "any specialty", false,
        CURRICULUM_END_DATE, null);
    ResponsibleOfficer theRo = new ResponsibleOfficer("roEmail", RO_FIRST_NAME, RO_LAST_NAME,
        "roGmc", "roPhone");
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setTisId(TIS_ID);
    programmeMembership.setPersonId(PERSON_ID);
    programmeMembership.setProgrammeName(PROGRAMME_NAME);
    programmeMembership.setProgrammeNumber(PROGRAMME_NUMBER);
    programmeMembership.setStartDate(START_DATE);
    programmeMembership.setCurricula(List.of(theCurriculum));
    programmeMembership.setConditionsOfJoining(new ConditionsOfJoining(Instant.MIN));
    programmeMembership.setManagingDeanery(MANAGING_DEANERY);
    programmeMembership.setResponsibleOfficer(theRo);
    programmeMembership.setDesignatedBody(DESIGNATED_BODY);
    return programmeMembership;
  }
}
