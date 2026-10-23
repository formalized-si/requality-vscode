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
package com.unitesk.requality.eclipse.ui.cnf;

import java.util.SortedSet;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.ICommonActionConstants;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.eclipse.ui.navigator.ICommonMenuConstants;
import org.eclipse.ui.navigator.ICommonViewerSite;

import com.unitesk.requality.core.descriptors.DocumentProcessorDescriptor;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.views.actions.OpenAction;
import com.unitesk.requality.eclipse.views.actions.documents.DocumentProcessorAction;
import com.unitesk.requality.eclipse.views.actions.documents.ImportAction;

public class CNFActionProvider extends CommonActionProvider {

  private ICommonViewerSite site;
  protected SortedSet<DocumentProcessorDescriptor> procs = null;

  public CNFActionProvider() {}

  @Override
  public void init(ICommonActionExtensionSite aSite) {
    site = aSite.getViewSite();
    procs = Activator.getDefault().getProcessors();
    super.init(aSite);
  }

  @Override
  public void fillContextMenu(IMenuManager mmanager) {
    super.fillContextMenu(mmanager);
    ISelectionProvider provider = site.getSelectionProvider();

    ImportAction docImport = new ImportAction(provider);
    if (docImport.isEnabled()) {
      mmanager.appendToGroup(ICommonMenuConstants.GROUP_NEW, docImport);
    }
    synchronized (this) {
      for(DocumentProcessorDescriptor procDesc: procs) {
        DocumentProcessorAction docProc = new DocumentProcessorAction(procDesc, provider, System.out);
        if (docProc.isEnabled()) {
          mmanager.appendToGroup(ICommonMenuConstants.GROUP_GENERATE, docProc);
        }
      }
    }
  }


  @Override
  public void fillActionBars(IActionBars actionBars) {
    super.fillActionBars(actionBars);
    actionBars.setGlobalActionHandler(ICommonActionConstants.OPEN, new OpenAction());
  }
}
