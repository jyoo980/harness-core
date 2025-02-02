/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.trafficrouting.HeaderConfig;
import io.harness.delegate.task.k8s.trafficrouting.IstioProviderConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.RouteType;
import io.harness.delegate.task.k8s.trafficrouting.RuleType;
import io.harness.delegate.task.k8s.trafficrouting.SMIProviderConfig;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoute;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRouteRule;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoutingDestination;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class K8sTrafficRoutingHelperTest extends CategoryTest {
  private static final String STABLE_HOST = "stable";
  private static final String STAGE_HOST = "stage";
  private static final String ROOT_SERVICE = "rootService";
  private static final List<String> HOSTS = List.of("host1", "host2");
  private static final List<String> GATEWAYS = List.of("gateway1", "gateway2");
  private static final String RULE_NAME = "rule-name";
  private static final String URI_VALUE = "/someUri";
  private static final String SCHEME_VALUE = "http";
  private static final Integer PORT_VALUE = 8080;
  private static final String AUTHORITY_VALUE = "api.harness.io";
  private static final List<HeaderConfig> HEADER_CONFIG =
      List.of(HeaderConfig.builder().key("user-agent").value(".*Android.*").build(),
          HeaderConfig.builder().key("Content-Type").value("application/json").build(),
          HeaderConfig.builder().key("cookie").value("^(.*?;)?(type=insider)(;.*)?$").build());
  private static final List<HeaderConfig> HEADER_CONFIG_WITH_MATCH_TYPE =
      List.of(HeaderConfig.builder()
                  .key("user-agent")
                  .value(".*Android.*")
                  .matchType(io.harness.delegate.task.k8s.trafficrouting.MatchType.EXACT)
                  .build(),
          HeaderConfig.builder()
              .key("Content-Type")
              .value("application/json")
              .matchType(io.harness.delegate.task.k8s.trafficrouting.MatchType.EXACT)
              .build(),
          HeaderConfig.builder()
              .key("cookie")
              .value("^(.*?;)?(type=insider)(;.*)?$")
              .matchType(io.harness.delegate.task.k8s.trafficrouting.MatchType.EXACT)
              .build());
  private static final List<K8sTrafficRoutingMethodRuleSpec.Method> METHOD_VALUES =
      List.of(K8sTrafficRoutingMethodRuleSpec.Method.GET, K8sTrafficRoutingMethodRuleSpec.Method.POST);

  @InjectMocks private K8sTrafficRoutingHelper k8sTrafficRoutingHelper;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSmiHttpURISpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.SMI, getUriRuleSpec(null));

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    SMIProviderConfig smiProviderConfig = (SMIProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(smiProviderConfig.getRootService()).isEqualTo(ROOT_SERVICE);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.URI, URI_VALUE, null, null, null);
    assertDestinations(smiProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSmiHttpMethodSpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.SMI, getMethodRuleSpec(null));

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    SMIProviderConfig smiProviderConfig = (SMIProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(smiProviderConfig.getRootService()).isEqualTo(ROOT_SERVICE);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.METHOD, null,
        METHOD_VALUES.stream().map(Enum::name).collect(Collectors.toList()), null, null);
    assertDestinations(smiProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSmiHttpHeaderSpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.SMI, getHeaderRuleSpec(null));

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    SMIProviderConfig smiProviderConfig = (SMIProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(smiProviderConfig.getRootService()).isEqualTo(ROOT_SERVICE);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.HEADER, null, null, null, HEADER_CONFIG);
    assertDestinations(smiProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpUriSpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getUriRuleSpec(MatchType.PREFIX));

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    IstioProviderConfig istioProviderConfig = (IstioProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(istioProviderConfig.getHosts()).isEqualTo(HOSTS);
    assertThat(istioProviderConfig.getGateways()).isEqualTo(GATEWAYS);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.URI, URI_VALUE, null, MatchType.PREFIX, null);
    assertDestinations(istioProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpSchemeSpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getSchemeRuleSpec());

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    IstioProviderConfig istioProviderConfig = (IstioProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(istioProviderConfig.getHosts()).isEqualTo(HOSTS);
    assertThat(istioProviderConfig.getGateways()).isEqualTo(GATEWAYS);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.SCHEME, SCHEME_VALUE, null, MatchType.EXACT, null);
    assertDestinations(istioProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpMethodSpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getMethodRuleSpec(MatchType.EXACT));

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    IstioProviderConfig istioProviderConfig = (IstioProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(istioProviderConfig.getHosts()).isEqualTo(HOSTS);
    assertThat(istioProviderConfig.getGateways()).isEqualTo(GATEWAYS);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.METHOD, "GET", null, MatchType.EXACT, null);
    assertDestinations(istioProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpAuthoritySpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getAuthorityRuleSpec());

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    IstioProviderConfig istioProviderConfig = (IstioProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(istioProviderConfig.getHosts()).isEqualTo(HOSTS);
    assertThat(istioProviderConfig.getGateways()).isEqualTo(GATEWAYS);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.AUTHORITY, AUTHORITY_VALUE, null, MatchType.EXACT, null);
    assertDestinations(istioProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpHeaderSpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getHeaderRuleSpec(MatchType.EXACT));

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    IstioProviderConfig istioProviderConfig = (IstioProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(istioProviderConfig.getHosts()).isEqualTo(HOSTS);
    assertThat(istioProviderConfig.getGateways()).isEqualTo(GATEWAYS);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.HEADER, null, null, null, HEADER_CONFIG_WITH_MATCH_TYPE);
    assertDestinations(istioProviderConfig.getDestinations());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpPortSpec() {
    K8sTrafficRouting k8sTrafficRouting = getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getPortRuleSpec());

    Optional<K8sTrafficRoutingConfig> k8sTrafficRoutingConfig =
        k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);

    assertThat(k8sTrafficRoutingConfig.isPresent()).isTrue();
    IstioProviderConfig istioProviderConfig = (IstioProviderConfig) k8sTrafficRoutingConfig.get();
    assertThat(istioProviderConfig.getHosts()).isEqualTo(HOSTS);
    assertThat(istioProviderConfig.getGateways()).isEqualTo(GATEWAYS);
    assertRoutes(k8sTrafficRoutingConfig.get(), RuleType.PORT, String.valueOf(PORT_VALUE), null, null, null);
    assertDestinations(istioProviderConfig.getDestinations());
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSmiHttpPortSpec() {
    K8sTrafficRouting k8sTrafficRouting = getK8sTrafficRouting(K8sTrafficRouting.ProviderType.SMI, getPortRuleSpec());

    k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSmiHttpAuthoritySpec() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.SMI, getAuthorityRuleSpec());

    k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSmiHttpSchemeSpec() {
    K8sTrafficRouting k8sTrafficRouting = getK8sTrafficRouting(K8sTrafficRouting.ProviderType.SMI, getSchemeRuleSpec());

    k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpUriSpecWithoutMatch() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getUriRuleSpec(null));

    k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpMethodSpecWithoutMatch() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getMethodRuleSpec(null));

    k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testIstioHttpHeaderSpecWithoutMatch() {
    K8sTrafficRouting k8sTrafficRouting =
        getK8sTrafficRouting(K8sTrafficRouting.ProviderType.ISTIO, getHeaderRuleSpec(null));

    k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(k8sTrafficRouting);
  }

  private List<K8sTrafficRoutingDestination> getDestinationSpec() {
    return List.of(K8sTrafficRoutingDestination.builder()
                       .destination(K8sTrafficRoutingDestination.DestinationSpec.builder()
                                        .host(ParameterField.<String>builder().value(STAGE_HOST).build())
                                        .weight(ParameterField.<Integer>builder().value(50).build())
                                        .build())
                       .build(),
        K8sTrafficRoutingDestination.builder()
            .destination(K8sTrafficRoutingDestination.DestinationSpec.builder()
                             .host(ParameterField.<String>builder().value(STABLE_HOST).build())
                             .weight(ParameterField.<Integer>builder().value(50).build())
                             .build())
            .build());
  }

  private K8sTrafficRoutingRule getUriRuleSpec(MatchType matchType) {
    return K8sTrafficRoutingRule.builder()
        .rule(K8sTrafficRoutingRule.Rule.builder()
                  .type(K8sTrafficRoutingRule.Rule.RuleType.URI)
                  .spec(K8sTrafficRoutingURIRuleSpec.builder()
                            .name(ParameterField.<String>builder().value(RULE_NAME).build())
                            .value(ParameterField.<String>builder().value(URI_VALUE).build())
                            .matchType(matchType)
                            .build())
                  .build())
        .build();
  }

  private K8sTrafficRoutingRule getMethodRuleSpec(MatchType matchType) {
    return K8sTrafficRoutingRule.builder()
        .rule(K8sTrafficRoutingRule.Rule.builder()
                  .type(K8sTrafficRoutingRule.Rule.RuleType.METHOD)
                  .spec(K8sTrafficRoutingMethodRuleSpec.builder()
                            .name(ParameterField.<String>builder().value(RULE_NAME).build())
                            .values(matchType == null ? METHOD_VALUES : null)
                            .value(matchType == null ? null : K8sTrafficRoutingMethodRuleSpec.Method.GET)
                            .matchType(matchType)
                            .build())
                  .build())
        .build();
  }

  private K8sTrafficRoutingRule getAuthorityRuleSpec() {
    return K8sTrafficRoutingRule.builder()
        .rule(K8sTrafficRoutingRule.Rule.builder()
                  .type(K8sTrafficRoutingRule.Rule.RuleType.AUTHORITY)
                  .spec(K8sTrafficRoutingAuthorityRuleSpec.builder()
                            .name(ParameterField.<String>builder().value(RULE_NAME).build())
                            .value(ParameterField.<String>builder().value(AUTHORITY_VALUE).build())
                            .matchType(MatchType.EXACT)
                            .build())
                  .build())
        .build();
  }
  private K8sTrafficRoutingRule getHeaderRuleSpec(MatchType matchType) {
    return K8sTrafficRoutingRule.builder()
        .rule(K8sTrafficRoutingRule.Rule.builder()
                  .type(K8sTrafficRoutingRule.Rule.RuleType.HEADER)
                  .spec(K8sTrafficRoutingHeaderRuleSpec.builder()
                            .name(ParameterField.<String>builder().value(RULE_NAME).build())
                            .values(List.of(K8sTrafficRoutingHeaderRuleSpec.HeaderSpec.builder()
                                                .key("user-agent")
                                                .value(".*Android.*")
                                                .matchType(matchType)
                                                .build(),
                                K8sTrafficRoutingHeaderRuleSpec.HeaderSpec.builder()
                                    .key("Content-Type")
                                    .value("application/json")
                                    .matchType(matchType)
                                    .build(),
                                K8sTrafficRoutingHeaderRuleSpec.HeaderSpec.builder()
                                    .key("cookie")
                                    .value("^(.*?;)?(type=insider)(;.*)?$")
                                    .matchType(matchType)
                                    .build()))
                            .build())
                  .build())
        .build();
  }

  private K8sTrafficRoutingRule getSchemeRuleSpec() {
    return K8sTrafficRoutingRule.builder()
        .rule(K8sTrafficRoutingRule.Rule.builder()
                  .type(K8sTrafficRoutingRule.Rule.RuleType.SCHEME)
                  .spec(K8sTrafficRoutingSchemaRuleSpec.builder()
                            .name(ParameterField.<String>builder().value(RULE_NAME).build())
                            .value(ParameterField.<String>builder().value(SCHEME_VALUE).build())
                            .matchType(MatchType.EXACT)
                            .build())
                  .build())
        .build();
  }

  private K8sTrafficRoutingRule getPortRuleSpec() {
    return K8sTrafficRoutingRule.builder()
        .rule(K8sTrafficRoutingRule.Rule.builder()
                  .type(K8sTrafficRoutingRule.Rule.RuleType.PORT)
                  .spec(K8sTrafficRoutingPortRuleSpec.builder()
                            .name(ParameterField.<String>builder().value(RULE_NAME).build())
                            .value(ParameterField.<Integer>builder().value(PORT_VALUE).build())
                            .build())
                  .build())
        .build();
  }
  private K8sTrafficRouting getK8sTrafficRouting(
      K8sTrafficRouting.ProviderType providerType, K8sTrafficRoutingRule trafficRoutingRule) {
    List<K8sTrafficRoutingDestination> destinations = getDestinationSpec();
    List<K8sTrafficRoutingRoute> routes = List.of(K8sTrafficRoutingRoute.builder()
                                                      .route(K8sTrafficRoutingRoute.RouteSpec.builder()
                                                                 .type(K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP)
                                                                 .rules(List.of(trafficRoutingRule))
                                                                 .build())
                                                      .build());

    K8sTrafficRoutingProvider routingProvider;
    if (providerType == K8sTrafficRouting.ProviderType.SMI) {
      routingProvider = TrafficRoutingSMIProvider.builder()
                            .rootService(ParameterField.<String>builder().value(ROOT_SERVICE).build())
                            .routes(routes)
                            .destinations(destinations)
                            .build();
    } else if (providerType == K8sTrafficRouting.ProviderType.ISTIO) {
      routingProvider = TrafficRoutingIstioProvider.builder()
                            .hosts(ParameterField.<List<String>>builder().value(HOSTS).build())
                            .gateways(ParameterField.<List<String>>builder().value(GATEWAYS).build())
                            .routes(routes)
                            .destinations(destinations)
                            .build();
    } else {
      routingProvider = null;
    }
    return K8sTrafficRouting.builder().spec(routingProvider).provider(providerType).build();
  }

  private void assertDestinations(List<TrafficRoutingDestination> destinations) {
    assertThat(
        destinations.stream().allMatch(destination
            -> List.of(STABLE_HOST, STAGE_HOST).contains(destination.getHost()) && destination.getWeight() == 50))
        .isTrue();
  }

  private void assertRoutes(K8sTrafficRoutingConfig k8sTrafficRoutingConfig, RuleType ruleType, String value,
      List<String> values, MatchType matchType, List<HeaderConfig> headerConfigs) {
    TrafficRoute trafficRoute = k8sTrafficRoutingConfig.getRoutes().get(0);
    assertThat(RouteType.HTTP).isEqualTo(trafficRoute.getRouteType());
    TrafficRouteRule rule = trafficRoute.getRules().get(0);
    assertThat(ruleType).isEqualTo(rule.getRuleType());
    assertThat(rule.getName()).isEqualTo(RULE_NAME);
    assertThat(value).isEqualTo(rule.getValue());
    assertThat(matchType == null || matchType.name().equals(rule.getMatchType().name())).isTrue();
    assertThat(values == null || values.containsAll(rule.getValues())).isTrue();
    assertThat(headerConfigs == null || headerConfigs.containsAll(rule.getHeaderConfigs())).isTrue();
  }
}
