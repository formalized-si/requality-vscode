package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.CompoundContributionItem;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/**
 * {@code org.eclipse.ui.internal.ShowInMenu}: the targets of "Show In" for the selected resource, in the order
 * Project Explorer, Properties, System Explorer, Terminal. A target runs {@code client/showIn} with the target and
 * the location of the resource ({@code target}: "explorer", "properties", "system" or "terminal"; the terminal opens
 * in the folder of a file).
 */
public class ShowInMenu extends CompoundContributionItem {
  /** The targets: label and the target of {@code client/showIn}. */
  static final String[][] TARGETS = {
      { "Project Explorer", "explorer" },
      { "Properties", "properties" },
      { "System Explorer", "system" },
      { "Terminal", "terminal" } };

  private final IWorkbenchWindow window;

  public ShowInMenu(IWorkbenchWindow window, String id) {
    super(id);
    this.window = window;
  }

  @Override
  protected IContributionItem[] getContributionItems() {
    List<IContributionItem> items = new ArrayList<>();
    IResource resource = selectedResource();
    if (resource == null) {
      Action none = new Action("<No Applicable Views>") {
        @Override
        public void run() {
        }
      };
      none.setEnabled(false);
      items.add(new ActionContributionItem(none));
      return items.toArray(new IContributionItem[0]);
    }
    for (String[] t : TARGETS)
      items.add(new ActionContributionItem(new ShowInAction(t[0], t[1], resource)));
    return items.toArray(new IContributionItem[0]);
  }

  /** The first resource of the selection of the window, or null. */
  private IResource selectedResource() {
    if (window == null || window.getSelectionService() == null)
      return null;
    Object s = window.getSelectionService().getSelection();
    if (s instanceof IStructuredSelection && !((IStructuredSelection) s).isEmpty()) {
      Object o = ((IStructuredSelection) s).getFirstElement();
      if (o instanceof IResource)
        return (IResource) o;
    }
    return null;
  }

  /** One target of Show In. */
  private static final class ShowInAction extends Action {
    private final String target;
    private final IResource resource;

    ShowInAction(String label, String target, IResource resource) {
      super(label);
      this.target = target;
      this.resource = resource;
    }

    @Override
    public void run() {
      IResource r = resource;
      if (target.equals("terminal") && r instanceof IFile)
        r = r.getParent();
      JsonObject p = new JsonObject();
      p.addProperty("target", target);
      p.addProperty("path", r.getLocation().toOSString());
      if (ServerContext.server() != null)
        ServerContext.client().notify("client/showIn", p);
    }
  }
}
