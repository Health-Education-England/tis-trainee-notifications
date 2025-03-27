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
 */

package uk.nhs.tis.trainee.notifications.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A LTFT update event.
 *
 * @param ltftName       The name of the LTFT.
 * @param status         The state the LTFT form has been changed to (e.g. SUBMITTED).
 * @param traineeTisId   The id of the person who submitted the form.
 * @param formRef        The LTFTs reference.
 * @param timestamp      The date and time the LTFT was updated.
 * @param ltftContentDto The LTFT content map of fields and values.
 */
public record LtftUpdateEvent(
    String ltftName,
    LtftStatus status,
    String traineeTisId,
    String formRef,
    Instant timestamp,
    Map<String, Object> ltftContentDto
) {

  /**
   * A representation of the status details of the LTFT included in an Amazon SES event.
   *
   * @param current The current state of the LTFT with a timestamp.
   */
  public record LtftStatus(StatusDetails current) {

    /**
     * A representation the status details included in an Amazon SES event.
     *
     * @param state     The current state of the LTFT.
     * @param timestamp The timestamp of the status change.
     */
    public record StatusDetails(String state, Instant timestamp) {
    }
  }
}
