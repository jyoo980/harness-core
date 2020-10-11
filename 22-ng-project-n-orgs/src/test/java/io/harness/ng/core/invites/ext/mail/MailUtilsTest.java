package io.harness.ng.core.invites.ext.mail;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.harness.CategoryTest;
import io.harness.queue.QueuePublisher;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MailUtilsTest extends CategoryTest {
  @Mock QueuePublisher<EmailData> queuePublisher;
  private MailUtils mailUtils;
  private EmailData emailData;
  private String emailId = randomAlphabetic(10);
  private String accountIdentifier = randomAlphabetic(10);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    SmtpConfig smtpConfig = SmtpConfig.builder()
                                .type("SMTP")
                                .host("local")
                                .port(456)
                                .fromAddress("norepy@harness.io")
                                .useSSL(true)
                                .username("apikey")
                                .password(randomAlphabetic(20).toCharArray())
                                .build();
    mailUtils = new MailUtils(queuePublisher, smtpConfig);
    emailData = EmailData.builder().to(ImmutableList.of(emailId)).subject(randomAlphabetic(20)).build();
    emailData.setTemplateName("invite");
    emailData.setTemplateModel(ImmutableMap.of("projectname", randomAlphabetic(10), "url", randomAlphabetic(10)));
    emailData.setRetries(2);
    emailData.setAccountId(accountIdentifier);
  }
}