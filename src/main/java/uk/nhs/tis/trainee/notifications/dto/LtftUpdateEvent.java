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

/**
 * A LTFT update event.
 *
 * @param traineeTisId The id of the person who submitted the form.
 * @param formRef      The reference of the LTFT form.
 * @param content      The content of the LTFT form.
 * @param status       The status of the LTFT form.
 */
public record LtftUpdateEvent(
    String traineeTisId,
    String formRef,
    LtftContent content,
    LtftStatus status,
    String managingDeanery,
    String LocalOfficeDetails
) {

  /**
   * A representation of the LTFT content.
   *
   * @param name The LTFT form name.
   */
  public record LtftContent(String name, ProgrammeMembershipDetails programmeMembership) {
    /**
     * A representation of the managingDeanery attatched to the LTFT.
     *
     * @param managingDeanery The LTFTs managingDeanery.
     */
    public record ProgrammeMembershipDetails(String managingDeanery) {

    }
  }

  /**
   * A representation of the status details of the LTFT.
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
