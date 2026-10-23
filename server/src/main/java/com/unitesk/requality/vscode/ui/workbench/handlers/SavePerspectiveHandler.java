package com.unitesk.requality.vscode.ui.workbench.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.dialogs.SavePerspectiveDialog;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry;

/**
 * {@code org.eclipse.ui.internal.handlers.SavePerspectiveHandler}: the default handler of {@code
 * org.eclipse.ui.window.savePerspective} (Window &gt; Perspective &gt; Save Perspective As...). The current perspective is
 * saved under the name chosen, as a new or an existing perspective (see {@link IWorkbenchPage#savePerspectiveAs}).
 */
public class SavePerspectiveHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
    if (window == null)
      return null;
    IWorkbenchPage page = window.getActivePage();
    if (page == null)
      return null;
    IPerspectiveDescriptor descriptor = page.getPerspective();
    if (descriptor == null)
      return null;
    PerspectiveRegistry reg = PerspectiveRegistry.INSTANCE;
    SavePerspectiveDialog dlg = new SavePerspectiveDialog(window.getShell(), reg);
    dlg.setInitialSelection(reg.findPerspectiveWithId(descriptor.getId()));
    if (dlg.open() != IDialogConstants.OK_ID)
      return null;
    IPerspectiveDescriptor newDesc = dlg.getPersp();
    if (newDesc == null) {
      PerspectiveRegistry.PerspectiveDescriptor original = (PerspectiveRegistry.PerspectiveDescriptor) reg
          .findPerspectiveWithId(descriptor.getId());
      newDesc = reg.createPerspective(dlg.getPerspName(), original);
    }
    page.savePerspectiveAs(newDesc);
    return null;
  }
}
