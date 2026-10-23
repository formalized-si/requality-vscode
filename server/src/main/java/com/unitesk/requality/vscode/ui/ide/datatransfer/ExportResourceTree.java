package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * The checkable tree of the workspace resources of the export pages ({@code ContainerCheckedTreeViewer} of the IDE):
 * the open projects and their folders and files.
 */
final class ExportResourceTree {
  private ExportResourceTree() {
  }

  /** A checkbox tree of the workspace resources, with its providers. */
  static CheckboxTreeViewer create(Composite parent) {
    CheckboxTreeViewer viewer = new CheckboxTreeViewer(parent, SWT.BORDER | SWT.MULTI);
    viewer.setContentProvider(new ITreeContentProvider() {
      @Override
      public Object[] getChildren(Object parentElement) {
        try {
          if (parentElement instanceof IWorkspaceRoot root)
            return openProjects(root.getProjects());
          if (parentElement instanceof IContainer c)
            return c.members();
        } catch (CoreException e) {
          // no children
        }
        return new Object[0];
      }

      @Override
      public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
      }

      @Override
      public Object getParent(Object element) {
        return element instanceof IResource r ? r.getParent() : null;
      }

      @Override
      public boolean hasChildren(Object element) {
        return getChildren(element).length > 0;
      }

      @Override
      public void dispose() {
      }

      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      }
    });
    viewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return element instanceof IResource r ? r.getName() : String.valueOf(element);
      }
    });
    viewer.setInput(ResourcesPlugin.getWorkspace().getRoot());
    return viewer;
  }

  private static Object[] openProjects(IProject[] projects) {
    List<Object> r = new ArrayList<>();
    for (IProject p : projects)
      if (p.isOpen())
        r.add(p);
    return r.toArray();
  }

  /** The checked resources that are not inside another checked resource. */
  static List<IResource> topmostChecked(CheckboxTreeViewer viewer) {
    List<IResource> r = new ArrayList<>();
    for (Object o : viewer.getCheckedElements()) {
      IResource res = (IResource) o;
      boolean covered = false;
      for (IResource p = res.getParent(); p != null && !covered; p = p.getParent())
        if (viewer.getChecked(p))
          covered = true;
      if (!covered)
        r.add(res);
    }
    return r;
  }

  /** Checks or unchecks every resource of the tree. */
  static void checkAll(CheckboxTreeViewer viewer, boolean state) {
    viewer.setCheckedElements(new Object[0]);
    if (state)
      for (Object p : openProjects(ResourcesPlugin.getWorkspace().getRoot().getProjects()))
        checkRecursively(viewer, p);
  }

  private static void checkRecursively(CheckboxTreeViewer viewer, Object element) {
    viewer.setChecked(element, true);
    if (element instanceof IContainer c) {
      try {
        for (IResource m : c.members())
          checkRecursively(viewer, m);
      } catch (CoreException e) {
        // not checked below
      }
    }
  }
}
