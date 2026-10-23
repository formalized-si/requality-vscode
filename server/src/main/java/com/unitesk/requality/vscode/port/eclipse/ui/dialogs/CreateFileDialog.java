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
package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecorationRegistry;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class CreateFileDialog extends TreeViewerDialog<IFolder> {
  private String getNextId(IFolder f) throws CoreException {
    String newId = "000";
    String fullNewId = "000";
    boolean found = false;

    for (int i = 0; i < f.members().length; i++) {
      IResource ir = f.members()[i];
      if (!(ir instanceof IFile)) continue;
      IFile file = (IFile) ir;

      String number = RequalityCLI.getNumberStringFromId(file.getName());
      if (number == null) {
        continue;
      }
      try {
        if (Integer.parseInt(number) >= Integer.parseInt(newId)) {
          fullNewId = file.getName();
          newId = number;
          found = true;
        }
      } catch (NumberFormatException e) {
      }
    }

    if (!found) {
      fullNewId = f.getName() + "-" + fullNewId;
    }

    do {
      fullNewId = RequalityCLI.incLastNumberInString(fullNewId);
    } while (f.findMember(fullNewId + extension) != null);
    return fullNewId;
  }

  private final class FolderSelectionListener implements ISelectionChangedListener {
    CreateFileDialog cf;

    public FolderSelectionListener(CreateFileDialog cfd) {
      cf = cfd;
    }

    @Override
    public void selectionChanged(SelectionChangedEvent event) {
      TreeSelection ts = (TreeSelection) event.getSelection();
      if (ts.getFirstElement() instanceof IFolder) {
        if (!ts.getFirstElement().equals(selectedNode)) {
          select(ts.getFirstElement());
          try {
            fileName.setText(cf.getNextId(selectedNode));
          } catch (CoreException e) {
          }
        }
      }
      updateUI();
    }
  }

  private static int TREE_WIDTH = 250;
  private static int TREE_HEIGHT = 300;

  protected String newNodeType = null;

  protected Text fileName;
  protected String lastCorrectId = null;

  @Override
  public int open() {
    return super.open();
  }

  @Override
  protected void buttonPressed(int buttonId) {
    super.buttonPressed(buttonId);
  }

  public String getResultFileName() {
    return lastCorrectId;
  }

  private String extension;

  private String title;
  private Image createdIm = null;

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite container = createClientContainer(parent, 1);

    header = new Composite(container, SWT.NONE);
    header.setLayout(new GridLayout(2, false));
    header.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    new Label(header, SWT.NONE).setText("File Name:");
    fileName = new Text(header, SWT.FILL);
    fileName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    final ControlDecoration fileNameDecorator = new ControlDecoration(fileName, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration =
        FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    createdIm = fieldDecoration.getImage();
    fileNameDecorator.setImage(createdIm );

    fileName.addModifyListener(
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
            String s = fileName.getText(), err = null;
            if (s == null
                || selectedNode == null
                || selectedNode.findMember(s + extension) != null) {
              err = "File with name " + fileName.getText() + " already exists";
            }
            //it is possible for this method to be moved to Utils in future
            try {
              if (!TreeLogic.isIdValidExc(s)) err = "File name " + s + " is not valid";
            } catch (IdEmpty e1) {
              err = "File name is empty";
            } catch (IdContainsWrongChars e1) {
              err =
                  "File name " + s + " contains wrong character - '" + e1.getWrongCharacter() + "'";
            }
            if (getButton(Dialog.OK) != null) {
              if (err != null) {
                getButton(Dialog.OK).setEnabled(false);
              } else {
                getButton(Dialog.OK).setEnabled(true);
              }
            }
            if (err != null) {
              fileNameDecorator.setDescriptionText(err);
              fileNameDecorator.show();
              return;
            }
            lastCorrectId = s + extension;
            fileNameDecorator.hide();
          }
        });

    Label commentLabel = new Label(container, SWT.NONE);
    String labelString = "Select a folder:";
    commentLabel.setText(labelString);
    commentLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, true));
    nodeTree = new TreeViewer(container);
    nodeTree.getControl().setLayoutData(new GridData(TREE_WIDTH, TREE_HEIGHT));
    nodeTree.setLabelProvider(getLabelProvider());
    nodeTree.setAutoExpandLevel(2);
    nodeTree.setContentProvider(getContentProvider());

    nodeTree.addSelectionChangedListener(new FolderSelectionListener(this));

    nodeTree.getTree().addMouseListener(mouseList);
    nodeTree.getTree().addKeyListener(keyList);
    nodeTree.setInput(ResourcesPlugin.getWorkspace());

    this.getShell().setText(getTitle());
    this.getShell()
        .setImage(
            PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));

    refresh();
    container.addDisposeListener(one -> {
      if(createdIm!=null)
        createdIm.dispose();
    });
    return container;
  }

  protected ITreeContentProvider getContentProvider() {
    ITreeContentProvider prov =
        new ITreeContentProvider() {

          public void dispose() {}

          public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

          public Object[] getElements(Object inputElement) {
            return ResourcesPlugin.getWorkspace().getRoot().getProjects();
          }

          public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof IProject) {
              IProject projects = (IProject) parentElement;
              try {
                List<Object> mems = new ArrayList<>();
                for (IResource ir : projects.members())
                  if (ir instanceof IFolder && !((IFolder) ir).isHidden()) mems.add(ir);
                return mems.toArray();
              } catch (CoreException e) {
                e.printStackTrace();
              }
            }
            if (parentElement instanceof IFolder) {
              IFolder ifolder = (IFolder) parentElement;
              try {
                List<Object> mems = new ArrayList<>();
                for (IResource ir : ifolder.members())
                  if (ir instanceof IFolder && !((IFolder) ir).isHidden()) mems.add(ir);
                return mems.toArray();
              } catch (CoreException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }

            return null;
          }

          public Object getParent(Object element) {
            if (element instanceof IProject) {
              IProject projects = (IProject) element;
              return projects.getParent();
            }
            if (element instanceof IFolder) {
              IFolder folder = (IFolder) element;
              return folder.getParent();
            }
            return null;
          }

          public boolean hasChildren(Object element) {
            Object[] child = getChildren(element);
            if (child == null) return false;
            return child.length > 0;
          }
        };
    return prov;
  }

  public CreateFileDialog(String extension, String title, Shell shell) {
    super(shell);
    this.extension = extension;
    this.title = title;
  }

  public String getTitle() {
    return title;
  }
}
