package io.harness.delegate.task;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.TaskData;
import io.harness.tasks.Task;

@OwnedBy(CDC)
public interface HDelegateTask extends Task {
  String getAccountId();
  TaskData getData();
}
