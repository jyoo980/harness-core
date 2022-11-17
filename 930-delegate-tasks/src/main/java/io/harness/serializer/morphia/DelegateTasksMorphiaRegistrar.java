/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.beans.EncryptedData;
import io.harness.beans.MigrateSecretTask;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretUsageLog;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import software.wings.api.AmiServiceDeployElement;
import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsAmiInfoVariables;
import software.wings.api.AwsCodeDeployRequestElement;
import software.wings.api.AwsLambdaContextElement;
import software.wings.api.AwsLambdaExecutionData;
import software.wings.api.AwsLambdaFunctionElement;
import software.wings.api.CanaryWorkflowStandardParams;
import software.wings.api.ClusterElement;
import software.wings.api.ContainerRollbackRequestElement;
import software.wings.api.ContainerServiceElement;
import software.wings.api.EcsSetupElement;
import software.wings.api.ForkElement;
import software.wings.api.HelmDeployContextElement;
import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PartitionElement;
import software.wings.api.PcfInstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.RouteUpdateRollbackElement;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.ScriptStateExecutionSummary;
import software.wings.api.ServiceInstanceArtifactParam;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.api.ServiceNowExecutionData;
import software.wings.api.ServiceTemplateElement;
import software.wings.api.ShellScriptProvisionerOutputElement;
import software.wings.api.SimpleWorkflowParam;
import software.wings.api.TerraformExecutionData;
import software.wings.api.TerraformOutputInfoElement;
import software.wings.api.artifact.ServiceArtifactElement;
import software.wings.api.artifact.ServiceArtifactVariableElement;
import software.wings.api.cloudformation.CloudFormationDeleteStackElement;
import software.wings.api.cloudformation.CloudFormationOutputInfoElement;
import software.wings.api.cloudformation.CloudFormationRollbackInfoElement;
import software.wings.api.helm.ServiceHelmElement;
import software.wings.api.jira.JiraExecutionData;
import software.wings.api.k8s.K8sContextElement;
import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.api.terraform.TerraformProvisionInheritPlanElement;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.api.terragrunt.TerragruntProvisionInheritPlanElement;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.BaseVaultConfig;
import software.wings.beans.BastionConnectionAttributes;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.beans.DatadogConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.ElkConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.InstanaConfig;
import software.wings.beans.JiraConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.ScalyrConfig;
import software.wings.beans.SecretManagerRuntimeParameters;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SftpConfig;
import software.wings.beans.SmbConfig;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SumoConfig;
import software.wings.beans.VaultConfig;
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.AwsLambdaCommandUnit;
import software.wings.beans.command.AzureARMCommandUnit;
import software.wings.beans.command.AzureVMSSDummyCommandUnit;
import software.wings.beans.command.AzureWebAppCommandUnit;
import software.wings.beans.command.CleanupPowerShellCommandUnit;
import software.wings.beans.command.CleanupSshCommandUnit;
import software.wings.beans.command.CodeDeployCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CopyConfigCommandUnit;
import software.wings.beans.command.DockerStartCommandUnit;
import software.wings.beans.command.DockerStopCommandUnit;
import software.wings.beans.command.DownloadArtifactCommandUnit;
import software.wings.beans.command.EcsSetupCommandUnit;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.FetchInstancesCommandUnit;
import software.wings.beans.command.HelmDummyCommandUnit;
import software.wings.beans.command.InitPowerShellCommandUnit;
import software.wings.beans.command.InitSshCommandUnit;
import software.wings.beans.command.InitSshCommandUnitV2;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.beans.command.KubernetesResizeCommandUnit;
import software.wings.beans.command.KubernetesSetupCommandUnit;
import software.wings.beans.command.KubernetesSetupParams;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.beans.command.PortCheckClearedCommandUnit;
import software.wings.beans.command.PortCheckListeningCommandUnit;
import software.wings.beans.command.ProcessCheckRunningCommandUnit;
import software.wings.beans.command.ProcessCheckStoppedCommandUnit;
import software.wings.beans.command.ResizeCommandUnit;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.SetupEnvCommandUnit;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.beans.command.TerragruntDummyCommandUnit;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.LogzConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.delegatetasks.validation.capabilities.ClusterMasterUrlValidationCapability;
import software.wings.delegatetasks.validation.capabilities.GitConnectionCapability;
import software.wings.delegatetasks.validation.capabilities.SSHHostValidationCapability;
import software.wings.delegatetasks.validation.capabilities.ShellConnectionCapability;
import software.wings.delegatetasks.validation.capabilities.WinrmHostValidationCapability;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.helpers.ext.external.comm.CollaborationProviderResponse;
import software.wings.helpers.ext.helm.response.HelmCollectChartResponse;
import software.wings.helpers.ext.k8s.response.K8sApplyResponse;
import software.wings.helpers.ext.k8s.response.K8sBlueGreenDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sCanaryDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sDeleteResponse;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployRollbackResponse;
import software.wings.helpers.ext.k8s.response.K8sScaleResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTrafficSplitResponse;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupResponse;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.elk.ElkDataCollectionInfo;
import software.wings.service.impl.elk.ElkDataCollectionInfoV2;
import software.wings.service.impl.instana.InstanaDataCollectionInfo;
import software.wings.service.impl.logz.LogzDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;
import software.wings.service.impl.stackdriver.StackDriverDataCollectionInfo;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.impl.sumo.SumoDataCollectionInfo;
import software.wings.sm.StateExecutionData;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.azure.AzureVMSSSetupContextElement;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement;
import software.wings.sm.states.spotinst.SpotInstSetupContextElement;
import software.wings.sm.states.spotinst.SpotinstTrafficShiftAlbSetupElement;

import java.util.Set;

public class DelegateTasksMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(NewRelicMetricDataRecord.class);
    set.add(DelegateConnectionResult.class);
    set.add(AwsSecretsManagerConfig.class);
    set.add(AzureVaultConfig.class);
    set.add(GcpKmsConfig.class);
    set.add(GcpSecretsManagerConfig.class);
    set.add(KmsConfig.class);
    set.add(LocalEncryptionConfig.class);
    set.add(VaultConfig.class);
    set.add(SecretManagerRuntimeParameters.class);
    set.add(BaseVaultConfig.class);
    set.add(SecretManagerConfig.class);
    set.add(SSHVaultConfig.class);
    set.add(SecretChangeLog.class);
    set.add(EncryptedData.class);
    set.add(SecretUsageLog.class);
    set.add(MigrateSecretTask.class);
    set.add(EmailData.class);
    set.add(CommandUnit.class);
    set.add(CustomSecretsManagerConfig.class);
    set.add(ServiceVariable.class);
    set.add(CustomSecretNGManagerConfig.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    w.put("api.AmiServiceDeployElement", AmiServiceDeployElement.class);
    w.put("api.AmiServiceSetupElement", AmiServiceSetupElement.class);
    w.put("api.AmiServiceTrafficShiftAlbSetupElement", AmiServiceTrafficShiftAlbSetupElement.class);
    w.put("api.AwsCodeDeployRequestElement", AwsCodeDeployRequestElement.class);
    w.put("api.AwsLambdaFunctionElement", AwsLambdaFunctionElement.class);
    w.put("sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement",
        AzureAppServiceSlotSetupContextElement.class);
    w.put("sm.states.azure.AzureVMSSSetupContextElement", AzureVMSSSetupContextElement.class);
    w.put("api.CanaryWorkflowStandardParams", CanaryWorkflowStandardParams.class);
    w.put("api.cloudformation.CloudFormationDeleteStackElement", CloudFormationDeleteStackElement.class);
    w.put("api.cloudformation.CloudFormationOutputInfoElement", CloudFormationOutputInfoElement.class);
    w.put("api.cloudformation.CloudFormationRollbackInfoElement", CloudFormationRollbackInfoElement.class);
    w.put("api.ClusterElement", ClusterElement.class);
    w.put("api.ContainerRollbackRequestElement", ContainerRollbackRequestElement.class);
    w.put("api.ContainerServiceElement", ContainerServiceElement.class);
    w.put("api.EcsSetupElement", EcsSetupElement.class);
    w.put("api.ForkElement", ForkElement.class);
    w.put("api.HelmDeployContextElement", HelmDeployContextElement.class);
    w.put("api.HostElement", HostElement.class);
    w.put("api.InstanceElement", InstanceElement.class);
    w.put("api.InstanceElementListParam", InstanceElementListParam.class);
    w.put("api.k8s.K8sContextElement", K8sContextElement.class);
    w.put("api.PartitionElement", PartitionElement.class);
    w.put("api.PcfInstanceElement", PcfInstanceElement.class);
    w.put("api.PhaseElement", PhaseElement.class);
    w.put("api.RouteUpdateRollbackElement", RouteUpdateRollbackElement.class);
    w.put("api.artifact.ServiceArtifactElement", ServiceArtifactElement.class);
    w.put("api.artifact.ServiceArtifactVariableElement", ServiceArtifactVariableElement.class);
    w.put("api.helm.ServiceHelmElement", ServiceHelmElement.class);
    w.put("api.ServiceInstanceArtifactParam", ServiceInstanceArtifactParam.class);
    w.put("api.ServiceInstanceIdsParam", ServiceInstanceIdsParam.class);
    w.put("api.ServiceTemplateElement", ServiceTemplateElement.class);
    w.put("api.ShellScriptProvisionerOutputElement", ShellScriptProvisionerOutputElement.class);
    w.put("api.SimpleWorkflowParam", SimpleWorkflowParam.class);
    w.put("api.ServiceArtifactElement", ServiceArtifactElement.class);
    w.put("sm.states.spotinst.SpotInstSetupContextElement", SpotInstSetupContextElement.class);
    w.put("sm.states.spotinst.SpotinstTrafficShiftAlbSetupElement", SpotinstTrafficShiftAlbSetupElement.class);
    w.put("api.TerraformOutputInfoElement", TerraformOutputInfoElement.class);
    w.put("api.terraform.TerraformProvisionInheritPlanElement", TerraformProvisionInheritPlanElement.class);
    w.put("api.terragrunt.TerragruntProvisionInheritPlanElement", TerragruntProvisionInheritPlanElement.class);
    w.put("api.AwsAmiInfoVariables", AwsAmiInfoVariables.class);
    w.put("service.impl.analysis.DataCollectionTaskResult", DataCollectionTaskResult.class);
    w.put("service.impl.analysis.CustomLogDataCollectionInfo", CustomLogDataCollectionInfo.class);
    w.put("delegatetasks.cv.beans.CustomLogResponseMapper", CustomLogResponseMapper.class);
    w.put("beans.AppDynamicsConfig", AppDynamicsConfig.class);
    w.put("beans.NewRelicConfig", NewRelicConfig.class);
    w.put("beans.DynaTraceConfig", DynaTraceConfig.class);
    w.put("beans.SumoConfig", SumoConfig.class);
    w.put("beans.DatadogConfig", DatadogConfig.class);
    w.put("service.impl.sumo.SumoDataCollectionInfo", SumoDataCollectionInfo.class);
    w.put("beans.config.LogzConfig", LogzConfig.class);
    w.put("beans.ElkConfig", ElkConfig.class);
    w.put("service.impl.elk.ElkDataCollectionInfo", ElkDataCollectionInfo.class);
    w.put("service.impl.logz.LogzDataCollectionInfo", LogzDataCollectionInfo.class);
    w.put("beans.AwsSecretsManagerConfig", AwsSecretsManagerConfig.class);
    w.put("beans.AzureVaultConfig", AzureVaultConfig.class);
    w.put("beans.GcpKmsConfig", GcpKmsConfig.class);
    w.put("beans.GcpSecretsManagerConfig", GcpSecretsManagerConfig.class);
    w.put("beans.KmsConfig", KmsConfig.class);
    w.put("beans.ScalyrConfig", ScalyrConfig.class);
    w.put("beans.LocalEncryptionConfig", LocalEncryptionConfig.class);
    w.put("beans.SecretManagerConfig", SecretManagerConfig.class);
    w.put("beans.VaultConfig", VaultConfig.class);
    w.put("beans.BastionConnectionAttributes", BastionConnectionAttributes.class);
    w.put("beans.HostConnectionAttributes", HostConnectionAttributes.class);
    w.put("beans.SSHExecutionCredential", SSHExecutionCredential.class);
    w.put("delegatetasks.validation.capabilities.ClusterMasterUrlValidationCapability",
        ClusterMasterUrlValidationCapability.class);
    w.put("beans.yaml.GitFetchFilesFromMultipleRepoResult", GitFetchFilesFromMultipleRepoResult.class);
    w.put("beans.KubernetesClusterConfig", KubernetesClusterConfig.class);
    w.put("delegatetasks.validation.capabilities.GitConnectionCapability", GitConnectionCapability.class);
    w.put("beans.SSHVaultConfig", SSHVaultConfig.class);
    w.put("beans.BaseVaultConfig", BaseVaultConfig.class);
    w.put("beans.ServiceNowConfig", ServiceNowConfig.class);
    w.put("beans.DockerConfig", DockerConfig.class);
    w.put("beans.config.NexusConfig", NexusConfig.class);
    w.put("beans.JiraConfig", JiraConfig.class);
    w.put("beans.SplunkConfig", SplunkConfig.class);
    w.put("beans.settings.azureartifacts.AzureArtifactsPATConfig", AzureArtifactsPATConfig.class);
    w.put("beans.SftpConfig", SftpConfig.class);
    w.put("beans.InstanaConfig", InstanaConfig.class);
    w.put("beans.PcfConfig", PcfConfig.class);
    w.put("beans.AzureConfig", AzureConfig.class);
    w.put("beans.BambooConfig", BambooConfig.class);
    w.put("beans.SmbConfig", SmbConfig.class);
    w.put("beans.config.ArtifactoryConfig", ArtifactoryConfig.class);
    w.put("helpers.ext.mail.SmtpConfig", SmtpConfig.class);
    w.put("helpers.ext.helm.response.HelmCollectChartResponse", HelmCollectChartResponse.class);
    w.put("delegatetasks.validation.capabilities.WinrmHostValidationCapability", WinrmHostValidationCapability.class);
    w.put("delegatetasks.validation.capabilities.ShellConnectionCapability", ShellConnectionCapability.class);
    w.put("delegatetasks.validation.capabilities.SSHHostValidationCapability", SSHHostValidationCapability.class);
    w.put("helpers.ext.external.comm.CollaborationProviderResponse", CollaborationProviderResponse.class);
    w.put("service.impl.stackdriver.StackDriverLogDataCollectionInfo", StackDriverLogDataCollectionInfo.class);
    w.put("service.impl.stackdriver.StackDriverDataCollectionInfo", StackDriverDataCollectionInfo.class);
    w.put("service.impl.splunk.SplunkDataCollectionInfoV2", SplunkDataCollectionInfoV2.class);
    w.put("beans.APMVerificationConfig", APMVerificationConfig.class);
    w.put("beans.command.EcsSetupParams", EcsSetupParams.class);
    w.put("beans.command.KubernetesSetupParams", KubernetesSetupParams.class);
    w.put("beans.command.FetchInstancesCommandUnit", FetchInstancesCommandUnit.class);
    w.put("beans.command.AmiCommandUnit", AmiCommandUnit.class);
    w.put("beans.command.AwsLambdaCommandUnit", AwsLambdaCommandUnit.class);
    w.put("beans.command.CleanupPowerShellCommandUnit", CleanupPowerShellCommandUnit.class);
    w.put("beans.command.CleanupSshCommandUnit", CleanupSshCommandUnit.class);
    w.put("beans.command.CodeDeployCommandUnit", CodeDeployCommandUnit.class);
    w.put("beans.command.CopyConfigCommandUnit", CopyConfigCommandUnit.class);
    w.put("beans.command.DockerStartCommandUnit", DockerStartCommandUnit.class);
    w.put("beans.command.DockerStopCommandUnit", DockerStopCommandUnit.class);
    w.put("beans.command.DownloadArtifactCommandUnit", DownloadArtifactCommandUnit.class);
    w.put("beans.command.EcsSetupCommandUnit", EcsSetupCommandUnit.class);
    w.put("beans.command.ExecCommandUnit", ExecCommandUnit.class);
    w.put("beans.command.HelmDummyCommandUnit", HelmDummyCommandUnit.class);
    w.put("beans.command.InitPowerShellCommandUnit", InitPowerShellCommandUnit.class);
    w.put("beans.command.InitSshCommandUnit", InitSshCommandUnit.class);
    w.put("beans.command.InitSshCommandUnitV2", InitSshCommandUnitV2.class);
    w.put("beans.command.K8sDummyCommandUnit", K8sDummyCommandUnit.class);
    w.put("beans.command.KubernetesResizeCommandUnit", KubernetesResizeCommandUnit.class);
    w.put("beans.command.KubernetesSetupCommandUnit", KubernetesSetupCommandUnit.class);
    w.put("beans.command.PcfDummyCommandUnit", PcfDummyCommandUnit.class);
    w.put("beans.command.PortCheckClearedCommandUnit", PortCheckClearedCommandUnit.class);
    w.put("beans.command.PortCheckListeningCommandUnit", PortCheckListeningCommandUnit.class);
    w.put("beans.command.ProcessCheckRunningCommandUnit", ProcessCheckRunningCommandUnit.class);
    w.put("beans.command.ProcessCheckStoppedCommandUnit", ProcessCheckStoppedCommandUnit.class);
    w.put("beans.command.ResizeCommandUnit", ResizeCommandUnit.class);
    w.put("beans.command.ScpCommandUnit", ScpCommandUnit.class);
    w.put("beans.command.SetupEnvCommandUnit", SetupEnvCommandUnit.class);
    w.put("beans.command.SpotinstDummyCommandUnit", SpotinstDummyCommandUnit.class);
    w.put("beans.command.AzureVMSSDummyCommandUnit", AzureVMSSDummyCommandUnit.class);
    w.put("beans.command.AzureWebAppCommandUnit", AzureWebAppCommandUnit.class);
    w.put("beans.command.AzureARMCommandUnit", AzureARMCommandUnit.class);
    w.put("beans.command.TerragruntDummyCommandUnit", TerragruntDummyCommandUnit.class);
    w.put("service.impl.newrelic.NewRelicDataCollectionInfoV2", NewRelicDataCollectionInfoV2.class);
    w.put("service.impl.instana.InstanaDataCollectionInfo", InstanaDataCollectionInfo.class);
    w.put("service.impl.elk.ElkDataCollectionInfoV2", ElkDataCollectionInfoV2.class);
    w.put("service.impl.cloudwatch.CloudWatchMetric", CloudWatchMetric.class);
    w.put("sm.WorkflowStandardParams", WorkflowStandardParams.class);
    w.put("sm.StateExecutionData", StateExecutionData.class);
    w.put("api.AwsLambdaContextElement", AwsLambdaContextElement.class);
    w.put("api.AwsLambdaExecutionData", AwsLambdaExecutionData.class);
    w.put("service.impl.aws.model.AwsLambdaExecuteFunctionResponse", AwsLambdaExecuteFunctionResponse.class);
    w.put("service.impl.aws.model.AwsLambdaExecuteWfResponse", AwsLambdaExecuteWfResponse.class);
    w.put("beans.BugsnagConfig", BugsnagConfig.class);
    w.put("delegatetasks.buildsource.BuildSourceExecutionResponse", BuildSourceExecutionResponse.class);
    w.put("api.TerraformExecutionData", TerraformExecutionData.class);
    w.put("api.terragrunt.TerragruntExecutionData", TerragruntExecutionData.class);
    w.put("service.impl.aws.model.AwsAmiServiceDeployResponse", AwsAmiServiceDeployResponse.class);
    w.put("service.impl.aws.model.AwsAmiServiceSetupResponse", AwsAmiServiceSetupResponse.class);
    w.put("service.impl.aws.model.AwsAmiSwitchRoutesResponse", AwsAmiSwitchRoutesResponse.class);
    w.put("api.jira.JiraExecutionData", JiraExecutionData.class);
    w.put("api.JiraExecutionData", JiraExecutionData.class);
    w.put("api.ScriptStateExecutionData", ScriptStateExecutionData.class);
    w.put("api.ScriptStateExecutionSummary", ScriptStateExecutionSummary.class);
    w.put("api.shellscript.provision.ShellScriptProvisionExecutionData", ShellScriptProvisionExecutionData.class);
    w.put("api.ServiceNowExecutionData", ServiceNowExecutionData.class);
    w.put("helpers.ext.k8s.response.K8sApplyResponse", K8sApplyResponse.class);
    w.put("helpers.ext.k8s.response.K8sBlueGreenDeployResponse", K8sBlueGreenDeployResponse.class);
    w.put("helpers.ext.k8s.response.K8sCanaryDeployResponse", K8sCanaryDeployResponse.class);
    w.put("helpers.ext.k8s.response.K8sDeleteResponse", K8sDeleteResponse.class);
    w.put("helpers.ext.k8s.response.K8sRollingDeployResponse", K8sRollingDeployResponse.class);
    w.put("helpers.ext.k8s.response.K8sScaleResponse", K8sScaleResponse.class);
    w.put("helpers.ext.k8s.response.K8sTaskExecutionResponse", K8sTaskExecutionResponse.class);
    w.put("helpers.ext.k8s.response.K8sTrafficSplitResponse", K8sTrafficSplitResponse.class);
    w.put("helpers.ext.k8s.response.K8sRollingDeployRollbackResponse", K8sRollingDeployRollbackResponse.class);
    w.put("security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig", CustomSecretsManagerConfig.class);
  }
}
