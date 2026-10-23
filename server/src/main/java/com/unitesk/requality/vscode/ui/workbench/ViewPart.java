package com.unitesk.requality.vscode.ui.workbench;

/**
 * {@code org.eclipse.ui.part.ViewPart}: a view rendered by the server in a VS Code webview view with the same id
 * (see {@link Workbench#registerView}).
 */
public abstract class ViewPart extends WorkbenchPart implements IViewPart {
  @Override
  public void init(IViewSite site) throws PartInitException {
    setSite(site);
  }

  @Override
  public IViewSite getViewSite() {
    return (IViewSite) getSite();
  }
}
