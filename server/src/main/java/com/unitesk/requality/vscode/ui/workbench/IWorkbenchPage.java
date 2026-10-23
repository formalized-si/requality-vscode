package com.unitesk.requality.vscode.ui.workbench;

import java.util.List;

/** {@code org.eclipse.ui.IWorkbenchPage}. */
public interface IWorkbenchPage {
  int VIEW_ACTIVATE = 1;
  int VIEW_VISIBLE = 2;
  int VIEW_CREATE = 3;
  int MATCH_NONE = 0;
  int MATCH_INPUT = 1;
  int MATCH_ID = 2;

  IWorkbenchPart getActivePart();

  IWorkbenchPartReference getActivePartReference();

  IEditorPart getActiveEditor();

  IEditorReference[] getEditorReferences();

  IEditorPart[] getEditors();

  IEditorPart findEditor(IEditorInput input);

  IViewReference[] getViewReferences();

  IViewPart findView(String viewId);

  IViewReference findViewReference(String viewId);

  IViewPart showView(String viewId) throws PartInitException;

  IViewPart showView(String viewId, String secondaryId, int mode) throws PartInitException;

  void hideView(IViewPart view);

  IEditorPart openEditor(IEditorInput input, String editorId) throws PartInitException;

  IEditorPart openEditor(IEditorInput input, String editorId, boolean activate) throws PartInitException;

  IEditorPart openEditor(IEditorInput input, String editorId, boolean activate, int matchFlags)
      throws PartInitException;

  boolean closeEditor(IEditorPart editor, boolean save);

  boolean closeEditors(IEditorReference[] editorRefs, boolean save);

  boolean closeAllEditors(boolean save);

  void activate(IWorkbenchPart part);

  void bringToTop(IWorkbenchPart part);

  boolean isPartVisible(IWorkbenchPart part);

  IWorkbenchWindow getWorkbenchWindow();

  /** The input of the page (the workspace root). */
  com.unitesk.requality.vscode.ui.runtime.IAdaptable getInput();

  com.unitesk.requality.vscode.ui.jface.viewers.ISelection getSelection();

  com.unitesk.requality.vscode.ui.jface.viewers.ISelection getSelection(String partId);

  void addSelectionListener(ISelectionListener listener);

  void addPartListener(IPartListener listener);

  /** {@code IWorkbenchPage.addPartListener(IPartListener2)}. */
  void addPartListener(IPartListener2 listener);

  void removePartListener(IPartListener2 listener);

  void removePartListener(IPartListener listener);

  void removeSelectionListener(ISelectionListener listener);

  IPerspectiveRegistry.IPerspectiveDescriptor getPerspective();

  /** {@code IWorkbenchPage#getShowViewShortcuts()}: the ids of the views of the show view shortcuts of the perspective. */
  String[] getShowViewShortcuts();

  /** {@code IWorkbenchPage#getNewWizardShortcuts()}: the ids of the New submenu shortcuts (wizards) of the perspective. */
  String[] getNewWizardShortcuts();

  /** The ids of the Open Perspective submenu shortcuts of the perspective. */
  String[] getPerspectiveShortcuts();

  /**
   * {@code Customize Perspective...}: the shortcuts of the three submenus and the hidden menu items become the customization
   * of the current perspective (kept with the perspective, applied whenever it is shown). The Show View menu is published.
   */
  void customizePerspective(List<String> newWizards, List<String> perspectives, List<String> showViews,
      List<String> menuHidden);

  void setPerspective(IPerspectiveRegistry.IPerspectiveDescriptor perspective);

  /** {@code IWorkbenchPage#getOpenPerspectives()}: the perspectives open in the page. */
  IPerspectiveRegistry.IPerspectiveDescriptor[] getOpenPerspectives();

  /** {@code IWorkbenchPage#resetPerspective()}: arranges the views of the current perspective as its defaults. */
  void resetPerspective();

  /** {@code IWorkbenchPage#closePerspective(IPerspectiveDescriptor, boolean, boolean)}. */
  void closePerspective(IPerspectiveRegistry.IPerspectiveDescriptor desc, boolean saveParts, boolean closePage);

  /** {@code IWorkbenchPage#closeAllPerspectives(boolean, boolean)}. */
  void closeAllPerspectives(boolean saveEditors, boolean closePage);

  /**
   * {@code WorkbenchPage#savePerspectiveAs(IPerspectiveDescriptor)}: the views of the page are saved as the layout of the
   * perspective, which becomes the current one. The descriptor is a saved perspective of the registry.
   */
  void savePerspectiveAs(IPerspectiveRegistry.IPerspectiveDescriptor desc);

  /** The working sets of the window: the page's working sets (the elements of the window working set). */
  IWorkingSet[] getWorkingSets();

  /** Sets the working sets of the window (null: none). */
  void setWorkingSets(IWorkingSet[] workingSets);

  /** The aggregate of the window's working sets ("Window Working Set"), kept up to date with them. */
  IAggregateWorkingSet getAggregateWorkingSet();
}
