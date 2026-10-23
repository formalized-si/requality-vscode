package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.Platform;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionContext;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.dialogs.PropertyDialogAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.PropertiesActionProvider}: adds the Properties action
 * (Alt+Enter, command org.eclipse.ui.file.properties) to the context menu, in group.properties, and makes it the
 * global Properties handler of the view.
 */
public class PropertiesActionProvider extends CommonActionProvider {
  private PropertyDialogAction propertiesAction;
  private ISelectionProvider delegateSelectionProvider;

  @Override
  public void init(final ICommonActionExtensionSite aSite) {
    super.init(aSite);
    delegateSelectionProvider = new DelegateSelectionProvider(aSite.getViewSite().getSelectionProvider());
    propertiesAction = new PropertyDialogAction(() -> aSite.getViewSite().getShell(), delegateSelectionProvider);
    propertiesAction.setActionDefinitionId(ActionFactory.PROPERTIES.getCommandId());
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    super.fillContextMenu(menu);
    if (propertiesAction.isApplicableForSelection())
      menu.appendToGroup(ICommonMenuConstants.GROUP_PROPERTIES, propertiesAction);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    super.fillActionBars(actionBars);
    actionBars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), propertiesAction);
  }

  @Override
  public void setContext(ActionContext context) {
    super.setContext(context);
    ISelection s = delegateSelectionProvider.getSelection();
    propertiesAction.selectionChanged(s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY);
  }

  /** The adapter of an element that is not IAdaptable itself: the adapters of the platform (Platform.getAdapterManager). */
  private static class DelegateIAdaptable implements IAdaptable {
    private final Object delegate;

    private DelegateIAdaptable(Object o) {
      delegate = o;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAdapter(Class<T> adapter) {
      if (adapter.isInstance(delegate) || delegate == null)
        return (T) delegate;
      return (T) Platform.getAdapterManager().getAdapter(delegate, adapter);
    }
  }

  /** The selection of the viewer, the elements wrapped so that the property dialog can adapt them. */
  private static class DelegateSelectionProvider implements ISelectionProvider {
    private final ISelectionProvider delegate;

    private DelegateSelectionProvider(ISelectionProvider s) {
      delegate = s;
    }

    @Override
    public void addSelectionChangedListener(ISelectionChangedListener listener) {
      delegate.addSelectionChangedListener(listener);
    }

    @Override
    public ISelection getSelection() {
      if (delegate.getSelection() instanceof IStructuredSelection) {
        IStructuredSelection sSel = (IStructuredSelection) delegate.getSelection();
        if (sSel.isEmpty() || sSel.getFirstElement() instanceof IAdaptable)
          return sSel;
        return new StructuredSelection(new DelegateIAdaptable(sSel.getFirstElement()));
      }
      return delegate.getSelection();
    }

    @Override
    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
      delegate.removeSelectionChangedListener(listener);
    }

    @Override
    public void setSelection(ISelection selection) {
      delegate.setSelection(selection);
    }
  }
}
