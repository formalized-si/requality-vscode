package com.unitesk.requality.core.selection;

import com.unitesk.requality.core.model.Pair;

public interface ISelectionConverter {
  public Pair<Boolean,SelectionRecord> convertSelection(FSId fsida, SelectionRecord sel, SelectionRecord oldTargSel);
}
