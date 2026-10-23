package com.unitesk.requality.eclipse.ui.dialogs;

import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.core.EclipseTracker;
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
