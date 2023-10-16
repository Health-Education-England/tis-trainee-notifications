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

package uk.nhs.tis.trainee.notifications.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A Form update event.
 *
 * @param formName       The name of the form in cloud storage.
 * @param lifecycleState The lifecycle state of the form (e.g. SUBMITTED).
 * @param traineeId      The id of the person who submitted the form.
 * @param formType       The form type (e.g. formr-a, formr-b).
 * @param eventDate      The date and time the form was updated.
 * @param formContentDto The form content map of fields and values.
 */
public record FormUpdateEvent(
    String formName,
    String lifecycleState,
    String traineeId,
    String formType,
    Instant eventDate,
    Map<String, Object> formContentDto
) {

}
