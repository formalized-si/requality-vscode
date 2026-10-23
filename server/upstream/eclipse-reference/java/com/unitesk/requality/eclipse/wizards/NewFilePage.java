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

package com.unitesk.requality.eclipse.wizards;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;

public class NewFilePage extends WizardNewFileCreationPage {
  String text;

  public NewFilePage(IStructuredSelection selection, String text) {
    super("chooseTestFileName", selection);
    this.text = text;
    updateSettings();
  }

  @Override
  protected InputStream getInitialContents() {
    //if(page == null)
    //return null;
    //String text = page.getFileContents();
    return new ByteArrayInputStream(text.getBytes());
  }

  public void updateSettings() {
    setTitle("Export to RegIF file");
    setFileExtension("reqif");
  }
}
