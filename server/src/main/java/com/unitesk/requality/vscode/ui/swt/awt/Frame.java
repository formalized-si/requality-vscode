package com.unitesk.requality.vscode.ui.swt.awt;

import java.awt.Component;

import javax.swing.JScrollPane;
import javax.swing.text.JTextComponent;

import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code java.awt.Frame} as returned by {@link SWT_AWT#new_Frame}. {@link #add} accepts a Swing text component, or a
 * scroll pane wrapping one: it is shown as an SWT {@code StyledText} filling the composite, kept in sync with the
 * Swing document (text, background attributes, editability, focus) by {@link SwingTextBridge}. Only the lightweight
 * text components are bridged: the server runs headless, so Swing top-level windows do not exist.
 */
public class Frame {
  private final Composite parent;
  private SwingTextBridge bridge;

  Frame(Composite parent) {
    this.parent = parent;
    if (parent.getLayout() == null)
      parent.setLayout(new FillLayout());
  }

  /** {@code java.awt.Container#add(Component)}: shows a text component (or a scroll pane of one) in the composite. */
  public void add(Component component) {
    if (bridge != null)
      throw new IllegalStateException("A Frame shows one Swing text component");
    JTextComponent text = textComponent(component);
    if (text == null)
      throw new IllegalArgumentException("Only Swing text components can be embedded: " + component);
    bridge = new SwingTextBridge(parent, text);
  }

  private static JTextComponent textComponent(Component component) {
    Component c = component;
    if (c instanceof JScrollPane)
      c = ((JScrollPane) c).getViewport().getView();
    return c instanceof JTextComponent ? (JTextComponent) c : null;
  }
}
