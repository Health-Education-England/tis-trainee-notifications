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

  //String personId,
  private String tisId;
  //String programmeName,
  private LocalDate startDate;
  private List<Curriculum> curricula = new ArrayList<>();
}
