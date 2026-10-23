package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.IColorProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.graphics.RGB;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public abstract class SelectNodeFromRequirementsTree extends SelectNodeDialog {
  private class SNLabelProvider extends LabelProvider implements IColorProvider {
    @Override
    public Image getImage(Object element) {
      if (element instanceof Requirement) {
        Requirement req = (Requirement) element;
        if (req.getQualifiedId().equals("/Requirements")) {
          try {
            return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider()
                .getImage(EclipseTracker.getResourceByNode(req, LockType.NO_LOCK).getProject());
          } catch (LockIsBusy e) {
            RLogger.logDebug(CoreUtils.getTrace(e),7);
          }
        }
      }
      return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider().getImage(element);
    }

    @Override
    public String getText(Object element) {
      if (element instanceof Requirement) {
        Requirement req = (Requirement) element;
        if (req.getQualifiedId().equals("/Requirements")) {
          try {
            return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider()
                .getText(EclipseTracker.getResourceByNode(req, LockType.NO_LOCK).getProject());
          } catch (LockIsBusy e) {
            RLogger.logDebug(CoreUtils.getTrace(e),7);
          }
        }
      }
      return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider().getText(element);
    }

    @Override
    public Color getForeground(Object element) {
      return Display.getCurrent().getSystemColor(SWT.COLOR_LIST_FOREGROUND);
    }

    @Override
    public Color getBackground(Object element) {
      if (getEnablementFilter() != null && !getEnablementFilter().select((TreeNode) element))
        return new Color(Display.getCurrent(), new RGB(225, 225, 225));

      return Display.getCurrent().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
    }
  }

  public SelectNodeFromRequirementsTree(Shell shell, TreeDB treedb, boolean b) {
    super(shell, treedb, b);
  }

  private ILabelProvider lprovider = new SNLabelProvider();

  @Override
  protected ILabelProvider getLabelProvider() {
    return lprovider;
  }
}
