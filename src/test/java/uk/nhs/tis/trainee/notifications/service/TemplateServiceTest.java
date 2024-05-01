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

package uk.nhs.tis.trainee.notifications.service;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uk.nhs.tis.trainee.notifications.model.MessageType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;

class TemplateServiceTest {

  private static final String TIMEZONE = "Europe/London";
  private static final Instant GMT = Instant.parse("2021-02-03T23:05:06Z");
  private static final Instant BST = Instant.parse("2021-08-03T23:05:06Z");

  private TemplateService service;
  private TemplateEngine templateEngine;

  @BeforeEach
  void setUp() {
    templateEngine = mock(TemplateEngine.class);
    service = new TemplateService(templateEngine, TIMEZONE);
  }

  @Test
  void shouldBuildContextWithLocalizedDateWhenTimestampIsGmt() {
    Date date = Date.from(GMT);
    Context context = service.buildContext(Map.of("timestamp", date));

    Object timestamp = context.getVariable("timestamp");
    assertThat("Unexpected timestamp type.", timestamp, instanceOf(ZonedDateTime.class));

    ZonedDateTime zonedDateTime = (ZonedDateTime) timestamp;
    assertThat("Unexpected timestamp year.", zonedDateTime.getYear(), is(2021));
    assertThat("Unexpected timestamp month.", zonedDateTime.getMonth(), is(Month.FEBRUARY));
    assertThat("Unexpected timestamp day.", zonedDateTime.getDayOfMonth(), is(3));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getHour(), is(23));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getMinute(), is(5));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getSecond(), is(6));
    assertThat("Unexpected timestamp zone.", zonedDateTime.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldBuildContextWithLocalizedDateWhenTimestampIsBst() {
    Date date = Date.from(BST);
    Context context = service.buildContext(Map.of("timestamp", date));

    Object timestamp = context.getVariable("timestamp");
    assertThat("Unexpected timestamp type.", timestamp, instanceOf(ZonedDateTime.class));

    ZonedDateTime zonedDateTime = (ZonedDateTime) timestamp;
    assertThat("Unexpected timestamp year.", zonedDateTime.getYear(), is(2021));
    assertThat("Unexpected timestamp month.", zonedDateTime.getMonth(), is(Month.AUGUST));
    assertThat("Unexpected timestamp day.", zonedDateTime.getDayOfMonth(), is(4));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getHour(), is(0));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getMinute(), is(5));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getSecond(), is(6));
    assertThat("Unexpected timestamp zone.", zonedDateTime.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldBuildContextWithLocalizedInstantWhenTimestampIsGmt() {
    Context context = service.buildContext(Map.of("timestamp", GMT));

    Object timestamp = context.getVariable("timestamp");
    assertThat("Unexpected timestamp type.", timestamp, instanceOf(ZonedDateTime.class));

    ZonedDateTime zonedDateTime = (ZonedDateTime) timestamp;
    assertThat("Unexpected timestamp year.", zonedDateTime.getYear(), is(2021));
    assertThat("Unexpected timestamp month.", zonedDateTime.getMonth(), is(Month.FEBRUARY));
    assertThat("Unexpected timestamp day.", zonedDateTime.getDayOfMonth(), is(3));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getHour(), is(23));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getMinute(), is(5));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getSecond(), is(6));
    assertThat("Unexpected timestamp zone.", zonedDateTime.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldBuildContextWithLocalizedInstantWhenTimestampIsBst() {
    Context context = service.buildContext(Map.of("timestamp", BST));

    Object timestamp = context.getVariable("timestamp");
    assertThat("Unexpected timestamp type.", timestamp, instanceOf(ZonedDateTime.class));

    ZonedDateTime zonedDateTime = (ZonedDateTime) timestamp;
    assertThat("Unexpected timestamp year.", zonedDateTime.getYear(), is(2021));
    assertThat("Unexpected timestamp month.", zonedDateTime.getMonth(), is(Month.AUGUST));
    assertThat("Unexpected timestamp day.", zonedDateTime.getDayOfMonth(), is(4));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getHour(), is(0));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getMinute(), is(5));
    assertThat("Unexpected timestamp hour.", zonedDateTime.getSecond(), is(6));
    assertThat("Unexpected timestamp zone.", zonedDateTime.getZone(), is(ZoneId.of(TIMEZONE)));
  }

  @Test
  void shouldBuildContextWhenNoLocalizableTimestamp() {
    Object object = new Object();
    Context context = service.buildContext(Map.of("not-timestamp", object));

    Object notTimestamp = context.getVariable("not-timestamp");
    assertThat("Unexpected variable type.", notTimestamp, sameInstance(object));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldGetTemplatePathFromEnum(MessageType messageType, NotificationType notificationType) {
    String templatePath = service.getTemplatePath(messageType, notificationType, "v1.2.3");

    String[] templatePathParts = templatePath.split("/");
    assertThat("Unexpected template sub-path.", templatePathParts[0],
        is(messageType.getTemplatePath()));
    assertThat("Unexpected template name.", templatePathParts[1],
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templatePathParts[2], is("v1.2.3"));
  }

  @ParameterizedTest
  @MethodSource("uk.nhs.tis.trainee.notifications.MethodArgumentUtil#getTemplateCombinations")
  void shouldGetTemplatePathFromString(MessageType messageType, NotificationType notificationType) {
    String templatePath = service.getTemplatePath(messageType, notificationType.getTemplateName(),
        "v1.2.3");

    String[] templatePathParts = templatePath.split("/");
    assertThat("Unexpected template sub-path.", templatePathParts[0],
        is(messageType.getTemplatePath()));
    assertThat("Unexpected template name.", templatePathParts[1],
        is(notificationType.getTemplateName()));
    assertThat("Unexpected template version.", templatePathParts[2], is("v1.2.3"));
  }

  @Test
  void shouldProcessTemplateWhenVariableMapProvided() {
    String template = "templatePath";
    Set<String> selectors = Set.of("selector1", "selector2");
    Map<String, Object> templateVariables = Map.of("key1", "value1");

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    when(templateEngine.process(eq(template), eq(selectors), contextCaptor.capture())).thenReturn(
        "processedTemplate");

    String processed = service.process(template, selectors, templateVariables);

    assertThat("Unexpected processed template.", processed, is("processedTemplate"));

    Context context = contextCaptor.getValue();
    assertThat("Unexpected context variable.", context.getVariable("key1"), is("value1"));
  }

  @Test
  void shouldProcessTemplateWhenContextProvided() {
    String template = "templatePath";
    Set<String> selectors = Set.of("selector1", "selector2");
    Context context = new Context();

    when(templateEngine.process(template, selectors, context)).thenReturn("processedTemplate");

    String processed = service.process(template, selectors, context);

    assertThat("Unexpected processed template.", processed, is("processedTemplate"));
  }
}
