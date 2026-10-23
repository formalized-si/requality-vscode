/**
 * ***************************************************************************** Institute for
 * System Programming of the Russian Academy of Sciences Copyright (c) 2009-2013 All rights
 * reserved. This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * ****************************************************************************
 */
package com.unitesk.requality.repo;

import java.util.List;

public abstract class CVSSourcePlugin{// implements BundleActivator {
  protected static CVSSourcePlugin plugin;

  protected abstract void actualStart() throws Exception;

  protected abstract void actualStop() throws Exception;

  public final void start() throws Exception {
    CVSSourcePlugin.plugin = this;
    actualStart();
  }

  public void stop() throws Exception {
    actualStop();
    CVSSourcePlugin.plugin = null;
  }

  public static CVSSourcePlugin getDefault() {
    return plugin;
  }

  public abstract List<String> getSupportedTeamProvidersIDList() throws Exception;

  public abstract ICVSClient getInstanceOfSVCClient() throws Exception;
}
