package com.unitesk.requality.core.operation.document;

import java.util.Map;
import com.unitesk.requality.nodetype.Document;

public interface IDocProcessorWithWizard {
	public Map<String, Object> showWizard(Document doc);

	public void setWizard(Object w);
}
