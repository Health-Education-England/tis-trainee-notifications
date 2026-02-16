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
  // Notification share with `ltft-submitted`, this type is for test only
  LTFT_ADMIN_UPDATED("ltft-admin-updated"),
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
  PROGRAMME_POG_MONTH_6("programme-pog-month-6"),
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
      PROGRAMME_CREATED);

  /**
   * The set of currently active Programme Updated email notification types.
   */
  @Getter
  private static final Set<NotificationType> activeProgrammeUpdateNotificationTypes = EnumSet.of(
      PROGRAMME_DAY_ONE,
      PROGRAMME_UPDATED_WEEK_12,
      PROGRAMME_UPDATED_WEEK_4,
      PROGRAMME_UPDATED_WEEK_2,
      PROGRAMME_CREATED);

  /**
   * The set of reminder Programme Updated email notification types.
   */
  @Getter
  private static final Set<NotificationType> reminderProgrammeUpdateNotificationTypes = EnumSet.of(
      PROGRAMME_UPDATED_WEEK_12,
      PROGRAMME_UPDATED_WEEK_4,
      PROGRAMME_UPDATED_WEEK_2);

  /**
   * The set of Programme POG email notification types.
   */
  @Getter
  private static final Set<NotificationType> programmePogNotificationTypes = EnumSet.of(
      PROGRAMME_POG_MONTH_12, PROGRAMME_POG_MONTH_6);

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
