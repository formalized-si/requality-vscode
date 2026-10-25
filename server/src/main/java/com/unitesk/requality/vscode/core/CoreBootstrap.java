package com.unitesk.requality.vscode.core;

import java.io.File;

import com.google.gson.JsonObject;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.tool.cli.CommandLineArgsProvider;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;
import com.unitesk.requality.vscode.ui.runtime.BundleContext;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.Platform;

/**
 * Start-up of the server, the place of the framework: the plug-in {@link Activator} is created and started as OSGi
 * does ({@code new Activator()}, then {@code start(BundleContext)}), which runs the whole start-up of the Requality
 * plug-in. Here are only the steps that belong to the server: its folders, its settings and the resource manager of
 * its workspace folder.
 */
public final class CoreBootstrap {
  private static EclipseTracker tracker;
  private static RequalityServer server;
  private static Activator activator;

  private CoreBootstrap() {
  }

  public static EclipseTracker tracker() {
    return tracker;
  }

  public static synchronized void start(RequalityServer srv, JsonObject params) {
    server = srv;
    File resources = srv.options().resources.getAbsoluteFile();
    File storage = srv.options().storage.getAbsoluteFile();
    storage.mkdirs();
    JsonObject settings = params.has("settings") && params.get("settings").isJsonObject()
        ? params.getAsJsonObject("settings") : new JsonObject();

    CommandLineArgsProvider.cLineArgs = new String[0];
    // The debug level of the settings is the -debuglevel argument of the platform (Activator.initDebug reads it).
    int debugLevel = Nodes.optInt(settings, "debugLevel", 0);
    Platform.setCommandLineArgs(debugLevel > 0 ? new String[] {"-debuglevel=" + debugLevel} : new String[0]);
    // The open mode of General (Double click / Single click) is the VS Code setting workbench.list.openMode.
    setListOpenMode(Nodes.optString(settings, "listOpenMode", null));

    Icons.init(resources, storage);
    File virtualRoot = new File(storage, "workspace");
    virtualRoot.mkdirs();
    File templates = new File(resources, "templates");
    ExtensionRegistry.setPluginXml(new File(resources, "plugin.xml"));
    seedExternalTools(settings);

    // The plug-in's constructor installs the trees tracker (Activator.initTreesTracker); the resource manager of the
    // server's workspace folder is created on it, before the API sources start.
    activator = new Activator();
    // The tree tracker of the server is inited when it is created: the resources server, which the API sources start,
    // waits for it (Activator.initTracker inits it again once the workspace projects are known).
    tracker = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    tracker.setInited(true);
    // The state hooks of the Links Explorer run on the UI thread (see UiStateHooks).
    UiStateHooks.install(tracker.getTreeStateManager());
    ResManager.getManager(new Pair<String, Object>(ResManager.PATH, virtualRoot.toURI().toString()),
        new Pair<String, Object>(ResManager.TEMPL_PATH, templates.toURI().toString()));
    notifyExternalToolPaths();
    try {
      activator.start(BundleContext.INSTANCE);
    } catch (Exception e) {
      RLogger.logError(e);
    }
    // The plug-in's message consumer (EclipseAPI.init), with the Change ID dialog of the web editors answered by the
    // server (see ChangeIdMessageConsumer).
    com.unitesk.requality.tool.message.RMessages.initMessageConsumer(new ChangeIdMessageConsumer(
        new com.unitesk.requality.vscode.port.eclipse.ui.dialogs.EclipseModalMessageConsumer()));
  }

  /** The open mode of the workbench preferences from the VS Code setting {@code workbench.list.openMode}. */
  public static void setListOpenMode(String mode) {
    if (mode != null)
      com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences.getStore().setValue(
          com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences.OPEN_ON_SINGLE_CLICK, "singleClick".equals(mode));
  }

  /**
   * The LibreOffice, ImageMagick and Inkscape paths of the settings are the plug-in preferences that
   * {@code Activator.initCLIs} reads; they are set before the start-up.
   */
  private static void seedExternalTools(JsonObject settings) {
    IPreferenceStore pref = PreferenceStore.getPlugin();
    pref.setValue(LIBRE_PATH, Nodes.optString(settings, "libreOfficePath", ""));
    pref.setValue(MAGICK_PATH, Nodes.optString(settings, "imageMagickPath", ""));
    pref.setValue(INKSCAPE_PATH, Nodes.optString(settings, "inkscapePath", ""));
  }

  /** The paths the CLIs find are saved back to the settings of the client (the preferences change). */
  private static void notifyExternalToolPaths() {
    PreferenceStore.getPlugin().addPropertyChangeListener(event -> {
      if (LIBRE_PATH.equals(event.getProperty()))
        saveSetting("libreOfficePath", String.valueOf(event.getNewValue()));
      else if (MAGICK_PATH.equals(event.getProperty()))
        saveSetting("imageMagickPath", String.valueOf(event.getNewValue()));
      else if (INKSCAPE_PATH.equals(event.getProperty()))
        saveSetting("inkscapePath", String.valueOf(event.getNewValue()));
    });
  }

  private static final String LIBRE_PATH = "com.unitesk.requality.librepath";
  private static final String MAGICK_PATH = "com.unitesk.requality.magick";
  private static final String INKSCAPE_PATH = "com.unitesk.requality.inkscape";

  private static void saveSetting(String key, String value) {
    JsonObject p = new JsonObject();
    p.addProperty("key", key);
    p.addProperty("value", value);
    server.client().notify("settings/update", p);
  }

  public static synchronized void stop() {
    if (activator == null)
      return;
    try {
      activator.stop(BundleContext.INSTANCE);
    } catch (Exception e) {
      RLogger.logError(e);
    } finally {
      closeCaches(tracker);
      activator = null;
      tracker = null;
    }
  }

  /**
   * The cross-project caches of the tracker (SQLite databases in the workspace storage, opened when first used) are
   * closed when the server stops: the plug-in leaves them to the end of the process, and Windows does not let other
   * programs delete their files while they are open.
   */
  private static void closeCaches(com.unitesk.requality.core.storage.AbstractResTracker t) {
    if (t == null)
      return;
    try {
      java.lang.reflect.Field global = com.unitesk.requality.core.storage.AbstractResTracker.class.getDeclaredField("gcss");
      global.setAccessible(true);
      Object cs = global.get(t);
      if (cs instanceof com.unitesk.requality.core.storage.cache.CacheStorage)
        ((com.unitesk.requality.core.storage.cache.CacheStorage) cs).close();
      java.lang.reflect.Field others = com.unitesk.requality.core.storage.AbstractResTracker.class.getDeclaredField("caches");
      others.setAccessible(true);
      Object map = others.get(t);
      if (map instanceof java.util.Map)
        for (Object c : ((java.util.Map<?, ?>) map).values())
          if (c instanceof com.unitesk.requality.core.storage.cache.CacheStorage)
            ((com.unitesk.requality.core.storage.cache.CacheStorage) c).close();
    } catch (ReflectiveOperationException | RuntimeException e) {
      RLogger.logError(e instanceof Exception ? (Exception) e : new RuntimeException(e));
    }
  }
}
