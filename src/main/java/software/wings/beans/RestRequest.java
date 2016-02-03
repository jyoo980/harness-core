package software.wings.beans;

import java.util.HashMap;
import java.util.Map;

public class RestRequest<T> {
  private Map<String, Object> metaData;

  private T resource;

  public RestRequest() {
    this(null);
  }
  public RestRequest(T resource) {
    this.resource = resource;
    metaData = new HashMap<String, Object>();
  }

  public Map<String, Object> getMetaData() {
    return metaData;
  }
  public void setMetaData(Map<String, Object> metaData) {
    this.metaData = metaData;
  }
  public T getResource() {
    return resource;
  }
  public void setResource(T resource) {
    this.resource = resource;
  }
}
