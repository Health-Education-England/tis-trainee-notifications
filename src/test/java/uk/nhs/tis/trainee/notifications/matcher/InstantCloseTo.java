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

package uk.nhs.tis.trainee.notifications.matcher;

import java.time.Instant;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.number.IsCloseTo;

/**
 * A custom matcher which wraps {@link IsCloseTo} to allow easier use with Java Instants.
 */
public class InstantCloseTo extends TypeSafeMatcher<Instant> {

  private final IsCloseTo isCloseTo;

  /**
   * Construct a matcher that matches when an instant value is equal, within the error range.
   *
   * @param value The expected value of matching doubles after conversion.
   * @param error The delta (+/-) within which matches will be allowed.
   */
  InstantCloseTo(double value, double error) {
    isCloseTo = new IsCloseTo(value, error);
  }

  @Override
  protected boolean matchesSafely(Instant instant) {
    return isCloseTo.matchesSafely((double) instant.getEpochSecond());
  }

  @Override
  public void describeTo(Description description) {
    isCloseTo.describeTo(description);
  }

  /**
   * Get an instance of the closeTo matcher.
   *
   * @param operand The expected value of matching doubles after conversion.
   * @param error   The delta (+/-) within which matches will be allowed.
   * @return the matcher instance.
   */
  public static Matcher<Instant> closeTo(double operand, double error) {
    return new InstantCloseTo(operand, error);
  }
}
