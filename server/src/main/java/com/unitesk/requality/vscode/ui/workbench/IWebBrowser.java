package com.unitesk.requality.vscode.ui.workbench;

import java.net.URL;

/** {@code org.eclipse.ui.browser.IWebBrowser}. */
public interface IWebBrowser {
  void openURL(URL url) throws PartInitException;

  default boolean close() {
    return true;
  }

  default String getId() {
    return "external";
  }
}
