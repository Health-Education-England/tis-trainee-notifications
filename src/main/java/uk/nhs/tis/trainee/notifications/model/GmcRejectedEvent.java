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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An event to receive details of a rejected trainee GMC update.
 *
 * @param traineeId        The trainee ID.
 * @param tisTrigger       The TIS trigger for the rejection.
 * @param tisTriggerDetail The details of the reason for rejection.
 * @param update           The reverted GMC details.
 */
public record GmcRejectedEvent(
    @JsonProperty("tisId") String traineeId,
    String tisTrigger,
    String tisTriggerDetail,
    @JsonProperty("record")
    Update update) {

  /**
   * A wrapper around the update data, used so the record structure matches the incoming message.
   *
   * @param gmcDetails The updated GMC details.
   */
  public record Update(
      @JsonProperty("data")
      GmcDetails gmcDetails) {

  }
}