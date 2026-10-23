package com.unitesk.requality.vscode.services;

import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.rpc.RpcException;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWizard;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * The new, import and export wizards of plugin.xml ({@code org.eclipse.ui.newWizards}, {@code importWizards},
 * {@code exportWizards}): {@code wizard/open} creates the wizard, initializes it with the workbench selection and
 * opens it in a WizardDialog (File > New / Import / Export in Eclipse). Returns true when the wizard finished.
 */
public final class WizardService implements Services.Service {
  private static final String[] POINTS = { "org.eclipse.ui.newWizards", "org.eclipse.ui.importWizards",
      "org.eclipse.ui.exportWizards" };

  @Override
  public void register(RequalityServer srv) {
    srv.rpc().onRequest("wizard/open", p -> {
      srv.requireInitialized();
      String id = Nodes.string(p, "id");
      IConfigurationElement el = find(id);
      if (el == null)
        throw RpcException.notFound("Wizard " + id + " is not defined");
      return Display.getDefault().syncCall(() -> {
        if (p.has("nodes") && p.get("nodes").isJsonArray())
          SelectionService.select(Nodes.optString(p, "source", "explorer"), p.getAsJsonArray("nodes"));
        return open(el);
      });
    });
  }

  static IConfigurationElement find(String id) {
    for (String point : POINTS)
      for (IConfigurationElement c : ExtensionRegistry.get().getConfigurationElementsFor(point))
        if ("wizard".equals(c.getName()) && id.equals(c.getAttribute("id")))
          return c;
    return null;
  }

  /** Opens a wizard contribution (on the UI thread); true if it finished. */
  static boolean open(IConfigurationElement el) {
    Object w;
    try {
      w = el.createExecutableExtension("class");
    } catch (CoreException e) {
      throw new RpcException(RpcException.NOT_FOUND, e.getMessage());
    }
    if (w instanceof IWorkbenchWizard) {
      Object sel = Workbench.currentSelection();
      ((IWorkbenchWizard) w).init(PlatformUI.getWorkbench(),
          sel instanceof IStructuredSelection ? (IStructuredSelection) sel : StructuredSelection.EMPTY);
    }
    WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), (IWizard) w);
    return dialog.open() == Window.OK;
  }
}
