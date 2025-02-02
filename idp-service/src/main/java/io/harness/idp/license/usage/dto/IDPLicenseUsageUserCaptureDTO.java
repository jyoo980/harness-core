/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.LicenseUsageSaveRequest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageUserCaptureDTO {
  private String accountIdentifier;
  private String userIdentifier;
  private String email;
  private String userName;
  private long accessedAt;

  public static IDPLicenseUsageUserCaptureDTO fromLicenseUsageSaveRequest(
      String accountIdentifier, LicenseUsageSaveRequest licenseUsageSaveRequest) {
    return IDPLicenseUsageUserCaptureDTO.builder()
        .accountIdentifier(accountIdentifier)
        .userIdentifier(licenseUsageSaveRequest.getUserIdentifier())
        .email(licenseUsageSaveRequest.getEmail())
        .userName(licenseUsageSaveRequest.getUserName())
        .accessedAt(licenseUsageSaveRequest.getAccessedAt())
        .build();
  }
}
