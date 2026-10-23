/**
 * ***************************************************************************** Copyright (c) 2006
 * IBM Corporation and others. All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * <p>Contributors: IBM Corporation - initial API and implementation
 * *****************************************************************************
 */
package com.unitesk.requality.eclipse.wizards;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.UUID;

import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

public class DocumentImportWizard extends Wizard implements IImportWizard {
  static final String SETTS_FILE_NAME = "dimwiz";
  private DocumentImportWizardPage mainPage;
  DialogSettings ds = new DialogSettings("com.unitesk.requality.importdocwizard");

  public DocumentImportWizard() {
    super();
  }

  public boolean performFinish() {
    return mainPage.finish();
  }

  public void init(IWorkbench workbench, IStructuredSelection selection) {
    setWindowTitle("Document Import Wizard");
    setNeedsProgressMonitor(false);
    if (selection == null)
      selection =
          (IStructuredSelection)
              PlatformUI.getWorkbench()
                  .getActiveWorkbenchWindow()
                  .getSelectionService()
                  .getSelection();
    try {
      File f = File.createTempFile(UUID.randomUUID().toString(), "");//TODO rewrite to global cache
      f = f.getParentFile().toPath().resolve(SETTS_FILE_NAME).toFile();
      if(!f.exists())
        f.createNewFile();
      f.deleteOnExit();
      FileReader fr = new FileReader(f);
      ds.load(fr);
      fr.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    mainPage = new DocumentImportWizardPage(workbench, selection, ds);
    mainPage.setTitle("Document Import");
    mainPage.setDescription("Select documents to import");
  }

  public void addPages() {
    super.addPages();
    addPage(mainPage);
  }

  @Override
  public boolean canFinish() {
    return super.canFinish();
  }
}
