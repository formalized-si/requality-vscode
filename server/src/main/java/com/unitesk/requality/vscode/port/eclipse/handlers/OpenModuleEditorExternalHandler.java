package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ModuleEditor;

public class OpenModuleEditorExternalHandler extends OpenEditorExternalHandler {
	public OpenModuleEditorExternalHandler() {
		super(ResourcesServlet.MODULEEDITOR, ModuleEditor.defaultAttrs);
	}
	
}
