package com.unitesk.requality.core.selection;

import com.unitesk.requality.core.model.Pair;

public interface ISelectionAdapter {

  /**
   * Returns adapted selection record for FSId for which it is registered. If no adaptation needed it returns null. 
   * @param selectionSource FSId of event source
   * @param record new record to be adapted
   * @param oldRecord old selection of target element
 * @param oldStoredSel 
 * @param storedSel 
   * @return
   */
  public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId selectionSource, SelectionRecord visRecord, SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel);
}
