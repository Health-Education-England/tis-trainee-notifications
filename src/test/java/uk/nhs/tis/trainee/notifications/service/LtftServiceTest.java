/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED_ASSIGNMENT;

import jakarta.mail.MessagingException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties;
import uk.nhs.tis.trainee.notifications.config.TemplateVersionsProperties.MessageTypeVersions;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftStatusAssignedDto;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent.LtftStatusModifiedByDto;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;

class LtftServiceTest {

  private static final String VERSION = "v1.0.0";
  private static final String TRAINEE_ID = "47165";
  private static final String ADMIN_EMAIL = "admin@example.com";
  private static final String ADMIN_NAME = "Admin Name";
  private static final String FORM_ID = "form-123";
  private static final String FORM_REF = "ltft_47165_001";

  private LtftService ltftService;
  private EmailService emailService;
  private HistoryService historyService;
  private RedisTemplate<String, String> redisTemplate;
  private ValueOperations<String, String> valueOperations;

  @BeforeEach
  void setUp() {
    emailService = mock(EmailService.class);
    historyService = mock(HistoryService.class);
    redisTemplate = mock(RedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        "ltft-updated-assignment", new MessageTypeVersions(VERSION, null)
    ));

    ltftService = new LtftService(emailService, historyService, templateVersions,
        redisTemplate, true, Duration.ofMinutes(15));
  }

  @Test
  void shouldIgnoreWhenNoAssignedAdmin() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .assignedAdmin(null)
        .build();

    ltftService.handleAssignmentNotification(event);

    verifyNoInteractions(emailService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldIgnoreWhenAssignedAdminHasNoEmail() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .assignedAdmin(LtftStatusAssignedDto.builder().name(ADMIN_NAME).build())
        .build();

    ltftService.handleAssignmentNotification(event);

    verifyNoInteractions(emailService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldIgnoreWhenModifiedByNameMatchesAssignedAdminName() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .modifiedBy(LtftStatusModifiedByDto.builder()
            .name(ADMIN_NAME.toLowerCase()).role("ADMIN").build())
        .build();

    ltftService.handleAssignmentNotification(event);

    verifyNoInteractions(emailService);
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldSendEmailWhenNoCooldownActive() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .formRef(FORM_REF)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .build();

    when(valueOperations.setIfAbsent(
        LtftService.ASSIGNMENT_COOLDOWN_KEY_PREFIX + ADMIN_EMAIL,
        "1", Duration.ofMinutes(15)))
        .thenReturn(true);

    ltftService.handleAssignmentNotification(event);

    verify(emailService).sendMessage(eq(TRAINEE_ID), eq(ADMIN_EMAIL),
        eq(LTFT_UPDATED_ASSIGNMENT), eq(VERSION), any(), any(), eq(false));
    verifyNoInteractions(historyService);
  }

  @Test
  void shouldNotSendEmailWhenNotificationsDisabled() throws MessagingException {
    TemplateVersionsProperties templateVersions = new TemplateVersionsProperties(Map.of(
        "ltft-updated-assignment", new MessageTypeVersions(VERSION, null)
    ));
    ltftService = new LtftService(emailService, historyService, templateVersions,
        redisTemplate, false, Duration.ofMinutes(15));

    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .formRef(FORM_REF)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .build();

    when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

    ltftService.handleAssignmentNotification(event);

    verify(emailService).sendMessage(eq(TRAINEE_ID), eq(ADMIN_EMAIL),
        eq(LTFT_UPDATED_ASSIGNMENT), eq(VERSION), any(), any(), eq(true));
  }

  @Test
  void shouldSkipAndLogHistoryWhenCooldownActive() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .formRef(FORM_REF)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .build();

    when(valueOperations.setIfAbsent(
        LtftService.ASSIGNMENT_COOLDOWN_KEY_PREFIX + ADMIN_EMAIL,
        "1", Duration.ofMinutes(15)))
        .thenReturn(false);

    ltftService.handleAssignmentNotification(event);

    verifyNoInteractions(emailService);

    ArgumentCaptor<History> historyCaptor = ArgumentCaptor.forClass(History.class);
    verify(historyService).save(historyCaptor.capture());

    History saved = historyCaptor.getValue();
    assertThat("Unexpected status.", saved.status(), is(NotificationStatus.SKIPPED));
    assertThat("Unexpected type.", saved.type(), is(LTFT_UPDATED_ASSIGNMENT));
    assertThat("Unexpected recipient contact.", saved.recipient().contact(), is(ADMIN_EMAIL));
    assertThat("Unexpected recipient id.", saved.recipient().id(), is(TRAINEE_ID));
  }

  @Test
  void shouldPassAdminNameAsTemplateVariable() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .formRef(FORM_REF)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .build();

    when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

    ltftService.handleAssignmentNotification(event);

    ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.captor();
    verify(emailService)
        .sendMessage(any(), any(), any(), any(), templateVarsCaptor.capture(), any(), anyBoolean());

    Map<String, Object> templateVariables = templateVarsCaptor.getValue();
    assertThat("Unexpected name variable.", templateVariables.get("name"), is(ADMIN_NAME));
  }

  @Test
  void shouldUseFifteenMinuteCooldown() throws MessagingException {
    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .build();

    when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

    ltftService.handleAssignmentNotification(event);

    verify(valueOperations).setIfAbsent(
        LtftService.ASSIGNMENT_COOLDOWN_KEY_PREFIX + ADMIN_EMAIL,
        "1",
        Duration.ofMinutes(15));
  }

  @Test
  void shouldThrowWhenNoTemplateVersionConfigured() {
    TemplateVersionsProperties templateVersionsWithNullEmail =
        new TemplateVersionsProperties(Map.of(
            "ltft-updated-assignment", new MessageTypeVersions(null, null)
        ));
    LtftService serviceWithNoTemplate = new LtftService(emailService, historyService,
        templateVersionsWithNullEmail, redisTemplate, true, Duration.ofMinutes(15));

    LtftUpdateEvent event = LtftUpdateEvent.builder()
        .traineeId(TRAINEE_ID)
        .formId(FORM_ID)
        .assignedAdmin(LtftStatusAssignedDto.builder()
            .name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build())
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> serviceWithNoTemplate.handleAssignmentNotification(event));
  }
}

