package software.wings.delegate.service;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static software.wings.beans.Delegate.Builder.aDelegate;
import static software.wings.beans.DelegateTaskResponse.Builder.aDelegateTaskResponse;

import com.google.inject.Injector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.Delegate;
import software.wings.beans.Delegate.Builder;
import software.wings.beans.Delegate.Status;
import software.wings.beans.DelegateTask;
import software.wings.beans.RestResponse;
import software.wings.delegate.app.DelegateConfiguration;
import software.wings.delegatetasks.DelegateRunnableTask;
import software.wings.dl.PageResponse;
import software.wings.managerclient.ManagerClient;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Created by peeyushaggarwal on 11/29/16.
 */
public class DelegateServiceImpl implements DelegateService {
  private final Logger logger = LoggerFactory.getLogger(DelegateServiceImpl.class);

  @Inject private DelegateConfiguration delegateConfiguration;

  @Inject private ManagerClient managerClient;

  @Inject @Named("heartbeatExecutor") private ScheduledExecutorService heartbeatExecutor;

  @Inject @Named("upgradeExecutor") private ScheduledExecutorService upgradeExecutor;

  @Inject private ExecutorService executorService;

  @Inject private Injector injector;

  @Override
  public void run() {
    try {
      String ip = InetAddress.getLocalHost().getHostAddress();
      String hostName = InetAddress.getLocalHost().getHostName();
      String accountId = delegateConfiguration.getAccountId();
      Delegate.Builder builder =
          aDelegate().withIp(ip).withAccountId(accountId).withHostName(hostName).withVersion(getVersion());

      String delegateId = registerDelegate(accountId, builder);

      startHeartbeat(accountId, builder, delegateId);

      startUpgradeCheck(accountId, delegateId);

      logger.info("Delegate started.");

      runTaskLoop(accountId, delegateId);

      return;

    } catch (Exception e) {
      logger.error("Exception while starting/running delegate ", e);
    }
  }

  private String registerDelegate(String accountId, Builder builder) throws IOException {
    logger.info("Registering delegate....");
    RestResponse<Delegate> delegateResponse =
        managerClient
            .registerDelegate(accountId,
                builder.but().withLastHeartBeat(System.currentTimeMillis()).withStatus(Status.ENABLED).build())
            .execute()
            .body();

    builder.withUuid(delegateResponse.getResource().getUuid()).withStatus(delegateResponse.getResource().getStatus());
    logger.info("Delegate registered with id " + delegateResponse.getResource().getUuid());

    return delegateResponse.getResource().getUuid();
  }

  private void startUpgradeCheck(String accountId, String delegateId) {
    logger.info("Starting upgrade check at interval {} ms", delegateConfiguration.getHeartbeatIntervalMs());
    upgradeExecutor.scheduleAtFixedRate(() -> {
      logger.debug("checking for upgrade");
      try {
        RestResponse<Delegate> restResponse = managerClient.checkForUpgrade(delegateId, accountId).execute().body();
        if (restResponse.getResource().isDoUpgrade()) {
          logger.info("Upgrading delegate...");
        }
      } catch (IOException e) {
        logger.error("Exception while checking for upgrade ", e);
      }
    }, 0, delegateConfiguration.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
  }

  private void startHeartbeat(String accountId, Builder builder, String delegateId) {
    logger.info("Starting heartbeat at interval {} ms", delegateConfiguration.getHeartbeatIntervalMs());
    heartbeatExecutor.scheduleAtFixedRate(() -> {
      logger.debug("sending heartbeat..");
      try {
        managerClient
            .sendHeartbeat(delegateId, accountId, builder.but().withLastHeartBeat(System.currentTimeMillis()).build())
            .execute();
      } catch (IOException e) {
        logger.error("Exception while sending heartbeat ", e);
      }
    }, 0, delegateConfiguration.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
  }

  private void runTaskLoop(String accountId, String delegateId)
      throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
    while (true) {
      RestResponse<PageResponse<DelegateTask>> delegateTasks = null;
      try {
        delegateTasks = managerClient.getTasks(delegateId, accountId).execute().body();
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (isNotEmpty(delegateTasks.getResource())) {
        DelegateTask delegateTask = delegateTasks.getResource().get(0);
        logger.info("DelegateTask received - uuid: {}, accountId: {}, taskType: {}", delegateTask.getUuid(),
            delegateTask.getAccountId(), delegateTask.getTaskType());

        DelegateRunnableTask delegateRunnableTask =
            delegateTask.getTaskType().getDelegateRunnableTask(delegateId, delegateTask, notifyResponseData -> {
              try {
                managerClient
                    .sendTaskStatus(delegateId, delegateTask.getUuid(), accountId,
                        aDelegateTaskResponse()
                            .withTaskId(delegateTask.getUuid())
                            .withAccountId(accountId)
                            .withResponse(notifyResponseData)
                            .build())
                    .execute();
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
        injector.injectMembers(delegateRunnableTask);
        executorService.submit(delegateRunnableTask).get();
      } else {
        // Loop for tasks.
        Thread.sleep(1000);
      }
    }
  }

  private String getVersion() {
    return System.getProperty("version", "1.0.0-DEV");
  }
}
