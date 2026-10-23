package com.unitesk.requality.vscode.ui.compare;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/**
 * {@code org.eclipse.compare.internal.AddFromHistoryDialog}: the dialog of Restore from Local History... of a folder or a
 * project. The deleted files that have states are listed with check boxes on the left; the states of the file of the
 * selected row are listed by day on the right, and the selected state is shown in the content pane. The OK button
 * ("Restore") returns the checked files with their selected states.
 * The states show the time image of the compare plug-in (the days have no image: Eclipse's dateIcon is not an image
 * of the plug-in). VSCODE: the content pane has its own compare configuration.
 */
public class AddFromHistoryDialog extends Dialog {
  /** A state of a deleted file: the element that is restored. */
  static class HistoryInput implements ITypedElement, IEncodedStreamContentAccessor, IModificationDate {
    final IFile fFile;
    final IFileState fFileState;

    HistoryInput(IFile file, IFileState fileState) {
      fFile = file;
      fFileState = fileState;
    }

    @Override
    public InputStream getContents() throws CoreException {
      return new BufferedInputStream(fFileState.getContents());
    }

    @Override
    public String getCharset() {
      String charset = fFileState.getCharset();
      if (charset == null) {
        try {
          charset = fFile.getCharset();
        } catch (CoreException e) {
          RLogger.logError(e);
        }
      }
      return charset;
    }

    @Override
    public String getName() {
      return fFile.getName();
    }

    @Override
    public String getType() {
      return fFile.getFileExtension();
    }

    @Override
    public Image getImage() {
      return CompareUI.getImage(fFile);
    }

    @Override
    public long getModificationDate() {
      return fFileState.getModificationTime();
    }
  }

  /** A deleted file and its states; the most recent state is selected at first. */
  static class FileHistory {
    private final IFile fFile;
    private IFileState[] fStates;
    private int fSelected;

    FileHistory(IFile file) {
      fFile = file;
    }

    IFile getFile() {
      return fFile;
    }

    IFileState[] getStates() {
      if (fStates == null) {
        try {
          fStates = fFile.getHistory(new NullProgressMonitor());
        } catch (CoreException ex) {
          RLogger.logError(ex);
        }
        if (fStates == null)
          fStates = new IFileState[0];
      }
      return fStates;
    }

    IFileState getSelectedState() {
      return getStates()[fSelected];
    }

    void setSelected(IFileState state) {
      for (int i = 0; i < fStates.length; i++) {
        if (fStates[i] == state) {
          fSelected = i;
          return;
        }
      }
    }

    HistoryInput getHistoryInput() {
      return new HistoryInput(fFile, getSelectedState());
    }

    boolean isSelected(int index) {
      return index == fSelected;
    }
  }

  private final ResourceBundle fBundle;
  private final CompareConfiguration fCompareConfiguration = new CompareConfiguration();
  /** The checked files. */
  private final List<FileHistory> fArrayList = new ArrayList<>();
  private FileHistory fCurrentFileHistory;

  private CompareViewerSwitchingPane fContentPane;
  private Button fCommitButton;
  private Table fMemberTable;
  private CompareViewerPane fMemberPane;
  private Tree fEditionTree;
  private CompareViewerPane fEditionPane;

  private Image fDateImage;
  private Image fTimeImage;

  public AddFromHistoryDialog(Shell parent, ResourceBundle bundle) {
    super(parent);
    fBundle = bundle;

    String iconName = CompareBundles.getString(fBundle, "dateIcon", "obj16/day_obj.svg");
    ImageDescriptor id = CompareUIPlugin.getImageDescriptor(iconName);
    if (id != null)
      fDateImage = id.createImage();
    iconName = CompareBundles.getString(fBundle, "timeIcon", "obj16/resource_obj.svg");
    id = CompareUIPlugin.getImageDescriptor(iconName);
    if (id != null)
      fTimeImage = id.createImage();
  }

  /**
   * Shows the dialog with the files of the container that have states.
   *
   * @return true when the user chose files to restore
   */
  public boolean select(IContainer root, IFile[] inputFiles) {
    create();
    String format = CompareBundles.getString(fBundle, "memberPaneTitle");
    fMemberPane.setImage(CompareUI.getImage(root));
    fMemberPane.setText(MessageFormat.format(format, root.getName()));
    IFile[] files = inputFiles.clone();
    Arrays.sort(files, AddFromHistoryDialog::compareByPath);
    String prefix = root.getFullPath().toString();
    if (fMemberTable != null && !fMemberTable.isDisposed()) {
      for (IFile file : files) {
        String path = file.getFullPath().toString();
        // ignore a recently deleted file at the same path as the container
        if (path.equals(prefix))
          continue;
        if (path.startsWith(prefix))
          path = path.substring(prefix.length() + 1);
        TableItem ti = new TableItem(fMemberTable, SWT.NONE);
        ti.setImage(CompareUI.getImage(file));
        ti.setText(path);
        ti.setData(new FileHistory(file));
      }
    }
    open();
    return getReturnCode() == OK && !fArrayList.isEmpty();
  }

  /** The inputs of the checked files: the selected state of each. */
  HistoryInput[] getSelected() {
    HistoryInput[] selected = new HistoryInput[fArrayList.size()];
    for (int i = 0; i < selected.length; i++)
      selected[i] = fArrayList.get(i).getHistoryInput();
    return selected;
  }

  @Override
  protected Control createDialogArea(Composite parent2) {
    Composite parent = (Composite) super.createDialogArea(parent2);
    getShell().setText(CompareBundles.getString(fBundle, "title"));

    Composite top = new Composite(parent, SWT.NONE);
    top.setLayout(new GridLayout(2, true));
    top.setLayoutData(new GridData(GridData.FILL_BOTH));

    // the left pane: the deleted files (check boxes)
    Composite c = new Composite(top, SWT.NONE);
    c.setLayout(column());
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    Label l1 = new Label(c, SWT.NONE);
    l1.setText(CompareBundles.getString(fBundle, "memberDescription"));
    fMemberPane = new CompareViewerPane(c, SWT.BORDER | SWT.FLAT);
    fMemberPane.setLayoutData(new GridData(GridData.FILL_BOTH));
    fMemberTable = new Table(fMemberPane, SWT.CHECK | SWT.H_SCROLL | SWT.V_SCROLL);
    fMemberTable.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (e.detail == SWT.CHECK) {
          if (e.item instanceof TableItem ti) {
            if (ti.getChecked())
              fArrayList.add((FileHistory) ti.getData());
            else
              fArrayList.remove(ti.getData());
            if (fCommitButton != null)
              fCommitButton.setEnabled(!fArrayList.isEmpty());
          }
        } else {
          handleMemberSelect(e.item);
        }
      }
    });
    fMemberPane.setContent(fMemberTable);

    // the right pane: the states of the selected file
    c = new Composite(top, SWT.NONE);
    c.setLayout(column());
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    Label l2 = new Label(c, SWT.NONE);
    l2.setText(CompareBundles.getString(fBundle, "editionDescription"));
    fEditionPane = new CompareViewerPane(c, SWT.BORDER | SWT.FLAT);
    fEditionPane.setLayoutData(new GridData(GridData.FILL_BOTH));
    fEditionTree = new Tree(fEditionPane, SWT.H_SCROLL | SWT.V_SCROLL);
    fEditionTree.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        feedContent(e.item);
      }
    });
    fEditionPane.setContent(fEditionTree);

    fContentPane = new CompareViewerSwitchingPane(parent, SWT.BORDER | SWT.FLAT) {
      @Override
      protected Viewer getViewer(Viewer oldViewer, Object input) {
        return CompareUI.findContentViewer(oldViewer, input, this, fCompareConfiguration);
      }
    };
    GridData cd = new GridData(GridData.FILL_BOTH);
    cd.heightHint = 300;
    fContentPane.setLayoutData(cd);
    return parent;
  }

  private static GridLayout column() {
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 2;
    layout.verticalSpacing = 2;
    layout.numColumns = 1;
    return layout;
  }

  /** Lists the states of the file of the selected row. */
  private void handleMemberSelect(Widget w) {
    Object data = w == null ? null : w.getData();
    if (data instanceof FileHistory h) {
      fCurrentFileHistory = h;
      IFile file = h.getFile();
      IFileState[] states = h.getStates();
      fEditionPane.setImage(CompareUI.getImage(file));
      fEditionPane.setText(MessageFormat.format(CompareBundles.getString(fBundle, "treeTitleFormat"), file.getName()));
      if (fEditionTree != null) {
        fEditionTree.setRedraw(false);
        fEditionTree.removeAll();
        for (int i = 0; i < states.length; i++)
          addEdition(new HistoryInput(file, states[i]), h.isSelected(i));
        fEditionTree.setRedraw(true);
      }
    } else {
      fCurrentFileHistory = null;
    }
  }

  /** Adds a state under the node of its day (a new day node is created when the day changes). */
  private void addEdition(HistoryInput input, boolean isSelected) {
    if (fEditionTree == null || fEditionTree.isDisposed())
      return;
    IFileState state = input.fFileState;
    TreeItem[] days = fEditionTree.getItems();
    TreeItem lastDay = days.length > 0 ? days[days.length - 1] : null;
    long ldate = state.getModificationTime();
    long day = dayNumber(ldate);
    Date date = new Date(ldate);
    if (lastDay == null || day != dayNumber(((Date) lastDay.getData()).getTime())) {
      lastDay = new TreeItem(fEditionTree, SWT.NONE);
      lastDay.setImage(fDateImage);
      String df = DateFormat.getDateInstance().format(date);
      long today = dayNumber(System.currentTimeMillis());
      String formatKey;
      if (day == today)
        formatKey = "todayFormat";
      else if (day == today - 1)
        formatKey = "yesterdayFormat";
      else
        formatKey = "dayFormat";
      String pattern = CompareBundles.getString(fBundle, formatKey);
      if (pattern != null)
        df = MessageFormat.format(pattern, df);
      lastDay.setText(df);
      lastDay.setData(date);
    }
    TreeItem ti = new TreeItem(lastDay, SWT.NONE);
    ti.setImage(fTimeImage);
    ti.setText(DateFormat.getTimeInstance().format(date));
    ti.setData(input);
    if (isSelected) {
      lastDay.setExpanded(true);
      fEditionTree.setSelection(new TreeItem[] { ti });
      feedContent(ti);
    }
  }

  /** The day number of a date in the local time zone. */
  private static long dayNumber(long date) {
    long oneDayMs = 24L * 60 * 60 * 1000;
    Calendar calendar = Calendar.getInstance();
    long localTimeOffset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
    return (date + localTimeOffset) / oneDayMs;
  }

  /** Shows the selected state in the content pane and remembers it as the state to restore. */
  private void feedContent(Widget w) {
    if (fContentPane != null && !fContentPane.isDisposed()) {
      Object o = w == null ? null : w.getData();
      if (o instanceof HistoryInput selected) {
        fContentPane.setInput(selected);
        fContentPane.setText(getEditionLabel(selected));
        fContentPane.setImage(fTimeImage);
        if (fCurrentFileHistory != null)
          fCurrentFileHistory.setSelected(selected.fFileState);
      } else {
        fContentPane.setInput(null);
      }
    }
  }

  protected String getEditionLabel(HistoryInput input) {
    String format = CompareBundles.getString(fBundle, "historyEditionLabel", null);
    if (format == null)
      format = CompareBundles.getString(fBundle, "editionLabel");
    if (format == null)
      format = "x{0}";
    String date = DateFormat.getDateTimeInstance().format(new Date(input.getModificationDate()));
    return MessageFormat.format(format, date);
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    String buttonLabel = CompareBundles.getString(fBundle, "buttonLabel", IDialogConstants.OK_LABEL);
    // a 'Cancel' and a 'Restore' button
    fCommitButton = createButton(parent, IDialogConstants.OK_ID, buttonLabel, true);
    fCommitButton.setEnabled(false);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  /** Orders the paths by their segments, ignoring the case (Eclipse {@code AddFromHistoryDialog.greaterThan}). */
  private static int compareByPath(IFile f1, IFile f2) {
    String[] ss1 = f1.getFullPath().segments();
    String[] ss2 = f2.getFullPath().segments();
    int n = Math.max(ss1.length, ss2.length);
    for (int i = 0; i < n; i++) {
      String s1 = i < ss1.length ? ss1[i] : "";
      String s2 = i < ss2.length ? ss2[i] : "";
      int rc = s1.compareToIgnoreCase(s2);
      if (rc != 0)
        return rc;
    }
    return 0;
  }
}
