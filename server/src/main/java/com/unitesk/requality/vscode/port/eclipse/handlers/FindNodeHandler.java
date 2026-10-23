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
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.FindNode;

public class FindNodeHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    RequalityCNF c = RequalityCNF.activeNavigator;
    if(c == null || c.getCommonViewer().getControl().isDisposed())
      return null;
    if(FindNode.one == null || FindNode.one.isDisposed()){
      FindNode dialog = 
            new FindNode(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), c);
      dialog.setBlockOnOpen(false);
      dialog.open();
    }else
      FindNode.one.open();
    return null;
  }
}
