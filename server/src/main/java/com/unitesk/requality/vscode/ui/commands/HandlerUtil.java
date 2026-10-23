package com.unitesk.requality.vscode.ui.commands;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.ISources;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/** {@code org.eclipse.ui.handlers.HandlerUtil}. */
public final class HandlerUtil {
  private HandlerUtil() {
  }

  public static Object getVariable(ExecutionEvent event, String name) {
    return getVariable(event.getApplicationContext(), name);
  }

  public static Object getVariable(Object context, String name) {
    if (context instanceof IEvaluationContext) {
      Object v = ((IEvaluationContext) context).getVariable(name);
      return v == IEvaluationContext.UNDEFINED_VARIABLE ? null : v;
    }
    return null;
  }

  public static ISelection getCurrentSelection(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_CURRENT_SELECTION_NAME);
    return o instanceof ISelection ? (ISelection) o : null;
  }

  /** The current selection as a structured selection (empty when it is not one). */
  public static com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection getCurrentStructuredSelection(ExecutionEvent event) {
    ISelection s = getCurrentSelection(event);
    return s instanceof com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection
        ? (com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) s
        : com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection.EMPTY;
  }

  public static ISelection getCurrentSelectionChecked(ExecutionEvent event) throws ExecutionException {
    ISelection s = getCurrentSelection(event);
    if (s == null)
      throw new ExecutionException("No current selection");
    return s;
  }

  public static ISelection getActiveMenuSelection(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_MENU_SELECTION_NAME);
    return o instanceof ISelection ? (ISelection) o : getCurrentSelection(event);
  }

  public static Shell getActiveShell(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_SHELL_NAME);
    return o instanceof Shell ? (Shell) o : null;
  }

  public static Shell getActiveShellChecked(ExecutionEvent event) {
    return getActiveShell(event);
  }

  public static IWorkbenchWindow getActiveWorkbenchWindow(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_WORKBENCH_WINDOW_NAME);
    return o instanceof IWorkbenchWindow ? (IWorkbenchWindow) o : null;
  }

  public static IWorkbenchWindow getActiveWorkbenchWindowChecked(ExecutionEvent event) throws ExecutionException {
    IWorkbenchWindow w = getActiveWorkbenchWindow(event);
    if (w == null)
      throw new ExecutionException("No active workbench window");
    return w;
  }

  public static IWorkbenchPart getActivePart(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_PART_NAME);
    return o instanceof IWorkbenchPart ? (IWorkbenchPart) o : null;
  }

  public static String getActivePartId(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_PART_ID_NAME);
    return o instanceof String ? (String) o : null;
  }

  public static IEditorPart getActiveEditor(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_EDITOR_NAME);
    return o instanceof IEditorPart ? (IEditorPart) o : null;
  }

  public static String getActiveEditorId(ExecutionEvent event) {
    Object o = getVariable(event, ISources.ACTIVE_EDITOR_ID_NAME);
    return o instanceof String ? (String) o : null;
  }
}
