package com.unitesk.requality.eclipse.handlers;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.eclipse.ui.editors.UniEditor;

public class OpenUniEditorExternalHandler extends OpenEditorExternalHandler {

	public OpenUniEditorExternalHandler() {
		super(ResourcesServlet.UNIEDITOR, UniEditor.defaultAttrs);
	}

    @Override
    public boolean isEnabled() {
      SelectionRecord sel = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
      if(sel == null || sel.fullSelection.size()!=1)
        return false;
      TreeNode node = sel.getFirstNode();
      return node!=null && node.getQualifiedId().startsWith("/TestProcedures")||super.isEnabled();
    }
}
