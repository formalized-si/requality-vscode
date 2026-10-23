package com.unitesk.requality.vscode.ui.compare;

import java.io.File;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.dialogs.TitleAreaDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.DragSource;
import com.unitesk.requality.vscode.ui.swt.dnd.DragSourceEvent;
import com.unitesk.requality.vscode.ui.swt.dnd.DragSourceListener;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTarget;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetListener;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;
import com.unitesk.requality.vscode.ui.workbench.ResourceTransfer;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.DirectoryDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.forms.events.ExpansionAdapter;
import com.unitesk.requality.vscode.ui.forms.events.ExpansionEvent;
import com.unitesk.requality.vscode.ui.forms.widgets.ExpandableComposite;

/**
 * {@code org.eclipse.compare.internal.CompareWithOtherResourceDialog}: the dialog of "Compare With Other Resource" that
 * chooses the left, right and (optionally) the ancestor of a compare: each side is a workspace resource (typed by its
 * path) or an external file or folder (chosen with a file dialog). The result holds the chosen sides: {@link IResource}s
 * of the workspace, or {@link File}s of the file system.
 * VSCODE: the external files and folders are not linked into a workspace project (see
 * {@code CompareWithOtherResourceHandler}); files are dragged from the VS Code views with Shift held (VS Code drops
 * into webviews only then).
 */
public class CompareWithOtherResourceDialog extends TitleAreaDialog {
  private static final int MIN_WIDTH = 320;
  private static final int MIN_HEIGHT_WITH_ANCESTOR = 320;
  private static final int MIN_HEIGHT_WITHOUT_ANCESTOR = 238;

  /** The kinds of content of a side. */
  static final int WORKSPACE = 0;
  static final int EXTERNAL_FILE = 1;
  static final int EXTERNAL_FOLDER = 2;

  private final ISelection selection;
  private Button okButton;
  private Side ancestorPanel;
  private Side leftPanel;
  private Side rightPanel;
  private ExpandableComposite ancestorExpandable;

  /** {@code CompareWithOtherResourceDialog.FileTextDragListener}: drags the text of a workspace field. */
  private static class FileTextDragListener implements DragSourceListener {
    private final Text element;

    FileTextDragListener(Text element) {
      this.element = element;
    }

    @Override
    public void dragFinished(DragSourceEvent event) {
      element.setText("");
    }

    @Override
    public void dragSetData(DragSourceEvent event) {
      event.data = element.getText();
    }

    @Override
    public void dragStart(DragSourceEvent event) {
      if (element.getText() == null)
        event.doit = false;
    }
  }

  /** {@code CompareWithOtherResourceDialog.FileTextDropListener}: a dropped resource or path sets a workspace field. */
  private static class FileTextDropListener implements DropTargetListener {
    private final Side element;
    private final ResourceTransfer resourceTransfer;
    private final TextTransfer textTransfer;

    FileTextDropListener(Side element) {
      this.element = element;
      resourceTransfer = ResourceTransfer.getInstance();
      textTransfer = TextTransfer.getInstance();
    }

    @Override
    public void dragEnter(DropTargetEvent event) {
      if (event.detail == DND.DROP_DEFAULT) {
        if ((event.operations & DND.DROP_COPY) != 0)
          event.detail = DND.DROP_COPY;
        else
          event.detail = DND.DROP_NONE;
      }
      for (TransferData dataType : event.dataTypes) {
        if (resourceTransfer.isSupportedType(dataType) || textTransfer.isSupportedType(dataType)) {
          event.currentDataType = dataType;
          if (event.detail != DND.DROP_COPY)
            event.detail = DND.DROP_NONE;
          break;
        }
      }
    }

    @Override
    public void dragLeave(DropTargetEvent event) {
      // intentionally empty
    }

    @Override
    public void dragOperationChanged(DropTargetEvent event) {
      if (event.detail == DND.DROP_DEFAULT) {
        if ((event.operations & DND.DROP_COPY) != 0)
          event.detail = DND.DROP_COPY;
        else
          event.detail = DND.DROP_NONE;
      } else if (resourceTransfer.isSupportedType(event.currentDataType)) {
        if (event.detail != DND.DROP_COPY)
          event.detail = DND.DROP_NONE;
      }
    }

    @Override
    public void dragOver(DropTargetEvent event) {
      // intentionally empty
    }

    @Override
    public void drop(DropTargetEvent event) {
      if (textTransfer.isSupportedType(event.currentDataType)) {
        String txt = (String) event.data;
        IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(txt);
        if (r != null)
          element.setResource(r);
      } else if (resourceTransfer.isSupportedType(event.currentDataType)) {
        IResource[] files = (IResource[]) event.data;
        if (files.length > 0)
          element.setResource(files[0]);
      }
    }

    @Override
    public void dropAccept(DropTargetEvent event) {
      // intentionally empty
    }
  }

  /** {@code CompareWithOtherResourceDialog.InternalSection}: a side with a radio button, a field and a button per kind. */
  private class Side {
    private final Button[] radios = new Button[3];
    private final Text[] texts = new Text[3];
    private final Button[] mains = new Button[3];
    private final String[] labels = {
        "&Workspace", "&External file", "External folder" };
    private int type = WORKSPACE;
    private Object value;

    Side(Composite parent, String title) {
      Group group = new Group(parent, SWT.NONE);
      group.setText(title);
      group.setLayout(new GridLayout(3, false));
      group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
      for (int i = 0; i < 3; i++) {
        final int kind = i;
        radios[i] = new Button(group, SWT.RADIO);
        radios[i].setText(labels[i]);
        radios[i].addSelectionListener(new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            if (radios[kind].getSelection())
              setContentType(kind);
          }
        });
        texts[i] = new Text(group, SWT.BORDER);
        texts[i].setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        texts[i].setEditable(kind == WORKSPACE);
        mains[i] = new Button(group, SWT.PUSH);
        mains[i].setText("Browse...");
        mains[i].addSelectionListener(new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            browse(kind);
          }
        });
        if (kind == WORKSPACE) {
          texts[i].addModifyListener(e -> {
            if (type == WORKSPACE)
              setWorkspaceResource(texts[WORKSPACE].getText());
          });
          mains[i].setVisible(false);
          initDrag(texts[i]);
          initDrop(texts[i]);
        }
      }
      setContentType(WORKSPACE);
    }

    void setContentType(int kind) {
      type = kind;
      for (int i = 0; i < 3; i++) {
        radios[i].setSelection(i == kind);
        texts[i].setEnabled(i == kind);
        mains[i].setEnabled(i == kind);
      }
      if (kind == WORKSPACE)
        setWorkspaceResource(texts[WORKSPACE].getText());
      updateErrorInfo();
    }

    private void browse(int kind) {
      String path = null;
      if (kind == EXTERNAL_FILE) {
        path = new FileDialog(getShell()).open();
        if (path != null && new File(path).isFile())
          setExternal(kind, new File(path));
      } else if (kind == EXTERNAL_FOLDER) {
        path = new DirectoryDialog(getShell()).open();
        if (path != null && new File(path).isDirectory())
          setExternal(kind, new File(path));
      }
    }

    private void setExternal(int kind, File file) {
      type = kind;
      value = file;
      texts[kind].setText(file.getAbsolutePath());
      setContentType(kind);
    }

    private void setWorkspaceResource(String path) {
      value = path.isEmpty() ? null : ResourcesPlugin.getWorkspace().getRoot().findMember(path);
      updateErrorInfo();
    }

    /** {@code WorkspaceContent.initDrag}: the text of the workspace resource can be dragged to another field. */
    private void initDrag(Text text) {
      DragSource source = new DragSource(text, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT);
      source.setTransfer(TextTransfer.getInstance(), ResourceTransfer.getInstance());
      source.addDragListener(new FileTextDragListener(text));
    }

    /** {@code WorkspaceContent.initDrop}: a resource (or its path) dropped on the field becomes the side. */
    private void initDrop(Text text) {
      DropTarget target = new DropTarget(text, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT);
      target.setTransfer(TextTransfer.getInstance(), ResourceTransfer.getInstance());
      target.addDropListener(new FileTextDropListener(this));
    }

    /** Sets the side to a workspace resource. */
    void setResource(IResource resource) {
      type = WORKSPACE;
      texts[WORKSPACE].setText(resource.getFullPath().toOSString());
      setContentType(WORKSPACE);
    }

    /** The chosen side: a workspace resource, an external file, or null when it is empty or not found. */
    Object getValue() {
      return value;
    }
  }

  /** The dialog for the selection: its resources prefill the sides (one, two or three). */
  public CompareWithOtherResourceDialog(Shell shell, ISelection selection) {
    super(shell);
    setShellStyle(SWT.MODELESS | SWT.RESIZE | SWT.MAX);
    this.selection = selection;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite mainPanel = new Composite(parent, SWT.NONE);
    mainPanel.setLayout(new GridLayout(1, true));
    mainPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    ancestorExpandable = new ExpandableComposite(mainPanel, SWT.NONE, ExpandableComposite.TWISTIE);
    ancestorExpandable.setText("Ancestor");
    ancestorExpandable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    Composite ancestorClient = new Composite(ancestorExpandable, SWT.NONE);
    ancestorClient.setLayout(new GridLayout(1, true));
    ancestorPanel = new Side(ancestorClient, "Ancestor");
    ancestorExpandable.setClient(ancestorClient);
    ancestorExpandable.addExpansionListener(new ExpansionAdapter() {
      @Override
      public void expansionStateChanged(ExpansionEvent e) {
        adjustSize(ancestorExpandable.isExpanded());
      }
    });

    leftPanel = new Side(mainPanel, "Left");
    rightPanel = new Side(mainPanel, "Right");

    setSelection(selection);
    getShell().setText("Compare with Other Resource");
    setTitle("Select resources to compare");
    adjustSize(ancestorExpandable.isExpanded());
    return mainPanel;
  }

  private void adjustSize(boolean expanded) {
    getShell().setMinimumSize(MIN_WIDTH, expanded ? MIN_HEIGHT_WITH_ANCESTOR : MIN_HEIGHT_WITHOUT_ANCESTOR);
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    super.createButtonsForButtonBar(parent);
    okButton = getButton(IDialogConstants.OK_ID);
    updateErrorInfo();
    setMessage("Drag files from a view or between dialog's fields.");
  }

  /** The selected resources prefill the sides: one (left), two (left, right) or three (ancestor, left, right). */
  private void setSelection(ISelection selection) {
    IResource[] selected = ResourceCompareInput.getResources(selection);
    switch (selected.length) {
      case 1:
        leftPanel.setResource(selected[0]);
        break;
      case 2:
        leftPanel.setResource(selected[0]);
        rightPanel.setResource(selected[1]);
        break;
      case 3:
        ancestorPanel.setResource(selected[0]);
        ancestorExpandable.setExpanded(true);
        leftPanel.setResource(selected[1]);
        rightPanel.setResource(selected[2]);
        break;
      default:
        break;
    }
  }

  /** The sides of the compare: the ancestor only when it is chosen. */
  private Object[] sides() {
    Object ancestor = ancestorExpandable != null && ancestorExpandable.isExpanded() ? ancestorPanel.getValue() : null;
    if (ancestor == null)
      return new Object[] { leftPanel.getValue(), rightPanel.getValue() };
    return new Object[] { ancestor, leftPanel.getValue(), rightPanel.getValue() };
  }

  /** Whether the chosen sides can be compared: the resources of a compare, or two external files. */
  private boolean isComparePossible() {
    Object[] sides = sides();
    boolean external = false;
    for (Object side : sides) {
      if (side instanceof File)
        external = true;
    }
    if (!external) {
      IResource[] resources = new IResource[sides.length];
      for (int i = 0; i < sides.length; i++)
        resources[i] = (IResource) sides[i];
      return new ResourceCompareInput(new CompareConfiguration())
          .isEnabled(new StructuredSelection(resources));
    }
    // the sides are all files or all folders/projects (ResourceCompareInput.isEnabled for external sides too)
    Boolean folders = null;
    for (Object side : sides) {
      boolean folder = side instanceof File ? ((File) side).isDirectory() : ((IResource) side).getType() != IResource.FILE;
      if (folders == null)
        folders = folder;
      else if (folders != folder)
        return false;
    }
    return sides.length == 2 || sides.length == 3;
  }

  private void updateErrorInfo() {
    if (okButton == null)
      return;
    if (leftPanel.getValue() == null || rightPanel.getValue() == null) {
      setMessage("Both left and right panel must contain a valid path.", IMessageProvider.ERROR);
      okButton.setEnabled(false);
    } else if (!isComparePossible()) {
      setMessage("Selected resources are not comparable.", IMessageProvider.ERROR);
      okButton.setEnabled(false);
    } else {
      setMessage("Drag files from a view or between dialog's fields.");
      okButton.setEnabled(true);
    }
  }

  /**
   * The chosen sides: the ancestor (when chosen) then the left and the right side. Each is an {@link IResource} of the
   * workspace or a {@link File} of the file system.
   */
  public Object[] getResult() {
    return sides();
  }
}
