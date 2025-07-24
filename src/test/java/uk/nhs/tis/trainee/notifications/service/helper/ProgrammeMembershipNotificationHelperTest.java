package uk.nhs.tis.trainee.notifications.service.helper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.REGISTER_TSS;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_COJ;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_FORM_R_PART_A;
import static uk.nhs.tis.trainee.notifications.model.ProgrammeActionType.SIGN_FORM_R_PART_B;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PERSON;
import static uk.nhs.tis.trainee.notifications.model.TisReferenceType.PROGRAMME_MEMBERSHIP;
import static uk.nhs.tis.trainee.notifications.service.helper.ProgrammeMembershipNotificationHelper.PERSON_ID_FIELD;
import static uk.nhs.tis.trainee.notifications.service.helper.ProgrammeMembershipNotificationHelper.PROGRAMME_ID_FIELD;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.nhs.tis.trainee.notifications.dto.ActionDto;

/**
 * Tests for the ProgrammeMembershipNotificationHelper class.
 */
class ProgrammeMembershipNotificationHelperTest {

  private static final String ACTIONS_URL = "actions-url";
  private static final String ACTIONS_PROGRAMME_URL = "/api/action/{personId}/{programmeId}";

  private static final String PERSON_ID = "person-id";
  private static final String TIS_ID = "tis-id";

  private JobDataMap jobDataMap;
  private RestTemplate restTemplate;
  private ProgrammeMembershipNotificationHelper service;

  @BeforeEach
  void setUp() {
    jobDataMap = new JobDataMap(Map.of("key", "value"));
    restTemplate = mock(RestTemplate.class);
    service = new ProgrammeMembershipNotificationHelper(ACTIONS_URL, restTemplate);
  }

  @Test
  void shouldHandleActionsRestClientExceptions() {
    when(restTemplate.getForObject(eq(ACTIONS_URL + ACTIONS_PROGRAMME_URL), eq(List.class),
        anyMap())).thenThrow(new RestClientException("error"));

    assertDoesNotThrow(()
        -> service.addProgrammeReminderDetailsToJobMap(jobDataMap, PERSON_ID, TIS_ID));
  }

  @Test
  void shouldAddReminderJobDetails() {
    List<ActionDto> reminderActions = List.of(
        new ActionDto("action-id-1", SIGN_COJ.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null,
            Instant.now()),
        new ActionDto("action-id-2", SIGN_FORM_R_PART_A.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null, null),
        new ActionDto("action-id-3", SIGN_FORM_R_PART_B.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(TIS_ID, PROGRAMME_MEMBERSHIP), null, null, null),
        new ActionDto("action-id-4", REGISTER_TSS.toString(), PERSON_ID,
            new ActionDto.TisReferenceInfo(PERSON_ID, PERSON), null, null, null)
    );
    when(restTemplate.getForObject(ACTIONS_URL + ACTIONS_PROGRAMME_URL, List.class,
        Map.of(PERSON_ID_FIELD, PERSON_ID, PROGRAMME_ID_FIELD, TIS_ID)))
        .thenReturn(reminderActions);

    service.addProgrammeReminderDetailsToJobMap(jobDataMap, PERSON_ID, TIS_ID);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(false));
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), is(false));
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(false));
  }

  @Test
  void shouldAddDefaultCompletedReminderIfActionMissing() {
    when(restTemplate.getForObject(ACTIONS_URL + ACTIONS_PROGRAMME_URL, List.class,
        Map.of(PERSON_ID_FIELD, PERSON_ID, PROGRAMME_ID_FIELD, TIS_ID)))
        .thenReturn(List.of());

    service.addProgrammeReminderDetailsToJobMap(jobDataMap, PERSON_ID, TIS_ID);

    assertThat("Unexpected action status SIGN_COJ.",
        jobDataMap.get(SIGN_COJ.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_A.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(true));
    assertThat("Unexpected action status SIGN_FORM_R_PART_B.",
        jobDataMap.get(SIGN_FORM_R_PART_B.toString()), is(true));
    assertThat("Unexpected action status REGISTER_TSS.",
        jobDataMap.get(SIGN_FORM_R_PART_A.toString()), is(true));
  }
}
