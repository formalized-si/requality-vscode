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
package com.unitesk.requality.vscode.port.eclipse.handlers.location;

import java.util.Collection;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.IDE;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.handlers.OpenEditorHandler;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.tool.CoreUtils;

public class OpenLocationHandler extends OpenEditorHandler {

  @Override
  public Object openOperation(ExecutionEvent event) throws ExecutionException {
    long time = CoreUtils.getTime();
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || (nodes.fullSelection.size() != 1)) return null;
    Collection<TreeNode> locs = nodes.get(Location.TYPE_NAME);
    if(locs == null || locs.size()!=1)
      return null;
    Location loc = (Location) locs.iterator().next();
    if(loc == null)
      return null;
    NodeEditorInput input = new NodeEditorInput(loc.getWorkParent(), loc, ReqMarker.ID, null);
    input.setData(loc);
    try {
      IEditorReference eref =
          RequalityPlugin.findMarkEditorForDocument(loc.getWorkParent().getQualifiedId());
      if (eref == null) {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        return IDE.openEditor(page, input, ReqMarker.ID);
      } else {
        ((ReqMarker) eref.getEditor(true)).getBrowser().getDocService().setTargetLocation(time, null, loc, true);
        //TODO * can be incorrect
        eref.getPage().bringToTop(eref.getEditor(true));
        return eref;
      }
    } catch (PartInitException e) {
      e.printStackTrace();
    }
    //TODO remove incorrect openProps OpenRequirementHandler.showProperties();//INCORRECT
    return null;
  }

  @Override
  public String getEditorId() {
    // TODO Auto-generated method stub
    return ReqMarker.ID;
  }
}
