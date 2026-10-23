package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.views.IViewRegistry;

/** {@code org.eclipse.ui.IWorkbench}. */
public interface IWorkbench {
  Display getDisplay();

  IWorkbenchWindow getActiveWorkbenchWindow();

  IWorkbenchWindow[] getWorkbenchWindows();

  ISharedImages getSharedImages();

  IWorkbenchOperationSupport getOperationSupport();

  IWorkbenchHelpSystem getHelpSystem();

  IWorkbenchBrowserSupport getBrowserSupport();

  IThemeManager getThemeManager();

  <T> T getService(Class<T> api);

  boolean isClosing();

  /** {@code IWorkbench.isStarting()}: the workbench is never starting once the server runs. */
  boolean isStarting();

  /** {@code IWorkbench.addWindowListener}. */
  void addWindowListener(IWindowListener listener);

  void removeWindowListener(IWindowListener listener);

  IDecoratorManager getDecoratorManager();

  IEditorRegistry getEditorRegistry();

  IPerspectiveRegistry getPerspectiveRegistry();

  /** The views and the categories of the {@code org.eclipse.ui.views} extension point. */
  IViewRegistry getViewRegistry();

  /**
   * {@code org.eclipse.ui.IWorkbench#showPerspective(String, IWorkbenchWindow)}: opens the perspective in the page of
   * the window (the active one if null).
   *
   * @throws WorkbenchException if there is no such perspective
   */
  IWorkbenchPage showPerspective(String perspectiveId, IWorkbenchWindow window) throws WorkbenchException;

  /** The wizards of the {@code org.eclipse.ui.newWizards} extension point. */
  IWizardRegistry getNewWizardRegistry();

  /** The wizards of the {@code org.eclipse.ui.importWizards} extension point. */
  IWizardRegistry getImportWizardRegistry();

  /** The wizards of the {@code org.eclipse.ui.exportWizards} extension point. */
  IWizardRegistry getExportWizardRegistry();

  /** The working set manager of the workbench. */
  IWorkingSetManager getWorkingSetManager();

  boolean saveAllEditors(boolean confirm);

  /** Restarts the workbench (VS Code: the user is asked to reload the window). */
  boolean restart();
}
