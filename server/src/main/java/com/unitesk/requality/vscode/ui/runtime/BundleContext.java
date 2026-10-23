package com.unitesk.requality.vscode.ui.runtime;

/**
 * {@code org.osgi.framework.BundleContext}: the server has no OSGi framework, the plug-in is started with this empty
 * context by the server's start-up ({@code CoreBootstrap}).
 */
public final class BundleContext {
  public static final BundleContext INSTANCE = new BundleContext();

  private BundleContext() {
  }
}
