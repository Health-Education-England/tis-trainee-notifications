/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.migration;

import static uk.nhs.tis.trainee.notifications.model.NotificationType.DEFERRAL;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.SPONSORSHIP;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_TYPE_FIELD;

import com.mongodb.MongoException;
import com.mongodb.client.result.UpdateResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

/**
 * Update Thames Valley contacts for all UNREAD in-app notifications.
 */
@Slf4j
@ChangeUnit(id = "updateInAppTvContact", order = "6")
public class UpdateInAppTvContact {

  protected static final String DESIGNATED_BODY = "NHSE Education Thames Valley";
  private static final String HISTORY_COLLECTION = "History";
  private final MongoTemplate mongoTemplate;
  private final NotificationService notificationService;


  /**
   * Constructor.
   */
  public UpdateInAppTvContact(MongoTemplate mongoTemplate,
                              NotificationService notificationService) {
    this.mongoTemplate = mongoTemplate;
    this.notificationService = notificationService;
  }

  /**
   * Update Thames Valley contacts for all UNREAD in-app notifications.
   */
  @Execution
  public void migrate() {
    List<Map<String, String>> contactList =
        notificationService.getOwnerContactList("Thames Valley");

    migrateByType(contactList, LTFT, LocalOfficeContactType.LTFT);
    migrateByType(contactList, DEFERRAL, LocalOfficeContactType.DEFERRAL);
    migrateByType(contactList, SPONSORSHIP, LocalOfficeContactType.SPONSORSHIP);
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'updateInAppTvContact' migration.");
  }

  /**
   * Update Thames Valley contacts by notification type.
   */
  private void migrateByType(List<Map<String, String>> contactList,
                             NotificationType notificationType,
                           LocalOfficeContactType localOfficeContactType) {
    CriteriaDefinition criteriaTv = Criteria.where("template.variables.designatedBody")
        .is(DESIGNATED_BODY);
    CriteriaDefinition criteriaUnread = Criteria.where("status").is("UNREAD");

    Query query = Query.query(criteriaTv);
    query.addCriteria(criteriaUnread);
    query.addCriteria(Criteria.where("type").is(notificationType));
    String localOfficeContact = notificationService.getOwnerContact(contactList,
        localOfficeContactType, LocalOfficeContactType.TSS_SUPPORT, "");
    String contactType =
        notificationService.getHrefTypeForContact(localOfficeContact);

    String fieldPrefix = "template.variables.";
    Update updateContact = Update.update(
        fieldPrefix + LOCAL_OFFICE_CONTACT_FIELD, localOfficeContact);
    Update updateContactType = Update.update(
        fieldPrefix + LOCAL_OFFICE_CONTACT_TYPE_FIELD, contactType);

    try {
      UpdateResult resultContact =
          mongoTemplate.updateMulti(query, updateContact, HISTORY_COLLECTION);
      mongoTemplate.updateMulti(query, updateContactType, HISTORY_COLLECTION);
      log.info("Thames Valley contact updated on {} {} historic notifications.",
          resultContact.getModifiedCount(), notificationType);
    } catch (MongoException e) {
      log.error("Unable to update Thames Valley {} contact due to an error: {} ",
          notificationType, e.toString());
    }
  }
}
