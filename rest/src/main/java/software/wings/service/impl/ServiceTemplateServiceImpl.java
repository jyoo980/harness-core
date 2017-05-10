package software.wings.service.impl;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toList;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.SearchFilter.Operator.EQ;
import static software.wings.beans.SearchFilter.Operator.IN;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.ConfigFile;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.utils.ArtifactType;
import software.wings.utils.Validator;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by anubhaw on 4/4/16.
 */
@ValidateOnExecution
@Singleton
public class ServiceTemplateServiceImpl implements ServiceTemplateService {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ConfigService configService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private ExecutorService executorService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<ServiceTemplate> list(PageRequest<ServiceTemplate> pageRequest, boolean withDetails) {
    PageResponse<ServiceTemplate> pageResponse = wingsPersistence.query(ServiceTemplate.class, pageRequest);
    List<ServiceTemplate> serviceTemplates = pageResponse.getResponse();
    setArtifactTypeAndInfraMappings(serviceTemplates);

    if (withDetails) {
      serviceTemplates.forEach(serviceTemplate -> {
        populateServiceAndOverrideConfigFiles(serviceTemplate);
        populateServiceAndOverrideServiceVariables(serviceTemplate);
      });
    }

    return pageResponse;
  }

  private void setArtifactTypeAndInfraMappings(List<ServiceTemplate> serviceTemplates) {
    if (serviceTemplates == null || serviceTemplates.size() == 0) {
      return;
    }
    String appId = serviceTemplates.get(0).getAppId();
    String envId = serviceTemplates.get(0).getEnvId();

    ImmutableMap<String, ServiceTemplate> serviceTemplateMap =
        Maps.uniqueIndex(serviceTemplates, ServiceTemplate::getUuid);

    List<Service> services = serviceResourceService.findServicesByApp(appId);
    ImmutableMap<String, Service> serviceMap = Maps.uniqueIndex(services, Service::getUuid);
    serviceTemplateMap.forEach((serviceTemplateId, serviceTemplate) -> {
      Service tempService = serviceMap.get(serviceTemplate.getServiceId());
      serviceTemplate.setServiceArtifactType(tempService != null ? tempService.getArtifactType() : ArtifactType.OTHER);
    });

    PageRequest<InfrastructureMapping> infraPageRequest =
        PageRequest.Builder.aPageRequest()
            .addFilter("appId", EQ, appId)
            .addFilter("envId", EQ, envId)
            .addFilter("serviceTemplateId", IN, serviceTemplateMap.keySet().toArray())
            .build();
    List<InfrastructureMapping> infrastructureMappings =
        infrastructureMappingService.list(infraPageRequest).getResponse();
    Map<String, List<InfrastructureMapping>> infraMappingListByTemplateId =
        infrastructureMappings.stream().collect(Collectors.groupingBy(InfrastructureMapping::getServiceTemplateId));
    infraMappingListByTemplateId.forEach(
        (templateId, infrastructureMappingList)
            -> serviceTemplateMap.get(templateId).setInfrastructureMappings(infrastructureMappingList));
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#save(software.wings.beans.ServiceTemplate)
   */
  @Override
  public ServiceTemplate save(ServiceTemplate serviceTemplate) {
    return wingsPersistence.saveAndGet(ServiceTemplate.class, serviceTemplate);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#update(software.wings.beans.ServiceTemplate)
   */
  @Override
  public ServiceTemplate update(ServiceTemplate serviceTemplate) {
    wingsPersistence.updateFields(ServiceTemplate.class, serviceTemplate.getUuid(),
        ImmutableMap.of("name", serviceTemplate.getName(), "description", serviceTemplate.getDescription()));
    return get(serviceTemplate.getAppId(), serviceTemplate.getEnvId(), serviceTemplate.getUuid(), true);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#get(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public ServiceTemplate get(String appId, String envId, String serviceTemplateId, boolean withDetails) {
    ServiceTemplate serviceTemplate = get(appId, serviceTemplateId);
    setArtifactTypeAndInfraMappings(Arrays.asList(serviceTemplate));
    if (withDetails) {
      populateServiceAndOverrideConfigFiles(serviceTemplate);
      populateServiceAndOverrideServiceVariables(serviceTemplate);
    }
    return serviceTemplate;
  }

  @Override
  public ServiceTemplate get(String appId, String serviceTemplateId) {
    ServiceTemplate serviceTemplate = wingsPersistence.get(ServiceTemplate.class, appId, serviceTemplateId);
    Validator.notNullCheck("Service Template", serviceTemplate);
    return serviceTemplate;
  }

  @Override
  public List<Key<ServiceTemplate>> getTemplateRefKeysByService(String appId, String serviceId, String envId) {
    Query<ServiceTemplate> templateQuery = wingsPersistence.createQuery(ServiceTemplate.class)
                                               .field("appId")
                                               .equal(appId)
                                               .field("serviceId")
                                               .equal(serviceId);
    if (!isNullOrEmpty(envId)) {
      templateQuery.field("envId").equal(envId);
    }
    return templateQuery.asKeyList();
  }

  @Override
  public void updateDefaultServiceTemplateName(
      String appId, String serviceId, String oldServiceName, String newServiceName) {
    Query<ServiceTemplate> query = wingsPersistence.createQuery(ServiceTemplate.class)
                                       .field("appId")
                                       .equal(appId)
                                       .field("serviceId")
                                       .equal(serviceId)
                                       .field("defaultServiceTemplate")
                                       .equal(true)
                                       .field("name")
                                       .equal(oldServiceName);
    UpdateOperations<ServiceTemplate> updateOperations =
        wingsPersistence.createUpdateOperations(ServiceTemplate.class).set("name", newServiceName);
    wingsPersistence.update(query, updateOperations);
  }

  @Override
  public boolean exist(String appId, String templateId) {
    return wingsPersistence.createQuery(ServiceTemplate.class)
               .field("appId")
               .equal(appId)
               .field(ID_KEY)
               .equal(templateId)
               .getKey()
        != null;
  }

  private void populateServiceAndOverrideConfigFiles(ServiceTemplate template) {
    List<ConfigFile> serviceConfigFiles =
        configService.getConfigFilesForEntity(template.getAppId(), DEFAULT_TEMPLATE_ID, template.getServiceId());
    template.setServiceConfigFiles(serviceConfigFiles);

    List<ConfigFile> overrideConfigFiles =
        configService.getConfigFileByTemplate(template.getAppId(), template.getEnvId(), template);

    ImmutableMap<String, ConfigFile> serviceConfigFilesMap = Maps.uniqueIndex(serviceConfigFiles, ConfigFile::getUuid);

    overrideConfigFiles.forEach(configFile -> {
      if (configFile.getParentConfigFileId() != null
          && serviceConfigFilesMap.containsKey(configFile.getParentConfigFileId())) {
        configFile.setOverriddenConfigFile(serviceConfigFilesMap.get(configFile.getParentConfigFileId()));
      }
    });
    template.setConfigFilesOverrides(overrideConfigFiles);
  }

  private void populateServiceAndOverrideServiceVariables(ServiceTemplate template) {
    List<ServiceVariable> serviceVariables = serviceVariableService.getServiceVariablesForEntity(
        template.getAppId(), DEFAULT_TEMPLATE_ID, template.getServiceId());
    template.setServiceVariables(serviceVariables);

    List<ServiceVariable> overrideServiceVariables =
        serviceVariableService.getServiceVariablesByTemplate(template.getAppId(), template.getEnvId(), template);

    ImmutableMap<String, ServiceVariable> serviceVariablesMap =
        Maps.uniqueIndex(serviceVariables, ServiceVariable::getUuid);

    overrideServiceVariables.forEach(serviceVariable -> {
      if (serviceVariable.getParentServiceVariableId() != null
          && serviceVariablesMap.containsKey(serviceVariable.getParentServiceVariableId())) {
        serviceVariable.setOverriddenServiceVariable(
            serviceVariablesMap.get(serviceVariable.getParentServiceVariableId()));
      }
    });
    template.setServiceVariablesOverrides(overrideServiceVariables);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#delete(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override
  public void delete(String appId, String envId, String serviceTemplateId) {
    boolean deleted = wingsPersistence.delete(wingsPersistence.createQuery(ServiceTemplate.class)
                                                  .field("appId")
                                                  .equal(appId)
                                                  .field("envId")
                                                  .equal(envId)
                                                  .field(ID_KEY)
                                                  .equal(serviceTemplateId));
    if (deleted) {
      executorService.submit(() -> serviceInstanceService.deleteByServiceTemplate(appId, envId, serviceTemplateId));
      configService.deleteByTemplateId(appId, serviceTemplateId);
      serviceVariableService.deleteByTemplateId(appId, serviceTemplateId);
    }
  }

  @Override
  public void deleteByEnv(String appId, String envId) {
    List<Key<ServiceTemplate>> keys = wingsPersistence.createQuery(ServiceTemplate.class)
                                          .field("appId")
                                          .equal(appId)
                                          .field("envId")
                                          .equal(envId)
                                          .asKeyList();
    for (Key<ServiceTemplate> key : keys) {
      delete(appId, envId, (String) key.getId());
    }
  }

  @Override
  public void deleteByService(String appId, String serviceId) {
    wingsPersistence.createQuery(ServiceTemplate.class)
        .field("appId")
        .equal(appId)
        .field("serviceId")
        .equal(serviceId)
        .asList()
        .forEach(serviceTemplate
            -> delete(serviceTemplate.getAppId(), serviceTemplate.getEnvId(), serviceTemplate.getUuid()));
  }

  @Override
  public void createDefaultTemplatesByEnv(Environment env) {
    List<Service> services = serviceResourceService.findServicesByApp(env.getAppId());
    services.forEach(service
        -> save(aServiceTemplate()
                    .withAppId(service.getAppId())
                    .withEnvId(env.getUuid())
                    .withServiceId(service.getUuid())
                    .withName(service.getName())
                    .build()));
  }

  @Override
  public void createDefaultTemplatesByService(Service service) {
    List<Environment> environments = environmentService.getEnvByApp(service.getAppId());
    environments.forEach(environment
        -> save(aServiceTemplate()
                    .withAppId(service.getAppId())
                    .withEnvId(environment.getUuid())
                    .withServiceId(service.getUuid())
                    .withName(service.getName())
                    .withDefaultServiceTemplate(true)
                    .build()));
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#computedConfigFiles(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override
  public List<ConfigFile> computedConfigFiles(String appId, String envId, String templateId, String hostId) {
    ServiceTemplate serviceTemplate;
    try { // TODO:: remove it
      serviceTemplate = get(appId, envId, templateId, false);
    } catch (Exception ex) {
      return Arrays.asList();
    }

    /* override order(left to right): Service -> [Tag Hierarchy] -> Host */

    List<ConfigFile> serviceConfigFiles =
        configService.getConfigFilesForEntity(appId, DEFAULT_TEMPLATE_ID, serviceTemplate.getServiceId(), envId);
    List<ConfigFile> templateConfigFiles =
        configService.getConfigFilesForEntity(appId, templateId, serviceTemplate.getServiceId(), envId);

    return overrideConfigFiles(serviceConfigFiles, templateConfigFiles);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#computedConfigFiles(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override
  public List<ServiceVariable> computeServiceVariables(String appId, String envId, String templateId, String hostId) {
    ServiceTemplate serviceTemplate;
    try { // TODO:: remove it
      serviceTemplate = get(appId, envId, templateId, false);
    } catch (Exception ex) {
      return Arrays.asList();
    }

    List<ServiceVariable> serviceVariables =
        serviceVariableService.getServiceVariablesForEntity(appId, DEFAULT_TEMPLATE_ID, serviceTemplate.getServiceId());
    List<ServiceVariable> templateServiceVariables =
        serviceVariableService.getServiceVariablesForEntity(appId, templateId, serviceTemplate.getServiceId());

    return overrideServiceSettings(serviceVariables, templateServiceVariables);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceTemplateService#overrideConfigFiles(java.util.List, java.util.List)
   */
  @Override
  public List<ConfigFile> overrideConfigFiles(List<ConfigFile> existingFiles, List<ConfigFile> newFiles) {
    logger.info("Config files before overrides [{}]", existingFiles.toString());
    logger.info("New override config files [{}]", newFiles != null ? newFiles.toString() : null);
    if (newFiles != null && !newFiles.isEmpty()) {
      existingFiles = Stream.concat(newFiles.stream(), existingFiles.stream())
                          .filter(new TreeSet<>(Comparator.comparing(ConfigFile::getName))::add)
                          .collect(toList());
    }
    logger.info("Config files after overrides [{}]", existingFiles.toString());
    return existingFiles;
  }

  /**
   * Override service settings list.
   *
   * @param existingFiles the existing files
   * @param newFiles      the new files
   * @return the list
   */
  public List<ServiceVariable> overrideServiceSettings(
      List<ServiceVariable> existingFiles, List<ServiceVariable> newFiles) {
    logger.info("Config files before overrides [{}]", existingFiles.toString());
    logger.info("New override config files [{}]", newFiles != null ? newFiles.toString() : null);
    if (newFiles != null && !newFiles.isEmpty()) {
      existingFiles = Stream.concat(newFiles.stream(), existingFiles.stream())
                          .filter(new TreeSet<>(Comparator.comparing(ServiceVariable::getName))::add)
                          .collect(toList());
    }
    logger.info("Config files after overrides [{}]", existingFiles.toString());
    return existingFiles;
  }
}
