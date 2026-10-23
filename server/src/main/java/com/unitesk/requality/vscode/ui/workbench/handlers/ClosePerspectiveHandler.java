package com.unitesk.requality.vscode.ui.workbench.handlers;

import java.util.Map;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.handlers.ClosePerspectiveHandler}: the default handler of
 * {@code org.eclipse.ui.window.closePerspective} (Window &gt; Perspective &gt; Close Perspective): closes the perspective
 * of the parameter, or the current one.
 */
public class ClosePerspectiveHandler extends AbstractHandler {
  /** {@code org.eclipse.ui.window.closePerspective.perspectiveId}: the perspective to close (the current one if absent). */
  public static final String PERSPECTIVE_ID_PARAMETER = "org.eclipse.ui.window.closePerspective.perspectiveId";

  @Override
  public Object execute(ExecutionEvent event) {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
    if (window == null)
      return null;
    IWorkbenchPage page = window.getActivePage();
    if (page == null)
      return null;
    Map<?, ?> parameters = event.getParameters();
    Object value = parameters.get(PERSPECTIVE_ID_PARAMETER);
    if (value == null) {
      page.closePerspective(page.getPerspective(), true, true);
    } else {
      IPerspectiveDescriptor perspective = PlatformUI.getWorkbench().getPerspectiveRegistry()
          .findPerspectiveWithId((String) value);
      if (perspective != null)
        page.closePerspective(perspective, true, true);
    }
    return null;
  }
}
