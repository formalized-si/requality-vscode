package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.patch.ApplyPatchOperation;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.PasteAction}: Paste Patch, the paste of a patch from the clipboard into
 * the Synchronize view. The clipboard text is a patch: the apply patch wizard opens with it. Otherwise the clipboard
 * contents cannot be pasted and an error is shown.
 */
public class PasteAction extends Action {
  /** The id of the action ({@code TeamUIPlugin.PLUGIN_ID + ".synchronize.action.paste"}). */
  public static final String ID = "org.eclipse.team.ui.synchronize.action.paste";

  private final IWorkbenchPart part;
  private final Clipboard clipboard;

  public PasteAction(IWorkbenchPart part) {
    super("&Paste Patch");
    this.part = part;
    setActionDefinitionId("org.eclipse.ui.edit.paste");
    setToolTipText("Paste a Patch");
    setId(ID);
    clipboard = new Clipboard(Display.getDefault());
  }

  @Override
  public void run() {
    IStorage patchStorage = getPatchStorage();
    if (patchStorage != null) {
      // the patch is applied in the Synchronize view, whatever the preference of the apply patch operation says
      new ApplyPatchOperation(null, patchStorage, null, new CompareConfiguration()).openWizard();
    } else {
      MessageDialog.openError(getShell(), "Cannot paste the clipboard contents.", "Clipboard does not contain a valid patch.");
    }
  }

  private Shell getShell() {
    return part.getSite().getShell();
  }

  /** The clipboard text as a storage, when the text is a patch; otherwise null. */
  private IStorage getPatchStorage() {
    String text = getClipboardText();
    if (text == null)
      return null;
    IStorage storage = new ClipboardStorage(text);
    try {
      if (ApplyPatchOperation.isPatch(storage))
        return storage;
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    return null;
  }

  private String getClipboardText() {
    Object contents = clipboard.getContents(TextTransfer.getInstance());
    return contents instanceof String ? (String) contents : null;
  }

  public void dispose() {
    clipboard.dispose();
  }

  /** The text of the clipboard as the storage of a patch (UTF-8). */
  private static final class ClipboardStorage implements IStorage {
    private final String text;

    ClipboardStorage(String text) {
      this.text = text;
    }

    @Override
    public java.io.InputStream getContents() throws CoreException {
      return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IPath getFullPath() {
      return null;
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }
  }
}
