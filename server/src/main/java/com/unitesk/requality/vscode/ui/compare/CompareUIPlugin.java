package com.unitesk.requality.vscode.ui.compare;

import java.io.File;

import com.unitesk.requality.vscode.core.Icons;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/**
 * {@code org.eclipse.compare.CompareUIPlugin}: the plug-in of the compare framework, whose preference store keeps the
 * Compare/Patch preferences (kept in the server storage).
 */
public final class CompareUIPlugin {
  public static final String PLUGIN_ID = "org.eclipse.compare";

  private static final java.util.Map<File, PreferenceStore> STORES = new java.util.HashMap<>();
  /** The compare filter of the "Filtered Members" text it was made from. */
  private static CompareResourceFilter fFilter;
  private static String fFilterText;

  private CompareUIPlugin() {
  }

  /**
   * {@code CompareUIPlugin.getImageDescriptor(String)}: the image of the compare plug-in at the path relative to its
   * icons ("obj16/hunk_obj.svg"), null when the plug-in has no such image.
   * VSCODE: the images of the compare plug-in are PNG copies in {@code icons/compare}.
   */
  public static ImageDescriptor getImageDescriptor(String relativePath) {
    if (relativePath == null)
      return null;
    String png = relativePath.replaceAll("\\.(svg|gif)$", ".png");
    File f = Icons.resource("icons/compare/" + png);
    return f.isFile() ? ImageDescriptor.fromResource("icons/compare/" + png) : null;
  }

  /**
   * {@code org.eclipse.compare.internal.CompareUIPlugin.filter}: whether the member of the given name is excluded by the
   * "Filtered Members" of the Compare/Patch preferences.
   */
  public static synchronized boolean filter(String name, boolean isFolder, boolean isArchive) {
    String text = getPreferenceStore().getString(ComparePreferencePage.PATH_FILTER);
    if (text == null)
      text = "";
    if (fFilter == null || !text.equals(fFilterText)) {
      fFilter = new CompareResourceFilter();
      fFilter.setFilters(text);
      fFilterText = text;
    }
    return fFilter.filter(name, isFolder, isArchive);
  }

  /** The preference store of the compare plug-in, with the defaults of {@code ComparePreferencePage.initDefaults}. */
  public static synchronized IPreferenceStore getPreferenceStore() {
    File dir = ServerContext.server() != null ? ServerContext.server().options().storage : null;
    File file = dir == null ? null : new File(dir, "org.eclipse.compare.prefs");
    PreferenceStore cached = STORES.get(file);
    if (cached == null) {
      PreferenceStore s = file == null ? new PreferenceStore() : new PreferenceStore(file);
      com.unitesk.requality.vscode.ui.compare.ComparePreferencePage.initDefaults(s);
      STORES.put(file, s);
      cached = s;
    }
    return cached;
  }
}
