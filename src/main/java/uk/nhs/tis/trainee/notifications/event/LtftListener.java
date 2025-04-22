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

package uk.nhs.tis.trainee.notifications.event;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT_UPDATED;

import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.LtftDto;
import uk.nhs.tis.trainee.notifications.dto.LtftEvent;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.mapper.LtftMapper;
import uk.nhs.tis.trainee.notifications.service.EmailService;

/**
 * A listener for LTFT update events.
 */
@Slf4j
@Component
public class LtftListener {
  private final EmailService emailService;
  private LtftMapper ltftMapper;
  private final Map<String, String> templateVersions;

  /**
   * Construct a listener for LTFT events.
   *
   * @param emailService The service to use for sending emails.
   * @param submittedTemplateVersion The email template version for submitted status.
   * @param updatedTemplateVersion The email template version for updated status.
   */
  public LtftListener(
      EmailService emailService,
      LtftMapper ltftMapper,
      @Value("${application.template-versions.ltft-submitted.email}")
      String submittedTemplateVersion,
      @Value("${application.template-versions.ltft-updated.email}") String updatedTemplateVersion) {
    this.emailService = emailService;
    this.ltftMapper = ltftMapper;
    this.templateVersions = Map.of(
        "SUBMITTED", submittedTemplateVersion,
        "UNSUBMITTED", updatedTemplateVersion,
        "APPROVED", updatedTemplateVersion,
        "WITHDRAWN", updatedTemplateVersion
    );
  }

  /**
   * Handle LTFT update events.
   *
   * @param event The LTFT update event message.
   * @throws MessagingException If the message could not be sent.
   */
  @SqsListener("${application.queues.ltft-updated}")
  public void handleLtftUpdate(LtftEvent event) throws MessagingException {
    log.info("Handling LTFT update event {}.", event);

    if (event.record() == null || event.record().getData() == null) {
      log.info("Ignoring non LTFT update event: {}", event);
      return;
    }

    Map<String, String> data = event.record().getData();

    LtftDto.LtftContent.ProgrammeMembershipDetails programmeMembership =
        new LtftDto.LtftContent.ProgrammeMembershipDetails(data.get("managingDeanery"));
    LtftDto.LtftContent content = new LtftDto.LtftContent(data.get("ltftName"), programmeMembership);

    LtftDto.LtftStatus.StatusDetails statusDetails = new LtftDto.LtftStatus.StatusDetails(
        data.get("state"),
        data.get("timestamp") != null ? Instant.parse(data.get("timestamp")) : null
    );
    LtftDto.LtftStatus status = new LtftDto.LtftStatus(statusDetails);

    LtftDto dto = new LtftDto(
        data.get("traineeTisId"),
        data.get("formRef"),
        content,
        status
    );

    LtftUpdateEvent eventEntity = ltftMapper.toEntity(dto);

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("ltftName", eventEntity.content().name());
    templateVariables.put("status", eventEntity.status().current().state());
    templateVariables.put("eventDate", eventEntity.status().current().timestamp());
    templateVariables.put("formRef", eventEntity.formRef());
    templateVariables.put("managingDeanery", eventEntity.content().programmeMembership().managingDeanery());

    String traineeTisId = eventEntity.traineeTisId();
    String currentState = eventEntity.status().current().state();

    String localOfficeDetails = getLocalOfficeSupportFromDeanery(
        eventEntity.content().programmeMembership().managingDeanery());
    templateVariables.put("LocalOfficeDetails", localOfficeDetails);

    if (currentState == null) {
      throw new IllegalStateException("LTFT update state is null for trainee " + traineeTisId);
    }

    String templateVersion = templateVersions.get(currentState.toUpperCase());
    if (templateVersion == null) {
      throw new IllegalStateException("No template version configured for LTFT state: "
          + currentState);
    }

    emailService.sendMessageToExistingUser(
        traineeTisId,
        LTFT_UPDATED,
        templateVersion,
        templateVariables,
        null);

    log.info("LTFT {} notification sent for trainee {}.",
        "SUBMITTED".equalsIgnoreCase(currentState) ? "submitted" : "updated",
        traineeTisId);
  }

  public String getLocalOfficeSupportFromDeanery(String managingDeanery) {

    String localOfficeSupport;

    switch (managingDeanery) {
      case "NHSE Education East of England":
        localOfficeSupport =
            "https://heeoe.hee.nhs.uk/faculty-educators/less-full-time-training";
        break;
      case "NHSE Education Kent, Surrey and Sussex":
        localOfficeSupport = "https://hee.freshdesk.com/support/solutions/7000006974";
        break;
      case "NHSE Education East Midlands":
        localOfficeSupport = "https://www.eastmidlandsdeanery.nhs.uk/policies/ltft";
        break;
      case "NHSE Education Thames Valley":
        localOfficeSupport =
            "https://thamesvalley.hee.nhs.uk/resources-information/trainee-information/training-"
                + "options/less-than-full-time-training-ltftt/";
        break;
      case "NHSE Education North West London":
        localOfficeSupport = "https://hee.freshdesk.com/support/solutions/7000006974";
        break;
      case "NHSE Education North Central and East London":
        localOfficeSupport = "https://hee.freshdesk.com/support/solutions/7000006974";
        break;
      case "NHSE Education South London":
        localOfficeSupport = "https://hee.freshdesk.com/support/solutions/7000006974";
        break;
      case "NHSE Education North East":
        localOfficeSupport = "https://madeinheene.hee.nhs.uk/PG-Dean/Less-than-"
            + "full-time-training";
        break;
      case "NHSE Education West Midlands":
        localOfficeSupport = "https://www.westmidlandsdeanery.nhs.uk/support/trainees/"
            + "less-than-full-time-training";
        break;
      case "NHSE Education Yorkshire and the Humber":
        localOfficeSupport = "https://www.yorksandhumberdeanery.nhs.uk/professional-"
            + "support/policies/ltftt";
        break;
      case "NHSE Education North West":
        localOfficeSupport = "";
        break;
      case "NHSE Education Wessex":
        localOfficeSupport = "https://wessex.hee.nhs.uk/trainee-information/trainee-"
            + "journey/less-than-full-time-training/";
        break;
      case "NHSE Education South West":
        localOfficeSupport = "https://www.severndeanery.nhs.uk/about-us/new-starters-"
            + "information-page/";
        break;
      case "NHS Education for Scotland":
        localOfficeSupport = "";
        break;
      case "Health Education and Improvement Wales":
        localOfficeSupport = "";
        break;
      case "Defence Postgraduate Medical Deanery":
        localOfficeSupport = "";
        break;
      case "Northern Ireland Medical and Dental Training Agency":
        localOfficeSupport = "";
        break;
      default:
        localOfficeSupport = "https://www.hee.nhs.uk/";
    }

    return localOfficeSupport;
  }
}
