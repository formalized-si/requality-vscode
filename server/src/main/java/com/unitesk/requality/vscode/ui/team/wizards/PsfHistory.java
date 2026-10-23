package com.unitesk.requality.vscode.ui.team.wizards;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/**
 * {@code org.eclipse.team.internal.ui.wizards.PsfFilenameStore} and {@code PsfUrlStore}: the Team Project Set files and
 * URLs used before, most recent first, kept in the Team UI preference store (one entry per line).
 */
final class PsfHistory {
  static final String FILENAMES = "psf_filenames";
  static final String URLS = "psf_urls";
  private static final int MAX = 10;

  /** The Team Project Set file selected in the workbench when the import starts ({@code PsfFilenameStore}), or null. */
  private static String selectedFilename;

  private PsfHistory() {
  }

  /**
   * {@code PsfFilenameStore.setDefaultFromSelection}: a .psf file selected in the active page of the workbench is the
   * default file of the import.
   */
  static void setDefaultFromSelection(IWorkbench workbench) {
    IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
    IWorkbenchPage page = window == null ? null : window.getActivePage();
    if (page == null || !(page.getSelection() instanceof IStructuredSelection selection))
      return;
    IResource resource = Adapters.adapt(selection.getFirstElement(), IResource.class);
    if (resource == null || resource.getType() != IResource.FILE || !resource.isAccessible())
      return;
    if (!"psf".equalsIgnoreCase(resource.getFileExtension()))
      return;
    selectedFilename = resource.getLocation().toOSString();
  }

  /** The file the import starts with: the selected file, else the previous one, else none. */
  static String suggestedFilename() {
    if (selectedFilename != null)
      return selectedFilename;
    String[] previous = get(FILENAMES);
    return previous.length > 0 ? previous[0] : "";
  }

  /** The entries of the history, most recent first. */
  static String[] get(String key) {
    String value = TeamUIPlugin.getPreferenceStore().getString(key);
    List<String> r = new ArrayList<>();
    if (value != null && !value.isEmpty())
      for (String line : value.split("\n"))
        if (!line.isEmpty())
          r.add(line);
    return r.toArray(new String[0]);
  }

  /** Puts the entry first in the history. */
  static void remember(String key, String entry) {
    if (entry == null || entry.isEmpty())
      return;
    List<String> r = new ArrayList<>();
    r.add(entry);
    for (String s : get(key))
      if (!s.equals(entry) && r.size() < MAX)
        r.add(s);
    TeamUIPlugin.getPreferenceStore().setValue(key, String.join("\n", r));
  }
}
