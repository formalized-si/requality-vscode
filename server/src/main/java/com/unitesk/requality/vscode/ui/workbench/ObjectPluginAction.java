package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.ObjectPluginAction}: an action of a popupMenus {@code objectContribution}. Its delegate
 * (the {@code class} of the action) is created when the action is first selected, is told the selection of the menu
 * and runs; the action is enabled when its {@code enablesFor} accepts the number of selected elements and the delegate
 * does not disable it.
 */
public final class ObjectPluginAction extends Action {
  private final IConfigurationElement element;
  private final String enablesFor;
  private IActionDelegate delegate;
  private boolean delegateFailed;
  private IStructuredSelection selection;

  ObjectPluginAction(IConfigurationElement element, String text, String enablesFor) {
    super(text);
    this.element = element;
    this.enablesFor = enablesFor;
  }

  /** The action of the contribution element, with its id and tooltip. */
  IConfigurationElement element() {
    return element;
  }

  /** ObjectPluginAction.selectionChanged: the menu is filled for the selection; the delegate may enable or disable. */
  void selectionChanged(IStructuredSelection sel) {
    selection = sel;
    boolean count = ObjectActionContributor.enablesFor(enablesFor, sel.size());
    setEnabled(count);
    IActionDelegate d = delegate();
    if (d != null)
      try {
        d.selectionChanged(this, sel);
      } catch (RuntimeException e) {
        RLogger.logError(e);
        setEnabled(false);
      }
    if (!count)
      setEnabled(false);
  }

  private IActionDelegate delegate() {
    if (delegate == null && !delegateFailed) {
      try {
        Object o = element.createExecutableExtension("class");
        if (o instanceof IObjectActionDelegate)
          ((IObjectActionDelegate) o).setActivePart(this, null);
        delegate = o instanceof IActionDelegate ? (IActionDelegate) o : null;
        if (delegate == null)
          delegateFailed = true;
      } catch (CoreException | RuntimeException e) {
        delegateFailed = true;
        RLogger.logInfo("Action " + element.getAttribute("id") + " is not available: " + e.getMessage());
      }
    }
    return delegate;
  }

  @Override
  public void run() {
    IActionDelegate d = delegate();
    if (d == null)
      return;
    try {
      d.selectionChanged(this, selection);
      d.run(this);
    } catch (RuntimeException e) {
      RLogger.logError(e);
      throw e;
    }
  }
}
