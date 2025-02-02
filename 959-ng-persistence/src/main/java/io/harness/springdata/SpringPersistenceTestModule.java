/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

public class SpringPersistenceTestModule extends PersistenceModule {
  @Override
  protected Class<?>[] getConfigClasses() {
    return new Class[] {SpringPersistenceTestConfig.class};
  }
}
