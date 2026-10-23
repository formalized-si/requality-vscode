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
package com.unitesk.requality.eclipse.handlers.requirement;

import java.util.Collection;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.views.properties.PropertySheet;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.eclipse.handlers.OpenEditorHandler;
import com.unitesk.requality.eclipse.ui.editors.UniEditor;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.CoreUtils;

public class OpenRequirementHandler extends OpenEditorHandler {

  @Override
  public Object openOperation(ExecutionEvent event) throws ExecutionException {
    long time = CoreUtils.getTime();
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || (nodes.fullSelection.size() != 1)) return false;
    Collection<TreeNode> reqs = nodes.get(Requirement.TYPE_NAME);//TODO or textnode?
    if(reqs == null || reqs.size() == 0) {
      reqs = nodes.get(TextNode.TYPE_NAME);
      if(reqs == null || reqs.size() == 0)
        return false;
    }
    Requirement req = (Requirement) reqs.iterator().next();
    if (req.getLocationQIds().size() == 0) {
      return null;
    }
    Location loc = req.getFirstLocation();
    if (loc == null) return null;
    NodeEditorInput input = new NodeEditorInput(loc.getWorkParent(),loc,ReqMarker.ID,null);
    input.setData(loc);
    try {
      IEditorReference eref =
          RequalityPlugin.findMarkEditorForDocument(loc.getWorkParent().getQualifiedId());
      if (eref == null) {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IDE.openEditor(page, input, ReqMarker.ID, false);
      } else {
        ((ReqMarker) eref.getEditor(true)).getBrowser().getDocService().setTargetLocation(time, null, loc, true);//TODO * can be incorrect
        eref.getPage().bringToTop(eref.getEditor(true));
      }
    } catch (PartInitException e) {
      e.printStackTrace();
    }
    showProperties();
    return null;
  }

  public static void showProperties() {
    PropertySheet props =
        (PropertySheet)
            PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage()
                .findView("org.eclipse.ui.views.PropertySheet");
    if (props == null) {
      try {
        PlatformUI.getWorkbench()
            .getActiveWorkbenchWindow()
            .getActivePage()
            .showView("org.eclipse.ui.views.PropertySheet");
      } catch (PartInitException e) {
        e.printStackTrace();
      }
      return;
    }

    boolean hasUnpinned = false;
    IViewReference[] viewReferences =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getViewReferences();
    for (int i = 0; i < viewReferences.length; i++) {
      if ("org.eclipse.ui.views.PropertySheet".equals(viewReferences[i].getId())) {
        PropertySheet propertyView = (PropertySheet) viewReferences[i].getView(true);
        if (!propertyView.isPinned()) {
          hasUnpinned = true;
          break;
        }
      }
    }
    if (!hasUnpinned) {
      IHandlerService handlerService =
          PlatformUI.getWorkbench()
          .getActiveWorkbenchWindow()
          .getService(IHandlerService.class);
      try {
        handlerService.executeCommand(
            "org.eclipse.ui.views.properties.NewPropertySheetCommand", null);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public String getEditorId() {
    return UniEditor.ID;
  }
}
