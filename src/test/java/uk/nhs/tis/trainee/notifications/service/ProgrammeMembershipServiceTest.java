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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.tis.trainee.notifications.dto.Curriculum;
import uk.nhs.tis.trainee.notifications.dto.ProgrammeMembershipEvent;

class ProgrammeMembershipServiceTest {

  private static final String MEDICAL_CURRICULUM_1 = "Medical_curriculum";
  private static final String MEDICAL_CURRICULUM_2 = "Medical_spr";
  private static final String EXCLUDE_SPECIALTY_1 = "Public health medicine";
  private static final String EXCLUDE_SPECIALTY_2 = "Foundation";

  private static final Curriculum IGNORED_CURRICULUM
      = new Curriculum("some-subtype", "some-specialty");

  ProgrammeMembershipService service;

  Scheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = mock(Scheduler.class);
    service = new ProgrammeMembershipService(scheduler);
  }

  @ParameterizedTest
  @ValueSource(strings = {MEDICAL_CURRICULUM_1, MEDICAL_CURRICULUM_2})
  void shouldNotExcludePmWithMedicalSubtypeAndNoExcludedSpecialties(String subtype) {
    Curriculum theCurriculum = new Curriculum(subtype, "some-specialty");
    List<Curriculum> curricula = List.of(theCurriculum, IGNORED_CURRICULUM);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(false));
  }

  @Test
  void shouldExcludePmWithNoMedicalSubtype() {
    List<Curriculum> curricula = List.of(IGNORED_CURRICULUM);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldExcludePmWithNoCurricula(List<Curriculum> curricula) {
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {EXCLUDE_SPECIALTY_1, EXCLUDE_SPECIALTY_2})
  void shouldExcludePmWithExcludedSpecialty(String specialty) {
    Curriculum theCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, specialty);
    Curriculum anotherCurriculum = new Curriculum(MEDICAL_CURRICULUM_1, "some-specialty");
    List<Curriculum> curricula = List.of(theCurriculum, anotherCurriculum);
    ProgrammeMembershipEvent event
        = new ProgrammeMembershipEvent(null, null, curricula);

    boolean isExcluded = service.isExcluded(event);

    assertThat("Unexpected excluded value.", isExcluded, is(true));
  }
}
