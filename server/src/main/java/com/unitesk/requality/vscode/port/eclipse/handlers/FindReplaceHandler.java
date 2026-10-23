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
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.FindDialog;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ReportViewer;

public class FindReplaceHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    IEditorReference[] parta = page.getEditorReferences();
    if(parta == null)
      return null;
    IEditorPart tref = 
        page.getActiveEditor();
//    for(IEditorReference apart:parta)
//      if(apart.getPage().isEditorAreaVisible()){
//        IEditorPart part = apart.getEditor(false);
//        if (part!=null && ((part instanceof ReqMarker) || (part instanceof JSEditor)|| (part instanceof ReportViewer))){
//          tref = part;
//          break;
//        }
//      }
    FindDialog dialog = null;
//    if (tref instanceof ReqMarker) {
//      ReqDocBrowser reqBrowser = ((ReqMarker) tref).getBrowser();
//      dialog =
//          new FindDialog(
//              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), reqBrowser);
//    }
//    if (tref instanceof JSEditor) {
//      JSEditor jse = (JSEditor) tref;
//      dialog = new FindDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), jse);
//    }
//    if (tref instanceof ReportViewer) {
//      ReportViewer jse = (ReportViewer) tref;
//      dialog = new FindDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), jse);
//    }
    if(dialog!=null){
      dialog.setBlockOnOpen(false);
      dialog.open();
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
    return (tref instanceof JSEditor) || (tref instanceof ReportViewer)
        ||(tref instanceof ReqMarker);
  }
}
