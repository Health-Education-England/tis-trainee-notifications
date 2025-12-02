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

package uk.nhs.tis.trainee.notifications.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * An enumeration of possible notification types.
 */
@Getter
@AllArgsConstructor
public enum NotificationType {

  COJ_CONFIRMATION("coj-confirmation"),
  CREDENTIAL_REVOKED("credential-revoked"),
  DAY_ONE("day-one"),
  DEFERRAL("deferral"),
  E_PORTFOLIO("e-portfolio"),
  EMAIL_UPDATED_NEW("email-updated-new"),
  EMAIL_UPDATED_OLD("email-updated-old"),
  FORM_SUBMITTED("form-submitted"),
  FORM_UPDATED("form-updated"),
  GMC_REJECTED_LO("gmc-rejected-lo"),
  GMC_REJECTED_TRAINEE("gmc-rejected-trainee"),
  GMC_UPDATED("gmc-updated"),
  LTFT_ADMIN_UNSUBMITTED("ltft-admin-unsubmitted"),
  LTFT_APPROVED("ltft-approved"),
  LTFT_APPROVED_TPD("ltft-approved-tpd"),
  LTFT_REJECTED("ltft-rejected"),
  LTFT_REJECTED_TPD("ltft-rejected-tpd"),
  LTFT_SUBMITTED("ltft-submitted"),
  LTFT_SUBMITTED_TPD("ltft-submitted-tpd"),
  LTFT_UNSUBMITTED("ltft-unsubmitted"),
  LTFT_UPDATED("ltft-updated"),
  LTFT_WITHDRAWN("ltft-withdrawn"),
  INDEMNITY_INSURANCE("indemnity-insurance"),
  LTFT("less-than-full-time"),
  NON_EMPLOYMENT("non-employment"),
  PLACEMENT_INFORMATION("placement-information"),
  PLACEMENT_ROLLOUT_2024_CORRECTION("placement-rollout-2024-correction"),
  USEFUL_INFORMATION("placement-useful-information"),
  PLACEMENT_UPDATED_WEEK_12("placement-updated-week-12"),
  PROGRAMME_DAY_ONE("programme-day-one"),
  PROGRAMME_UPDATED_WEEK_12("programme-updated-week-12"),
  PROGRAMME_UPDATED_WEEK_8("programme-updated-week-8"),
  PROGRAMME_UPDATED_WEEK_4("programme-updated-week-4"),
  PROGRAMME_UPDATED_WEEK_2("programme-updated-week-2"),
  PROGRAMME_UPDATED_WEEK_1("programme-updated-week-1"),
  PROGRAMME_UPDATED_WEEK_0("programme-updated-week-0"),
  PROGRAMME_CREATED("programme-created"),
  PROGRAMME_POG_MONTH_12("programme-pog-month-12"),
  SPONSORSHIP("sponsorship"),
  WELCOME("welcome");

  /**
   * The set of Programme Updated email notification types.
   */
  @Getter
  private static final Set<NotificationType> programmeUpdateNotificationTypes = EnumSet.of(
      PROGRAMME_DAY_ONE,
      PROGRAMME_UPDATED_WEEK_12,
      PROGRAMME_UPDATED_WEEK_8,
      PROGRAMME_UPDATED_WEEK_4,
      PROGRAMME_UPDATED_WEEK_2,
      PROGRAMME_UPDATED_WEEK_1,
      PROGRAMME_UPDATED_WEEK_0,
      PROGRAMME_CREATED,
      PROGRAMME_POG_MONTH_12);

  /**
   * The set of currently active Programme Updated email notification types.
   */
  @Getter
  private static final Set<NotificationType> activeProgrammeUpdateNotificationTypes = EnumSet.of(
      PROGRAMME_DAY_ONE,
      PROGRAMME_UPDATED_WEEK_12,
      PROGRAMME_UPDATED_WEEK_4,
      PROGRAMME_UPDATED_WEEK_2,
      PROGRAMME_CREATED,
      PROGRAMME_POG_MONTH_12);

  /**
   * The set of reminder Programme Updated email notification types.
   */
  @Getter
  private static final Set<NotificationType> reminderProgrammeUpdateNotificationTypes = EnumSet.of(
      PROGRAMME_UPDATED_WEEK_12,
      PROGRAMME_UPDATED_WEEK_4,
      PROGRAMME_UPDATED_WEEK_2);

  /**
   * The set of Programme Updated in-app notification types.
   */
  @Getter
  private static final Set<NotificationType> programmeInAppNotificationTypes = EnumSet.of(
      DEFERRAL,
      E_PORTFOLIO,
      INDEMNITY_INSURANCE,
      LTFT,
      SPONSORSHIP,
      DAY_ONE);

  private final String templateName;

  private static final Map<NotificationType, NotificationTypeBehaviour> behaviours = new HashMap<>();

  static {
    //PM emails
    behaviours.put(PROGRAMME_DAY_ONE, new NotificationTypeBehaviour(PROGRAMME_DAY_ONE,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.START, 0));
    behaviours.put(PROGRAMME_CREATED, new NotificationTypeBehaviour(PROGRAMME_CREATED,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.START, null));
    behaviours.put(PROGRAMME_UPDATED_WEEK_12, new NotificationTypeBehaviour(PROGRAMME_UPDATED_WEEK_12,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, true, true,
        AnchorReferenceState.START, 84));
    behaviours.put(PROGRAMME_UPDATED_WEEK_8, new NotificationTypeBehaviour(PROGRAMME_UPDATED_WEEK_8,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, false, true,
        AnchorReferenceState.START, 56));
    behaviours.put(PROGRAMME_UPDATED_WEEK_4, new NotificationTypeBehaviour(PROGRAMME_UPDATED_WEEK_4,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, true, true,
        AnchorReferenceState.START, 28));
    behaviours.put(PROGRAMME_UPDATED_WEEK_2, new NotificationTypeBehaviour(PROGRAMME_UPDATED_WEEK_2,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, true, true,
        AnchorReferenceState.START, 14));
    behaviours.put(PROGRAMME_UPDATED_WEEK_1, new NotificationTypeBehaviour(PROGRAMME_UPDATED_WEEK_1,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, false, true,
        AnchorReferenceState.START, 7));
    behaviours.put(PROGRAMME_UPDATED_WEEK_0, new NotificationTypeBehaviour(PROGRAMME_UPDATED_WEEK_0,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, false, true,
        AnchorReferenceState.START, 0));
    behaviours.put(PROGRAMME_POG_MONTH_12, new NotificationTypeBehaviour(PROGRAMME_POG_MONTH_12,
        MessageType.EMAIL, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.END, 365));

    //PM in-app
    behaviours.put(DAY_ONE, new NotificationTypeBehaviour(DAY_ONE,
        MessageType.IN_APP, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.START, 0));
    behaviours.put(DEFERRAL, new NotificationTypeBehaviour(DEFERRAL,
        MessageType.IN_APP, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(E_PORTFOLIO, new NotificationTypeBehaviour(E_PORTFOLIO,
        MessageType.IN_APP, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(INDEMNITY_INSURANCE, new NotificationTypeBehaviour(INDEMNITY_INSURANCE,
        MessageType.IN_APP, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT, new NotificationTypeBehaviour(LTFT,
        MessageType.IN_APP, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(SPONSORSHIP, new NotificationTypeBehaviour(SPONSORSHIP,
        MessageType.IN_APP, TisReferenceType.PROGRAMME_MEMBERSHIP, true, false,
        AnchorReferenceState.NONE, null));

    //Placement email
    behaviours.put(PLACEMENT_UPDATED_WEEK_12, new NotificationTypeBehaviour(PLACEMENT_UPDATED_WEEK_12,
        MessageType.EMAIL, TisReferenceType.PLACEMENT, true, false,
        AnchorReferenceState.START, 84));
    behaviours.put(PLACEMENT_ROLLOUT_2024_CORRECTION, new NotificationTypeBehaviour(PLACEMENT_ROLLOUT_2024_CORRECTION,
        MessageType.EMAIL, TisReferenceType.PLACEMENT, true, false,
        AnchorReferenceState.NONE, null));

    //Placement in-app
    behaviours.put(NON_EMPLOYMENT, new NotificationTypeBehaviour(NON_EMPLOYMENT,
        MessageType.IN_APP, TisReferenceType.PLACEMENT, true, false,
        AnchorReferenceState.NONE, 84));
    behaviours.put(PLACEMENT_INFORMATION, new NotificationTypeBehaviour(PLACEMENT_INFORMATION,
        MessageType.IN_APP, TisReferenceType.PLACEMENT, true, false,
        AnchorReferenceState.NONE, 84));
    behaviours.put(USEFUL_INFORMATION, new NotificationTypeBehaviour(USEFUL_INFORMATION,
        MessageType.IN_APP, TisReferenceType.PLACEMENT, true, false,
        AnchorReferenceState.NONE, 84));

    //LTFT emails
    behaviours.put(LTFT_ADMIN_UNSUBMITTED, new NotificationTypeBehaviour(LTFT_ADMIN_UNSUBMITTED,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_APPROVED, new NotificationTypeBehaviour(LTFT_APPROVED,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_APPROVED_TPD, new NotificationTypeBehaviour(LTFT_APPROVED_TPD,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_REJECTED, new NotificationTypeBehaviour(LTFT_REJECTED,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_REJECTED_TPD, new NotificationTypeBehaviour(LTFT_REJECTED_TPD,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_SUBMITTED, new NotificationTypeBehaviour(LTFT_SUBMITTED,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_SUBMITTED_TPD, new NotificationTypeBehaviour(LTFT_SUBMITTED_TPD,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_UNSUBMITTED, new NotificationTypeBehaviour(LTFT_UNSUBMITTED,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_UPDATED, new NotificationTypeBehaviour(LTFT_UPDATED,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(LTFT_WITHDRAWN, new NotificationTypeBehaviour(LTFT_WITHDRAWN,
        MessageType.EMAIL, TisReferenceType.LTFT, true, false,
        AnchorReferenceState.NONE, null));

    //form emails
    behaviours.put(FORM_SUBMITTED, new NotificationTypeBehaviour(FORM_SUBMITTED,
        MessageType.EMAIL, TisReferenceType.FORM, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(FORM_UPDATED, new NotificationTypeBehaviour(FORM_UPDATED,
        MessageType.EMAIL, TisReferenceType.FORM, true, false,
        AnchorReferenceState.NONE, null));

    //GMC emails
    behaviours.put(GMC_REJECTED_LO, new NotificationTypeBehaviour(GMC_REJECTED_LO,
        MessageType.EMAIL, TisReferenceType.GMC, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(GMC_REJECTED_TRAINEE, new NotificationTypeBehaviour(GMC_REJECTED_TRAINEE,
        MessageType.EMAIL, TisReferenceType.GMC, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(GMC_UPDATED, new NotificationTypeBehaviour(GMC_UPDATED,
        MessageType.EMAIL, TisReferenceType.GMC, true, false,
        AnchorReferenceState.NONE, null));

    //COJ email
    behaviours.put(COJ_CONFIRMATION,
        new NotificationTypeBehaviour(COJ_CONFIRMATION, MessageType.EMAIL,
            TisReferenceType.COJ, true, false,
            AnchorReferenceState.NONE, null));

    //Credential revoked email
    behaviours.put(CREDENTIAL_REVOKED,
        new NotificationTypeBehaviour(CREDENTIAL_REVOKED, MessageType.EMAIL,
            TisReferenceType.PERSON, false, false,
            AnchorReferenceState.NONE, null));
    //PERSON is a bit of a stretch, but we are not using this right now so can ignore it

    //User account emails/in-app
    behaviours.put(EMAIL_UPDATED_NEW, new NotificationTypeBehaviour(EMAIL_UPDATED_NEW,
        MessageType.EMAIL, TisReferenceType.PERSON, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(EMAIL_UPDATED_OLD, new NotificationTypeBehaviour(EMAIL_UPDATED_OLD,
        MessageType.EMAIL, TisReferenceType.PERSON, true, false,
        AnchorReferenceState.NONE, null));
    behaviours.put(WELCOME, new NotificationTypeBehaviour(WELCOME,
        MessageType.IN_APP, TisReferenceType.ACCOUNT, true, false,
        AnchorReferenceState.NONE, null));
  }

  public static NotificationTypeBehaviour getBehaviour(NotificationType type) {
    return behaviours.get(type);
  }

  /**
   * Converts a template name to its corresponding notification type.
   *
   * @param templateName The name of the template to be mapped to a notification type.
   * @return The matching notification type that is found, or null if no match is found.
   */
  public static NotificationType fromTemplateName(String templateName) {
    return Arrays.stream(values())
        .filter(v -> v.getTemplateName().equals(templateName))
        .findFirst()
        .orElse(null);
  }
}
