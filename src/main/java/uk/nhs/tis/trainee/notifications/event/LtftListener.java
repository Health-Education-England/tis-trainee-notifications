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
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.notifications.dto.LtftUpdateEvent;
import uk.nhs.tis.trainee.notifications.service.EmailService;

/**
 * A listener for LTFT update events.
 */
@Slf4j
@Component
public class LtftListener {
  private final EmailService emailService;

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
      @Value("${application.template-versions.ltft-submitted.email}")
      String submittedTemplateVersion,
      @Value("${application.template-versions.ltft-updated.email}") String updatedTemplateVersion) {
    this.emailService = emailService;
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
  public void handleLtftUpdate(LtftUpdateEvent event) throws MessagingException {
    log.info("Handling LTFT update event {}.", event);

    Map<String, Object> templateVariables = new HashMap<>();
    templateVariables.put("ltftName", event.content().name());
    templateVariables.put("status", event.status().current().state());
    templateVariables.put("eventDate", event.status().current().timestamp());
    templateVariables.put("formRef", event.formRef());

    String dbc = event.content().programmeMembership().designatedBodyCode();
    templateVariables.put("dbc", dbc);

    String traineeTisId = event.traineeTisId();
    String currentState = event.status().current().state();

    Map<String, String> localOfficeDetails = getLocalOfficeDetailsFromDbc(dbc);

    templateVariables.put("LocalOfficeDetails", localOfficeDetails);

    if (currentState == null) {
      throw new IllegalStateException("LTFT update state is null for trainee " + traineeTisId);
    }

    String templateVersion = templateVersions.get(currentState);
    if (templateVersion == null) {
      throw new IllegalStateException("No template version configured for LTFT state: " +
          currentState);
    }

    emailService.sendMessageToExistingUser(traineeTisId, LTFT_UPDATED, templateVersion,
        templateVariables, null);

    log.info("LTFT {} notification sent for trainee {}.",
        "SUBMITTED".equalsIgnoreCase(currentState) ? "submitted" : "updated",
        traineeTisId);
  }

  public Map<String, String> getLocalOfficeDetailsFromDbc(String dbc) {

    Map<String, String> details = new HashMap<>();

    switch (dbc) {
      case "1-1RSSQ05": // East of England
        details.put("localOffice", "NHSE Education East of England");
        details.put("localOfficeSupport",
            "https://heeoe.hee.nhs.uk/faculty-educators/less-full-time-training");
        break;
      case "1-1RUZV1D": // Kent, Surrey and Sussex
        details.put("localOffice", "NHSE Education Kent, Surrey and Sussex");
        details.put("localOfficeSupport", "https://hee.freshdesk.com/support/solutions/7000006974");
        break;
      case "1-1RSSPZ7": // East Midlands
        details.put("localOffice", "NHSE Education East Midlands");
        details.put("localOfficeSupport", "https://www.eastmidlandsdeanery.nhs.uk/policies/ltft");
        break;
      case "1-1RSSQ6R": // Thames Valley
        details.put("localOffice", "NHSE Education Thames Valley");
        details.put("localOfficeSupport",
            "https://thamesvalley.hee.nhs.uk/resources-information/trainee-information/training-"
                + "options/less-than-full-time-training-ltftt/");
        break;
      case "1-1RUZV6H": // North West London
        details.put("localOffice", "NHSE Education North West London");
        details.put("localOfficeSupport", "https://hee.freshdesk.com/support/solutions/7000006974");
        break;
      case "1-1RUZV4H": // North Central and East London
        details.put("localOffice", "NHSE Education North Central and East London");
        details.put("localOfficeSupport", "https://hee.freshdesk.com/support/solutions/7000006974");
        break;
      case "1-1RSSQ5L": // South London
        details.put("localOffice", "NHSE Education South London");
        details.put("localOfficeSupport", "https://hee.freshdesk.com/support/solutions/7000006974");
        break;
      case "1-1RSSQ1B": // North East
        details.put("localOffice", "NHSE Education North East");
        details.put("localOfficeSupport", "https://madeinheene.hee.nhs.uk/PG-Dean/Less-than-"
            + "full-time-training");
        break;
      case "1-1RUZUYF": // West Midlands
        details.put("localOffice", "NHSE Education West Midlands");
        details.put("localOfficeSupport", "https://www.westmidlandsdeanery.nhs.uk/support/trainees/"
            + "less-than-full-time-training");
        break;
      case "1-1RSG4X0": // Yorkshire and the Humber
        details.put("localOffice", "NHSE Education Yorkshire and the Humber");
        details.put("localOfficeSupport", "https://www.yorksandhumberdeanery.nhs.uk/professional-"
            + "support/policies/ltftt");
        break;
      case "1-1RSSQ2H": // North West
        details.put("localOffice", "NHSE Education North West");
        details.put("localOfficeSupport", "");
        break;
      case "1-1RUZUSF": // Wessex
        details.put("localOffice", "NHSE Education Wessex");
        details.put("localOfficeSupport", "https://wessex.hee.nhs.uk/trainee-information/trainee-"
            + "journey/less-than-full-time-training/");
        break;
      case "1-1RUZUVB": // South West
        details.put("localOffice", "NHSE Education South West");
        details.put("localOfficeSupport", "https://www.severndeanery.nhs.uk/about-us/new-starters-"
            + "information-page/");
        break;
      case "1-8W6121": // Scotland
        details.put("localOffice", "NHS Education for Scotland");
        details.put("localOfficeSupport", "");
        break;
      case "1-36QUOY": // Wales
        details.put("localOffice", "Health Education and Improvement Wales");
        details.put("localOfficeSupport", "");
        break;
      case "1-2SXJST": // Defence
        details.put("localOffice", "Defence Postgraduate Medical Deanery");
        details.put("localOfficeSupport", "");
        break;
      case "1-25U-830": // Northern Ireland
        details.put("localOffice", "Northern Ireland Medical and Dental Training Agency");
        details.put("localOfficeSupport", "");
        break;
      default:
        details.put("localOffice", "Unknown Local Office");
        details.put("localOfficeSupport", "https://www.hee.nhs.uk/");
    }

    return details;
  }
}
