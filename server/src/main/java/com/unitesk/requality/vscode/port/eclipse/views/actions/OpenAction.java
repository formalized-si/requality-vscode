/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.vscode.port.eclipse.views.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.IHandlerService;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityPreferences;

public class OpenAction extends Action {
  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size()<1) {
      return false;
    }
    boolean was = false;
    //TODO maybe check db state
    TreeNode nd = nodes.getFirstNode();
    if(nd == null)
      return false;
    if (RequalityPlugin.getBrowserType() == SWT.NONE)
      if (Activator.getDefault()
          .getPreferenceStore()
          .getBoolean(RequalityPreferences.NOT_OPEN_EDITORS)) return false;
    return true;
  }

  public void run() {
    try {
      runAction();
    } catch (PartInitException e) {
      e.printStackTrace();
    }
  }

  private void runAction() throws PartInitException {
    if (!isEnabled()) {
      return;
    }
    IHandlerService handlerService =
        PlatformUI.getWorkbench()
        .getActiveWorkbenchWindow()
        .getActivePage()
        .getActivePart()
        .getSite()
        .getService(IHandlerService.class);
    try {
      handlerService.executeCommand("com.unitesk.requality.commands.OpenNode", null);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
