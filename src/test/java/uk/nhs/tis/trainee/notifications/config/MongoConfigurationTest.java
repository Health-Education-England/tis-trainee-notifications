///*
// * The MIT License (MIT)
// *
// * Copyright 2024 Crown Copyright (Health Education England)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
// * associated documentation files (the "Software"), to deal in the Software without restriction,
// * including without limitation the rights to use, copy, modify, merge, publish, distribute,
// * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all copies or
// * substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
// * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//
//package uk.nhs.tis.trainee.notifications.config;
//
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.util.Date;
//import java.util.Optional;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
//import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
//import org.springframework.test.context.TestPropertySource;
//
//@SpringBootTest
//@UseTransactionalTestContainers
//@EnableMongoRepositories(basePackageClasses = MongoDbTransactionApplication.class)
//@LoadTransactionSupport
//@TestPropertySource(properties = {
//    "app.transactions.mongo.enabled=false"
//})
//class MongoConfigurationTest {
//
//  @Autowired
//  private TxMessageRepository repo;
//
//  @Autowired
//  private MongoTemplate template;
//
//  @Autowired
//  private MappingMongoConverter mongoConverter;
//
//  @BeforeEach
//  public void setup() {
//    super.setup();
//    assertNotNull(template);
//  }
//
//  @Test
//  void saveAndReadSimple() {
//    LocalDate now = LocalDate.now();
//    TxMessage message = new TxMessage();
//    message.setDate(now);
//    message.setMessage("ta da");
//
//    assertNull(message.getId());
//
//    TxMessage saved = repo.save(message);
//
//    assertEquals(message, saved);
//
//    String custId = message.getId();
//    assertNotNull(custId);
//
//  }
//
//  void checkReadAndWrite(LocalDate value) {
//    TxMessage message = new TxMessage();
//    message.setDate(value);
//    message.setMessage("ta da");
//
//    assertNull(message.getId());
//
//    TxMessage saved = repo.save(message);
//
//    assertEquals(message, saved);
//
//    String messageId = message.getId();
//    assertNotNull(messageId);
//
//    Optional<TxMessage> fromDb = repo.findById(messageId);
//    assertTrue(fromDb.isPresent());
//    assertEquals(value, fromDb.get().getDate());
//  }
//
//  @Nested
//  class RepositoryTests {
//
//    @Test
//    void saveAndReadLocalDateNull() {
//      checkReadAndWrite(null);
//    }
//
//    @Test
//    void saveAndReadLocalDateMin() {
//      checkReadAndWrite(LocalDate.MIN);
//    }
//
//    @Test
//    void saveAndReadLocalDateMax() {
//      checkReadAndWrite(LocalDate.MAX);
//    }
//
//    @Test
//    void saveAndReadLocalDate() {
//      checkReadAndWrite(LocalDate.now());
//    }
//
//    @Test
//    void saveAndReadLocalDateFromString() {
//      checkReadAndWrite(LocalDate.parse("-999999999-01-01"));
//    }
//
//    @Test
//    void saveAndReadBadLocalDateFromString() {
//      RuntimeException rte = assertThrows(RuntimeException.class, () -> {
//        checkReadAndWrite(LocalDate.parse("-999999998-01-01"));
//      });
//      assertEquals("Failed to convert from type [java.time.LocalDate] to type [java.util.Date] for value '-999999998-01-01'; nested exception is java.lang.IllegalArgumentException: java.lang.ArithmeticException: long overflow", rte.getMessage());
//    }
//
//  }
//
//  @Nested
//  class ConverterTests {
//
//    <T> T checkConversion(Object value, Class<T> clazz) {
//      return mongoConverter.getConversionService().convert(value, clazz);
//    }
//
//    @Nested
//    class LocalDateToDateAndBackTests {
//
//      void checkLocalDateToDateAndBack(LocalDate localDate){
//        assertEquals(localDate, checkConversion(checkConversion(localDate, Date.class),LocalDate.class));
//      }
//
//      @Test
//      void testNullLocalDate() {
//        checkLocalDateToDateAndBack(null);
//      }
//
//      @Test
//      void testLocalDateNow() {
//        checkLocalDateToDateAndBack(LocalDate.now());
//      }
//
//      @Test
//      void testLocalDateMin() {
//        checkLocalDateToDateAndBack(LocalDate.MIN);
//      }
//
//      @Test
//      void testLocalDateMax() {
//        checkLocalDateToDateAndBack(LocalDate.MAX);
//      }
//    }
//
//    @Nested
//    class DateToLocalDateAndBackTests {
//
//      void checkDateToLocalDateAndBack(Date date) {
//        checkDateToLocalDateAndBack(date, date);
//      }
//
//      void checkDateToLocalDateAndBack(Date date, Date expected) {
//        assertEquals(expected, checkConversion(checkConversion(date, LocalDate.class), Date.class));
//      }
//
//      @Test
//      void testNullDate() {
//        checkDateToLocalDateAndBack(null);
//      }
//
//      @Test
//      void testDateNow() {
//        Date now = getDate(16, 11, 5);
//        Date expected = getDate(0, 0, 0);
//        checkDateToLocalDateAndBack(now, expected);
//      }
//
//      @Test
//      void testDateMinValue() {
//        checkDateToLocalDateAndBack(new Date(Long.MIN_VALUE));
//      }
//
//      @Test
//      void testLocalDateMax() {
//        checkDateToLocalDateAndBack(new Date(Long.MAX_VALUE));
//      }
//    }
//
//    private Date getDate(int hours, int mins, int secs) {
//      ZonedDateTime time = ZonedDateTime.of(
//          LocalDateTime.of(2020, 6, 12, hours, mins, secs),
//          ZoneId.systemDefault());
//      return new Date(time.toInstant().toEpochMilli());
//    }
//  }
//
//}