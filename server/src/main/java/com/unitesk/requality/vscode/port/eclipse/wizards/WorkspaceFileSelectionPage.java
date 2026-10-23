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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogPage;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.MouseEvent;
import com.unitesk.requality.vscode.ui.swt.events.MouseListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

public class WorkspaceFileSelectionPage extends WizardPage {
  private ISelectionChangedListener listener;
  private Text filePath;
  private TreeViewer nodeTree = null;
  private Set<String> extensions;
  private boolean optional = false;
  private String targetPath;
  private DialogSettings sett;

  public class FileTreeContentProvider implements ITreeContentProvider {

    public void dispose() {}

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

    public Object[] getElements(Object inputElement) {
      return ResourcesPlugin.getWorkspace().getRoot().getProjects();
    }

    public Object[] getChildren(Object parentElement) {
      if (parentElement instanceof IContainer) {
        IContainer c = (IContainer) parentElement;
        return getShownChildren(c);
      }
      return null;
    }

    private Object[] getShownChildren(IContainer parentElement) {
      List<IResource> rl = new LinkedList<>();
      try {
        if (parentElement.isAccessible())
          for (IResource r : parentElement.members())
            if (r != null
                && !r.isHidden()
                && r.isAccessible()
                && (!(r instanceof IFile) || extensions.contains("*") || extensions.contains(((IFile) r).getFileExtension().toUpperCase())))
              rl.add(r);
      } catch (CoreException e) {
        e.printStackTrace();
      }
      return rl.toArray();
    }

    public Object getParent(Object element) {
      if (element instanceof IResource) return ((IResource) element).getParent();
      return null;
    }

    public boolean hasChildren(Object element) {
      if (element instanceof IContainer) {
        return getShownChildren((IContainer) element).length > 0;
      }
      return false;
    }
  }
  /**
   * Constructor for SampleNewWizardPage.
   *
   * @param multi
   * @param pageName
   */
  public WorkspaceFileSelectionPage(DialogSettings selection, String[] extensions) {
    super("fileSelection");
    setTitle("Select Element");
    setDescription("Please select element from listed tree.");
    this.sett = selection;
    if (extensions == null) this.extensions = Collections.emptySet();
    else {
      this.extensions = new HashSet<>();
      for(String s:extensions)
        this.extensions.add(s.toUpperCase());
    }
    String p = selection.get("filePath");
    if (p != null
        && !p.equals("")
        && ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString(p)) != null) {
      setTargetPath(Path.fromOSString(p));
      updateStatus(null);
    } else {
      setTargetPath(null);
      updateStatus(WRONG_PATH);
    }
  }

  protected ILabelProvider getLabelProvider() {
    return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
  }

  /** @see IDialogPage#createControl(Composite) */
  public void createControl(Composite parent) {
    setPageComplete(true);
    Composite container = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);
    layout.numColumns = 2;
    layout.verticalSpacing = 9;
    Label label = new Label(container, SWT.NULL);
    label.setText("&Element id:");

    filePath = new Text(container, SWT.BORDER | SWT.SINGLE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    filePath.setLayoutData(gd);

    filePath.addFocusListener(
        new FocusListener() {

          @Override
          public void focusLost(FocusEvent e) {
            dialogChanged();
            setTargetPath(Path.fromOSString(filePath.getText()));
          }

          @Override
          public void focusGained(FocusEvent e) {
            // TODO Auto-generated method stub

          }
        });

    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;

    nodeTree = new TreeViewer(container, SWT.NONE);
    nodeTree.getControl().setLayoutData(gd);
    nodeTree.setContentProvider(new FileTreeContentProvider());
    nodeTree.setLabelProvider(getLabelProvider());

    nodeTree.setInput(ResourcesPlugin.getWorkspace());
    //nodeTree.setSorter(new RequalitySorter());
    nodeTree.setAutoExpandLevel(2);
    //nodeTree.setInput(ResourcesPlugin.getWorkspace().getRoot().getProjects());

    nodeTree.addSelectionChangedListener(new NodeSelectionListener());

    nodeTree.getTree().addMouseListener(mouseList);
    nodeTree.getTree().addKeyListener(keyList);

    if (targetPath != null) setTargetPath(Path.fromOSString(targetPath));

    filePath.setFocus();
    setControl(container);
  }

  public boolean canSelectNode() {
    if (filePath == null
        || ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString(filePath.getText()))
            == null) return false;
    return true;
  }

  private MouseListener mouseList =
      new MouseListener() {
        public void mouseDoubleClick(MouseEvent e) {
          //TODO implement
          @SuppressWarnings("unused")
          TreeItem item = nodeTree.getTree().getItem(new Point(e.x, e.y));
          //if (item != null && canSelectNode())
          //  okPressed();
          //buttonBar.setEnabled(canSelectNode());
        }

        public void mouseDown(MouseEvent e) {}

        public void mouseUp(MouseEvent e) {}
      };

  private KeyListener keyList =
      new KeyListener() {
        public void keyPressed(KeyEvent e) {
          if (e.keyCode == ' ') {
            TreeItem[] items = nodeTree.getTree().getSelection();
            if (items.length > 0) {
              TreeItem item = items[0];
              if (item.getExpanded() == false) {
                item.setExpanded(true);
                Event e1 = new Event();
                e1.item = item;
                nodeTree.getTree().notifyListeners(SWT.Expand, e1);
              } else item.setExpanded(false);
            }
          }
        }

        public void keyReleased(KeyEvent e) {}
      };
  private static String WRONG_PATH = "Path is not exists or target to non-File resource";

  private final class NodeSelectionListener implements ISelectionChangedListener {
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
      TreeSelection ts = (TreeSelection) event.getSelection();
      Object[] sel = ts.toArray();
      select(sel);
    }
  }

  public void select(Object[] obj) {
    if (obj != null && obj.length == 1 && obj[0] instanceof IFile) {
      setTargetPath(((IFile) obj[0]).getFullPath());
      updateStatus(null);
    } else {
      setTargetPath(null);
      updateStatus(WRONG_PATH);
    }
  }

  private void dialogChanged() {
    updateStatus(null);
  }

  private void updateStatus(String message) {
    //Set node id nodeId to node.getQualifiedId()
    setErrorMessage(message);
    if (!optional) setPageComplete(message == null);
    else setPageComplete(true);
  }

  public String getContainerName() {
    return filePath.getText();
  }

  public IResource getModifiedResource() {
    return ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(getContainerName()));
  }

  public void setTargetPath(IPath input) {
    if (input == null && filePath != null) {
      filePath.setText("");
      sett.put("filePath", "");
      return;
    }
    String path = null;
    if (input != null) path = input.toPortableString();
    if (filePath != null && !filePath.getText().equals(path)&&path!=null)
      filePath.setText(path);

    try {
      if(path!=null){
        IPath p = Path.fromOSString(path);
        if (p == null || ResourcesPlugin.getWorkspace().getRoot().getFile(p) == null)
          updateStatus(WRONG_PATH);
      }
    } catch (Exception e) {
      updateStatus(WRONG_PATH);
      return;
    }
    if(path!=null){
      IFile file =
          ResourcesPlugin.getWorkspace()
              .getRoot()
              .getFile(Path.fromPortableString(path));
      if(file.exists()){
	      sett.put("fullPath", file.getLocation().toString());
	      sett.put("filePath", path);
	      targetPath = path;
	      if (nodeTree != null)
	        if (listener != null)
	          listener.selectionChanged(
	              new SelectionChangedEvent(
	                  nodeTree,
	                  new StructuredSelection(ResourcesPlugin.getWorkspace().getRoot().getFile(input))));
      }
  	}
    if (nodeTree != null) {
      TreeSelection ts = (TreeSelection) nodeTree.getSelection();
      if (ts == null
          || (IResource) ts.getFirstElement() == null
          || !((IResource) ts.getFirstElement()).getFullPath().equals(input))
        nodeTree.setSelection(
            new StructuredSelection(ResourcesPlugin.getWorkspace().getRoot().getFile(input)), true);
      updateStatus(null);
    } else updateStatus("node tree is not initiated");
  }

  public void setChangeListener(ISelectionChangedListener ncl) {
    listener = ncl;
  }

  public IFile getSelectedResource() {
    if (nodeTree == null) return null;
    StructuredSelection ss = (StructuredSelection) nodeTree.getSelection();
    if (!(ss.getFirstElement() instanceof IFile)) return null;
    return (IFile) ss.getFirstElement();
  }

  public void setOptional(boolean b) {
    optional = b;
  }
}
