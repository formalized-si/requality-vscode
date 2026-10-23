package com.unitesk.requality.vscode.ui.e4.workbench.modeling;

import com.unitesk.requality.vscode.ui.e4.model.application.ui.MUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPartSashContainerElement;

/**
 * {@code org.eclipse.e4.ui.workbench.modeling.EModelService}: the operations on the E4 model of the editor area, mapped
 * onto the VS Code editor groups by {@link EditorModel}.
 */
public interface EModelService {
  int ABOVE = 1 << 0;
  int BELOW = 1 << 1;
  int LEFT_OF = 1 << 2;
  int RIGHT_OF = 1 << 3;

  /**
   * Clones a container without its children (an editor group: an empty group with the same tags).
   * VSCODE: the application fragment argument of the E4 API is not modeled.
   */
  MUIElement cloneElement(MUIElement element, Object applicationFragment);

  /**
   * Inserts an element into the container of {@code relativeTo}, next to it. For RIGHT_OF (LEFT_OF) the selected editors
   * are placed side by side in VS Code. VSCODE: VS Code splits the groups evenly, so the ratio is not applied.
   */
  void insert(MPartSashContainerElement toInsert, MPartSashContainerElement relativeTo, int where, float ratio);

  /** Shows the element; an editor is revealed beside the editor group before its own. */
  void bringToTop(MUIElement element);
}
