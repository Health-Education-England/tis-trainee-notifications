package uk.nhs.tis.trainee.notifications.service;

import static org.quartz.DateBuilder.futureDate;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
  private final Scheduler scheduler;

  public RegistrationService(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  public void registerUser() throws SchedulerException {
    // save user
    // ...
    // schedule email
    JobDetail job = newJob(EmailJob.class)
        .withIdentity("email-job-" + "123")
        .usingJobData("userId", "123")
        .build();
    Trigger trigger = newTrigger()
        .withIdentity("trigger-email-job-" + "123")
        .startAt(futureDate(60, IntervalUnit.SECOND))
        .build();

    scheduler.scheduleJob(job, trigger);
  }
}
