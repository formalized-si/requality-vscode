/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqDocBrowser;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.FindDialog;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor;

public class FindNextHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    ReqDocBrowser reqBrowser = null;
    IWorkbenchPart part =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
    if (part instanceof ReqMarker) {
      reqBrowser = ((ReqMarker) part).getBrowser();
      if (FindDialog.comboStrings.isEmpty()) return null;
      reqBrowser.findText(FindDialog.comboStrings.get(0), false, false, false);
      reqBrowser.setFocus();
    }
    if (part instanceof JSEditor) {
      JSEditor ediBrowser;
      ediBrowser = (JSEditor) part;
      if (FindDialog.comboStrings.isEmpty()) return null;
      ediBrowser.findText(FindDialog.comboStrings.get(0), false, false, false);
      ediBrowser.setFocus();
    }

    return null;
  }
  @Override
  public boolean isEnabled() {
    if(!super.isEnabled())
      return false;
    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    IEditorReference[] parta = page.getEditorReferences();
    if(parta == null)
      return false;
    IEditorPart tref = 
        page.getActiveEditor();
    return (tref instanceof JSEditor) ||(tref instanceof ReqMarker);
  }
}
