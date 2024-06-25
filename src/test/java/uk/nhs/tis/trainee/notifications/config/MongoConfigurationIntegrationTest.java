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

package uk.nhs.tis.trainee.notifications.config;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.notifications.model.History;
import uk.nhs.tis.trainee.notifications.model.History.TemplateInfo;

@SpringBootTest(properties = {"embedded.containers.enabled=true", "embedded.mongodb.enabled=true"})
@ActiveProfiles({"mongodb", "test"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class MongoConfigurationIntegrationTest {

  private MongoConfiguration configuration;

  @Autowired
  private MongoTemplate template;

  @Autowired
  private MappingMongoConverter mongoConverter;

  @BeforeEach
  void setUp() {
    configuration = new MongoConfiguration();
  }

  @Test
  void shouldRetrieveDateAsLocalDateWhenTargetIsObject() {
    ObjectId objectId = ObjectId.get();
    Map<String, Object> variables = Map.of("date", Date.from(Instant.now()));
    TemplateInfo templateInfo = new TemplateInfo(null, null, variables);
    History history = History.builder()
        .id(objectId)
        .template(templateInfo)
        .build();

    template.save(history);

    History savedHistory = template.findById(objectId, History.class);

    assert savedHistory != null;
    Map<String, Object> savedVariables = savedHistory.template().variables();
    assertThat(savedVariables.get("date"), instanceOf(LocalDate.class));
  }

  @Test
  void shouldRetrieveNullDateAsNullWhenTargetIsObject() {
    ObjectId objectId = ObjectId.get();
    Map<String, Object> variables = new HashMap<>();
    variables.put("date", null);
    TemplateInfo templateInfo = new TemplateInfo(null, null, variables);
    History history = History.builder()
        .id(objectId)
        .template(templateInfo)
        .build();

    template.save(history);

    History savedHistory = template.findById(objectId, History.class);

    assert savedHistory != null;
    Map<String, Object> savedVariables = savedHistory.template().variables();
    assertThat(savedVariables.get("date"), is(nullValue()));
  }

  @Test
  void shouldRetrieveMinimumDateAsMinimumLocalDateWhenTargetIsObject() {
    ObjectId objectId = ObjectId.get();
    Map<String, Object> variables = Map.of("date", new Date(Long.MIN_VALUE));
    TemplateInfo templateInfo = new TemplateInfo(null, null, variables);
    History history = History.builder()
        .id(objectId)
        .template(templateInfo)
        .build();

    template.save(history);

    History savedHistory = template.findById(objectId, History.class);

    assert savedHistory != null;
    Map<String, Object> savedVariables = savedHistory.template().variables();
    assertThat(savedVariables.get("date"), is(LocalDate.MIN));
  }

  @Test
  void shouldRetrieveMaximumDateAsMaximumLocalDateWhenTargetIsObject() {
    ObjectId objectId = ObjectId.get();
    Map<String, Object> variables = Map.of("date", new Date(Long.MAX_VALUE));
    TemplateInfo templateInfo = new TemplateInfo(null, null, variables);
    History history = History.builder()
        .id(objectId)
        .template(templateInfo)
        .build();

    template.save(history);

    History savedHistory = template.findById(objectId, History.class);

    assert savedHistory != null;
    Map<String, Object> savedVariables = savedHistory.template().variables();
    assertThat(savedVariables.get("date"), is(LocalDate.MAX));
  }
}
