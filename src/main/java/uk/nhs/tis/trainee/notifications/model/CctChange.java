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

package uk.nhs.tis.trainee.notifications.model;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import uk.nhs.tis.trainee.notifications.model.content.CctChangeType;

/**
 * The calculated LTFT change.
 *
 * @param id            The ID of the CCT change used to start the application.
 * @param calculationId The ID of the CCT calculation used to start the application.
 * @param type          The type of change.
 * @param wte           The whole time equivalent after the change.
 * @param startDate     The start date of the change.
 * @param endDate       The end date of the change.
 * @param cctDate       The expected CCT date after this change is applied.
 */
@Builder
public
record CctChange(
    UUID id,
    UUID calculationId,
    CctChangeType type,
    Double wte,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate cctDate) {

}
