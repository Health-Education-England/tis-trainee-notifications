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
  E_PORTFOLIO("e-portfolio"),
  FORM_UPDATED("form-updated"),
  INDEMNITY_INSURANCE("indemnity-insurance"),
  LTFT("less-than-full-time"),
  PLACEMENT_UPDATED_WEEK_12("placement-updated-week-12"),
  PROGRAMME_UPDATED_WEEK_8("programme-updated-week-8"),
  PROGRAMME_UPDATED_WEEK_4("programme-updated-week-4"),
  PROGRAMME_UPDATED_WEEK_1("programme-updated-week-1"),
  PROGRAMME_UPDATED_WEEK_0("programme-updated-week-0"),
  PROGRAMME_CREATED("programme-created"),
  WELCOME("welcome");

  /**
   * The set of Programme Updated notification types.
   */
  @Getter
  private static final Set<NotificationType> programmeUpdateNotificationTypes = EnumSet.of(
      PROGRAMME_UPDATED_WEEK_8,
      PROGRAMME_UPDATED_WEEK_4,
      PROGRAMME_UPDATED_WEEK_1,
      PROGRAMME_UPDATED_WEEK_0,
      PROGRAMME_CREATED);

  private final String templateName;
}
