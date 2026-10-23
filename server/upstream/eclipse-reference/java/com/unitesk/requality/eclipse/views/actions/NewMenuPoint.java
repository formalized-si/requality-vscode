package com.unitesk.requality.eclipse.views.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.actions.CompoundContributionItem;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.handlers.NewElementHandler;

public class NewMenuPoint extends CompoundContributionItem {

  private static final IContributionItem[] NO_ITEMS = new IContributionItem[0];

  public NewMenuPoint() {
    super();
    WorkspaceJob j = new WorkspaceJob("Load new elements handlers"){
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        loadNewElementHandlers();
        return Status.OK_STATUS;
      }
    };
    j.schedule();
  }

  protected Set<NewElementHandler> loadNewElementHandlers() {
    synchronized (this) {
      return Activator.getDefault().getNewElementHandlers();
    }
  }
  
  public NewMenuPoint(String id) {
    super(id);
    WorkspaceJob j = new WorkspaceJob("Load new elements handlers"){
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
         loadNewElementHandlers();
        return Status.OK_STATUS;
      }
    };
    j.schedule();
  }

  @Override
  protected IContributionItem[] getContributionItems() {
    SelectionRecord sels = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (sels == null || sels.fullSelection.size() != 1) return NO_ITEMS;
    TreeNode nd = sels.getFirstNode();
    if(nd == null)
      return NO_ITEMS;
    TreeNode node = nd;
    TreeDB db = node.getTreeDB();
    List<IContributionItem> items = new ArrayList<>();
    List<IContributionItem> before = new LinkedList<>(),
        after = new LinkedList<>();
    Collection<CreateElementOperation> els = CreateElementOperation.getHandlers();
    Set<String> was = new HashSet<>();
    for (CreateElementOperation neh : els)
      if (neh.isEnabled(node)) {
    	String wid = neh.getClass().getName();
        if(was.contains(wid))
          continue;
        was.add(wid);
        String[] types = neh.getElementTypes();
        for (String type : types) {
          TreeNode templ = node.getTreeDB().getActiveTemplate(type);
          String label = neh.getShowedElementType(db,type);
          if (templ != null) {
            String id = templ.getId();
            if (id.length() > 17) id = id.substring(0, 17) + "...";
            label += " ('" + id + "')";
          }
          ActionContributionItem aci =
              new ActionContributionItem(
                  new NewTypedTemplatedMenuPoint(
                      node, type, templ != null ? templ.getId() : "", neh, label));
          if (aci.isVisible()) before.add(aci);
          NewTypedMenuPoint mp = new NewTypedMenuPoint(node, neh, type);
          if (mp.isVisible()) after.add(mp);
        }
      }
    Comparator<IContributionItem> comp =
        new Comparator<IContributionItem>() {
          @Override
          public int compare(IContributionItem o1, IContributionItem o2) {
            return o1.toString().compareTo(o2.toString());
          }
        };
    Collections.sort(before, comp);
    Collections.sort(after, comp);
    items.addAll(before);
    if (after.size() > 0) {
      items.add(new Separator());
      items.addAll(after);
    }
    return items.toArray(new IContributionItem[0]);
  }
}
