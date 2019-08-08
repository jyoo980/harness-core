package software.wings.delegatetasks.aws.perpetualtask;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Timestamp;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.harness.event.client.EventPublisher;
import io.harness.event.payloads.Ec2InstanceInfo;
import io.harness.event.payloads.Ec2Lifecycle;
import io.harness.event.payloads.EcsContainerInstanceDescription;
import io.harness.event.payloads.EcsContainerInstanceInfo;
import io.harness.event.payloads.EcsContainerInstanceLifecycle;
import io.harness.event.payloads.EcsSyncEvent;
import io.harness.event.payloads.EcsTaskDescription;
import io.harness.event.payloads.EcsTaskInfo;
import io.harness.event.payloads.EcsTaskLifecycle;
import io.harness.event.payloads.InstanceState;
import io.harness.event.payloads.Lifecycle;
import io.harness.event.payloads.Lifecycle.EventType;
import io.harness.event.payloads.ReservedResource;
import io.harness.exception.WingsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import software.wings.service.impl.aws.model.AwsEcsListClusterServicesRequest;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class EcsPerpetualTask {
  @Inject private AwsEcsHelperServiceDelegate ecsHelperServiceDelegate;
  @Inject private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Inject EventPublisher eventPublisher;

  private Cache<String, EcsActiveInstancesCache> cache = Caffeine.newBuilder().build();

  private static final String INSTANCE_TERMINATED_NAME = "terminated";

  public void run(AwsEcsListClusterServicesRequest awsEcsListClusterServicesRequest) {
    try {
      logger.info("ECS Perpetual cluster service request");
      Instant startTime = Instant.now();
      String clusterName = awsEcsListClusterServicesRequest.getCluster();
      Instant lastProcessedTime = fetchLastProcessedTimestamp(clusterName);
      List<ContainerInstance> containerInstances = listContainerInstances(awsEcsListClusterServicesRequest);
      Set<String> instanceIds = fetchEc2InstanceIds(awsEcsListClusterServicesRequest, containerInstances);
      List<Instance> instances = listEc2Instances(awsEcsListClusterServicesRequest, instanceIds);

      Map<String, String> taskArnServiceNameMap = new HashMap<>();
      loadTaskArnServiceNameMap(awsEcsListClusterServicesRequest, taskArnServiceNameMap);
      List<Task> tasks = listTask(awsEcsListClusterServicesRequest);

      Set<String> currentActiveEc2InstanceIds = new HashSet<>();
      publishEc2InstanceEvent(clusterName, currentActiveEc2InstanceIds, instances);
      Set<String> currentActiveContainerInstanceArns = getCurrentActiveContainerInstanceArns(containerInstances);
      publishContainerInstanceEvent(
          clusterName, currentActiveContainerInstanceArns, lastProcessedTime, containerInstances);
      Set<String> currentActiveTaskArns = new HashSet<>();
      publishTaskEvent(clusterName, currentActiveTaskArns, lastProcessedTime, tasks, taskArnServiceNameMap);

      updateActiveInstanceCache(clusterName, currentActiveEc2InstanceIds, currentActiveContainerInstanceArns,
          currentActiveTaskArns, startTime);
      publishEcsClusterSyncEvent(clusterName, currentActiveEc2InstanceIds, currentActiveContainerInstanceArns,
          currentActiveTaskArns, startTime);
    } catch (Exception ex) {
      throw new WingsException("Exception while executing task: ", ex);
    }
  }

  private void publishEcsClusterSyncEvent(String clusterName, Set<String> activeEc2InstanceIds,
      Set<String> activeContainerInstanceArns, Set<String> activeTaskArns, Instant startTime) {
    EcsSyncEvent ecsSyncEvent = EcsSyncEvent.newBuilder()
                                    .setClusterArn(clusterName)
                                    .addAllActiveEc2InstanceArns(activeEc2InstanceIds)
                                    .addAllActiveContainerInstanceArns(activeContainerInstanceArns)
                                    .addAllActiveTaskArns(activeTaskArns)
                                    .setLastProcessedTimestamp(convertInstantToTimestamp(startTime))
                                    .build();

    logger.info("Esc sync published Message {} ", ecsSyncEvent.toString());
    eventPublisher.publishMessage(ecsSyncEvent);
  }

  private void publishTaskEvent(String clusterName, Set<String> currentActiveTaskArns, Instant lastProcessedTime,
      List<Task> tasks, Map<String, String> taskArnServiceNameMap) {
    Set<String> activeTaskArns = fetchActiveTaskArns(clusterName);
    logger.info("Active tasks {} task size {} ", activeTaskArns, tasks.size());
    publishMissingTaskLifecycleEvent(activeTaskArns, tasks);

    for (Task task : tasks) {
      if (null != task.getStoppedAt() && taskStoppedEventRequired(lastProcessedTime, task, activeTaskArns)) {
        publishTaskLifecycleEvent(task.getTaskArn(), task.getStoppedAt(), EventType.STOP);
      }

      if (null == task.getStoppedAt()) {
        currentActiveTaskArns.add(task.getTaskArn());
      }

      if (!activeTaskArns.contains(task.getTaskArn())
          && convertDateToInstant(task.getPullStartedAt()).isAfter(lastProcessedTime)) {
        int memory = Integer.valueOf(task.getMemory());
        int cpu = Integer.valueOf(task.getCpu());
        if (null != task.getPullStartedAt()) {
          publishTaskLifecycleEvent(task.getTaskArn(), task.getPullStartedAt(), EventType.START);
        }

        EcsTaskDescription.Builder ecsTaskDescriptionBuilder = EcsTaskDescription.newBuilder()
                                                                   .setTaskArn(task.getTaskArn())
                                                                   .setLaunchType(task.getLaunchType())
                                                                   .setClusterArn(task.getClusterArn())
                                                                   .setDesiredStatus(task.getDesiredStatus());

        if (null != taskArnServiceNameMap.get(task.getTaskArn())) {
          ecsTaskDescriptionBuilder.setServiceName(taskArnServiceNameMap.get(task.getTaskArn()));
        }
        if (null != task.getContainerInstanceArn()) {
          ecsTaskDescriptionBuilder.setContainerInstanceArn(task.getContainerInstanceArn());
        }

        EcsTaskInfo ecsTaskInfo =
            EcsTaskInfo.newBuilder()
                .setEcsTaskDescription(ecsTaskDescriptionBuilder.build())
                .setEcsTaskResource(ReservedResource.newBuilder().setCpu(cpu).setMemory(memory).build())
                .build();
        logger.info("Task published Message {} ", ecsTaskInfo.toString());
        eventPublisher.publishMessage(ecsTaskInfo);
      }
    }
  }

  private boolean taskStoppedEventRequired(Instant lastProcessedTime, Task task, Set<String> activeTaskArns) {
    boolean eventRequired = true;
    if (!activeTaskArns.contains(task.getTaskArn())
        && convertDateToInstant(task.getStoppedAt()).isBefore(lastProcessedTime)) {
      eventRequired = false;
    }
    return eventRequired;
  }

  private Instant convertDateToInstant(Date date) {
    return Instant.ofEpochMilli(date.getTime());
  }

  private Lifecycle createLifecycle(String instanceId, Date date, EventType eventType) {
    return Lifecycle.newBuilder()
        .setInstanceId(instanceId)
        .setTimestamp(convertDateToTimestamp(date))
        .setType(eventType)
        .setCreatedTimestamp(convertDateToTimestamp(Date.from(Instant.now())))
        .build();
  }

  private void publishEc2LifecycleEvent(String instanceId, Date date, EventType eventType) {
    Ec2Lifecycle ec2Lifecycle =
        Ec2Lifecycle.newBuilder().setLifecycle(createLifecycle(instanceId, date, eventType)).build();
    eventPublisher.publishMessage(ec2Lifecycle);
  }

  private void publishContainerInstanceLifecycleEvent(String instanceId, Date date, EventType eventType) {
    EcsContainerInstanceLifecycle ecsContainerInstanceLifecycle =
        EcsContainerInstanceLifecycle.newBuilder().setLifecycle(createLifecycle(instanceId, date, eventType)).build();
    eventPublisher.publishMessage(ecsContainerInstanceLifecycle);
  }

  private void publishTaskLifecycleEvent(String instanceId, Date date, EventType eventType) {
    EcsTaskLifecycle ecsTaskLifecycle =
        EcsTaskLifecycle.newBuilder().setLifecycle(createLifecycle(instanceId, date, eventType)).build();
    eventPublisher.publishMessage(ecsTaskLifecycle);
  }

  private Set<String> getCurrentActiveContainerInstanceArns(List<ContainerInstance> containerInstances) {
    return containerInstances.stream()
        .map(containerInstance -> containerInstance.getContainerInstanceArn())
        .collect(Collectors.toSet());
  }

  private void publishContainerInstanceEvent(String clusterName, Set<String> currentActiveArns,
      Instant lastProcessedTime, List<ContainerInstance> containerInstances) {
    logger.info("Container instance size is {} ", containerInstances.size());
    Set<String> activeContainerInstancesArns = fetchActiveContainerInstancesArns(clusterName);
    logger.info("Container instances in cache {} ", activeContainerInstancesArns);

    publishStoppedContainerInstanceEvents(activeContainerInstancesArns, currentActiveArns);
    for (ContainerInstance containerInstance : containerInstances) {
      if (!activeContainerInstancesArns.contains(containerInstance.getContainerInstanceArn())
          && convertDateToInstant(containerInstance.getRegisteredAt()).isAfter(lastProcessedTime)) {
        publishContainerInstanceLifecycleEvent(
            containerInstance.getContainerInstanceArn(), containerInstance.getRegisteredAt(), EventType.START);

        List<Resource> registeredResources = containerInstance.getRegisteredResources();
        Map<String, Resource> resourceMap =
            registeredResources.stream().collect(Collectors.toMap(Resource::getName, resource -> resource));

        int memory = resourceMap.get("MEMORY").getIntegerValue();
        int cpu = resourceMap.get("CPU").getIntegerValue();

        EcsContainerInstanceInfo ecsContainerInstanceInfo =
            EcsContainerInstanceInfo.newBuilder()
                .setEcsContainerInstanceDescription(
                    EcsContainerInstanceDescription.newBuilder()
                        .setClusterArn(clusterName)
                        .setContainerInstanceArn(containerInstance.getContainerInstanceArn())
                        .setEc2InstanceId(containerInstance.getEc2InstanceId())
                        .build())
                .setEcsContainerInstanceResource(ReservedResource.newBuilder().setCpu(cpu).setMemory(memory).build())
                .build();

        logger.info("Container published Message {} ", ecsContainerInstanceInfo.toString());
        eventPublisher.publishMessage(ecsContainerInstanceInfo);
      }
    }
  }

  /**
   * once container instance is stopped its data is not available so
   * arns which are in cache and not in response are stopped
   */
  private void publishStoppedContainerInstanceEvents(
      Set<String> activeContainerInstancesArns, Set<String> currentActiveArns) {
    SetView<String> stoppedContainerInstanceArns = Sets.difference(activeContainerInstancesArns, currentActiveArns);
    for (String stoppedContainerInstanceArn : stoppedContainerInstanceArns) {
      publishContainerInstanceLifecycleEvent(stoppedContainerInstanceArn, Date.from(Instant.now()), EventType.STOP);
    }
  }

  private void publishEc2InstanceEvent(
      String clusterName, Set<String> currentActiveEc2InstanceIds, List<Instance> instances) {
    logger.info("Instance list size is {} ", instances.size());
    Set<String> activeEc2InstanceIds = fetchActiveEc2InstanceIds(clusterName);
    logger.info("Active instance in cache {}", activeEc2InstanceIds);
    publishMissingInstancesLifecycleEvent(activeEc2InstanceIds, instances);

    for (Instance instance : instances) {
      if (INSTANCE_TERMINATED_NAME.equals(instance.getState().getName())) {
        publishEc2LifecycleEvent(instance.getInstanceId(), Date.from(Instant.now()), EventType.STOP);
      } else {
        currentActiveEc2InstanceIds.add(instance.getInstanceId());
      }

      if (!activeEc2InstanceIds.contains(instance.getInstanceId())) {
        publishEc2LifecycleEvent(instance.getInstanceId(), instance.getLaunchTime(), EventType.START);

        InstanceState.Builder instanceStateBuilder =
            InstanceState.newBuilder().setCode(instance.getState().getCode()).setName(instance.getState().getName());

        Ec2InstanceInfo.Builder ec2InstanceInfoBuilder = Ec2InstanceInfo.newBuilder()
                                                             .setInstanceId(instance.getInstanceId())
                                                             .setClusterArn(clusterName)
                                                             .setInstanceType(instance.getInstanceType())
                                                             .setInstanceState(instanceStateBuilder.build());

        if (null != instance.getCapacityReservationId()) {
          ec2InstanceInfoBuilder.setCapacityReservationId(instance.getCapacityReservationId());
        }

        if (null != instance.getSpotInstanceRequestId()) {
          ec2InstanceInfoBuilder.setSpotInstanceRequestId(instance.getSpotInstanceRequestId());
        }

        if (null != instance.getInstanceLifecycle()) {
          ec2InstanceInfoBuilder.setInstanceLifecycle(instance.getInstanceLifecycle());
        }

        Ec2InstanceInfo ec2InstanceInfo = ec2InstanceInfoBuilder.build();
        logger.info("EC2 published Message {} ", ec2InstanceInfo.toString());
        eventPublisher.publishMessage(ec2InstanceInfo);
      }
    }
  }

  private void publishMissingTaskLifecycleEvent(Set<String> activeTaskArns, List<Task> tasks) {
    Set<String> currentlyActiveTaskArns = tasks.stream().map(task -> task.getTaskArn()).collect(Collectors.toSet());
    SetView<String> missingTaskArns = Sets.difference(activeTaskArns, currentlyActiveTaskArns);
    for (String missingTask : missingTaskArns) {
      publishTaskLifecycleEvent(missingTask, Date.from(Instant.now()), EventType.STOP);
    }
  }

  /* Instances which were in activeInstances cache but were not present in api response.
   * Can happen if perpetual task didn't run within 1 hr of ec2 instance was termination.
   */
  private void publishMissingInstancesLifecycleEvent(Set<String> activeEc2InstanceIds, List<Instance> instances) {
    Set<String> ec2InstanceIds =
        instances.stream().map(instance -> instance.getInstanceId()).collect(Collectors.toSet());
    SetView<String> missingInstances = Sets.difference(activeEc2InstanceIds, ec2InstanceIds);
    logger.info("Missing instances {} ", missingInstances);
    for (String missingInstance : missingInstances) {
      publishEc2LifecycleEvent(missingInstance, Date.from(Instant.now()), EventType.STOP);
    }
  }

  private List<Task> listTask(AwsEcsListClusterServicesRequest request) {
    List<DesiredStatus> desiredStatuses = listTaskDesiredStatus();
    List<Task> tasks = new ArrayList<>();
    for (DesiredStatus desiredStatus : desiredStatuses) {
      tasks.addAll(ecsHelperServiceDelegate.listTasksForService(request.getAwsConfig(), request.getEncryptionDetails(),
          request.getRegion(), request.getCluster(), null, desiredStatus));
    }
    return tasks;
  }

  private List<DesiredStatus> listTaskDesiredStatus() {
    return new ArrayList<>(Arrays.asList(DesiredStatus.RUNNING, DesiredStatus.STOPPED));
  }

  private List<ContainerInstanceStatus> listContainerInstanceStatus() {
    return new ArrayList<>(Arrays.asList(ContainerInstanceStatus.ACTIVE, ContainerInstanceStatus.DRAINING));
  }

  private void loadTaskArnServiceNameMap(
      AwsEcsListClusterServicesRequest request, Map<String, String> taskArnServiceNameMap) {
    List<DesiredStatus> desiredStatuses = listTaskDesiredStatus();
    for (Service service : listServices(request)) {
      for (DesiredStatus desiredStatus : desiredStatuses) {
        List<String> taskArns =
            ecsHelperServiceDelegate.listTasksArnForService(request.getAwsConfig(), request.getEncryptionDetails(),
                request.getRegion(), request.getCluster(), service.getServiceArn(), desiredStatus);
        if (!CollectionUtils.isEmpty(taskArns)) {
          for (String taskArn : taskArns) {
            taskArnServiceNameMap.put(taskArn, service.getServiceArn());
          }
        }
      }
    }
  }

  private List<Service> listServices(AwsEcsListClusterServicesRequest request) {
    return ecsHelperServiceDelegate.listServicesForCluster(
        request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion(), request.getCluster());
  }

  private List<ContainerInstance> listContainerInstances(
      AwsEcsListClusterServicesRequest awsEcsListClusterServicesRequest) {
    List<ContainerInstanceStatus> containerInstanceStatuses = listContainerInstanceStatus();
    List<ContainerInstance> containerInstances = new ArrayList<>();
    for (ContainerInstanceStatus containerInstanceStatus : containerInstanceStatuses) {
      containerInstances.addAll(
          ecsHelperServiceDelegate.listContainerInstancesForCluster(awsEcsListClusterServicesRequest.getAwsConfig(),
              awsEcsListClusterServicesRequest.getEncryptionDetails(), awsEcsListClusterServicesRequest.getRegion(),
              awsEcsListClusterServicesRequest.getCluster(), containerInstanceStatus));
    }
    return containerInstances;
  }

  private Set<String> fetchEc2InstanceIds(
      AwsEcsListClusterServicesRequest request, List<ContainerInstance> containerInstances) {
    Set<String> instanceIds = new HashSet<>();
    if (!CollectionUtils.isEmpty(containerInstances)) {
      instanceIds = containerInstances.stream()
                        .map(containerInstance -> containerInstance.getEc2InstanceId())
                        .collect(Collectors.toSet());
      instanceIds.addAll(fetchActiveEc2InstanceIds(request.getCluster()));
    }
    return instanceIds;
  }

  private Set<String> fetchActiveEc2InstanceIds(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache
        && !CollectionUtils.isEmpty(ecsActiveInstancesCache.getActiveEc2InstanceIds())) {
      return ecsActiveInstancesCache.getActiveEc2InstanceIds();
    } else {
      return Collections.EMPTY_SET;
    }
  }

  private Set<String> fetchActiveContainerInstancesArns(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache
        && !CollectionUtils.isEmpty(ecsActiveInstancesCache.getActiveContainerInstanceArns())) {
      return ecsActiveInstancesCache.getActiveContainerInstanceArns();
    } else {
      return Collections.EMPTY_SET;
    }
  }

  private Instant fetchLastProcessedTimestamp(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache && null != ecsActiveInstancesCache.getLastProcessedTimestamp()) {
      return ecsActiveInstancesCache.getLastProcessedTimestamp();
    } else {
      return Instant.now().minus(150, ChronoUnit.DAYS);
    }
  }

  private Set<String> fetchActiveTaskArns(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache && !CollectionUtils.isEmpty(ecsActiveInstancesCache.getActiveTaskArns())) {
      return ecsActiveInstancesCache.getActiveTaskArns();
    } else {
      return Collections.EMPTY_SET;
    }
  }

  private void updateActiveInstanceCache(String clusterName, Set<String> activeEc2InstanceIds,
      Set<String> activeContainerInstanceArns, Set<String> activeTaskArns, Instant startTime) {
    logger.info("Params to update cache {} ; {} ; {} ; {} ; {} ", clusterName, activeEc2InstanceIds,
        activeContainerInstanceArns, activeTaskArns, startTime);
    Optional.ofNullable(cache.get(clusterName, s -> createDefaultCache()))
        .ifPresent(activeInstancesCache
            -> updateCache(
                activeInstancesCache, activeEc2InstanceIds, activeContainerInstanceArns, activeTaskArns, startTime));
  }

  public void updateCache(EcsActiveInstancesCache activeInstancesCache, Set<String> activeEc2InstanceIds,
      Set<String> activeContainerInstanceArns, Set<String> activeTaskArns, Instant startTime) {
    activeInstancesCache.setActiveEc2InstanceIds(activeEc2InstanceIds);
    activeInstancesCache.setActiveContainerInstanceArns(activeContainerInstanceArns);
    activeInstancesCache.setActiveTaskArns(activeTaskArns);
    activeInstancesCache.setLastProcessedTimestamp(startTime);
  }

  private EcsActiveInstancesCache createDefaultCache() {
    return new EcsActiveInstancesCache(
        Collections.EMPTY_SET, Collections.EMPTY_SET, Collections.EMPTY_SET, Instant.now());
  }

  private List<Instance> listEc2Instances(AwsEcsListClusterServicesRequest request, Set<String> instanceIds) {
    List<Instance> instances = new ArrayList<>();
    if (!CollectionUtils.isEmpty(instanceIds)) {
      instances = ec2ServiceDelegate.listEc2Instances(
          request.getAwsConfig(), request.getEncryptionDetails(), new ArrayList<>(instanceIds), request.getRegion());
      instances = instances.stream().filter(instance -> null != instance.getLaunchTime()).collect(Collectors.toList());
      logger.info("Instances {} ", instances.toString());
    }
    return instances;
  }

  private Timestamp convertDateToTimestamp(Date date) {
    return Timestamp.newBuilder()
        .setSeconds(date.getTime() / 1000)
        .setNanos((int) ((date.getTime() % 1000) * 1000000))
        .build();
  }

  private Timestamp convertInstantToTimestamp(Instant instant) {
    return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).build();
  }
}
