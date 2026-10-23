package com.unitesk.requality.vscode.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tool.message.IMessageConsumer;
import com.unitesk.requality.tool.message.InputValidator;
import com.unitesk.requality.tool.message.RMessages;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.ui.jface.dialogs.IInputValidator;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * The message consumer of the server: the plug-in's {@code EclipseModalMessageConsumer} (installed by EclipseAPI.init),
 * which answers every message, except the Change ID dialog of the web editors ({@code RMessages.ID_CHANGE_DIALOG}),
 * answered here with the same dialog. In the plug-in's consumer that case falls through to the file dialog case (no
 * break): after the ID was entered, a file dialog opened (filtered by the node id) and its result replaced the ID.
 */
public final class ChangeIdMessageConsumer implements IMessageConsumer {
  private final IMessageConsumer plugin;

  public ChangeIdMessageConsumer(IMessageConsumer plugin) {
    this.plugin = plugin;
  }

  @Override
  public Object openView(TreeDB db, String name, boolean async, String... args) {
    if (!RMessages.ID_CHANGE_DIALOG.equals(name))
      return plugin.openView(db, name, async, args);
    AtomicReference<Object> ret = new AtomicReference<>();
    Runnable r = () -> {
      Shell sh = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
      // EclipseModalMessageConsumer.openView, case RMessages.ID_CHANGE_DIALOG
      TreeNode node = db.getNode(args[0]);
      boolean checkId = args[4].equals("true");
      InputDialog id = new InputDialog(sh, args[1], args[2], args[3], new IInputValidator() {
        @Override
        public String isValid(String newText) {
          if (newText.length() == 0)
            return "id cannot be empty";
          if (checkId) {
            if (!node.getTreeDB().getTreeLogic().isChildIdValid(node, newText))
              return newText + " is not valid id for the child of " + node.getUserVisibleName(true) + "(" + node.getType()
                  + ")";
          }
          return null;
        }
      });
      if (id.open() == InputDialog.OK)
        ret.set(id.getValue());
    };
    if (async)
      EclipseAPI.getDisplay().asyncExec(r);
    else
      EclipseAPI.getDisplay().syncExec(r);
    return ret.get();
  }

  @Override
  public void openError(String title, String message, String errorStatus, boolean wait) {
    plugin.openError(title, message, errorStatus, wait);
  }

  @Override
  @SafeVarargs
  public final void openInfo(String title, String message, boolean wait, Pair<String, Object>... metaParams) {
    plugin.openInfo(title, message, wait, metaParams);
  }

  @Override
  public void openBigInfo(String messageBoxTitle, String messageBoxMessage, boolean wait) {
    plugin.openBigInfo(messageBoxTitle, messageBoxMessage, wait);
  }

  @Override
  public boolean openQuestion(String title, String message, boolean defval, boolean wait) {
    return plugin.openQuestion(title, message, defval, wait);
  }

  @Override
  public boolean openQuestionOkCancel(String title, String message, boolean defval, boolean wait) {
    return plugin.openQuestionOkCancel(title, message, defval, wait);
  }

  @Override
  public Document openSelectDoc(Document docfrom, Document docdefault) {
    return plugin.openSelectDoc(docfrom, docdefault);
  }

  @Override
  public Pair<String, Boolean> openInputDialog(String title, String message, String def, InputValidator inputValidator) {
    return plugin.openInputDialog(title, message, def, inputValidator);
  }

  @Override
  public Pair<AtomicBoolean, OperationResult> openQuestionOkCancel(String title, String message, boolean def, boolean waitFor,
      Runnable okActons, Runnable cancelActions) {
    return plugin.openQuestionOkCancel(title, message, def, waitFor, okActons, cancelActions);
  }

  @Override
  public Object openEditor(String editorid, TreeNode node, TreeNode sel) {
    return plugin.openEditor(editorid, node, sel);
  }
}
