package com.unitesk.requality.core.filter;

import com.unitesk.requality.core.selection.SelectionRecord;

public interface ITreeNodesFilter {
  /** @return Set of filtered elements. Returns empty set in a case no elements were filtered */
	SelectionRecord select(SelectionRecord nodes);
}
