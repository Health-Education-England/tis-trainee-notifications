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
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.spring.aop.AbstractXRayInterceptor;
import com.amazonaws.xray.strategy.sampling.SamplingRequest;
import com.amazonaws.xray.strategy.sampling.SamplingResponse;
import com.amazonaws.xray.strategy.sampling.SamplingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Configuration for AWS X-Ray interceptor, conditional on daemon configuration being present.
 */
@Slf4j
@Aspect
@Component
@ConditionalOnExpression("!T(org.springframework.util.StringUtils)"
    + ".isEmpty('${com.amazonaws.xray.emitters.daemon-address}')")
public class AwsXrayInterceptor extends AbstractXRayInterceptor {

  private final String tracingName;

  /**
   * Construct an XRay interceptor.
   *
   * @param tracingName The name of the application to add to the trace.
   */
  public AwsXrayInterceptor(
      @Value("${com.amazonaws.xray.strategy.tracing-name}") String tracingName) {
    this.tracingName = tracingName;
  }

  /**
   * X-Ray handles HTTP requests automatically, other triggers need manual handling.
   */
  @Override
  @Pointcut("@within(com.amazonaws.xray.spring.aop.XRayEnabled)")
  public void xrayEnabledClasses() {

  }

  /**
   * Scheduled jobs must be handled manually.
   */
  @Order(0)
  @Pointcut("@within(com.amazonaws.xray.spring.aop.XRayEnabled)"
      + " && @annotation(org.springframework.scheduling.annotation.Scheduled)")
  public void xrayEnabledScheduledJobs() {

  }

  /**
   * Manually wrap scheduled jobs in an X-Ray segment.
   *
   * @param pjp The join point.
   * @return The result of proceeding with the join point.
   * @throws Throwable If the invoked proceed throws anything.
   */
  @Around("xrayEnabledScheduledJobs()")
  public Object traceAroundScheduledJobs(ProceedingJoinPoint pjp) throws Throwable {
    AWSXRayRecorder recorder = AWSXRay.getGlobalRecorder();
    SamplingStrategy samplingStrategy = recorder.getSamplingStrategy();
    SamplingResponse trace = samplingStrategy.shouldTrace(
        new SamplingRequest(null, null, null, null, null));

    try (Segment serviceSegment = recorder.beginSegment(tracingName)) {
      // NoOpSegments cause error logs as they are not closed, so disable sampling instead.
      serviceSegment.setSampled(trace.isSampled());

      if (trace.getRuleName().isPresent()) {
        String ruleName = trace.getRuleName().get();
        log.debug("Sampling strategy decided to use rule named: {}.", ruleName);
        serviceSegment.setRuleName(ruleName);
      }
      Signature signature = pjp.getSignature();
      String className = signature.getDeclaringType().getSimpleName();

      try (Subsegment classSegment = recorder.beginSubsegment(className)) {
        String methodName = signature.getName();
        try (Subsegment methodSegment = recorder.beginSubsegment(methodName)) {
          return pjp.proceed();
        }
      }
    }
  }
}
