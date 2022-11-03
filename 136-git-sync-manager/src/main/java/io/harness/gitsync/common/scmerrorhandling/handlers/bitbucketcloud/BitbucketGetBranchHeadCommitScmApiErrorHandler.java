package io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.SCMExceptionErrorMessages;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class BitbucketGetBranchHeadCommitScmApiErrorHandler implements ScmApiErrorHandler {
  public static final String GET_BRANCH_HEAD_COMMIT_FAILED_MESSAGE =
      "Failed to fetch branch head commit details from Bitbucket";

  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                GET_BRANCH_HEAD_COMMIT_FAILED_MESSAGE + ScmErrorExplanations.INVALID_CONNECTOR_CREDS, errorMetadata),
            new ScmUnauthorizedException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(ScmErrorHints.WRONG_REPO_OR_BRANCH, errorMetadata),
            ErrorMessageFormatter.formatMessage(ScmErrorExplanations.WRONG_REPO_OR_BRANCH, errorMetadata),
            new ScmBadRequestException(SCMExceptionErrorMessages.BITBUCKET_REPOSITORY_OR_BRANCH_NOT_FOUND_ERROR));
      default:
        log.error(String.format(
            "Error while fetching the branch head commit from bitbucket: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
