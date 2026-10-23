package com.unitesk.requality.vscode.ui.resources;

import java.net.URI;

import com.unitesk.requality.vscode.ui.runtime.IPath;

/** {@code org.eclipse.core.resources.IProjectDescription}: the contents of a .project file. */
public interface IProjectDescription {
  String DESCRIPTION_FILE_NAME = ".project";

  String getName();

  void setName(String projectName);

  String getComment();

  void setComment(String comment);

  String[] getNatureIds();

  void setNatureIds(String[] natures);

  boolean hasNature(String natureId);

  ICommand[] getBuildSpec();

  void setBuildSpec(ICommand[] buildSpec);

  ICommand newCommand();

  IPath getLocation();

  void setLocation(IPath location);

  URI getLocationURI();

  void setLocationURI(URI location);

  IProject[] getReferencedProjects();

  void setReferencedProjects(IProject[] projects);
}
