package com.unitesk.requality.vscode.ui.workbench;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@code org.eclipse.ui.IDecoratorManager}: {@link #update(String)} asks the views showing decorated elements (the
 * Requality Explorer) to recompute labels.
 */
public interface IDecoratorManager {
  List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

  default void update(String decoratorId) {
    for (Consumer<String> l : LISTENERS)
      l.accept(decoratorId);
  }

  default boolean getEnabled(String decoratorId) {
    return true;
  }

  /**
   * {@code IDecoratorManager.getLabelDecorator()}: the decorator of the decorated labels. The VS Code trees decorate
   * their items themselves (decorations of the explorer items), so no workbench-wide label decorator is kept.
   */
  // VSCODE: decorations are computed by the VS Code item renderer (ExplorerService.item), not by a label decorator.
  default com.unitesk.requality.vscode.ui.jface.viewers.ILabelDecorator getLabelDecorator() {
    return null;
  }
}
