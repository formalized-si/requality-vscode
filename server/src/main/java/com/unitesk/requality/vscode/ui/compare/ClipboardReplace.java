package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.compare.internal.ClipboardReplace}: Replace With &gt; Clipboard of a file. When the active text
 * editor shows a file of the same name with a selection, the selection is replaced with the text of the clipboard
 * (the document becomes dirty); otherwise the contents of the file are replaced (IResource.FORCE).
 * VSCODE: the active editor is the active text editor of VS Code ({@code client/activeTextEditor}), its selection is
 * replaced with {@code client/replaceText}. When no editor is active Eclipse fails (a null editor) and shows "Replace
 * Failed"; here the contents of the file are replaced, as for an editor of another file.
 */
public class ClipboardReplace extends ActionDelegate {
  private IStructuredSelection selection;

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    selection = sel instanceof IStructuredSelection ? (IStructuredSelection) sel : null;
    action.setEnabled(file() != null && ClipboardCompare.clipboardText() != null);
  }

  @Override
  public void run(IAction action) {
    IFile file = file();
    if (file == null)
      return;
    try {
      String text = ClipboardCompare.clipboardText();
      if (text == null)
        return;
      JsonObject editor = activeTextEditor();
      if (editor != null && file.getName().equals(new java.io.File(editor.get("path").getAsString()).getName())) {
        int start = editor.get("selectionStart").getAsInt();
        int end = editor.get("selectionEnd").getAsInt();
        if (end > start) {
          JsonObject p = new JsonObject();
          p.addProperty("path", editor.get("path").getAsString());
          p.addProperty("start", start);
          p.addProperty("end", end);
          p.addProperty("text", text);
          ServerContext.client().call("client/replaceText", p);
          return;
        }
      }
      file.setContents(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), IResource.FORCE, null);
    } catch (Exception e) {
      RLogger.logError(e);
      MessageDialog.openError(EditionAction.shell(), "Replace Failed", e.getMessage());
    }
  }

  /** The active text editor of VS Code: its file path and selection offsets, or null. */
  static JsonObject activeTextEditor() {
    if (ServerContext.server() == null)
      return null;
    try {
      JsonElement r = ServerContext.client().call("client/activeTextEditor", new JsonObject());
      return r != null && r.isJsonObject() && r.getAsJsonObject().has("path") ? r.getAsJsonObject() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The one file of the selection (the file itself or the file of its mapping), or null. */
  private IFile file() {
    if (selection == null || selection.size() != 1)
      return null;
    return CompareAction.file(selection.getFirstElement());
  }
}
