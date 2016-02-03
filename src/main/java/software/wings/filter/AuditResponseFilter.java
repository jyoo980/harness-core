package software.wings.filter;

import java.io.IOException;
import java.sql.Timestamp;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.wings.beans.AuditHeader;
import software.wings.beans.AuditPayload;
import software.wings.beans.AuditPayload.RequestType;
import software.wings.common.AuditHelper;

/**
 *  AuditResponseFilter preserves the http response details.
 *
 *
 * @author Rishi
 *
 */
public class AuditResponseFilter implements Filter {
  AuditHelper auditHelper = AuditHelper.getInstance();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = ((HttpServletRequest) request).getPathInfo();
    logger.debug("path :" + path);
    if (response.getCharacterEncoding() == null) {
      response.setCharacterEncoding("UTF-8");
    }

    HttpServletResponseCopier responseCopier = new HttpServletResponseCopier((HttpServletResponse) response);

    try {
      chain.doFilter(request, responseCopier);
      responseCopier.flushBuffer();
    } finally {
      byte[] copy = responseCopier.getCopy();

      AuditHeader header = auditHelper.get();
      if (header != null) {
        AuditPayload detail = new AuditPayload();
        detail.setHeaderId(header.getUuid());
        detail.setRequestType(RequestType.RESPONSE);
        detail.setPayload(copy);
        auditHelper.create(detail);
        header.setResponseTime(new Timestamp(System.currentTimeMillis()));
        header.setResponseStatusCode(((HttpServletResponse) response).getStatus());
        auditHelper.finalizeAudit(header);
      }
    }
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig arg0) throws ServletException {}

  private static Logger logger = LoggerFactory.getLogger(AuditResponseFilter.class);
}
