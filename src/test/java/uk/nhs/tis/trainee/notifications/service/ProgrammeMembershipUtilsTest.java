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
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.DEFERRAL_IF_MORE_THAN_DAYS;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.START_DATE_FIELD;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

  private static final String DESIGNATED_BODY = "deisgnatedBody";
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
            LocalDate.now(TIMEZONE), true)
    ));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when CCT date is today.", isExcludedPog, is(false));
  }

  @Test
  void shouldNotExcludePogWhenCctDateIsInFuture() {
    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(
        new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
            LocalDate.now(TIMEZONE).plusDays(10), true)
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
        now.plusDays(5), true);
    Curriculum notEligibleCurriculum = new Curriculum("MEDICAL_CURRICULUM", "specialty", false,
        now.minusDays(5), false); //would be excluded

    ProgrammeMembership programmeMembership = new ProgrammeMembership();
    programmeMembership.setCurricula(List.of(eligibleCurriculum, notEligibleCurriculum));

    boolean isExcludedPog = service.isExcludedPog(programmeMembership);

    assertThat("Expected not to be excluded when one eligible curriculum.",
        isExcludedPog, is(false));
  }

  @Test
  void shouldNotScheduleNotificationIfNewStartDateIsNull() {
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL, "test@email.com");
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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL, "test@email.com");
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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL, "test@email.com");
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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL, "test@email.com");

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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL, "test@email.com");

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
    History.RecipientInfo recipientInfo = new History.RecipientInfo("id", MessageType.EMAIL, "test@email.com");

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
    programmeMembership.setConditionsOfJoining(new CojPublishedEvent.ConditionsOfJoining(Instant.MIN));
    programmeMembership.setManagingDeanery(MANAGING_DEANERY);
    programmeMembership.setResponsibleOfficer(theRo);
    programmeMembership.setDesignatedBody(DESIGNATED_BODY);
    return programmeMembership;
  }
}

