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

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.Jsr310Converters;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import uk.nhs.tis.trainee.notifications.model.History;

/**
 * Additional configuration for MongoDB.
 */
@Configuration
@Slf4j
public class MongoConfiguration {

  @Bean
  public MongoTemplate mongoTemplate(MongoDatabaseFactory dbFactory,
      MappingMongoConverter mongoConverter) {

    List<Converter<?,?>> converters = List.of(
        LocalDateToDateConverter.INSTANCE,
        DateToLocalDateConverter.INSTANCE);

    MongoCustomConversions customConversions = new MongoCustomConversions(converters);
    mongoConverter.setCustomConversions(customConversions);
    mongoConverter.afterPropertiesSet();
    return new MongoTemplate(dbFactory, mongoConverter);
  }

  @ReadingConverter
  public static class DateToLocalDateConverter implements Converter<Date, LocalDate> {

    public static final DateToLocalDateConverter INSTANCE = new DateToLocalDateConverter();

    public LocalDate convert(Date source) {
      log.info("Reader: converting date {} to localdate", source);
      if (source == null) {
        return null;
      }
      long timestamp = source.getTime();
      if (Long.MIN_VALUE == timestamp) {
        return LocalDate.MIN;
      }
      if (Long.MAX_VALUE == timestamp) {
        return LocalDate.MAX;
      }
      return Jsr310Converters.DateToLocalDateConverter.INSTANCE.convert(source);
    }
  }

  @WritingConverter
  public static class LocalDateToDateConverter implements Converter<LocalDate, Date> {

    public static final LocalDateToDateConverter INSTANCE = new LocalDateToDateConverter();

    public Date convert(LocalDate source) {
      log.info("Writer: converting localDate {} to date", source);
      try {
        if (LocalDate.MIN.equals(source)) {
          return new Date(Long.MIN_VALUE);
        }
        if (LocalDate.MAX.equals(source)) {
          return new Date(Long.MAX_VALUE);
        }
        return Jsr310Converters.LocalDateToDateConverter.INSTANCE.convert(source);
      } catch (RuntimeException ex) {
        log.error("Failed to convert from type [java.time.LocalDate] to type [java.util.Date] for value '{}'", source, ex);
        throw ex;
      }
    }
  }
}
