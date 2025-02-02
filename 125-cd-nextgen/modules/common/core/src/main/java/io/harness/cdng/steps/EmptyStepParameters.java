/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@RecasterAlias("io.harness.cdng.steps.EmptyStepParameters")
public class EmptyStepParameters implements StepParameters, WithDelegateSelector {
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return this.delegateSelectors;
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    this.delegateSelectors = delegateSelectors;
  }
}
