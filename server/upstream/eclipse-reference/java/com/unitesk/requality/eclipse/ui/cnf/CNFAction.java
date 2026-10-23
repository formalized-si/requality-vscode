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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IActionDelegate;

public abstract class CNFAction extends Action implements IActionDelegate {

  protected ISelection selection;

  public CNFAction() {}

  @Override
  public void run(IAction action) {
    if (action instanceof CNFAction) {
      CNFAction a = (CNFAction) action;
      a.run();
    }
  }

  @Override
  public void selectionChanged(IAction action, ISelection selection) {
    if (action instanceof CNFAction) {
      CNFAction cnfa = (CNFAction) action;
      cnfa.setSelection(selection);
    }
  }

  private void setSelection(ISelection selection) {
    this.selection = selection;
  }
}
