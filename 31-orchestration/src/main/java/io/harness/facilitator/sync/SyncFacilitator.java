package io.harness.facilitator.sync;

import io.harness.facilitate.Facilitator;
import io.harness.facilitate.FacilitatorResponse;
import io.harness.facilitate.FacilitatorType;
import io.harness.facilitate.modes.ExecutionMode;
import io.harness.state.io.StateTransput;
import io.harness.state.io.ambiance.Ambiance;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SyncFacilitator implements Facilitator {
  @Override
  public FacilitatorType getType() {
    return FacilitatorType.builder().type(FacilitatorType.SYNC).build();
  }

  @Override
  public FacilitatorResponse facilitate(Ambiance ambiance, List<StateTransput> inputs) {
    return FacilitatorResponse.builder().executionMode(ExecutionMode.SYNC).build();
  }
}
