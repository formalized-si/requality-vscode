package com.unitesk.requality.vscode.ui.tabbed;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/** {@code org.eclipse.ui.views.properties.tabbed.AbstractPropertySection}. */
public abstract class AbstractPropertySection implements ISection {
  public static final int STANDARD_LABEL_WIDTH = 85;
  private TabbedPropertySheetPage tabbedPropertySheetPage;
  private IWorkbenchPart part;
  private ISelection selection;

  public TabbedPropertySheetWidgetFactory getWidgetFactory() {
    return tabbedPropertySheetPage == null ? new TabbedPropertySheetWidgetFactory() : tabbedPropertySheetPage.getWidgetFactory();
  }

  public IWorkbenchPart getPart() {
    return part;
  }

  public ISelection getSelection() {
    return selection;
  }

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage page) {
    this.tabbedPropertySheetPage = page;
  }

  @Override
  public void setInput(IWorkbenchPart part, ISelection selection) {
    this.part = part;
    this.selection = selection;
  }

  @Override
  public void aboutToBeShown() {
  }

  @Override
  public void aboutToBeHidden() {
  }

  @Override
  public void dispose() {
  }

  @Override
  public int getMinimumHeight() {
    return -1;
  }

  @Override
  public boolean shouldUseExtraSpace() {
    return false;
  }

  @Override
  public void refresh() {
  }
}
