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

package com.unitesk.requality.vscode.port.eclipse.wizards;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogPage;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

public class NewRequalityProjectPage extends WizardPage {
  private Text containerText;

  /**
   * Constructor for SampleNewWizardPage.
   *
   * @param pageName
   */
  public NewRequalityProjectPage(ISelection selection) {
    super("wizardPage");
    setPageComplete(false);
    setTitle("New Requality Project");
    // setDescription("This wizard creates a new file with *.mpe extension that can be opened by a multi-page editor.");
    setDescription("This wizard creates a new catalogue of the requirements.");
  }

  /** @see IDialogPage#createControl(Composite) */
  public void createControl(Composite parent) {
    Composite container = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);
    layout.numColumns = 2;
    layout.verticalSpacing = 9;
    Label label = new Label(container, SWT.NULL);
    label.setText("&Project name:");

    containerText = new Text(container, SWT.BORDER | SWT.SINGLE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    containerText.setLayoutData(gd);
    containerText.addModifyListener(
        new ModifyListener() {
          public void modifyText(ModifyEvent e) {
            dialogChanged();
          }
        });
    containerText.setFocus();
    setControl(container);
  }

  /** Ensures that both text fields are set. */
  private void dialogChanged() {
    if (getContainerName().length() == 0) {
      updateStatus("Project name must be specified");
      return;
    }

    IPath projectLocation =
        ResourcesPlugin.getWorkspace().getRoot().getLocation().append(getContainerName());
    if (projectLocation.toFile().exists()) {
      updateStatus("Project with same name already exists");
      return;
    }
    updateStatus(null);
  }

  private void updateStatus(String message) {
    setErrorMessage(message);
    setPageComplete(message == null);
  }

  public String getContainerName() {
    return containerText.getText();
  }

  public IResource getModifiedResource() {
    return ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(getContainerName()));
  }
}
