package io.harness.facilitator.modes.sync;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.ambiance.Ambiance;
import io.harness.annotations.Redesign;
import io.harness.annotations.dev.OwnedBy;
import io.harness.facilitator.PassThroughData;
import io.harness.state.io.StepInputPackage;
import io.harness.state.io.StepParameters;
import io.harness.state.io.StepResponse;

/**
 * Use this interface whn you want to perform synchronous requests.
 *
 * InterfaceDefinition:
 *
 * executeSync: This straight away responds with {@link StepResponse}
 */
@OwnedBy(CDC)
@Redesign
public interface SyncExecutable<T extends StepParameters> {
  StepResponse executeSync(
      Ambiance ambiance, T stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData);
}
