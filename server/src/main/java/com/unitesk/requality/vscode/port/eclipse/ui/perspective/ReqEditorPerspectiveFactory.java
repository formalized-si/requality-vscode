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

package com.unitesk.requality.vscode.port.eclipse.ui.perspective;

import com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.ui.workbench.IPageLayout;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveFactory;


public class ReqEditorPerspectiveFactory implements IPerspectiveFactory {
  public static final String ID = "com.unitesk.requality.main.perspective";

  @Override
  public void createInitialLayout(IPageLayout layout) {
    layout.addView(RequalityCNF.ID, IPageLayout.LEFT, 0.2f, layout.getEditorArea());
    layout.addView(
        "org.eclipse.ui.views.PropertySheet", IPageLayout.BOTTOM, 0.7f, layout.getEditorArea());
    layout.addView(
        "org.eclipse.ui.views.ContentOutline", IPageLayout.RIGHT, 0.8f, layout.getEditorArea());
    layout.addView(
        RequalityLCNF.ID, IPageLayout.BOTTOM, 0.5f, "org.eclipse.ui.views.ContentOutline");
    layout.setEditorAreaVisible(true);
  }
}
