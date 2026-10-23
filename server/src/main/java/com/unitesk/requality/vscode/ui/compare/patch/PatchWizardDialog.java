/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.compare.patch;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.compare.internal.patch.PatchWizardDialog}: the wizard dialog of Apply Patch, resizable with a minimum
 * page size.
 */
public class PatchWizardDialog extends WizardDialog {

  public PatchWizardDialog(Shell parent, IWizard wizard) {
    super(parent, wizard);
    setShellStyle(getShellStyle() | SWT.RESIZE);
    // VSCODE: the minimum page size of the Eclipse wizard dialog is not set (the VS Code panel sizes the page).
  }
}
