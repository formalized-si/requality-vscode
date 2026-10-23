package com.unitesk.requality.vscode.ui.swt.awt;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.swt.awt.SWT_AWT}: embeds Swing content in an SWT composite. In the remote SWT layer there is no
 * native AWT window (the server runs headless), so {@link #new_Frame} returns a {@link Frame} that shows Swing text
 * components as an SWT {@code StyledText} of the composite.
 */
public class SWT_AWT {
  /** {@code org.eclipse.swt.awt.SWT_AWT#new_Frame(Composite)}: the composite must have the {@code SWT.EMBEDDED} style. */
  public Frame new_Frame(Composite parent) {
    if (parent == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    if (!SWT.has(parent.getStyle(), SWT.EMBEDDED))
      SWT.error(SWT.ERROR_INVALID_ARGUMENT);
    return new Frame(parent);
  }
}
