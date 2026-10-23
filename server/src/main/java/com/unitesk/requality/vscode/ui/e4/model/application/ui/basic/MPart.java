package com.unitesk.requality.vscode.ui.e4.model.application.ui.basic;

import com.unitesk.requality.vscode.ui.e4.model.application.ui.AbstractUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.impl.PartStackImpl;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;

/**
 * {@code org.eclipse.e4.ui.model.application.ui.basic.MPart}: the model of an editor. It is in the editor group (a
 * {@link PartStackImpl} tagged "Editor") that shows it; the editor is the VS Code editor tab of the server.
 */
public class MPart extends AbstractUIElement {
  private final IEditorPart editor;

  public MPart(IEditorPart editor) {
    this.editor = editor;
    getTags().add("Editor");
  }

  /** The editor this part models. */
  public IEditorPart getEditor() {
    return editor;
  }

  /** Visible: the shown element of its editor group. */
  @Override
  public boolean isVisible() {
    MUIElement parent = null;
    if (getParent() instanceof PartStackImpl)
      parent = ((PartStackImpl) getParent()).getSelectedElement();
    return parent == this;
  }
}
