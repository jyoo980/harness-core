package software.wings.service;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceInstanceSelectionParams.Builder.aServiceInstanceSelectionParams;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.dl.PageResponse.Builder.aPageResponse;
import static software.wings.settings.SettingValue.SettingVariableTypes.AWS;
import static software.wings.settings.SettingValue.SettingVariableTypes.PHYSICAL_DATA_CENTER;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_ID;
import static software.wings.utils.WingsTestConstants.HOST_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_INSTANCE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import com.google.common.collect.ImmutableMap;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Instance;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig.Builder;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.ErrorCode;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder;
import software.wings.beans.infrastructure.Host;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsException;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.StaticInfrastructureProvider;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvider;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.utils.WingsTestConstants;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Created by anubhaw on 1/10/17.
 */
public class InfrastructureMappingServiceTest extends WingsBaseTest {
  @Mock private WingsPersistence wingsPersistence;
  @Mock private Map<String, InfrastructureProvider> infrastructureProviders;
  @Mock private StaticInfrastructureProvider staticInfrastructureProvider;
  @Mock private AwsInfrastructureProvider awsInfrastructureProvider;

  @Mock private ServiceInstanceService serviceInstanceService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private SettingsService settingsService;
  @Mock private HostService hostService;

  @Mock private Query<InfrastructureMapping> query;
  @Mock private UpdateOperations<InfrastructureMapping> updateOperations;
  @Mock private WorkflowService workflowService;
  @Mock private FieldEnd end;

  @Inject @InjectMocks private InfrastructureMappingService infrastructureMappingService;

  @Before
  public void setUp() throws Exception {
    when(infrastructureProviders.get(SettingVariableTypes.AWS.name())).thenReturn(awsInfrastructureProvider);
    when(infrastructureProviders.get(PHYSICAL_DATA_CENTER.name())).thenReturn(staticInfrastructureProvider);
    when(wingsPersistence.createQuery(InfrastructureMapping.class)).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(InfrastructureMapping.class)).thenReturn(updateOperations);
    when(query.field(any())).thenReturn(end);
    when(end.equal(any())).thenReturn(query);
  }

  @Test
  public void shouldList() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping = aPhysicalInfrastructureMapping()
                                                                      .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                                      .withComputeProviderSettingId(SETTING_ID)
                                                                      .withAppId(APP_ID)
                                                                      .withEnvId(ENV_ID)
                                                                      .withServiceTemplateId(TEMPLATE_ID)
                                                                      .build();

    PageRequest<InfrastructureMapping> pageRequest = new PageRequest<>();
    PageResponse pageResponse = aPageResponse().withResponse(singletonList(physicalInfrastructureMapping)).build();
    when(wingsPersistence.query(InfrastructureMapping.class, pageRequest)).thenReturn(pageResponse);

    PageResponse<InfrastructureMapping> infrastructureMappings = infrastructureMappingService.list(pageRequest);
    assertThat(infrastructureMappings).hasSize(1).containsExactly(physicalInfrastructureMapping);
    verify(wingsPersistence).query(InfrastructureMapping.class, pageRequest);
  }

  @Test
  public void shouldSave() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
            .withComputeProviderSettingId(SETTING_ID)
            .withAppId(APP_ID)
            .withEnvId(ENV_ID)
            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withDeploymentType(DeploymentType.SSH.name())
            .withServiceTemplateId(TEMPLATE_ID)
            .withHostNames(singletonList(HOST_NAME))
            .build();

    PhysicalInfrastructureMapping savedPhysicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
            .withComputeProviderSettingId(SETTING_ID)
            .withUuid(WingsTestConstants.INFRA_MAPPING_ID)
            .withAppId(APP_ID)
            .withEnvId(ENV_ID)
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
            .withDeploymentType(DeploymentType.SSH.name())
            .withServiceId(SERVICE_ID)
            .withServiceTemplateId(TEMPLATE_ID)
            .withHostNames(singletonList(HOST_NAME))
            .build();

    when(wingsPersistence.saveAndGet(InfrastructureMapping.class, physicalInfrastructureMapping))
        .thenReturn(savedPhysicalInfrastructureMapping);
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID))
        .thenReturn(aServiceTemplate().withAppId(APP_ID).withServiceId(SERVICE_ID).withUuid(TEMPLATE_ID).build());
    when(settingsService.get(COMPUTE_PROVIDER_ID))
        .thenReturn(
            aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(aPhysicalDataCenterConfig().build()).build());
    Host host = aHost()
                    .withAppId(APP_ID)
                    .withEnvId(ENV_ID)
                    .withServiceTemplateId(TEMPLATE_ID)
                    .withInfraMappingId(INFRA_MAPPING_ID)
                    .withUuid(HOST_ID)
                    .withHostName(HOST_NAME)
                    .build();
    when(staticInfrastructureProvider.saveHost(any(Host.class))).thenReturn(host);

    InfrastructureMapping returnedInfrastructureMapping =
        infrastructureMappingService.save(physicalInfrastructureMapping);

    assertThat(returnedInfrastructureMapping.getUuid()).isEqualTo(INFRA_MAPPING_ID);
    verify(wingsPersistence).saveAndGet(InfrastructureMapping.class, physicalInfrastructureMapping);
    verify(serviceTemplateService).get(APP_ID, TEMPLATE_ID);
    verify(wingsPersistence)
        .updateField(InfrastructureMapping.class, INFRA_MAPPING_ID, "hostNames", singletonList(HOST_NAME));
    verify(staticInfrastructureProvider).saveHost(any(Host.class));
    verify(serviceInstanceService)
        .updateInstanceMappings(
            aServiceTemplate().withAppId(APP_ID).withServiceId(SERVICE_ID).withUuid(TEMPLATE_ID).build(),
            savedPhysicalInfrastructureMapping, singletonList(host), emptyList());
  }

  @Test
  public void shouldGet() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping = aPhysicalInfrastructureMapping()
                                                                      .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                                      .withComputeProviderSettingId(SETTING_ID)
                                                                      .withAppId(APP_ID)
                                                                      .withEnvId(ENV_ID)
                                                                      .withUuid(INFRA_MAPPING_ID)
                                                                      .withServiceTemplateId(TEMPLATE_ID)
                                                                      .withHostNames(singletonList(HOST_NAME))
                                                                      .build();

    when(wingsPersistence.get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID))
        .thenReturn(physicalInfrastructureMapping);

    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID);
    assertThat(infrastructureMapping.getUuid()).isEqualTo(INFRA_MAPPING_ID);
    verify(wingsPersistence).get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID);
  }

  @Test
  public void shouldUpdate() {
    PhysicalInfrastructureMapping savedInfra = aPhysicalInfrastructureMapping()
                                                   .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                   .withComputeProviderSettingId(SETTING_ID)
                                                   .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                   .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
                                                   .withDeploymentType(DeploymentType.SSH.name())
                                                   .withAppId(APP_ID)
                                                   .withEnvId(ENV_ID)
                                                   .withServiceId(SERVICE_ID)
                                                   .withUuid(INFRA_MAPPING_ID)
                                                   .withServiceTemplateId(TEMPLATE_ID)
                                                   .withHostNames(singletonList(HOST_NAME))
                                                   .build();

    PhysicalInfrastructureMapping updatedInfra = aPhysicalInfrastructureMapping()
                                                     .withHostConnectionAttrs("HOST_CONN_ATTR_ID_1")
                                                     .withComputeProviderSettingId(SETTING_ID)
                                                     .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                     .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
                                                     .withDeploymentType(DeploymentType.SSH.name())
                                                     .withAppId(APP_ID)
                                                     .withEnvId(ENV_ID)
                                                     .withUuid(INFRA_MAPPING_ID)
                                                     .withServiceId(SERVICE_ID)
                                                     .withServiceTemplateId(TEMPLATE_ID)
                                                     .withHostNames(singletonList("HOST_NAME_1"))
                                                     .build();

    when(wingsPersistence.get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID)).thenReturn(savedInfra);
    when(settingsService.get(COMPUTE_PROVIDER_ID))
        .thenReturn(
            aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(aPhysicalDataCenterConfig().build()).build());
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID))
        .thenReturn(aServiceTemplate().withAppId(APP_ID).withServiceId(SERVICE_ID).withUuid(TEMPLATE_ID).build());
    Host host = aHost()
                    .withAppId(APP_ID)
                    .withEnvId(ENV_ID)
                    .withServiceTemplateId(TEMPLATE_ID)
                    .withInfraMappingId(INFRA_MAPPING_ID)
                    .withUuid(HOST_ID)
                    .withHostName("HOST_NAME_1")
                    .build();
    when(staticInfrastructureProvider.saveHost(any(Host.class))).thenReturn(host);

    InfrastructureMapping returnedInfra = infrastructureMappingService.update(updatedInfra);

    verify(wingsPersistence, times(2)).get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID);
    verify(staticInfrastructureProvider).updateHostConnAttrs(updatedInfra, updatedInfra.getHostConnectionAttrs());

    verify(serviceTemplateService).get(APP_ID, TEMPLATE_ID);
    verify(wingsPersistence).update(any(InfrastructureMapping.class), any(UpdateOperations.class));
    verify(staticInfrastructureProvider).saveHost(any(Host.class));
    verify(serviceInstanceService)
        .updateInstanceMappings(
            aServiceTemplate().withAppId(APP_ID).withServiceId(SERVICE_ID).withUuid(TEMPLATE_ID).build(), updatedInfra,
            singletonList(host), singletonList(HOST_NAME));
    verify(updateOperations).set("hostConnectionAttrs", "HOST_CONN_ATTR_ID_1");
  }

  @Test
  public void shouldDelete() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
            .withComputeProviderSettingId(SETTING_ID)
            .withAppId(APP_ID)
            .withEnvId(ENV_ID)
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withUuid(INFRA_MAPPING_ID)
            .withServiceTemplateId(TEMPLATE_ID)
            .withHostNames(singletonList(HOST_NAME))
            .build();

    when(wingsPersistence.get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID))
        .thenReturn(physicalInfrastructureMapping);
    when(wingsPersistence.delete(physicalInfrastructureMapping)).thenReturn(true);
    when(workflowService.listWorkflows(any(PageRequest.class))).thenReturn(aPageResponse().build());

    infrastructureMappingService.delete(APP_ID, ENV_ID, INFRA_MAPPING_ID);

    verify(wingsPersistence).get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID);
    verify(wingsPersistence).delete(physicalInfrastructureMapping);
    verify(staticInfrastructureProvider).deleteHostByInfraMappingId(APP_ID, INFRA_MAPPING_ID);
    verify(serviceInstanceService).deleteByInfraMappingId(APP_ID, INFRA_MAPPING_ID);
  }

  @Test
  public void shouldThrowExceptionOnReferencedInfraMappingDelete() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withUuid(INFRA_MAPPING_ID)
            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
            .withComputeProviderSettingId(SETTING_ID)
            .withAppId(APP_ID)
            .withEnvId(ENV_ID)
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withUuid(INFRA_MAPPING_ID)
            .withServiceTemplateId(TEMPLATE_ID)
            .withHostNames(singletonList(HOST_NAME))
            .build();

    when(wingsPersistence.get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID))
        .thenReturn(physicalInfrastructureMapping);
    Workflow workflow =
        aWorkflow()
            .withName(WORKFLOW_NAME)
            .withOrchestrationWorkflow(
                aBasicOrchestrationWorkflow()
                    .withWorkflowPhaseIdMap(ImmutableMap.of(
                        "PHASE_ID", WorkflowPhaseBuilder.aWorkflowPhase().withInfraMappingId(INFRA_MAPPING_ID).build()))
                    .build())
            .build();
    when(workflowService.listWorkflows(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(asList(workflow)).build());

    assertThatThrownBy(() -> infrastructureMappingService.delete(APP_ID, ENV_ID, INFRA_MAPPING_ID))
        .isInstanceOf(WingsException.class)
        .hasMessage(ErrorCode.INVALID_REQUEST.name());
  }

  @Test
  public void shouldSelectServiceInstancesForPhysicalInfrastructure() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
            .withAppId(APP_ID)
            .withEnvId(ENV_ID)
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withUuid(INFRA_MAPPING_ID)
            .withServiceTemplateId(TEMPLATE_ID)
            .withHostNames(asList(HOST_NAME))
            .build();

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalInfrastructureMapping);
    when(settingsService.get(COMPUTE_PROVIDER_ID))
        .thenReturn(
            aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(aPhysicalDataCenterConfig().build()).build());
    when(serviceInstanceService.list(any(PageRequest.class)))
        .thenReturn(
            aPageResponse().withResponse(asList(aServiceInstance().withUuid(SERVICE_INSTANCE_ID).build())).build());

    List<ServiceInstance> serviceInstances = infrastructureMappingService.selectServiceInstances(
        APP_ID, ENV_ID, INFRA_MAPPING_ID, aServiceInstanceSelectionParams().withCount(Integer.MAX_VALUE).build());

    assertThat(serviceInstances).hasSize(1);
    assertThat(serviceInstances).containsExactly(aServiceInstance().withUuid(SERVICE_INSTANCE_ID).build());
    verify(settingsService).get(COMPUTE_PROVIDER_ID);
    verify(serviceInstanceService).list(any(PageRequest.class));
  }

  @Test
  public void shouldSelectServiceInstancesForAwsInfrastructure() {
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping()
                                                            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                            .withAppId(APP_ID)
                                                            .withEnvId(ENV_ID)
                                                            .withComputeProviderType(AWS.name())
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withServiceTemplateId(TEMPLATE_ID)
                                                            .withRegion(Regions.US_EAST_1.getName())
                                                            .withUsePublicDns(false)
                                                            .build();

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);

    SettingAttribute computeProviderSetting =
        aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(Builder.anAwsConfig().build()).build();
    when(settingsService.get(COMPUTE_PROVIDER_ID)).thenReturn(computeProviderSetting);

    List<Host> newHosts = singletonList(aHost().withEc2Instance(new Instance().withPrivateDnsName(HOST_NAME)).build());

    when(awsInfrastructureProvider.listHosts(awsInfrastructureMapping, computeProviderSetting, new PageRequest<>()))
        .thenReturn(aPageResponse().withResponse(newHosts).build());
    List<Host> existingHosts = singletonList(aHost().withHostName("OLD_HOST_NAME").build());

    when(hostService.list(any(PageRequest.class))).thenReturn(aPageResponse().withResponse(existingHosts).build());
    when(awsInfrastructureProvider.saveHost(newHosts.get(0))).thenReturn(newHosts.get(0));

    ServiceTemplate serviceTemplate = aServiceTemplate().withAppId(APP_ID).withUuid(TEMPLATE_ID).build();
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(serviceTemplate);

    when(serviceInstanceService.list(any(PageRequest.class)))
        .thenReturn(
            aPageResponse().withResponse(asList(aServiceInstance().withUuid(SERVICE_INSTANCE_ID).build())).build());

    List<ServiceInstance> serviceInstances = infrastructureMappingService.selectServiceInstances(
        APP_ID, ENV_ID, INFRA_MAPPING_ID, aServiceInstanceSelectionParams().withCount(Integer.MAX_VALUE).build());

    assertThat(serviceInstances).hasSize(1);
    assertThat(serviceInstances).containsExactly(aServiceInstance().withUuid(SERVICE_INSTANCE_ID).build());
    verify(settingsService).get(COMPUTE_PROVIDER_ID);
    verify(awsInfrastructureProvider).listHosts(awsInfrastructureMapping, computeProviderSetting, new PageRequest<>());
    verify(hostService).list(any(PageRequest.class));
    verify(awsInfrastructureProvider).saveHost(newHosts.get(0));
    verify(awsInfrastructureProvider).deleteHost(APP_ID, INFRA_MAPPING_ID, "OLD_HOST_NAME");
    verify(serviceTemplateService).get(APP_ID, TEMPLATE_ID);
    verify(serviceInstanceService)
        .updateInstanceMappings(serviceTemplate, awsInfrastructureMapping, newHosts, asList("OLD_HOST_NAME"));
    verify(serviceInstanceService).list(any(PageRequest.class));
  }

  @Test
  public void shouldListPhysicalComputeProviderHosts() {
    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
            .withComputeProviderSettingId(SETTING_ID)
            .withAppId(APP_ID)
            .withEnvId(ENV_ID)
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withUuid(INFRA_MAPPING_ID)
            .withServiceTemplateId(TEMPLATE_ID)
            .withHostNames(singletonList(HOST_NAME))
            .build();

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));
    when(query.get()).thenReturn(physicalInfrastructureMapping);

    List<String> hostNames =
        infrastructureMappingService.listComputeProviderHosts(APP_ID, ENV_ID, SERVICE_ID, COMPUTE_PROVIDER_ID);
    assertThat(hostNames).hasSize(1).containsExactly(HOST_NAME);
    verify(serviceTemplateService).getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID);
    verify(query).get();
  }

  @Test
  public void shouldListAwsComputeProviderHosts() {
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping()
                                                            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                            .withAppId(APP_ID)
                                                            .withEnvId(ENV_ID)
                                                            .withComputeProviderType(AWS.name())
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withServiceTemplateId(TEMPLATE_ID)
                                                            .withRegion(Regions.US_EAST_1.getName())
                                                            .withUsePublicDns(true)
                                                            .build();

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));
    when(query.get()).thenReturn(awsInfrastructureMapping);

    SettingAttribute computeProviderSetting =
        aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(Builder.anAwsConfig().build()).build();
    when(settingsService.get(COMPUTE_PROVIDER_ID)).thenReturn(computeProviderSetting);

    when(awsInfrastructureProvider.listHosts(awsInfrastructureMapping, computeProviderSetting, new PageRequest<>()))
        .thenReturn(aPageResponse().withResponse(asList(aHost().withHostName(HOST_NAME).build())).build());

    List<String> hostNames =
        infrastructureMappingService.listComputeProviderHosts(APP_ID, ENV_ID, SERVICE_ID, COMPUTE_PROVIDER_ID);

    assertThat(hostNames).hasSize(1).containsExactly(HOST_NAME);
    verify(serviceTemplateService).getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID);
    verify(settingsService).get(COMPUTE_PROVIDER_ID);
    verify(awsInfrastructureProvider).listHosts(awsInfrastructureMapping, computeProviderSetting, new PageRequest<>());
  }

  @Test
  public void shouldProvisionNodes() {
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping()
                                                            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                            .withComputeProviderSettingId(SETTING_ID)
                                                            .withAppId(APP_ID)
                                                            .withEnvId(ENV_ID)
                                                            .withComputeProviderType(AWS.name())
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                            .withServiceTemplateId(TEMPLATE_ID)
                                                            .withRegion(Regions.US_EAST_1.getName())
                                                            .withAutoScalingGroupName("AUTOSCALING_GROUP")
                                                            .withProvisionInstances(true)
                                                            .withSetDesiredCapacity(true)
                                                            .withDesiredCapacity(1)
                                                            .build();

    when(wingsPersistence.get(InfrastructureMapping.class, APP_ID, INFRA_MAPPING_ID))
        .thenReturn(awsInfrastructureMapping);

    SettingAttribute computeProviderSetting =
        aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(Builder.anAwsConfig().build()).build();
    when(settingsService.get(COMPUTE_PROVIDER_ID)).thenReturn(computeProviderSetting);

    Host provisionedHost = aHost().withHostName(HOST_NAME).build();
    when(awsInfrastructureProvider.maybeSetAutoScaleCapacityAndGetHosts(
             awsInfrastructureMapping, computeProviderSetting))
        .thenReturn(singletonList(provisionedHost));

    when(awsInfrastructureProvider.saveHost(provisionedHost)).thenReturn(provisionedHost);

    ServiceTemplate serviceTemplate = aServiceTemplate().withAppId(APP_ID).withUuid(TEMPLATE_ID).build();
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(serviceTemplate);

    when(serviceInstanceService.list(any(PageRequest.class)))
        .thenReturn(
            aPageResponse().withResponse(asList(aServiceInstance().withUuid(SERVICE_INSTANCE_ID).build())).build());

    List<ServiceInstance> serviceInstances =
        infrastructureMappingService.getAutoScaleGroupNodes(APP_ID, INFRA_MAPPING_ID);

    assertThat(serviceInstances).hasSize(1);
    assertThat(serviceInstances).containsExactly(aServiceInstance().withUuid(SERVICE_INSTANCE_ID).build());
    verify(settingsService).get(COMPUTE_PROVIDER_ID);
    verify(awsInfrastructureProvider)
        .maybeSetAutoScaleCapacityAndGetHosts(awsInfrastructureMapping, computeProviderSetting);
    verify(awsInfrastructureProvider).saveHost(provisionedHost);
    verify(serviceTemplateService).get(APP_ID, TEMPLATE_ID);
    verify(serviceInstanceService)
        .updateInstanceMappings(serviceTemplate, awsInfrastructureMapping, singletonList(provisionedHost), emptyList());
    verify(serviceInstanceService).list(any(PageRequest.class));
  }

  @Test
  public void shouldDeProvisionNodes() {
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping()
                                                            .withHostConnectionAttrs(HOST_CONN_ATTR_ID)
                                                            .withComputeProviderSettingId(SETTING_ID)
                                                            .withAppId(APP_ID)
                                                            .withEnvId(ENV_ID)
                                                            .withComputeProviderType(AWS.name())
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withServiceTemplateId(TEMPLATE_ID)
                                                            .withRegion(Regions.US_EAST_1.getName())
                                                            .build();
    when(query.get()).thenReturn(awsInfrastructureMapping);

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    SettingAttribute computeProviderSetting =
        aSettingAttribute().withUuid(COMPUTE_PROVIDER_ID).withValue(Builder.anAwsConfig().build()).build();
    when(settingsService.get(COMPUTE_PROVIDER_ID)).thenReturn(computeProviderSetting);

    ServiceTemplate serviceTemplate = aServiceTemplate().withAppId(APP_ID).withUuid(TEMPLATE_ID).build();
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(serviceTemplate);

    infrastructureMappingService.deProvisionNodes(
        APP_ID, SERVICE_ID, ENV_ID, COMPUTE_PROVIDER_ID, singletonList(HOST_NAME));

    verify(serviceTemplateService).getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID);
    verify(settingsService).get(COMPUTE_PROVIDER_ID);
    verify(awsInfrastructureProvider)
        .deProvisionHosts(
            APP_ID, INFRA_MAPPING_ID, computeProviderSetting, Regions.US_EAST_1.getName(), singletonList(HOST_NAME));
    verify(awsInfrastructureProvider).deleteHost(APP_ID, INFRA_MAPPING_ID, HOST_NAME);
    verify(serviceTemplateService).get(APP_ID, TEMPLATE_ID);
    verify(serviceInstanceService)
        .updateInstanceMappings(serviceTemplate, awsInfrastructureMapping, emptyList(), singletonList(HOST_NAME));
  }

  @Test
  public void shouldGetInfraMappingByComputeProviderAndServiceId() {
    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));
    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping().withUuid(INFRA_MAPPING_ID).build();
    when(query.get()).thenReturn(physicalInfrastructureMapping);

    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.getInfraMappingByComputeProviderAndServiceId(
            APP_ID, ENV_ID, SERVICE_ID, COMPUTE_PROVIDER_ID);

    assertThat(infrastructureMapping).isEqualTo(physicalInfrastructureMapping);
    verify(serviceTemplateService).getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID);
    verify(query).field("appId");
    verify(end).equal(APP_ID);
    verify(query).field("envId");
    verify(end).equal(ENV_ID);
    verify(query).field("serviceTemplateId");
    verify(end).equal(TEMPLATE_ID);
    verify(query).field("computeProviderSettingId");
    verify(end).equal(COMPUTE_PROVIDER_ID);
    verify(query).get();
  }
}
