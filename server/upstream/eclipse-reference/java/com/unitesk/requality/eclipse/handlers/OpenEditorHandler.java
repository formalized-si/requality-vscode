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

package com.unitesk.requality.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWT;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.views.RequalityPreferences;
import com.unitesk.requality.tool.message.RMessages;

public abstract class OpenEditorHandler extends AbstractHandler {
  public abstract String getEditorId();

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
//    if (RequalityPlugin.getBrowserType() == SWT.NONE) {
//      EclipseAPI.showNoBrowserEngine();
//    }
    return openOperation(event);
  }

  public Object openOperation(ExecutionEvent event) throws ExecutionException {

    if(!ResourcesProvider.getOne().initiated)
    {
      RMessages.openInfo("Resources server is still initiating","Content provider is still initiating, please wait until it to be loaded.", false);
      return null;
    }
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    for(TreeNode node:nodes.fullSelection) {
      NodeEditorInput input = new NodeEditorInput(node, getEditorId(), null);
      try {
        PlatformUI.getWorkbench()
            .getActiveWorkbenchWindow()
            .getActivePage()
            .openEditor(input, getEditorId(), false);
      } catch (PartInitException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    if (RequalityPlugin.getBrowserType() == SWT.NONE)
      if (Activator.getDefault()
          .getPreferenceStore()
          .getBoolean(RequalityPreferences.NOT_OPEN_EDITORS))
    	  return false;
    return true;
  }
}
