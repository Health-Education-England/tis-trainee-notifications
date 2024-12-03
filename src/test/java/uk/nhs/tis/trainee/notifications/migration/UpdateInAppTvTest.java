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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.notifications.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.notifications.TestContainerConfiguration.MONGODB;
import static uk.nhs.tis.trainee.notifications.migration.UpdateInAppTvContact.DESIGNATED_BODY;
import static uk.nhs.tis.trainee.notifications.model.NotificationStatus.UNREAD;
import static uk.nhs.tis.trainee.notifications.model.NotificationType.LTFT;
import static uk.nhs.tis.trainee.notifications.service.NotificationService.CONTACT_TYPE_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_FIELD;
import static uk.nhs.tis.trainee.notifications.service.ProgrammeMembershipService.LOCAL_OFFICE_CONTACT_TYPE_FIELD;

import com.mongodb.MongoException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.HrefType;
import uk.nhs.tis.trainee.notifications.model.LocalOfficeContactType;
import uk.nhs.tis.trainee.notifications.model.NotificationStatus;
import uk.nhs.tis.trainee.notifications.model.NotificationType;
import uk.nhs.tis.trainee.notifications.service.NotificationService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UpdateInAppTvTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer MONGODB_CONTAINER = new MongoDBContainer(MONGODB);

  @SpyBean
  private MongoTemplate mongoTemplate;

  @MockBean
  private NotificationService notificationService;

  private UpdateInAppTvContact migrator;

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(History.class);
    migrator = new UpdateInAppTvContact(mongoTemplate, notificationService);


  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      LTFT | LTFT | email@example.com | PROTOCOL_EMAIL
      DEFERRAL | DEFERRAL | https://example.com | ABSOLUTE_URL
      SPONSORSHIP | SPONSORSHIP | not a href | NON_HREF""")
  void shouldUpdateInAppTvContact(NotificationType notificationType,
                                  LocalOfficeContactType localOfficeContactType,
                                  String contact, HrefType contactType) {
    List<Map<String, String>> contactList = List.of(
        Map.of(CONTACT_TYPE_FIELD, localOfficeContactType.getContactTypeName()));
    when(notificationService.getOwnerContactList("Thames Valley"))
        .thenReturn(contactList);
    when(notificationService.getOwnerContact(contactList, localOfficeContactType,
        LocalOfficeContactType.TSS_SUPPORT, "")).thenReturn(contact);
    when(notificationService.getHrefTypeForContact(contact)).thenReturn(
        contactType.getHrefTypeName());
    History history = buildHistoryByType(notificationType, DESIGNATED_BODY, UNREAD);

    migrator.migrate();

    History migratedHistoryLtft = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected LTFT contact.",
        migratedHistoryLtft.template().variables().get(LOCAL_OFFICE_CONTACT_FIELD),
        is(contact));
    assertThat("Unexpected LTFT contact type.",
        migratedHistoryLtft.template().variables().get(LOCAL_OFFICE_CONTACT_TYPE_FIELD),
        is(contactType.getHrefTypeName()));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"LTFT", "DEFERRAL", "SPONSORSHIP"})
  void shouldNotUpdateInAppTvContactWithWrongType(NotificationType notificationType) {
    History history = buildHistoryByType(notificationType, DESIGNATED_BODY, UNREAD);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected contact.",
        migratedHistory.template().variables().get(LOCAL_OFFICE_CONTACT_FIELD),
        is(nullValue()));
    assertThat("Unexpected contact type.",
        migratedHistory.template().variables().get(LOCAL_OFFICE_CONTACT_TYPE_FIELD),
        is(nullValue()));
  }

  @ParameterizedTest
  @EnumSource(value = NotificationStatus.class, mode = EnumSource.Mode.EXCLUDE,
      names = "UNREAD")
  void shouldNotUpdateNonUnreadInAppTvContact(NotificationStatus notificationStatus) {
    History history = buildHistoryByType(LTFT, DESIGNATED_BODY, notificationStatus);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected contact.",
        migratedHistory.template().variables().get(LOCAL_OFFICE_CONTACT_FIELD),
        is(nullValue()));
    assertThat("Unexpected contact type.",
        migratedHistory.template().variables().get(LOCAL_OFFICE_CONTACT_TYPE_FIELD),
        is(nullValue()));
  }

  @Test
  void shouldNotUpdateNonTvInAppTvContact() {
    History history = buildHistoryByType(LTFT, "something else", UNREAD);

    migrator.migrate();

    History migratedHistory = mongoTemplate.findById(history.id(), History.class);

    assertThat("Unexpected contact.",
        migratedHistory.template().variables().get(LOCAL_OFFICE_CONTACT_FIELD),
        is(nullValue()));
    assertThat("Unexpected contact type.",
        migratedHistory.template().variables().get(LOCAL_OFFICE_CONTACT_TYPE_FIELD),
        is(nullValue()));
  }

  @Test
  void shouldNotFailMigration() {
    doThrow(new MongoException("error")).when(mongoTemplate)
        .updateMulti(any(), any(), anyString());

    Assertions.assertDoesNotThrow(() -> migrator.migrate());
  }

  @Test
  void rollback() {
    Mockito.clearInvocations(mongoTemplate);
    migrator.rollback();
    verifyNoInteractions(mongoTemplate);
  }

  private History buildHistoryByType(NotificationType notificationType,
                                     String designatedBody,
                                     NotificationStatus notificationStatus) {
    ObjectId objectId = ObjectId.get();
    Map<String, Object> variables = new HashMap<>();
    variables.put("designatedBody", designatedBody);
    History.TemplateInfo templateInfo =
        new History.TemplateInfo(null, null, variables);
    History history = History.builder()
        .id(objectId)
        .template(templateInfo)
        .type(notificationType)
        .status(notificationStatus)
        .build();

    return mongoTemplate.save(history);
  }
}
