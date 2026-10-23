package com.unitesk.requality.core.selection;

public interface ISelectionComparer {
  ISelectionComparer DEFAULT = new ISelectionComparer() {
    @Override
    public boolean selectionChanged(FSId source, SelectionRecord record, SelectionRecord oldRecord) {
      if(record == null)
        return oldRecord!=null;
      return !record.isSameWith(oldRecord);
    }
  };

  boolean selectionChanged(FSId source, SelectionRecord record, SelectionRecord oldRecord);
}
