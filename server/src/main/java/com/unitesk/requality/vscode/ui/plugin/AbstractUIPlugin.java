package com.unitesk.requality.vscode.ui.plugin;

import java.io.File;
import java.io.IOException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.resource.ImageRegistry;
import com.unitesk.requality.vscode.ui.runtime.Bundle;
import com.unitesk.requality.vscode.ui.runtime.BundleContext;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.plugin.AbstractUIPlugin}: the base class of the plug-in activator. The bundle is the Requality
 * plug-in (the server resources), the preferences are the plug-in store and the dialog settings are persisted in the
 * server storage.
 */
public abstract class AbstractUIPlugin {
  private ImageRegistry imageRegistry;
  private DialogSettings dialogSettings;

  /** {@code Plugin.start(BundleContext)}: nothing to do beyond the activator's own start-up. */
  public void start(BundleContext context) throws Exception {
  }

  /** {@code Plugin.stop(BundleContext)}: nothing to do beyond the activator's own stop. */
  public void stop(BundleContext context) throws Exception {
  }

  /** {@code Plugin.getBundle()}: the plug-in's files are the server resources. */
  public Bundle getBundle() {
    return Bundle.REQUALITY;
  }

  /** {@code AbstractUIPlugin.getWorkbench()}. */
  public static IWorkbench getWorkbench() {
    return PlatformUI.getWorkbench();
  }

  /** {@code AbstractUIPlugin.getPreferenceStore()}: the store of the plug-in. */
  public IPreferenceStore getPreferenceStore() {
    return PreferenceStore.getPlugin();
  }

  /** {@code AbstractUIPlugin.imageDescriptorFromPlugin(String, String)}: a plug-in relative image. */
  public static ImageDescriptor imageDescriptorFromPlugin(String pluginId, String path) {
    return ImageDescriptor.fromResource(path);
  }

  /** {@code AbstractUIPlugin.createImageRegistry()}: the images of the plug-in are read on demand. */
  protected ImageRegistry createImageRegistry() {
    return new ImageRegistry();
  }

  /** {@code AbstractUIPlugin.getImageRegistry()}. */
  public synchronized ImageRegistry getImageRegistry() {
    if (imageRegistry == null)
      imageRegistry = createImageRegistry();
    return imageRegistry;
  }

  /** {@code AbstractUIPlugin.getDialogSettings()}: persisted in the server storage (dialog_settings.xml). */
  public synchronized IDialogSettings getDialogSettings() {
    if (dialogSettings == null) {
      dialogSettings = new DialogSettings("Workbench");
      File f = dialogSettingsFile();
      if (f != null && f.isFile()) {
        try {
          dialogSettings.load(f.getAbsolutePath());
        } catch (IOException e) {
          RLogger.logError(e);
        }
      }
    }
    return dialogSettings;
  }

  /** {@code AbstractUIPlugin.saveDialogSettings()}: also called when the server stops. */
  public synchronized void saveDialogSettings() {
    File f = dialogSettingsFile();
    if (dialogSettings != null && f != null) {
      try {
        dialogSettings.save(f.getAbsolutePath());
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
  }

  private static File dialogSettingsFile() {
    RequalityServer s = ServerContext.server();
    return s == null ? null : new File(s.options().storage, "dialog_settings.xml");
  }
}
