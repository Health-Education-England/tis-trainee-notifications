package uk.nhs.tis.trainee.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailJob implements Job {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailJob.class);

  @Override
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    String userId = jobExecutionContext.getJobDetail().getJobDataMap().getString("userId");
    log.info("executing job {}", userId);
    // ...
  }
}
