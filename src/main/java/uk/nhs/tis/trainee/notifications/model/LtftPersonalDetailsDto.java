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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.model;

/**
 * A trainee's personal details.
 *
 * @param id                      The trainee's TIS ID.
 * @param title                   The trainee's title.
 * @param forenames               The trainee's forenames or given name.
 * @param surname                 The trainee's surname or family name.
 * @param email                   The trainee's email address.
 * @param telephoneNumber         The trainee's contact telephone number.
 * @param mobileNumber            The trainee's contact mobile number.
 * @param gmcNumber               The trainee's GMC registration number.
 * @param gdcNumber               The trainee's GDC registration number.
 * @param skilledWorkerVisaHolder Whether the trainee holds a skilled worker visa.
 */
public record LtftPersonalDetailsDto(
    String id,
    String title,
    String forenames,
    String surname,
    String email,
    String telephoneNumber,
    String mobileNumber,
    String gmcNumber,
    String gdcNumber,
    Boolean skilledWorkerVisaHolder) {

}
