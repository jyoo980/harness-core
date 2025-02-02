/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import software.wings.api.DeploymentSummary;
import software.wings.beans.InfrastructureMapping;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@OwnedBy(CDP)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public interface InstanceSyncPerpetualTaskCreator {
  List<String> createPerpetualTasks(InfrastructureMapping infrastructureMapping);

  List<String> createPerpetualTasksForNewDeployment(List<DeploymentSummary> deploymentSummaries,
      List<PerpetualTaskRecord> existingPerpetualTasks, InfrastructureMapping infrastructureMapping);

  default List<PerpetualTaskRecord> createPerpetualTasksBackup(List<DeploymentSummary> deploymentSummaries,
      List<PerpetualTaskRecord> existingPerpetualTasks, InfrastructureMapping infrastructureMapping) {
    return Collections.emptyList();
  }

  default Optional<String> restorePerpetualTask(
      PerpetualTaskRecord perpetualTask, List<PerpetualTaskRecord> existingPerpetualTasks) {
    throw new UnsupportedOperationException("Restore perpetual task is not yet implemented for this handler");
  }
}
