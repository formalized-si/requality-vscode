package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.browser.IWorkbenchBrowserSupport}. */
public interface IWorkbenchBrowserSupport {
  int AS_EDITOR = 1;
  int AS_VIEW = 2;
  int AS_EXTERNAL = 4;
  int LOCATION_BAR = 8;
  int NAVIGATION_BAR = 16;

  IWebBrowser getExternalBrowser() throws PartInitException;

  IWebBrowser createBrowser(int style, String browserId, String name, String tooltip) throws PartInitException;

  IWebBrowser createBrowser(String browserId) throws PartInitException;

  default boolean isInternalWebBrowserAvailable() {
    return false;
  }
}
