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

package com.unitesk.requality.eclipse.natures;

import java.util.ArrayList;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import com.unitesk.requality.core.Consts;
import com.unitesk.requality.eclipse.core.EclipseChecker;

// TODO: rename me
public class ReqCatalogue implements IProjectNature {

  public static String ID = Consts.CATALOG_NATURE;

  private IProject project = null;

  @Override
  public void configure() throws CoreException {
    IProjectDescription desc = project.getDescription();
    ICommand[] commands = desc.getBuildSpec();
    ICommand command = desc.newCommand();
    command.setBuilderName(EclipseChecker.BUILDER_ID);
    ICommand[] newCommands = new ICommand[commands.length + 1];
    System.arraycopy(commands, 0, newCommands, 1, commands.length);
    newCommands[0] = command;
    desc.setBuildSpec(newCommands);
    project.setDescription(desc, null);
  }

  @Override
  public void deconfigure() throws CoreException {
    IProjectDescription desc = project.getDescription();
    ICommand[] commands = desc.getBuildSpec();
    ArrayList<ICommand> newCommandsList = new ArrayList<>();
    for (ICommand cmd : commands) {
      if (!cmd.getBuilderName().equals(EclipseChecker.BUILDER_ID)) {
        newCommandsList.add(cmd);
      }
    }
    desc.setBuildSpec(newCommandsList.toArray(new ICommand[0]));
    project.setDescription(desc, null);
  }

  @Override
  public IProject getProject() {
    return project;
  }

  @Override
  public void setProject(IProject project) {
    this.project = project;
  }
}
