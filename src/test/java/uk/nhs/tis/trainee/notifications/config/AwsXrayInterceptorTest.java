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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AwsXrayInterceptorTest {

  private static final String TRACING_NAME = "tis-trainee-notification-test";

  private AwsXrayInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new AwsXrayInterceptor(TRACING_NAME);
  }

  @Test
  void shouldTraceAroundScheduledJobWhenSampleEnabled() throws Throwable {
    Signature signature = mock(Signature.class);
    when(signature.getDeclaringType()).thenReturn(TestTracedClass.class);
    when(signature.getName()).thenReturn("testTracedMethod");

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getSignature()).thenReturn(signature);

    AWSXRayRecorder recorder = mock(AWSXRayRecorder.class);
    when(recorder.getSamplingStrategy()).thenReturn(new AllSamplingStrategy());
    AWSXRay.setGlobalRecorder(recorder);

    Segment segment = mock(Segment.class);
    when(recorder.beginSegment(any())).thenReturn(segment);

    interceptor.traceAroundScheduledJobs(pjp);

    InOrder inOrder = inOrder(recorder);
    inOrder.verify(recorder).beginSegment(TRACING_NAME);
    inOrder.verify(recorder).beginSubsegment("TestTracedClass");
    inOrder.verify(recorder).beginSubsegment("testTracedMethod");

    verify(segment, never()).setRuleName(any());
    verify(segment).setSampled(true);
    verify(pjp).proceed();
  }

  @Test
  void shouldTraceAroundScheduledJobWhenSampleDisabled() throws Throwable {
    Signature signature = mock(Signature.class);
    when(signature.getDeclaringType()).thenReturn(TestTracedClass.class);
    when(signature.getName()).thenReturn("testTracedMethod");

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getSignature()).thenReturn(signature);

    AWSXRayRecorder recorder = mock(AWSXRayRecorder.class);
    when(recorder.getSamplingStrategy()).thenReturn(new NoSamplingStrategy());
    AWSXRay.setGlobalRecorder(recorder);

    Segment segment = mock(Segment.class);
    when(recorder.beginSegment(any())).thenReturn(segment);

    interceptor.traceAroundScheduledJobs(pjp);

    InOrder inOrder = inOrder(recorder);
    inOrder.verify(recorder).beginSegment(TRACING_NAME);
    inOrder.verify(recorder).beginSubsegment("TestTracedClass");
    inOrder.verify(recorder).beginSubsegment("testTracedMethod");

    verify(segment).setRuleName("");
    verify(segment).setSampled(false);
    verify(pjp).proceed();
  }

  /**
   * A dummy class for trace testing.
   */
  private static class TestTracedClass {

  }
}
