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

package uk.nhs.tis.trainee.notifications.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.plugins.ECSPlugin;
import com.amazonaws.xray.strategy.IgnoreErrorContextMissingStrategy;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AWS X-Ray, conditional on daemon configuration being present.
 */
@Configuration
@ConditionalOnExpression("!T(org.springframework.util.StringUtils)"
    + ".isEmpty('${com.amazonaws.xray.emitters.daemon-address}')")
public class AwsXrayConfiguration {

  /*
   * Enable the ECS plugin for additional metadata.
   */
  static {
    AWSXRayRecorder recorder = AWSXRayRecorderBuilder.standard()
        .withPlugin(new ECSPlugin())
        .withFastIdGenerator()
        // TODO: Manual segments are not shared across threads, ignore errors to avoid alerts.
        .withContextMissingStrategy(new IgnoreErrorContextMissingStrategy())
        .build();

    AWSXRay.setGlobalRecorder(recorder);
  }

  /**
   * Create XRay tracing filter.
   *
   * @param tracingName The tracing name of the service, used as the segment name..
   * @return The created tracing filter.
   */
  @Bean
  public Filter tracingFilter(
      @Value("${com.amazonaws.xray.strategy.tracing-name}") String tracingName) {
    return new AWSXRayServletFilter(tracingName);
  }
}
