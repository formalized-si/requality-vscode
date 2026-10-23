package com.unitesk.requality.core.selection;

import java.util.Map;
import com.unitesk.requality.core.model.TreeNode;

public abstract class SelectionConsumer {
	private static long MAX_ID = 0;
	public long id = MAX_ID++;
	public abstract void newSelection(EventSource es, String sourceid, Map<String, TreeNode[]>newSelection);
	public abstract boolean allowEventSource(EventSource es, String sourceId); 
}
