package io.harness.batch.processing.k8s.rcd;

import static io.harness.rule.OwnerRule.AVMOHAN;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1JobBuilder;
import io.kubernetes.client.util.Yaml;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class JobRcdCalculatorTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleAdd() throws Exception {
    assertThat(new JobRcdCalculator().computeResourceDiff("", jobYaml("100m", "1200Mi")))
        .isEqualTo(ResourceClaim.builder().cpuNano(100000000L).memBytes(1258291200L).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleDelete() throws Exception {
    assertThat(new JobRcdCalculator().computeResourceDiff(jobYaml("750m", "1300Mi"), ""))
        .isEqualTo(ResourceClaim.builder().cpuNano(-750000000L).memBytes(-1363148800L).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleUpdate() throws Exception {
    assertThat(new JobRcdCalculator().computeResourceDiff(jobYaml("0.1", "1300Mi"), jobYaml("100m", "1200Mi")))
        .isEqualTo(ResourceClaim.builder().cpuNano(0L).memBytes(-104857600L).build());
  }
  private String jobYaml(String cpu, String memory) {
    return Yaml.dump(
        new V1JobBuilder()
            .withNewSpec()
            .withNewTemplate()
            .withNewSpec()
            .addNewContainer()
            .withNewResources()
            .withRequests(ImmutableMap.of("cpu", Quantity.fromString(cpu), "memory", Quantity.fromString(memory)))
            .endResources()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build());
  }
}
