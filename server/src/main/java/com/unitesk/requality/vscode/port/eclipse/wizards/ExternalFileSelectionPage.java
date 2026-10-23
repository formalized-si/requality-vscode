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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogPage;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
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
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;

public class ExternalFileSelectionPage extends WizardPage {
  private ISelectionChangedListener listener;
  private Text filePath;
  private TreeViewer nodeTree = null;
  private Set<String> extensions;
  private String targetPath;
  private DialogSettings sett;

  public class FileTreeContentProvider implements ITreeContentProvider {

    public void dispose() {}

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

    public Object[] getElements(Object inputElement) {
      if (inputElement instanceof File) {
        File f = (File) inputElement;
        if (f.listFiles() == null) return new Object[0];
        return f.listFiles();
      }
      if (inputElement instanceof File[]) {
        return (File[]) inputElement;
      }
      return new Object[0];
    }

    public Object[] getChildren(Object parentElement) {
      return getElements(parentElement);
    }
    //       private Object[] getShownChildren(File parentElement){
    //		   List<File> rl = new LinkedList<File>();
    //		   if(parentElement.canRead())
    //			   for(File r:parentElement.listFiles())
    //				   if(r!=null && r.isDirectory() || r.isFile() && r.canRead() && extensions.contains(getExtension(r.getName())))
    //					   rl.add(r);
    //		   return rl.toArray();
    //       }
    public Object getParent(Object element) {
      if (element instanceof File) return ((File) element).getParentFile();
      return null;
    }

    public boolean hasChildren(Object element) {
      if (element == null) return false;
      if (element instanceof File) {
        return getElements(element).length > 0;
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
  public ExternalFileSelectionPage(DialogSettings selection, String[] extensions) {
    super("fileSelection");
    setTitle("Select Element");
    setDescription("Please select element from listed tree.");
    this.sett = selection;
    if (extensions == null) this.extensions = Collections.emptySet();
    else this.extensions = new HashSet<>(Arrays.asList(extensions));
    String p = selection.get("filePath");
    if (p != null
        && !p.equals("")
        && ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString(p)) != null) {
      setTargetPath(p);
      updateStatus(null);
    } else {
      setTargetPath(null);
      updateStatus(WRONG_PATH);
    }
  }

  public String getExtension(String name) {
    int p = name.lastIndexOf(".");
    if (p > -1) {
      return name.substring(p + 1);
    }
    return null;
  }

  protected ILabelProvider getLabelProvider() {
    return new ILabelProvider() {

      @Override
      public void removeListener(ILabelProviderListener listener) {
        // TODO Auto-generated method stub

      }

      @Override
      public boolean isLabelProperty(Object element, String property) {
        // TODO Auto-generated method stub
        return false;
      }

      @Override
      public void dispose() {
        // TODO Auto-generated method stub

      }

      @Override
      public void addListener(ILabelProviderListener listener) {
        // TODO Auto-generated method stub

      }

      @Override
      public String getText(Object element) {
        if (element instanceof File) {
          File f = (File) element;
          return f.getName();
        }
        return null;
      }

      @Override
      public Image getImage(Object element) {
        // TODO Auto-generated method stub
        return null;
      }
    };
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
            setTargetPath(filePath.getText());
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

    nodeTree.setInput(File.listRoots());
    //nodeTree.setSorter(new RequalitySorter());
    nodeTree.setAutoExpandLevel(2);
    //nodeTree.setInput(ResourcesPlugin.getWorkspace().getRoot().getProjects());

    nodeTree.addSelectionChangedListener(new NodeSelectionListener());

    nodeTree.getTree().addMouseListener(mouseList);
    nodeTree.getTree().addKeyListener(keyList);

    if (targetPath != null) setTargetPath(targetPath);

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
          //TODO add func
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
      setTargetPath(((File) obj[0]).getAbsolutePath());
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
    setPageComplete(message == null);
  }

  public String getContainerName() {
    return filePath.getText();
  }

  public IResource getModifiedResource() {
    return ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(getContainerName()));
  }

  public void setTargetPath(String input) {
    if (input == null) {
      if (filePath != null) filePath.setText("");
      sett.put("filePath", "");
      sett.put("fullPath", "");
      return;
    }
    if (filePath != null && !filePath.getText().equals(input)) filePath.setText(input);

    try {
      String p = input;
      if (!new File(p).exists()) updateStatus(WRONG_PATH);
    } catch (Exception e) {
      updateStatus(WRONG_PATH);
      return;
    }
    try {
      IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromPortableString(input));
      sett.put("fullPath", file.getLocation().toString());
    } catch (Exception exx) {
    }
    sett.put("filePath", input);
    if (nodeTree != null)
      if (listener != null)
        listener.selectionChanged(
            new SelectionChangedEvent(nodeTree, new StructuredSelection(new File(input))));
    if (nodeTree != null) {
      TreeSelection ts = (TreeSelection) nodeTree.getSelection();
      if (ts == null
          || (File) ts.getFirstElement() == null
          || !((File) ts.getFirstElement()).getAbsolutePath().equals(input))
        nodeTree.setSelection(new StructuredSelection(new File(input)), true);
      updateStatus(null);
    } else updateStatus("node tree is not initiated");
  }

  public void setChangeListener(ISelectionChangedListener ncl) {
    listener = ncl;
  }

  public File getSelectedResource() {
    if (nodeTree == null) return null;
    StructuredSelection ss = (StructuredSelection) nodeTree.getSelection();
    if (!(ss.getFirstElement() instanceof IFile)) return null;
    return (File) ss.getFirstElement();
  }
}
