package com.unitesk.requality.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.DialogSettings;

public class ProjectDialogSettingsHandler {
  private IFile settingsFile;
  private String filename;

  public ProjectDialogSettingsHandler(String string) {
    filename = "." + string + "settings";
  }

  public void initOverProject(IProject project) {
    settingsFile = project.getFile(filename);
  }

  public void loadSettings(DialogSettings settings) {
    if (settingsFile == null) return;
    try (InputStreamReader isr = new InputStreamReader(settingsFile.getContents())){
      settings.load(isr);
    } catch (CoreException e) {
    } catch (IOException e1) {
	}
  }

  public void saveSettings(DialogSettings settings) {
    if (settingsFile == null) return;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      settings.save(new OutputStreamWriter(baos));
      baos.close();
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      if (!settingsFile.exists()) settingsFile.create(bais, true, null);
      else settingsFile.setContents(bais, true, false, null);
    } catch (CoreException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
