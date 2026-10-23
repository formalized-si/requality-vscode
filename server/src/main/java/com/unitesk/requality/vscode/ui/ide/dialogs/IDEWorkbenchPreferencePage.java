package com.unitesk.requality.vscode.ui.ide.dialogs;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferencePage;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.IDEWorkbenchPreferencePage}: the General page of the IDE (the workbench
 * page with the IDE settings: the inline rename, the text filter of the marker views, the save interval and the open mode).
 */
public class IDEWorkbenchPreferencePage extends WorkbenchPreferencePage implements IWorkbenchPreferencePage {
  @Override
  protected Control createContents(Composite parent) {
    Composite composite = createComposite(parent);
    createSettings(composite);
    createInlineRenamePref(composite);
    createShowFilterTextInMarkerViewsPref(composite);
    createSaveIntervalGroup(composite);
    createOpenModeGroup(composite);
    return composite;
  }
}
