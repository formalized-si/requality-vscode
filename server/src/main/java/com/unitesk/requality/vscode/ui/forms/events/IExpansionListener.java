package com.unitesk.requality.vscode.ui.forms.events;

/** {@code org.eclipse.ui.forms.events.IExpansionListener}: notified when an expandable composite expands or collapses. */
public interface IExpansionListener {
  void expansionStateChanging(ExpansionEvent e);

  void expansionStateChanged(ExpansionEvent e);
}
