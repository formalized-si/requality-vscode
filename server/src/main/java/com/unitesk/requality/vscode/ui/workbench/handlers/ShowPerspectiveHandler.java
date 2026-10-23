package com.unitesk.requality.vscode.ui.workbench.handlers;

import java.util.Map;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;
import com.unitesk.requality.vscode.ui.workbench.dialogs.SelectPerspectiveDialog;

/**
 * {@code org.eclipse.ui.handlers.ShowPerspectiveHandler}: the default handler of {@code org.eclipse.ui.perspectives.showPerspective}
 * (Window &gt; Open Perspective). Without a perspective parameter the Open Perspective dialog asks for one; with it the
 * perspective is opened. VSCODE: the workbench has one window, so the newWindow parameter opens in the same window.
 */
public class ShowPerspectiveHandler extends AbstractHandler {
  /** {@code org.eclipse.ui.perspectives.showPerspective.perspectiveId}: the perspective to open. */
  public static final String PERSPECTIVE_ID_PARAMETER = "org.eclipse.ui.perspectives.showPerspective.perspectiveId";
  /** {@code org.eclipse.ui.perspectives.showPerspective.newWindow}: "true" to open in a new window. */
  public static final String NEW_WINDOW_PARAMETER = "org.eclipse.ui.perspectives.showPerspective.newWindow";

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
    Map<?, ?> parameters = event.getParameters();
    Object value = parameters.get(PERSPECTIVE_ID_PARAMETER);
    if (value == null)
      openOther(window);
    else
      openPerspective((String) value);
    return null;
  }

  /** Opens the Open Perspective dialog and opens the perspective chosen in it. */
  private void openOther(IWorkbenchWindow window) throws ExecutionException {
    SelectPerspectiveDialog dialog = new SelectPerspectiveDialog(window.getShell(),
        PlatformUI.getWorkbench().getPerspectiveRegistry());
    dialog.open();
    if (dialog.getReturnCode() == Window.CANCEL)
      return;
    IPerspectiveDescriptor descriptor = dialog.getSelection();
    if (descriptor != null)
      openPerspective(descriptor.getId());
  }

  private void openPerspective(String perspectiveId) throws ExecutionException {
    try {
      PlatformUI.getWorkbench().showPerspective(perspectiveId, null);
    } catch (WorkbenchException e) {
      throw new ExecutionException("Perspective could not be opened.", e);
    }
  }
}
