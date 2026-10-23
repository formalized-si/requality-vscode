package com.unitesk.requality.eclipse.ui.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.editors.EditorUtils;

public abstract class TreeViewerDialog<T> extends Dialog {

  protected TreeViewer nodeTree = null;
  protected Button createButton = null;

  protected T selectedNode = null;
  protected Composite header = null;

  protected String getRootQId() {
    return "/";
  }

  public TreeViewerDialog(Shell parentShell) {
    super(parentShell);
  }

  protected MouseListener mouseList =
      new MouseListener() {
        public void mouseDoubleClick(MouseEvent e) {
          TreeItem item = nodeTree.getTree().getItem(new Point(e.x, e.y));
          if (item != null && canSelectNode()) okPressed();
          //buttonBar.setEnabled(canSelectNode());
        }

        public void mouseDown(MouseEvent e) {}

        public void mouseUp(MouseEvent e) {}
      };

  protected KeyListener keyList =
      new KeyListener() {
        public void keyPressed(KeyEvent e) {
          if (e.keyCode == ' ') {
            TreeItem[] items = nodeTree.getTree().getSelection();
            if (items.length > 0) {
              TreeItem item = items[0];
              if (item.getExpanded() == false) {
                item.setExpanded(true);
                Event e1 = new Event();
                e1.item = item;
                nodeTree.getTree().notifyListeners(SWT.Expand, e1);
              } else item.setExpanded(false);
            }
          }
        }

        public void keyReleased(KeyEvent e) {}
      };

  protected ILabelProvider getLabelProvider() {
    return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
  }

  public TreeViewerDialog(IShellProvider parentShell) {
    super(parentShell);
  }

  public boolean canCreateNode() {
    return selectedNode != null;
  }

  public boolean canSelectNode() {
    if (selectedNode == null) return false;
    return true;
  }

  public boolean canSelect() {
    return selectedNode != null && canSelectNode();
  }

  protected Composite createClientContainer(Composite parent, int span) {
    Composite container = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = layout.marginHeight = 10;
    layout.verticalSpacing = 2;
    layout.numColumns = span;
    container.setLayout(layout);
    return container;
  }

  protected void refresh() {
    if (nodeTree != null && !nodeTree.getControl().isDisposed()) {
      nodeTree.refresh();

      if (selectedNode != null) {
        select(selectedNode);
      }
    }
  }

  protected void updateUI() {
    Button ok = getButton(IDialogConstants.OK_ID);
    if (createButton != null && !createButton.isDisposed()) {
      createButton.setEnabled(canCreateNode());
    }
    if (ok != null && !ok.isDisposed()) {
      ok.setEnabled(canSelect());
    }
  }

  public T getResult() {
    if (selectedNode != null) {
      return selectedNode;
    }
    return null;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    super.createButtonsForButtonBar(parent);
    updateUI();
  }

  @SuppressWarnings("unchecked")
  public void select(Object obj) {
    if (obj != null) {
      selectedNode = (T) obj;
      if (nodeTree != null && !nodeTree.getControl().isDisposed()) {
        if(selectedNode instanceof TreeNode)
          nodeTree.expandToLevel(EditorUtils.formPathOrElement(nodeTree.getContentProvider(),nodeTree.getInput(), (TreeNode)selectedNode), 0);
        else
          nodeTree.expandToLevel(selectedNode, 0);
        nodeTree.setSelection(new StructuredSelection(obj), true);
        updateUI();
      }
    }
  }
}
