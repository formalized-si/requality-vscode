package com.unitesk.requality.vscode.ui.runtime;

import java.io.File;

import com.unitesk.requality.vscode.core.ServerContext;

/** {@code org.eclipse.core.runtime.Platform}. */
public final class Platform {
  public static final String OS_WIN32 = "win32";
  public static final String OS_LINUX = "linux";
  public static final String OS_MACOSX = "macosx";
  public static final String WS_WIN32 = "win32";
  public static final String WS_GTK = "gtk";
  public static final String WS_COCOA = "cocoa";

  private static String[] commandLineArgs = new String[0];

  private Platform() {
  }

  /** {@code Platform.getAdapterManager()}: adapters are the elements' own getAdapter implementations. */
  public static IAdapterManager getAdapterManager() {
    return AdapterManager.INSTANCE;
  }

  /** {@code org.eclipse.core.runtime.IAdapterManager}. */
  public interface IAdapterManager {
    default Object getAdapter(Object adaptable, Class<?> adapterType) {
      if (adapterType.isInstance(adaptable))
        return adaptable;
      if (adaptable instanceof IAdaptable)
        return ((IAdaptable) adaptable).getAdapter(adapterType);
      return null;
    }

    default boolean hasAdapter(Object adaptable, String adapterTypeName) {
      return false;
    }
  }

  public static IExtensionRegistry getExtensionRegistry() {
    return ExtensionRegistry.get();
  }

  /** {@code Platform.getBundle(String)}: the Requality plug-in is the only bundle; other bundles are not installed. */
  public static Bundle getBundle(String symbolicName) {
    return "com.unitesk.requality".equals(symbolicName) ? Bundle.REQUALITY : null;
  }

  public static String getOS() {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win"))
      return OS_WIN32;
    if (os.contains("mac"))
      return OS_MACOSX;
    return OS_LINUX;
  }

  public static String getWS() {
    String os = getOS();
    return os.equals(OS_WIN32) ? WS_WIN32 : os.equals(OS_MACOSX) ? WS_COCOA : WS_GTK;
  }

  public static boolean inDebugMode() {
    return Boolean.getBoolean("requality.debug");
  }

  public static String[] getCommandLineArgs() {
    return commandLineArgs.clone();
  }

  public static void setCommandLineArgs(String[] args) {
    commandLineArgs = args == null ? new String[0] : args.clone();
  }

  /** The instance location (Eclipse workspace metadata): the extension storage folder of the server. */
  public static IPath getLocation() {
    File storage = ServerContext.server() != null ? ServerContext.server().options().storage : null;
    return Path.fromOSString((storage != null ? storage : new File(".")).getAbsolutePath());
  }
}
