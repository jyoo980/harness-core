package software.wings.resources;

import com.google.inject.Inject;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import org.glassfish.jersey.media.multipart.FormDataParam;
import retrofit2.http.Body;
import software.wings.app.MainConfiguration;
import software.wings.beans.RestResponse;
import software.wings.beans.ServiceVariable;
import software.wings.beans.UuidAware;
import software.wings.security.EncryptionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.encryption.EncryptedData;
import software.wings.security.encryption.SecretChangeLog;
import software.wings.security.encryption.SecretUsageLog;
import software.wings.service.impl.security.SecretText;
import software.wings.service.intfc.security.EncryptionConfig;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.utils.BoundedInputStream;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Created by rsingh on 10/30/17.
 */
@Api("secrets")
@Path("/secrets")
@Produces("application/json")
@Consumes("application/json")
@AuthRule(ResourceType.SETTING)
public class SecretManagementResource {
  @Inject private SecretManager secretManager;
  @Inject private MainConfiguration configuration;

  @GET
  @Path("/usage")
  @Timed
  @ExceptionMetered
  public RestResponse<List<SecretUsageLog>> getUsageLogs(@QueryParam("accountId") final String accountId,
      @QueryParam("entityId") final String entityId, @QueryParam("type") final SettingVariableTypes variableType)
      throws IllegalAccessException {
    return new RestResponse<>(secretManager.getUsageLogs(entityId, variableType));
  }

  @GET
  @Path("/change-logs")
  @Timed
  @ExceptionMetered
  public RestResponse<List<SecretChangeLog>> getChangeLogs(@QueryParam("accountId") final String accountId,
      @QueryParam("entityId") final String entityId, @QueryParam("type") final SettingVariableTypes variableType)
      throws IllegalAccessException {
    return new RestResponse<>(secretManager.getChangeLogs(entityId, variableType));
  }

  @GET
  @Path("/list-values")
  @Timed
  @ExceptionMetered
  public RestResponse<Collection<UuidAware>> listEncryptedValues(@QueryParam("accountId") final String accountId) {
    return new RestResponse<>(secretManager.listEncryptedValues(accountId));
  }

  @GET
  @Path("/list-configs")
  @Timed
  @ExceptionMetered
  public RestResponse<List<EncryptionConfig>> lisKmsConfigs(@QueryParam("accountId") final String accountId) {
    return new RestResponse<>(secretManager.listEncryptionConfig(accountId));
  }

  @GET
  @Path("/transition-config")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> transitionConfig(@QueryParam("accountId") final String accountId,
      @QueryParam("fromKmsId") String fromKmsId, @QueryParam("toKmsId") String toKmsId,
      @QueryParam("encryptionType") EncryptionType encryptionType) {
    return new RestResponse<>(secretManager.transitionSecrets(accountId, fromKmsId, toKmsId, encryptionType));
  }

  @POST
  @Path("/add-secret")
  @Timed
  @ExceptionMetered
  public RestResponse<String> addSecret(@QueryParam("accountId") final String accountId, @Body SecretText secretText) {
    return new RestResponse<>(secretManager.saveSecret(accountId, secretText.getName(), secretText.getValue()));
  }

  @POST
  @Path("/update-secret")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateSecret(@QueryParam("accountId") final String accountId,
      @QueryParam("uuid") final String uuId, @Body SecretText secretText) {
    return new RestResponse<>(secretManager.updateSecret(accountId, uuId, secretText.getName(), secretText.getValue()));
  }

  @DELETE
  @Path("/delete-secret")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteSecret(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String uuId) {
    return new RestResponse<>(secretManager.deleteSecret(accountId, uuId));
  }

  @POST
  @Path("/add-file")
  @Timed
  @ExceptionMetered
  public RestResponse<String> saveFile(@QueryParam("accountId") final String accountId,
      @QueryParam("name") final String name, @FormDataParam("file") InputStream uploadedInputStream) {
    return new RestResponse<>(secretManager.saveFile(accountId, name,
        new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getConfigFileLimit())));
  }

  @POST
  @Path("/update-file")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateSecret(@QueryParam("accountId") final String accountId,
      @QueryParam("name") final String name, @QueryParam("uuid") final String fileId,
      @FormDataParam("file") InputStream uploadedInputStream) {
    return new RestResponse<>(secretManager.updateFile(accountId, name, fileId,
        new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getConfigFileLimit())));
  }

  @DELETE
  @Path("/delete-file")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteFile(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String uuId) {
    return new RestResponse<>(secretManager.deleteFile(accountId, uuId));
  }

  @GET
  @Path("/list-secrets")
  @Timed
  @ExceptionMetered
  public RestResponse<List<EncryptedData>> listSecrets(
      @QueryParam("accountId") final String accountId, @QueryParam("type") final SettingVariableTypes type) {
    return new RestResponse<>(secretManager.listSecrets(accountId, type));
  }

  @GET
  @Path("/list-secret-usage")
  @Timed
  @ExceptionMetered
  public RestResponse<List<UuidAware>> listSecretUsage(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String secretId) {
    return new RestResponse<>(secretManager.getSecretUsage(accountId, secretId));
  }
}
