package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IEditorRegistry}: the editors of the plug-in and of VS Code, and their associations. */
public interface IEditorRegistry {
  /** {@code IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID}: the operating system's application for the file. */
  String SYSTEM_EXTERNAL_EDITOR_ID = "org.eclipse.ui.systemExternalEditor";
  /** {@code IEditorRegistry.SYSTEM_INPLACE_EDITOR_ID}: not offered by the server (no in-place system editor). */
  String SYSTEM_INPLACE_EDITOR_ID = "org.eclipse.ui.systemInPlaceEditor";
  /** {@code IDEWorkbenchPlugin.DEFAULT_TEXT_EDITOR_ID}: the text editor of VS Code. */
  String DEFAULT_TEXT_EDITOR_ID = "org.eclipse.ui.DefaultTextEditor";

  IEditorDescriptor findEditor(String editorId);

  /** {@code IEditorRegistry.getDefaultEditor(String)}: the editor associated with the file name, else the text editor. */
  IEditorDescriptor getDefaultEditor(String fileName);

  /** {@code IEditorRegistry.getEditors(String)}: the editors associated with the file name. */
  IEditorDescriptor[] getEditors(String fileName);

  /** {@code IEditorRegistry.getEditors(String, IContentType)}: content type given by its id (null: none). */
  IEditorDescriptor[] getEditors(String fileName, String contentTypeId);

  /** {@code IEditorRegistry.getSortedEditorsFromPlugins()}: every editor the plug-in and VS Code declare. */
  IEditorDescriptor[] getSortedEditorsFromPlugins();

  /** {@code org.eclipse.ui.IEditorRegistry#getImageDescriptor(String)}: the image of the default editor for a file name. */
  com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor getImageDescriptor(String filename);

  /** {@code org.eclipse.ui.IEditorDescriptor}. */
  interface IEditorDescriptor {
    String getId();

    String getLabel();

    default com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor getImageDescriptor() {
      return null;
    }

    /** {@code IEditorDescriptor.isOpenExternal}: the editor runs outside the workbench (system editor). */
    default boolean isOpenExternal() {
      return false;
    }
  }
}
