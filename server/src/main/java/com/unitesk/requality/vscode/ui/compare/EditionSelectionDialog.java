package com.unitesk.requality.vscode.ui.compare;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.ResourceBundle;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
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
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;

/**
 * {@code org.eclipse.compare.EditionSelectionDialog}: the dialog of the local history actions. The editions of an
 * element (its local history states, the element itself first) are listed by day in a tree; the selected edition is
 * compared with the target in the content pane (and in the structure pane in compare mode). The OK button ("Replace",
 * "Restore") selects the edition; in compare mode the dialog only shows the comparison.
 * The editions show the time image of the compare plug-in (timeIcon); its dateIcon (obj16/day_obj) is not an image of
 * the plug-in, so the days have none, as in Eclipse.
 * The editions are those of the element: the callers of the product (the local history actions) select no sub-element
 * path (Eclipse's path editions serve the structure creators of language plug-ins).
 * The add mode of Eclipse (restoring deleted members of a container) is {@link AddFromHistoryDialog}.
 */
public class EditionSelectionDialog extends Dialog {
  /** An edition of the list: its contents are read when they are compared (once). */
  private static final class Pair {
    final ITypedElement edition;
    private String content;

    Pair(ITypedElement edition) {
      this.edition = edition;
    }

    String getContent() {
      if (content == null) {
        try {
          content = CompareBundles.readString(edition);
        } catch (CoreException e) {
          RLogger.logError(e);
          content = "";
        }
      }
      return content;
    }

    /** Two editions are equal when their contents are equal (Eclipse {@code Pair.equals}). */
    @Override
    public boolean equals(Object other) {
      if (other instanceof Pair p)
        return getContent().equals(p.getContent());
      return false;
    }

    @Override
    public int hashCode() {
      return getContent().hashCode();
    }
  }

  private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

  private final ResourceBundle fBundle;
  private CompareConfiguration fCompareConfiguration;
  /** Hide the editions whose contents equal the target (Eclipse's default). */
  private final boolean fHideIdentical = true;
  /** Compare mode: only a close button, and a structure pane above the content pane. */
  private boolean fCompareMode;
  private Pair fTargetPair;
  private ITypedElement fSelectedItem;
  private String fTitleArg;
  private Image fTitleImage;
  private Image fDateImage;
  private Image fTimeImage;

  private CompareViewerPane fEditionPane;
  private Tree fEditionTree;
  private CompareViewerSwitchingPane fStructuredComparePane;
  private CompareViewerSwitchingPane fContentPane;
  private Button fCommitButton;

  /**
   * Creates a new modal dialog configured by the resource bundle of the action (the keys: title, treeTitleFormat,
   * dayFormat, todayFormat, yesterdayFormat, editionLabel, targetLabel, workspaceTargetLabel, buttonLabel ...).
   *
   * @param parent the shell the dialog stays on top of
   * @param bundle the resource bundle of the action
   */
  public EditionSelectionDialog(Shell parent, ResourceBundle bundle) {
    super(parent);
    fBundle = bundle;
  }

  /** Sets an additional argument for the edition pane's title (the file name). */
  public void setEditionTitleArgument(String titleArgument) {
    fTitleArg = titleArgument;
  }

  /** Sets an optional image for the edition pane's title. */
  public void setEditionTitleImage(Image titleImage) {
    fTitleImage = titleImage;
  }

  /** Compare mode: the dialog compares the selected edition with the target and has no OK button. */
  public void setCompareMode(boolean compareMode) {
    fCompareMode = compareMode;
  }

  /**
   * Selects the previous edition: the most recent edition whose contents differ from the target. No dialog is shown.
   *
   * @param target the element the editions are compared with
   * @param inputEditions the editions (each an {@link IModificationDate} {@link ITypedElement})
   * @return the selected edition, or null when there is none
   */
  public ITypedElement selectPreviousEdition(ITypedElement target, ITypedElement[] inputEditions) {
    fTargetPair = new Pair(target);
    for (ITypedElement edition : sortedNewestFirst(inputEditions)) {
      Pair pair = new Pair(edition);
      if (!fTargetPair.equals(pair))
        return edition;
    }
    return null;
  }

  /**
   * Shows the dialog with the editions of the target. In replace mode the OK button returns the selected edition.
   *
   * @param target the element the editions are compared with
   * @param inputEditions the editions (each an {@link IModificationDate} {@link ITypedElement})
   * @return the selected edition, or null when the dialog was cancelled
   */
  public ITypedElement selectEdition(ITypedElement target, ITypedElement[] inputEditions) {
    fTargetPair = new Pair(target);
    ITypedElement[] editions = sortedNewestFirst(inputEditions);
    // the labels of the compare viewer
    String targetLabel = getTargetLabel(target);
    getCompareConfiguration().setLeftLabel(targetLabel);
    // create the tree widget, then the editions from front (newest) to back (oldest)
    create();
    for (ITypedElement edition : editions)
      addMemberEdition(new Pair(edition));
    open();
    if (getReturnCode() == OK)
      return fSelectedItem;
    return null;
  }

  private static ITypedElement[] sortedNewestFirst(ITypedElement[] inputEditions) {
    ITypedElement[] editions = inputEditions.clone();
    Arrays.sort(editions, (a, b) -> {
      long d = ((IModificationDate) b).getModificationDate() - ((IModificationDate) a).getModificationDate();
      return d < 0 ? -1 : d > 0 ? 1 : 0;
    });
    return editions;
  }

  private CompareConfiguration getCompareConfiguration() {
    if (fCompareConfiguration == null) {
      fCompareConfiguration = new CompareConfiguration();
      fCompareConfiguration.setLeftEditable(false);
      fCompareConfiguration.setRightEditable(false);
    }
    return fCompareConfiguration;
  }

  private String getTargetLabel(ITypedElement target) {
    String format = target instanceof ResourceNode ? CompareBundles.getString(fBundle, "workspaceTargetLabel", null) : null;
    if (format == null)
      format = CompareBundles.getString(fBundle, "targetLabel");
    if (format == null)
      format = "x{0}";
    return formatString(format, target.getName());
  }

  /**
   * {@code EditionSelectionDialog.getEditionImage}: the image of the edition side of a compare viewer: the image of
   * the workspace resource, or the time image of a local history state.
   */
  private Image getEditionImage(ITypedElement selectedEdition) {
    if (selectedEdition instanceof ResourceNode)
      return selectedEdition.getImage();
    if (selectedEdition instanceof HistoryItem) {
      if (fTimeImage == null) {
        String iconName = CompareBundles.getString(fBundle, "timeIcon", "obj16/resource_obj.svg");
        ImageDescriptor id = CompareUIPlugin.getImageDescriptor(iconName);
        if (id != null)
          fTimeImage = id.createImage();
      }
      return fTimeImage;
    }
    return null;
  }

  private String getEditionLabel(ITypedElement selectedEdition) {
    String format = null;
    if (selectedEdition instanceof ResourceNode)
      format = CompareBundles.getString(fBundle, "workspaceEditionLabel", null);
    else if (selectedEdition instanceof HistoryItem)
      format = CompareBundles.getString(fBundle, "historyEditionLabel", null);
    if (format == null)
      format = CompareBundles.getString(fBundle, "editionLabel");
    if (format == null)
      format = "x{0}";
    String date = "";
    if (selectedEdition instanceof IModificationDate)
      date = DateFormat.getDateTimeInstance().format(new Date(((IModificationDate) selectedEdition).getModificationDate()));
    return formatString(format, date);
  }

  private String getShortEditionLabel(ITypedElement edition, Date date) {
    String format = null;
    if (edition instanceof ResourceNode)
      format = CompareBundles.getString(fBundle, "workspaceTreeFormat", null);
    if (format == null)
      format = CompareBundles.getString(fBundle, "treeFormat", null);
    if (format == null)
      format = "x{0}";
    return formatString(format, DateFormat.getTimeInstance().format(date));
  }

  /** The bundle text formatted with the variable; a text without a variable or a quote is used as it is. */
  private static String formatString(String string, String variable) {
    if (string.contains("{0}") || string.contains("''"))
      return MessageFormat.format(string, variable);
    return string;
  }

  @Override
  protected Control createDialogArea(Composite parent2) {
    Composite parent = (Composite) super.createDialogArea(parent2);
    getShell().setText(CompareBundles.getString(fBundle, "title"));
    if (fCompareMode) {
      // the structure pane (of the structure viewer registered for the type) beside the editions
      Composite top = new Composite(parent, SWT.NONE);
      top.setLayout(new GridLayout(2, true));
      top.setLayoutData(new GridData(GridData.FILL_BOTH));
      fEditionPane = new CompareViewerPane(top, SWT.BORDER | SWT.FLAT);
      fEditionPane.setLayoutData(new GridData(GridData.FILL_BOTH));
      fStructuredComparePane = new CompareViewerSwitchingPane(top, SWT.BORDER | SWT.FLAT, true) {
        @Override
        protected Viewer getViewer(Viewer oldViewer, Object input) {
          if (input instanceof ICompareInput)
            return CompareUI.findStructureViewer(oldViewer, (ICompareInput) input, this, getCompareConfiguration());
          return null;
        }
      };
      fStructuredComparePane.setLayoutData(new GridData(GridData.FILL_BOTH));
      fStructuredComparePane.addSelectionChangedListener(e -> feedInput2(e.getSelection()));
    } else {
      fEditionPane = new CompareViewerPane(parent, SWT.BORDER | SWT.FLAT);
      GridData gd = new GridData(GridData.FILL_HORIZONTAL);
      gd.heightHint = 150;
      fEditionPane.setLayoutData(gd);
    }
    if (fTitleArg == null)
      fTitleArg = fTargetPair.edition.getName();
    fEditionPane.setText(MessageFormat.format(CompareBundles.getString(fBundle, "treeTitleFormat"), fTitleArg));
    if (fTitleImage != null)
      fEditionPane.setImage(fTitleImage);
    fEditionTree = new Tree(fEditionPane, SWT.H_SCROLL | SWT.V_SCROLL);
    fEditionTree.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (e.item instanceof TreeItem ti)
          feedInput(ti.getData());
      }
    });
    fEditionPane.setContent(fEditionTree);

    fContentPane = new CompareViewerSwitchingPane(parent, SWT.BORDER | SWT.FLAT) {
      @Override
      protected Viewer getViewer(Viewer oldViewer, Object input) {
        return CompareUI.findContentViewer(oldViewer, input, this, getCompareConfiguration());
      }
    };
    GridData cd = new GridData(GridData.FILL_BOTH);
    cd.heightHint = 300;
    fContentPane.setLayoutData(cd);
    return parent;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    String buttonLabel = CompareBundles.getString(fBundle, "buttonLabel", IDialogConstants.OK_LABEL);
    if (fCompareMode) {
      // only a 'Done' button
      createButton(parent, IDialogConstants.CANCEL_ID, buttonLabel, false);
    } else {
      // a 'Cancel' and a 'Add/Replace' button
      fCommitButton = createButton(parent, IDialogConstants.OK_ID, buttonLabel, true);
      fCommitButton.setEnabled(false);
      createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
  }

  @Override
  protected void okPressed() {
    // in compare mode the dialog is not dismissed by its (close) button
    if (!fCompareMode)
      super.okPressed();
  }

  /** Adds an edition unless it equals the target (the editions are added from the newest to the oldest). */
  private void addMemberEdition(Pair pair) {
    if (fHideIdentical && pair.equals(fTargetPair))
      return;
    addEdition(pair);
  }

  /** The day number of a date in the local time zone. */
  private static long dayNumber(long date) {
    Calendar calendar = Calendar.getInstance();
    long localTimeOffset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
    return (date + localTimeOffset) / ONE_DAY_MS;
  }

  /** Adds an edition under the node of its day (a new day node is created when the day changes). */
  private void addEdition(Pair pair) {
    if (fEditionTree == null || fEditionTree.isDisposed())
      return;
    TreeItem[] days = fEditionTree.getItems();
    TreeItem lastDay = days.length > 0 ? days[days.length - 1] : null;
    boolean first = lastDay == null;
    ITypedElement edition = pair.edition;
    long ldate = ((IModificationDate) edition).getModificationDate();
    long day = dayNumber(ldate);
    Date date = new Date(ldate);
    if (lastDay == null || day != dayNumber(((Date) lastDay.getData()).getTime())) {
      lastDay = new TreeItem(fEditionTree, SWT.NONE);
      if (fDateImage == null) {
        String iconName = CompareBundles.getString(fBundle, "dateIcon", "obj16/day_obj.svg");
        ImageDescriptor id = CompareUIPlugin.getImageDescriptor(iconName);
        if (id != null)
          fDateImage = id.createImage();
      }
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
    ti.setImage(getEditionImage(edition));
    ti.setText(getShortEditionLabel(edition, date));
    ti.setData(pair);
    // the newest edition is selected first
    if (first) {
      fEditionTree.setSelection(new TreeItem[] { ti });
      fEditionTree.setFocus();
      feedInput(pair);
    }
    if (first)
      lastDay.setExpanded(true);
  }

  /** Shows the selected edition against the target (the compare of the dialog). */
  private void feedInput(Object data) {
    boolean isOK = false;
    if (data instanceof Pair pair) {
      fSelectedItem = pair.edition;
      isOK = true;
      String editionLabel = getEditionLabel(pair.edition);
      CompareConfiguration config = getCompareConfiguration();
      config.setRightLabel(editionLabel);
      config.setRightImage(getEditionImage(pair.edition));
      setInput(new DiffNode(fTargetPair.edition, fSelectedItem));
    } else {
      fSelectedItem = null;
      setInput(null);
    }
    if (fCommitButton != null)
      fCommitButton.setEnabled(isOK && fSelectedItem != null && fTargetPair.edition != fSelectedItem);
  }

  private void setInput(Object input) {
    fContentPane.setInput(input);
    if (fStructuredComparePane != null)
      fStructuredComparePane.setInput(input);
  }

  /** The element selected in the structure pane is shown in the content pane. */
  private void feedInput2(ISelection sel) {
    if (sel instanceof IStructuredSelection ss && ss.size() == 1)
      fContentPane.setInput(ss.getFirstElement());
  }
}
