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

import static io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes.SQS_AWS_TRACE_HEADER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceHeader.SampleDecision;
import com.amazonaws.xray.entities.TraceID;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

class AwsXrayInterceptorTest {

  private static final String TRACING_NAME = "tis-trainee-notification-test";
  private static final TraceID ROOT_TRACE_ID = TraceID.fromString(
      "1-581cf771-a006649127e371903a2de979");
  private static final TraceID PARENT_TRACE_ID = TraceID.fromString(
      "1-692da882-b117750237f482014b2ef080");

  private static AWSXRayRecorder originalRecorder;

  private AwsXrayInterceptor interceptor;

  @BeforeAll
  static void setUpBeforeAll() {
    originalRecorder = AWSXRay.getGlobalRecorder();
  }

  @BeforeEach
  void setUp() {
    interceptor = new AwsXrayInterceptor(TRACING_NAME);
  }

  @AfterAll
  static void tearDownAfterAll() {
    AWSXRay.setGlobalRecorder(originalRecorder);
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

  @Test
  void shouldTraceAroundSqsListenerWhenNoTraceHeader() throws Throwable {
    Signature signature = mock(Signature.class);
    when(signature.getDeclaringType()).thenReturn(TestTracedClass.class);
    when(signature.getName()).thenReturn("testTracedMethod");

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getSignature()).thenReturn(signature);

    Message<String> message = new GenericMessage<>("testPayload");
    when(pjp.getArgs()).thenReturn(new Object[]{message});

    AWSXRayRecorder recorder = mock(AWSXRayRecorder.class);
    AWSXRay.setGlobalRecorder(recorder);

    Segment segment = mock(Segment.class);
    when(recorder.beginSegment(any())).thenReturn(segment);

    interceptor.traceAroundSqsListeners(pjp);

    InOrder inOrder = inOrder(recorder);
    inOrder.verify(recorder).beginSegment(TRACING_NAME);
    inOrder.verify(recorder).beginSubsegment("TestTracedClass");
    inOrder.verify(recorder).beginSubsegment("testTracedMethod");

    verify(segment, never()).setRuleName(any());
    verify(segment).setSampled(false);
    verify(pjp).proceed();
  }

  @Test
  void shouldTraceAroundSqsListenerWhenEmptyTraceHeader() throws Throwable {
    Signature signature = mock(Signature.class);
    when(signature.getDeclaringType()).thenReturn(TestTracedClass.class);
    when(signature.getName()).thenReturn("testTracedMethod");

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getSignature()).thenReturn(signature);

    TraceHeader traceHeader = new TraceHeader();
    Map<String, Object> headers = Map.of(SQS_AWS_TRACE_HEADER, traceHeader.toString());
    Message<String> message = new GenericMessage<>("testPayload", headers);
    when(pjp.getArgs()).thenReturn(new Object[]{message});

    AWSXRayRecorder recorder = mock(AWSXRayRecorder.class);
    AWSXRay.setGlobalRecorder(recorder);

    Segment segment = mock(Segment.class);
    when(recorder.beginSegment(any())).thenReturn(segment);

    interceptor.traceAroundSqsListeners(pjp);

    InOrder inOrder = inOrder(recorder);
    inOrder.verify(recorder).beginSegment(TRACING_NAME);
    inOrder.verify(recorder).beginSubsegment("TestTracedClass");
    inOrder.verify(recorder).beginSubsegment("testTracedMethod");

    verify(segment, never()).setRuleName(any());
    verify(segment).setSampled(false);
    verify(pjp).proceed();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldTraceAroundSqsListenerWhenTraceHeaderPopulated(boolean sampled) throws Throwable {
    Signature signature = mock(Signature.class);
    when(signature.getDeclaringType()).thenReturn(TestTracedClass.class);
    when(signature.getName()).thenReturn("testTracedMethod");

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getSignature()).thenReturn(signature);

    SampleDecision sampleDecision = sampled ? SampleDecision.SAMPLED : SampleDecision.NOT_SAMPLED;
    TraceHeader traceHeader = new TraceHeader(ROOT_TRACE_ID, PARENT_TRACE_ID.toString(),
        sampleDecision);
    Map<String, Object> headers = Map.of(SQS_AWS_TRACE_HEADER, traceHeader.toString());
    Message<String> message = new GenericMessage<>("testPayload", headers);
    when(pjp.getArgs()).thenReturn(new Object[]{message});

    AWSXRayRecorder recorder = mock(AWSXRayRecorder.class);
    AWSXRay.setGlobalRecorder(recorder);

    Segment segment = mock(Segment.class);
    when(recorder.beginSegment(any(), any(), any())).thenReturn(segment);

    interceptor.traceAroundSqsListeners(pjp);

    InOrder inOrder = inOrder(recorder);
    inOrder.verify(recorder).beginSegment(TRACING_NAME, ROOT_TRACE_ID, PARENT_TRACE_ID.toString());
    inOrder.verify(recorder).beginSubsegment("TestTracedClass");
    inOrder.verify(recorder).beginSubsegment("testTracedMethod");

    verify(segment, never()).setRuleName(any());
    verify(segment).setSampled(sampled);
    verify(pjp).proceed();
  }

  /**
   * A dummy class for trace testing.
   */
  private static class TestTracedClass {

  }
}
