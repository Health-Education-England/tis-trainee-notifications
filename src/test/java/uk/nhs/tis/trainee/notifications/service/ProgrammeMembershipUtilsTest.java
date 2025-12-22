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
 *
 */

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SENT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_POG_MONTH_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_POG_MONTH_6;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_UPDATED_WEEK_1;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.TEMPLATE_OWNER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.CCT_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.COJ_SYNCED_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DEFERRAL_IF_MORE_THAN_DAYS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DESIGNATED_BODY_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.POG_ALL_NOTIFICATION_CUTOFF_WEEKS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.PROGRAMME_NUMBER_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.RO_NAME_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.TIS_ID_FIELD;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.notifications.dto.CojPublishedEvent;
import uk.nhs.tis.trainee.notifications.model.Curriculum;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.model.ProgrammeMembership;
import uk.nhs.tis.trainee.notifications.model.ResponsibleOfficer;

class ProgrammeMembershipUtilsTest {

  private static final String TIS_ID = "123";
  private static final String PERSON_ID = "abc";
  private static final String PROGRAMME_NAME = "the programme";
  private static final String PROGRAMME_NUMBER = "the programme number";
  private static final String MANAGING_DEANERY = "the local office";

  private static final String DESIGNATED_BODY = "designatedBody";
  private static final String RO_FIRST_NAME = "RO First Name";
  private static final String RO_LAST_NAME = "RO Last Name";

  private static final String MEDICAL_CURRICULUM_1 = "Medical_curriculum";
  private static final String MEDICAL_CURRICULUM_2 = "Medical_spr";
  private static final String EXCLUDE_SPECIALTY_1 = "Public health medicine";
  private static final String EXCLUDE_SPECIALTY_2 = "Foundation";
  private static final LocalDate START_DATE = LocalDate.now().plusYears(1);
  private static final LocalDate CURRICULUM_END_DATE = LocalDate.now().plusYears(2);
  private static final ZoneId TIMEZONE = ZoneId.of("Europe/London");

  private static final Curriculum IGNORED_CURRICULUM
      = new Curriculum("some-subtype", "some-specialty", false,
      CURRICULUM_END_DATE, false);

  ProgrammeMembershipUtils service = new ProgrammeMembershipUtils(TIMEZONE);

  @Test
  void shouldGetPog12monthDaysBeforeEndDate() {
    int pogDaysBeforeEndDate = service.getDaysBeforeEndForNotification(PROGRAMME_POG_MONTH_12);

    assertThat("Unexpected POG days before end date.",
        pogDaysBeforeEndDate, is(365));
  }

  @Test
  void shouldGetPog6monthDaysBeforeEndDate() {
    int pogDaysBeforeEndDate = service.getDaysBeforeEndForNotification(PROGRAMME_POG_MONTH_6);

    assertThat("Unexpected POG days before end date.",
        pogDaysBeforeEndDate, is(182));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldGetNullDaysBeforeEndDateForNonPogNotificationTypes(NotificationType nonPogType) {
    Integer pogDaysBeforeEndDate = service.getDaysBeforeEndForNotification(nonPogType);

    assertThat("Expected null for non-POG notification type.",
        pogDaysBeforeEndDate, is(nullValue()));
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
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"PROGRAMME_DAY_ONE", "PROGRAMME_UPDATED_WEEK_12", "PROGRAMME_UPDATED_WEEK_8",
          "PROGRAMME_UPDATED_WEEK_4", "PROGRAMME_UPDATED_WEEK_2", "PROGRAMME_UPDATED_WEEK_1",
          "PROGRAMME_UPDATED_WEEK_0"})
  void shouldReturnNullForNonProgrammeMilestoneNotifications(NotificationType notificationType) {
    Integer daysBeforeStartDate = service.getDaysBeforeStartForNotification(notificationType);

    assertThat("Expected null for non-programme notification type " + notificationType,
        daysBeforeStartDate, is(nullValue()));
  }

  @Test
  void shouldExcludePogWhenCctDateIsWithinPogAllNotificationCutoffWeeks() {
    // CCT date is before the cutoff (should be excluded)
    LocalDate cctDate = LocalDate.now(TIMEZONE)
        .plusWeeks(POG_ALL_NOTIFICATION_CUTOFF_WEEKS)
        .minusDays(1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected exclusion when CCT date is within cutoff weeks.", isExcludedPog, is(true));
  }

  @Test
  void shouldNotExcludePogWhenCctDateIsAfterPogAllNotificationCutoffWeeks() {
    // CCT date is on or after the cutoff (should NOT be excluded)
    LocalDate cctDate = LocalDate.now(TIMEZONE)
        .plusWeeks(POG_ALL_NOTIFICATION_CUTOFF_WEEKS);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when CCT date is on or after cutoff weeks.",
        isExcludedPog, is(false));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  void shouldExcludePogWhenCurriculumEligibleForPeriodOfGraceIsNullOrFalse(Boolean eligibleForPog) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(TIMEZONE).plusDays(10), eligibleForPog)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected exclusion when curriculumEligibleForPeriodOfGrace is null or false.",
        isExcludedPog, is(true));
  }

  @Test
  void shouldIgnoreCurriculaNotEligibleForPeriodOfGrace() {
    LocalDate now = LocalDate.now(TIMEZONE);
    Curriculum eligibleCurriculum = new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
        now.plusYears(1), true);
    Curriculum notEligibleCurriculum = new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
        now.plusYears(1), false); //would be excluded

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(eligibleCurriculum, notEligibleCurriculum));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when one eligible curriculum.",
        isExcludedPog, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfNewStartDateIsNull() {
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL,
        "test@email.com");
    //original start date was > DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    //and we don't know updated programme start date
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));

    History sentNotification = new History(ObjectId.get(),
        new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(TIMEZONE)), Instant.MAX,
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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL,
        "test@email.com");
    //original start date was <= DEFERRAL_IF_MORE_THAN_DAYS days before START_DATE,
    LocalDate originalStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS);
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, originalStartDate));

    History sentNotification = new History(ObjectId.get(),
        new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(TIMEZONE)), Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfHistoryStartDateCorrupt() {
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL,
        "test@email.com");
    LocalDate originalSentAt = LocalDate.now().minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, "not a date"));

    History sentNotification = new History(ObjectId.get(),
        new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
        PROGRAMME_CREATED, recipientInfo,
        templateInfo, null,
        Instant.from(originalSentAt.atStartOfDay(TIMEZONE)), Instant.MAX,
        SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_CREATED, sentNotification);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    boolean shouldSchedule
        = service.shouldScheduleNotification(PROGRAMME_CREATED, programmeMembership, alreadySent);
    assertThat("Unexpected should schedule value.", shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfHistoryTemplateMissing() {
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL,
        "test@email.com");

    History sentNotification = new History(ObjectId.get(),
        new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL,
        "test@email.com");

    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null, null);
    History sentNotification = new History(ObjectId.get(),
        new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL,
        "test@email.com");

    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of("some field", "some field value"));
    History sentNotification = new History(ObjectId.get(),
        new History.TisReferenceInfo(PROGRAMME_MEMBERSHIP, TIS_ID),
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
  void shouldReturnCctDateFromHistoryWhenPresentAndValid() {
    LocalDate cctDate = LocalDate.now(TIMEZONE).plusDays(30);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(CCT_DATE_FIELD, cctDate));
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    LocalDate result = service.getProgrammeCctDate(history);

    assertThat("Expected CCT date to be returned from history.", result, is(cctDate));
  }

  @Test
  void shouldReturnNullIfHistoryTemplateIsNull() {
    History history = new History(ObjectId.get(), null, null, null, null, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    LocalDate result = service.getProgrammeCctDate(history);

    assertThat("Expected null when template is null.", result, is(nullValue()));
  }

  @Test
  void shouldReturnNullIfHistoryTemplateVariablesIsNull() {
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null, null);
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    LocalDate result = service.getProgrammeCctDate(history);

    assertThat("Expected null when template variables are null.", result, is(nullValue()));
  }

  @Test
  void shouldReturnNullIfHistoryTemplateVariablesDoesNotContainCctDate() {
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of("someOtherField", LocalDate.now(TIMEZONE)));
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    LocalDate result = service.getProgrammeCctDate(history);

    assertThat("Expected null when CCT date field is missing.", result, is(nullValue()));
  }

  @Test
  void shouldReturnNullIfHistoryTemplateVariablesCctDateIsNotLocalDate() {
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(CCT_DATE_FIELD, "not a date"));
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    LocalDate result = service.getProgrammeCctDate(history);

    assertThat("Expected null when CCT date field is not a LocalDate.", result, is(nullValue()));
  }


  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldReturnNullWhenScheduleProgrammePogNotificationIfCctDateIsInPast(
      NotificationType pogType) {
    LocalDate cctDate = LocalDate.now(TIMEZONE).minusDays(1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    Date result = service.whenScheduleProgrammePogNotification(pogType, programmeMembership);

    assertThat("Expected null when CCT date is in the past.", result, is(nullValue()));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldReturnNullWhenScheduleProgrammePogNotificationIfCctDateIsToday(
      NotificationType pogType) {
    LocalDate cctDate = LocalDate.now(TIMEZONE);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    Date result = service.whenScheduleProgrammePogNotification(pogType, programmeMembership);

    assertThat("Expected null when CCT date is today.", result, is(nullValue()));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldReturnScheduledDateWhenCctDateIsInFuture(NotificationType pogType) {
    LocalDate cctDate = LocalDate.now(TIMEZONE).plusDays(400);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    Date result = service.whenScheduleProgrammePogNotification(pogType, programmeMembership);

    LocalDate expectedDate = cctDate.minusDays(service.getDaysBeforeEndForNotification(pogType));
    Date expected = Date.from(expectedDate.atStartOfDay(TIMEZONE).toInstant());

    assertThat("Expected scheduled date to be POG-applicable days before CCT date.", result,
        is(expected));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldNotScheduleNonPogNotifications(NotificationType nonPogType) {
    LocalDate newCctDate = LocalDate.now(TIMEZONE).plusWeeks(40);

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, newCctDate, true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        nonPogType, programmeMembership, Map.of());

    assertThat("Expected not to schedule non-POG notification.", shouldSchedule, is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldSchedulePogNotificationIfNoHistoryExists(NotificationType pogType) {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(TIMEZONE).plusDays(service.getDaysBeforeEndForNotification(pogType) + 1),
            true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, Map.of());

    assertThat("Expected to schedule POG notification when no history exists.", shouldSchedule,
        is(true));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldSchedulePogNotificationIfCctDateIsExtended(NotificationType pogType) {
    LocalDate oldCctDate = LocalDate.now(TIMEZONE).plusDays(10);
    LocalDate newCctDate = oldCctDate.plusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);

    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(CCT_DATE_FIELD, oldCctDate));
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, newCctDate, true)
    ));

    Map<NotificationType, History> alreadySent = Map.of(pogType, history);

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, alreadySent);

    assertThat("Expected to schedule POG notification when CCT date is extended.", shouldSchedule
        , is(true));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldNotSchedulePogNotificationIfCctDateIsNotExtended(NotificationType pogType) {
    LocalDate oldCctDate = LocalDate.now(TIMEZONE).plusDays(10);
    LocalDate newCctDate = oldCctDate.plusDays(DEFERRAL_IF_MORE_THAN_DAYS);

    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(CCT_DATE_FIELD, oldCctDate));
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, newCctDate, true)
    ));

    Map<NotificationType, History> alreadySent = Map.of(pogType, history);

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, alreadySent);

    assertThat("Expected not to schedule POG notification when CCT date is not extended.",
        shouldSchedule, is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldNotSchedulePogNotificationIfHistoryCctDateIsNull(NotificationType pogType) {
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null, Map.of());
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(TIMEZONE).plusDays(400), true)
    ));

    Map<NotificationType, History> alreadySent = Map.of(pogType, history);

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, alreadySent);

    assertThat("Expected not to schedule POG notification when history CCT date is null.",
        shouldSchedule, is(false));
  }

  @Test
  void shouldNotSchedulePog6NotificationIfCctDateIsBeforeCutoff() {
    // CCT date is less than 12 weeks from now, should return false for 6-month POG
    LocalDate cctDate = LocalDate.now(TIMEZONE).plusWeeks(POG_ALL_NOTIFICATION_CUTOFF_WEEKS)
        .minusDays(1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        PROGRAMME_POG_MONTH_6, programmeMembership, Map.of());

    assertThat("Expected not to schedule 6-month POG notification if CCT date is less than cutoff.",
        shouldSchedule, is(false));
  }

  @Test
  void shouldReturnNullWhenScheduleNotificationIfDeadlineIsTodayOrPast() {
    LocalDate startDate = LocalDate.now(TIMEZONE);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(startDate);

    Date result = service.whenScheduleProgrammeNotification(
        PROGRAMME_DAY_ONE, programmeMembership, Map.of());

    assertThat("Expected null when deadline is today.", result, is(nullValue()));
  }

  @Test
  void shouldReturnScheduledDateWhenDeadlineIsInFuture() {
    LocalDate startDate = LocalDate.now(TIMEZONE).plusDays(10);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(startDate);

    Date result = service.whenScheduleProgrammeNotification(
        PROGRAMME_UPDATED_WEEK_1, programmeMembership, Map.of());

    LocalDate expectedDate = startDate.minusDays(7);
    Date expected = Date.from(expectedDate.atStartOfDay(TIMEZONE).toInstant());

    assertThat("Expected scheduled date to be 7 days before start date.", result, is(expected));
  }

  @Test
  void shouldReturnNullWhenScheduleProgrammeNotificationIfDeferred() {
    // Simulate deferred notification logic: old start date and sentAt present, but new start
    // date is today (should send immediately)
    LocalDate oldStartDate = LocalDate.now(TIMEZONE).minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate oldSentAt = LocalDate.now(TIMEZONE).minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, oldStartDate));
    History sentNotification = new History(ObjectId.get(), null,
        NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, Instant.from(oldSentAt.atStartOfDay(TIMEZONE)), Instant.MAX, SENT,
        null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        sentNotification);

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(LocalDate.now(TIMEZONE));

    Date result = service.whenScheduleProgrammeNotification(
        PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected null for deferred notification when deadline is today.", result,
        is(nullValue()));
  }

  @Test
  void shouldReturnScheduledDateForProgrammeCreatedIfDeferred() {
    // Simulate deferred notification logic: old start date and sentAt present, new start date is
    // in future (should schedule)
    LocalDate oldStartDate = LocalDate.now(TIMEZONE).minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    LocalDate oldSentAt = LocalDate.now(TIMEZONE).minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 10);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, oldStartDate));
    History sentNotification = new History(ObjectId.get(), null,
        NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, Instant.from(oldSentAt.atStartOfDay(TIMEZONE)), Instant.MAX, SENT,
        null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        sentNotification);

    LocalDate newStartDate = LocalDate.now(TIMEZONE).plusDays(10);
    // 10 > (10 - 1) lead days between oldStartDate and oldSentAt
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(newStartDate);

    // leadDays calculation
    LocalDateTime oldSentDateTime = oldSentAt.atStartOfDay();
    long leadDays = Duration.between(oldSentDateTime, oldStartDate.atStartOfDay()).toDays();
    LocalDate expectedSendDate = newStartDate.minusDays(leadDays);
    Date expected = Date.from(expectedSendDate.atStartOfDay(TIMEZONE).toInstant());

    Date result = service.whenScheduleProgrammeNotification(
        PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected scheduled date for deferred notification.", result, is(expected));
  }

  @Test
  void shouldReturnEmptyStringWhenResponsibleOfficerIsNull() {
    String roName = service.getRoName(null);
    assertThat("Expected empty string when ResponsibleOfficer is null.", roName, is(""));
  }

  @Test
  void shouldReturnTrimmedFirstNameWhenLastNameIsNull() {
    ResponsibleOfficer ro = new ResponsibleOfficer("email", "First", null, "gmc", "phone");
    String roName = service.getRoName(ro);
    assertThat("Expected first name only when last name is null.", roName, is("First"));
  }

  @Test
  void shouldReturnTrimmedLastNameWhenFirstNameIsNull() {
    ResponsibleOfficer ro = new ResponsibleOfficer("email", null, "Last", "gmc", "phone");
    String roName = service.getRoName(ro);
    assertThat("Expected last name only when first name is null.", roName, is("Last"));
  }

  @Test
  void shouldReturnTrimmedFullNameWhenBothNamesPresent() {
    ResponsibleOfficer ro = new ResponsibleOfficer("email", "First", "Last", "gmc", "phone");
    String roName = service.getRoName(ro);
    assertThat("Expected full name when both names present.", roName, is("First Last"));
  }

  @Test
  void shouldReturnEmptyStringWhenBothNamesAreEmpty() {
    ResponsibleOfficer ro = new ResponsibleOfficer("email", "", "", "gmc", "phone");
    String roName = service.getRoName(ro);
    assertThat("Expected empty string when both names are empty.", roName, is(""));
  }

  @Test
  void shouldAddAllStandardProgrammeDetailsToJobMap() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    Map<String, Object> jobDataMap = new HashMap<>();

    service.addStandardProgrammeDetailsToJobMap(jobDataMap, programmeMembership);

    assertThat(jobDataMap.get(TIS_ID_FIELD), is(programmeMembership.getTisId()));
    assertThat(jobDataMap.get(PERSON_ID_FIELD), is(programmeMembership.getPersonId()));
    assertThat(jobDataMap.get(PROGRAMME_NAME_FIELD), is(programmeMembership.getProgrammeName()));
    assertThat(jobDataMap.get(PROGRAMME_NUMBER_FIELD),
        is(programmeMembership.getProgrammeNumber()));
    assertThat(jobDataMap.get(START_DATE_FIELD), is(programmeMembership.getStartDate()));
    assertThat(jobDataMap.get(TEMPLATE_OWNER_FIELD), is(programmeMembership.getManagingDeanery()));
    assertThat(jobDataMap.get(RO_NAME_FIELD),
        is(service.getRoName(programmeMembership.getResponsibleOfficer())));
    assertThat(jobDataMap.get(DESIGNATED_BODY_FIELD), is(programmeMembership.getDesignatedBody()));
    assertThat(jobDataMap.get(CCT_DATE_FIELD),
        is(service.getProgrammeCctDate(programmeMembership)));
    assertThat(jobDataMap.get(COJ_SYNCED_FIELD),
        is(programmeMembership.getConditionsOfJoining().syncedAt()));
  }

  @Test
  void shouldHandleNullConditionsOfJoiningInJobMap() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setConditionsOfJoining(null);
    Map<String, Object> jobDataMap = new HashMap<>();

    service.addStandardProgrammeDetailsToJobMap(jobDataMap, programmeMembership);

    // Should not throw and should not contain COJ_SYNCED_FIELD
    assertThat(jobDataMap.containsKey(COJ_SYNCED_FIELD), is(false));
  }

  @Test
  void shouldHandleNullResponsibleOfficerInJobMap() {
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setResponsibleOfficer(null);
    Map<String, Object> jobDataMap = new HashMap<>();

    service.addStandardProgrammeDetailsToJobMap(jobDataMap, programmeMembership);

    assertThat(jobDataMap.get(RO_NAME_FIELD), is(""));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldSchedulePogNotificationIfNotificationTypeIsProgrammePog(NotificationType pogType) {
    // For notification types other than PROGRAMME_POG_MONTH_12 / _6, should always return false
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(TIMEZONE).plusDays(400), true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, Map.of());

    assertThat("Expected to schedule POG notification for POG type.", shouldSchedule, is(true));
  }

  @Test
  void shouldNotSchedulePog12MonthNotificationIfCctDateIsBefore6MonthCutoff() {
    // CCT date is less than 6 months from now, should return false
    LocalDate cctDate = LocalDate.now(TIMEZONE).plusMonths(6).minusDays(1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        PROGRAMME_POG_MONTH_12, programmeMembership, Map.of());

    assertThat("Expected not to schedule 12-month POG notification if CCT date is less than 6 "
        + "months away.", shouldSchedule, is(false));
  }

  @Test
  void shouldSchedulePog6MonthNotificationIfCctDateIsBefore6MonthCutoff() {
    // CCT date is less than 6 months from now, but more than 12 weeks, should return true
    LocalDate cctDate = LocalDate.now(TIMEZONE).plusMonths(6).minusDays(1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        PROGRAMME_POG_MONTH_6, programmeMembership, Map.of());

    assertThat("Expected to schedule 6-month POG notification if CCT date is less than 6 "
        + "months away.", shouldSchedule, is(true));
  }

  @Test
  void shouldSchedulePog12NotificationIfCctDateIsOnOrAfter6MonthCutoff() {
    // CCT date is at least 12 months from now, should return true
    LocalDate cctDate = LocalDate.now(TIMEZONE).plusMonths(6);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        PROGRAMME_POG_MONTH_12, programmeMembership, Map.of());

    assertThat("Expected to schedule 12-month POG notification if CCT date is at least 6 months "
        + "away.", shouldSchedule, is(true));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldNotSchedulePogNotificationIfOldCctDateIsNull(NotificationType pogType) {
    // If history exists but old CCT date is null, should return false
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null, Map.of());
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    LocalDate cctDate = LocalDate.now(TIMEZONE).plusMonths(13);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false, cctDate, true)
    ));

    Map<NotificationType, History> alreadySent = Map.of(pogType, history);

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, alreadySent);

    assertThat("Expected not to schedule POG notification when old CCT date is null.",
        shouldSchedule, is(false));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class,
      names = {"PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6"})
  void shouldNotSchedulePogNotificationIfCctDateIsNull(NotificationType pogType) {
    // If current CCT date is null, should return false
    LocalDate oldCctDate = LocalDate.now(TIMEZONE).plusMonths(13);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(CCT_DATE_FIELD, oldCctDate));
    History history = new History(ObjectId.get(), null, null, null, templateInfo, null,
        Instant.MIN, Instant.MAX, SENT, null, null);

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    // No curricula, so getProgrammeCctDate returns null

    Map<NotificationType, History> alreadySent = Map.of(pogType, history);

    boolean shouldSchedule = service.shouldSchedulePogNotification(
        pogType, programmeMembership, alreadySent);

    assertThat("Expected not to schedule POG notification when current CCT date is null.",
        shouldSchedule, is(false));
  }

  @ParameterizedTest
  @MethodSource("provideAllReminderNotifications")
  void shouldScheduleReminderNotificationIfDeadlineIsToday(NotificationType reminderType) {
    int daysBeforeStart = service.getDaysBeforeStartForNotification(reminderType);
    LocalDate startDate = LocalDate.now(TIMEZONE).plusDays(daysBeforeStart);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(startDate);

    boolean shouldSchedule = service.shouldScheduleNotification(reminderType, programmeMembership,
        Map.of());

    assertThat("Expected to schedule reminder notification if deadline is today.",
        shouldSchedule, is(true));
  }

  @ParameterizedTest
  @MethodSource("provideAllReminderNotifications")
  void shouldScheduleReminderNotificationIfDeadlineIsInFuture(NotificationType reminderType) {
    int daysBeforeStart = service.getDaysBeforeStartForNotification(reminderType);
    LocalDate startDate = LocalDate.now(TIMEZONE).plusDays(daysBeforeStart + 1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(startDate);

    boolean shouldSchedule = service.shouldScheduleNotification(reminderType, programmeMembership,
        Map.of());

    assertThat("Expected to schedule reminder notification if deadline is in the future.",
        shouldSchedule, is(true));
  }

  @ParameterizedTest
  @MethodSource("provideAllReminderNotifications")
  void shouldNotScheduleReminderNotificationIfDeadlineIsInPast(NotificationType reminderType) {
    int daysBeforeStart = service.getDaysBeforeStartForNotification(reminderType);
    LocalDate startDate = LocalDate.now(TIMEZONE).plusDays(daysBeforeStart - 1);
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(startDate);

    boolean shouldSchedule = service.shouldScheduleNotification(reminderType, programmeMembership,
        Map.of());

    assertThat("Expected not to schedule reminder notification if deadline is in the past.",
        shouldSchedule, is(false));
  }

  @Test
  void shouldScheduleNotificationForNonDeferralAndNonReminderType() {
    // PROGRAMME_DAY_ONE is not a reminder notification
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setStartDate(LocalDate.now(TIMEZONE).plusDays(10));

    boolean shouldSchedule = service.shouldScheduleNotification(
        PROGRAMME_DAY_ONE, programmeMembership, Map.of()); //no history

    assertThat("Expected to schedule notification for non-deferral, non-reminder type.",
        shouldSchedule, is(true));
  }

  @Test
  void shouldScheduleNotificationIfStartDateChangeIsDeferral() {
    LocalDate oldStartDate = START_DATE;
    LocalDate oldSentAt = START_DATE.minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, oldStartDate));
    History sentNotification = new History(ObjectId.get(), null,
        NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, Instant.from(oldSentAt.atStartOfDay(TIMEZONE)), Instant.MAX, SENT,
        null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        sentNotification);

    // New start date is after oldStartDate + DEFERRAL_IF_MORE_THAN_DAYS
    LocalDate newStartDate = oldStartDate.plusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(newStartDate);

    boolean shouldSchedule = service.shouldScheduleNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected to schedule notification if start date change is a deferral.",
        shouldSchedule, is(true));
  }

  @Test
  void shouldNotScheduleNotificationIfStartDateChangeIsNotDeferral() {
    LocalDate oldStartDate = START_DATE;
    LocalDate oldSentAt = START_DATE.minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, oldStartDate));
    History sentNotification = new History(ObjectId.get(), null,
        NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, Instant.from(oldSentAt.atStartOfDay(TIMEZONE)), Instant.MAX, SENT,
        null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        sentNotification);

    // New start date is exactly at the threshold (should not be a deferral)
    LocalDate newStartDate = oldStartDate.plusDays(DEFERRAL_IF_MORE_THAN_DAYS);
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(newStartDate);

    boolean shouldSchedule = service.shouldScheduleNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected not to schedule notification if start date change is not a deferral.",
        shouldSchedule, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfStartDateChangeIsBeforeOldStartDate() {
    LocalDate oldStartDate = START_DATE;
    LocalDate oldSentAt = START_DATE.minusDays(100);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, oldStartDate));
    History sentNotification = new History(ObjectId.get(), null,
        NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, Instant.from(oldSentAt.atStartOfDay(TIMEZONE)), Instant.MAX, SENT,
        null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        sentNotification);

    // New start date is before oldStartDate
    LocalDate newStartDate = oldStartDate.minusDays(5);
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(newStartDate);

    boolean shouldSchedule = service.shouldScheduleNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected not to schedule notification if new start date is before old start date.",
        shouldSchedule, is(false));
  }

  @Test
  void shouldReturnNullWhenScheduleDeferrableNotificationIfNoHistoryExists() {
    // notificationsAlreadySent does not contain the notificationType
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();

    Date result = service.whenScheduleDeferrableNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, Map.of());

    assertThat("Expected null when no history exists for deferred notification.", result,
        is(nullValue()));
  }

  @Test
  void shouldReturnNullWhenScheduleDeferrableNotificationIfHistoryExistsButNotForType() {
    // notificationsAlreadySent contains a different notificationType
    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    History history = new History(ObjectId.get(), null, PROGRAMME_DAY_ONE,
        null, null, null, Instant.MIN, Instant.MAX, SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(PROGRAMME_DAY_ONE,
        history);

    Date result = service.whenScheduleDeferrableNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected null when history exists but not for the given notificationType.",
        result, is(nullValue()));
  }

  @Test
  void shouldReturnNullWhenScheduleDeferrableNotificationIfLastSentSentAtIsNull() {
    // Setup: lastSent.sentAt() is null, oldStartDate is valid
    LocalDate oldStartDate = START_DATE.minusDays(DEFERRAL_IF_MORE_THAN_DAYS + 1);
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null,
        Map.of(START_DATE_FIELD, oldStartDate));
    History lastSent = new History(ObjectId.get(), null, NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, null, Instant.MAX, SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        lastSent);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);

    Date result = service.whenScheduleDeferrableNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected null when lastSent.sentAt() is null.", result, is(nullValue()));
  }

  @Test
  void shouldReturnNullWhenScheduleDeferrableNotificationIfOldStartDateIsNull() {
    // Setup: lastSent.sentAt() is valid, but oldStartDate is null
    History.TemplateInfo templateInfo = new History.TemplateInfo(null, null, Map.of());
    History lastSent = new History(ObjectId.get(), null, NotificationType.PROGRAMME_CREATED, null,
        templateInfo, null, Instant.now(), Instant.MAX, SENT, null, null);
    Map<NotificationType, History> alreadySent = Map.of(NotificationType.PROGRAMME_CREATED,
        lastSent);

    ProgrammeMembership programmeMembership = getDefaultProgrammeMembership();
    programmeMembership.setStartDate(START_DATE);

    Date result = service.whenScheduleDeferrableNotification(
        NotificationType.PROGRAMME_CREATED, programmeMembership, alreadySent);

    assertThat("Expected null when oldStartDate is null.", result, is(nullValue()));
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
    programmeMembership.setConditionsOfJoining(
        new CojPublishedEvent.ConditionsOfJoining(Instant.MIN));
    programmeMembership.setManagingDeanery(MANAGING_DEANERY);
    programmeMembership.setResponsibleOfficer(theRo);
    programmeMembership.setDesignatedBody(DESIGNATED_BODY);
    return programmeMembership;
  }

  /**
   * Provide all programme reminder notification types.
   *
   * @return The stream of reminder notification types.
   */
  private static Stream<NotificationType> provideAllReminderNotifications() {
    return NotificationType.getReminderProgrammeUpdateNotificationTypes().stream();
  }
}
