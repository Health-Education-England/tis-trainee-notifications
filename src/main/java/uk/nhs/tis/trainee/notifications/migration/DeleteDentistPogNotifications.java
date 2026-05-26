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
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.migration;

import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.SCHEDULED;

import com.mongodb.MongoException;
import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.tis.trainee.notifications.model.History;

/**
 * Delete orphan SCHEDULED history from DB.
 */
@Slf4j
@ChangeUnit(id = "deleteDentistPogNotifications", order = "13")
public class DeleteDentistPogNotifications {

  private final MongoTemplate mongoTemplate;

  /**
   * Constructor.
   */
  public DeleteDentistPogNotifications(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Delete past SCHEDULED history from DB.
   */
  @Execution
  public void migrate() {
    CriteriaDefinition notScheduled = Criteria.where("status").ne(SCHEDULED);
    CriteriaDefinition isPog = Criteria.where("type")
        .in("PROGRAMME_POG_MONTH_12", "PROGRAMME_POG_MONTH_6");
    CriteriaDefinition isProgrammeOfInterest
        = Criteria.where("template.variables.ProgrammeName").in(
        "Additional Dental Specialties - Dental and Maxillofacial Radiology - Legacy",
        "Additional Dental Specialties - Oral Medicine - Legacy",
        "Additional Dental Specialties - Oral Microbiology - Legacy",
        "Additional Dental Specialties Dental and Maxillofacial Radiology Pan LDN",
        "Additional Dental Specialties Oral and Maxillofacial Pathology Pan LDN",
        "Additional Dental Specialties Oral Medicine Pan LDN",
        "Dental and Maxillofacial Radiology",
        "Dental and Maxillofacial Radiology/HESW",
        "Dental Public Health",
        "Dental Public Health - Military - Local",
        "Dental Public Health/HESW",
        "Dental public health/Oxford",
        "Endodontics ",
        "Endodontics - Legacy",
        "Endodontics Pan LDN",
        "MER Dental Public Health",
        "MER Oral Medicine",
        "MER Orthodontics",
        "MER Paediatric Dentistry",
        "MER Restorative Dentistry",
        "MER Special Care Dentistry",
        "NWN Dental and Maxillofacial Radiology",
        "NWN Dental Public Health",
        "NWN Oral Surgery",
        "NWN Orthodontics",
        "NWN Paediatric Dentistry",
        "NWN Restorative Dentistry",
        "NWN Special Care Dentistry",
        "Oral and Maxillofacial Pathology",
        "Oral and Maxillofacial Pathology - Lecturer",
        "Oral and Maxillofacial Pathology/HESW",
        "Oral Medicine",
        "Oral Medicine - Lecturer",
        "Oral Medicine/HESW",
        "Oral Surgery",
        "Oral Surgery - Lecturer",
        "Oral Surgery - Legacy",
        "Oral Surgery Pan LDN",
        "Oral Surgery/HESW ",
        "Oral Surgery/Oxford/Wessex",
        "Orthodontics",
        "Orthodontics - Legacy",
        "Orthodontics Pan LDN",
        "Orthodontics/HESW",
        "Orthodontics/Oxford/Wessex",
        "Paediatric Dentistry",
        "Paediatric Dentistry - Legacy",
        "Paediatric Dentistry Pan LDN",
        "Paediatric Dentistry/HESW",
        "Paediatric Dentistry/Oxford/Wessex",
        "Paeds Dentistry - Lecturer",
        "Periodontics",
        "Periodontics Pan LDN",
        "Periodontics/HESW",
        "Periodontics?- Legacy",
        "Prosthodontics",
        "Prosthodontics Pan LDN",
        "Prosthodontics/HESW",
        "Prosthodontics?- Legacy",
        "Public health dental - Legacy",
        "Public health dental Pan LDN",
        "Restorative Dentistry",
        "Restorative Dentistry - Lecturer",
        "Restorative Dentistry - Legacy",
        "Restorative Dentistry Pan LDN",
        "Restorative Dentistry/HESW",
        "Restorative Dentistry/Oxford/Wessex",
        "Special Care Dentistry",
        "Special Care Dentistry - Legacy",
        "Special Care Dentistry Pan LDN",
        "Special Care Dentistry/HESW",
        "Special Care Dentistry/Oxford/Wessex");
    Query query = Query.query(notScheduled);
    query.addCriteria(isPog);
    query.addCriteria(isProgrammeOfInterest);

    try {
      DeleteResult result = mongoTemplate.remove(query, History.class);
      log.info("{} non-SCHEDULED POG history deleted for dental programmes",
          result.getDeletedCount());
    } catch (MongoException me) {
      log.error("Unable to delete non-SCHEDULED POG history for dental programmes due to an "
              + "error: {} ", me.toString());
    }
  }

  /**
   * Do not attempt rollback, the collection should be left as-is.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'DeleteDentistPogNotifications' migration.");
  }
}
