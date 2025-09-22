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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * A Form R published event.
 *
 * @param traineeId The person associated with the Form R.
 * @param formId    The id of the form.
 * @param form      The form details.
 * @param pdf       The reference to the stored PDF.
 */
public record FormPublishedEvent(
    String traineeId,
    @JsonAlias("id")
    String formId,
    FormR form,
    StoredFile pdf) {

  /**
   * Form R details.
   *
   * @param id             The id of the form.
   * @param lifecycleState The lifecycle state of the form (e.g. SUBMITTED).
   * @param submissionDate The date and time the form was submitted.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FormR(
      String id,
      String lifecycleState,
      LocalDateTime submissionDate) {

  }
}
