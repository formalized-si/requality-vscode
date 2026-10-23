package com.unitesk.requality.core.api;

import java.util.Map;
import com.unitesk.requality.core.operation.create.CreateElementOperation;

public interface IAPICreateElementOperationsExtension {
	public Map<String, CreateElementOperation> getCreateOperations();
}
