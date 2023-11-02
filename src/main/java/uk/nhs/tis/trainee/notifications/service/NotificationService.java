package uk.nhs.tis.trainee.notifications.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * A service for executing notification scheduling jobs.
 */
@Slf4j
@Component
public class NotificationService implements Job {

  /**
   * Execute a given notification job.
   *
   * @param jobExecutionContext The job execution context.
   * @throws JobExecutionException if the job could not be executed.
   */
  @Override
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    //get job details (ProgrammeMembershipEvent, NotificationType)
    //use job details to check status of trainee (signed-up, submitted coj, submitted formR)
    //send email using correct template
    String jobKey = jobExecutionContext.getJobDetail().getKey().toString();
    String processedOn = Instant.now().toString();
    Map<String, String> result = new HashMap<>();
    JobDataMap jobDetails = jobExecutionContext.getJobDetail().getJobDataMap();

    log.info("Sent {} notification for {}", jobKey, jobDetails.getString("tisId"));
    result.put("status", "sent " + processedOn);
    jobExecutionContext.setResult(result);
  }
}
