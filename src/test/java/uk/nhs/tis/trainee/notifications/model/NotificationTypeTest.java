/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_CREATED;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_DAY_ONE;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_UPDATED_WEEK_12;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_UPDATED_WEEK_2;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.PROGRAMME_UPDATED_WEEK_4;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.getActiveFoundationProgrammeUpdateNotificationTypes;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.getActiveProgrammeUpdateNotificationTypes;

import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationTypeTest {

  @Test
  void shouldGetActiveProgrammeUpdateNotificationTypes() {
    Set<NotificationType> activeTypes = getActiveProgrammeUpdateNotificationTypes();

    assertThat("Unexpected active type count.", activeTypes, hasSize(5));
    assertThat("Unexpected active types.", activeTypes, hasItems(
        PROGRAMME_DAY_ONE,
        PROGRAMME_UPDATED_WEEK_12,
        PROGRAMME_UPDATED_WEEK_4,
        PROGRAMME_UPDATED_WEEK_2,
        PROGRAMME_CREATED)
    );
  }

  @Test
  void shouldGetActiveFoundationProgrammeUpdateNotificationTypes() {
    Set<NotificationType> activeTypes = getActiveFoundationProgrammeUpdateNotificationTypes();

    assertThat("Unexpected active type count.", activeTypes, hasSize(2));
    assertThat("Unexpected active types.", activeTypes,
        hasItems(PROGRAMME_CREATED, PROGRAMME_DAY_ONE));
  }
}
