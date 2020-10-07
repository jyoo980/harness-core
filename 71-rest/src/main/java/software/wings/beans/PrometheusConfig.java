package software.wings.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import io.harness.beans.UsageRestrictions;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.settings.SettingValue;
import software.wings.sm.StateType;
import software.wings.sm.states.APMVerificationState;
import software.wings.yaml.setting.VerificationProviderYaml;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by rsingh on 3/15/18.
 */
@Data
@JsonTypeName("PROMETHEUS")
@Builder
@EqualsAndHashCode(callSuper = false)
public class PrometheusConfig extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander {
  public static final String VALIDATION_URL = "api/v1/query?query=up";
  @Attributes(title = "URL", required = true) private String url;

  @SchemaIgnore @NotEmpty private String accountId;

  public PrometheusConfig() {
    super(StateType.PROMETHEUS.name());
  }

  public PrometheusConfig(String url, String accountId) {
    this();
    this.url = url;
    this.accountId = accountId;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities() {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(url));
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig() {
    return createAPMValidateCollectorConfig(VALIDATION_URL);
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig(String urlToFetch) {
    return APMValidateCollectorConfig.builder()
        .baseUrl(url)
        .url(urlToFetch)
        .collectionMethod(APMVerificationState.Method.GET)
        .headers(new HashMap<>())
        .options(new HashMap<>())
        .build();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class PrometheusYaml extends VerificationProviderYaml {
    private String prometheusUrl;

    @Builder
    public PrometheusYaml(
        String type, String harnessApiVersion, String prometheusUrl, UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.prometheusUrl = prometheusUrl;
    }
  }
}
