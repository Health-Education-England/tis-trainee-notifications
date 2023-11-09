package uk.nhs.tis.trainee.notifications.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * A programme membership.
 */
@Data
public class ProgrammeMembership {

  private String tisId;
  private LocalDate startDate;
  private List<Curriculum> curricula = new ArrayList<>();
}
