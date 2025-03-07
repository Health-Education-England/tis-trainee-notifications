/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.notifications.model;

import java.time.Instant;
import jdk.jshell.Snippet.Status;
import lombok.Data;
import uk.nhs.tis.trainee.notifications.model.content.LtftContent.Declarations;
import uk.nhs.tis.trainee.notifications.model.content.LtftContent.Discussions;
import uk.nhs.tis.trainee.notifications.model.content.LtftContent.Reasons;

/**
 * An LTFT.
 */
@Data
public class LTFT {

  private String traineeTisId;
  private String formRef;
  private int revision;
  private String name;

  private PersonalDetails personalDetails;
  private ProgrammeMembership programmeMembership;
  private Declarations declarations;
  private Discussions discussions;
  private CctChange change;
  private Reasons reasons;
  private Person assignedAdmin;
  private Status status;

  private Instant created;
  private Instant lastModified;

}

