package com.unitesk.requality.vscode.ui.compare;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffTreeViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.compare.CompareEditorInput}: the input of a compare editor or dialog. {@link #prepareInput}
 * computes the compare result (a {@link ICompareInput}, a tree of differences, or null when there are none);
 * {@link #createContents} shows it: the structure input pane (the tree of differences, or a custom pane) above the
 * content pane, which shows the selected difference in the content merge viewer of its type.
 * VSCODE: the compare editor of a single text comparison is the VS Code diff editor ({@link CompareEditor}); the
 * structure panes of the structure creators (none are contributed) are not created.
 */
public abstract class CompareEditorInput implements IRunnableWithProgress {
  public static final String DIRTY_STATE = "DIRTY_STATE";
  public static final String PROP_TITLE = "org.eclipse.compare.Title";
  public static final String PROP_TITLE_IMAGE = "org.eclipse.compare.TitleImage";
  public static final String PROP_SELECTED_EDITION = "org.eclipse.compare.SelectedEdition";

  private final CompareConfiguration fCompareConfiguration;
  private final List<IPropertyChangeListener> fListenerList = new ArrayList<>();
  private Object fInput;
  private String fMessage;
  private String fTitle;
  private boolean fLeftDirty;
  private boolean fRightDirty;
  private Composite fComposite;
  private CompareViewerPane fStructureInputPane;
  private CompareViewerSwitchingPane fContentInputPane;
  private CompareViewerPane fFocusPane;
  private String fHelpContextId;
  private Object fContainer;

  public CompareEditorInput(CompareConfiguration configuration) {
    fCompareConfiguration = configuration;
    fContainer = configuration.getContainer();
    configuration.setContainer(this);
  }

  public CompareConfiguration getCompareConfiguration() {
    return fCompareConfiguration;
  }

  protected void setMessage(String message) {
    fMessage = message;
  }

  public String getMessage() {
    return fMessage;
  }

  public String getTitle() {
    return fTitle == null ? "Compare" : fTitle;
  }

  public void setTitle(String title) {
    String old = fTitle;
    fTitle = title;
    firePropertyChange(new PropertyChangeEvent(this, PROP_TITLE, old, title));
  }

  public Image getTitleImage() {
    return null;
  }

  public String getName() {
    return getTitle();
  }

  public String getToolTipText() {
    return getTitle();
  }

  @Override
  public void run(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
    fInput = prepareInput(monitor);
  }

  /** Runs the compare operation: the compare result, or null when there are no differences. */
  protected abstract Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException;

  public Object getCompareResult() {
    return fInput;
  }

  /** Creates the panes that show the compare result and feeds the result into them. */
  public Control createContents(Composite parent) {
    fComposite = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout(1, false);
    gl.marginWidth = gl.marginHeight = 0;
    fComposite.setLayout(gl);
    fComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    fComposite.setData(this);
    if (isStructured(fInput) || isCustomStructureInputPane()) {
      fStructureInputPane = createStructureInputPane(fComposite);
      GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
      gd.heightHint = 200;
      fStructureInputPane.setLayoutData(gd);
      fFocusPane = fStructureInputPane;
      fStructureInputPane.addOpenListener(oe -> openInEditor(getElement(oe.getSelection())));
      fStructureInputPane.addSelectionChangedListener(e -> {
        feed1(e.getSelection());
        if (isEditionSelectionDialog())
          firePropertyChange(new PropertyChangeEvent(this, PROP_SELECTED_EDITION, null, getSelectedEdition()));
      });
    }
    fContentInputPane = createContentViewerSwitchingPane(fComposite, SWT.BORDER | SWT.FLAT, this);
    fContentInputPane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    if (fFocusPane == null)
      fFocusPane = fContentInputPane;
    feedInput();
    fComposite.addDisposeListener(e -> handleDispose());
    contentsCreated();
    return fComposite;
  }

  /** VSCODE: opening an element of the structure pane (double-click) shows its text compare in a VS Code diff editor. */
  private void openInEditor(Object element) {
    if (element instanceof ICompareInput && !hasChildren(element) && CompareEditor.isTextCompare((ICompareInput) element))
      CompareEditor.openDiff(getTitle(), (ICompareInput) element, fCompareConfiguration);
  }

  /** The pane of the structure of the compare result (the tree of differences); subclasses may provide their own. */
  protected CompareViewerPane createStructureInputPane(Composite parent) {
    return new CompareViewerSwitchingPane(parent, SWT.BORDER | SWT.FLAT, true) {
      @Override
      protected Viewer getViewer(Viewer oldViewer, Object input) {
        if (CompareEditorInput.this.hasChildren(input))
          return createDiffViewer(this);
        return input instanceof ICompareInput ? findStructureViewer(oldViewer, (ICompareInput) input, this) : null;
      }
    };
  }

  /** The pane of the content of the selected difference. */
  protected CompareViewerSwitchingPane createContentViewerSwitchingPane(Composite parent, int style,
      CompareEditorInput cei) {
    return new CompareViewerSwitchingPane(parent, style) {
      @Override
      protected Viewer getViewer(Viewer oldViewer, Object input) {
        if (input instanceof ICompareInput)
          return cei.findContentViewer(oldViewer, (ICompareInput) input, this);
        return CompareUI.findContentViewer(oldViewer, input, this, cei.getCompareConfiguration());
      }
    };
  }

  boolean hasChildren(Object input) {
    return input instanceof IDiffContainer && ((IDiffContainer) input).hasChildren();
  }

  /** Whether the input is shown as a structure: it has children, or its type has a structure creator (an archive). */
  private boolean isStructured(Object input) {
    return hasChildren(input) || CompareUI.hasStructureCreator(input);
  }

  /** Whether a subclass creates its own structure input pane (createStructureInputPane is overridden). */
  private boolean isCustomStructureInputPane() {
    for (Class<?> c = getClass(); c != null && c != CompareEditorInput.class; c = c.getSuperclass()) {
      try {
        c.getDeclaredMethod("createStructureInputPane", Composite.class);
        return true;
      } catch (NoSuchMethodException e) {
        // not in this class
      }
    }
    return false;
  }

  private void feedInput() {
    if (fStructureInputPane != null && (fInput instanceof ICompareInput || isCustomStructureInputPane())) {
      fStructureInputPane.setInput(fInput);
      ISelection sel = fStructureInputPane.getSelection();
      feed1(sel);
    } else {
      fContentInputPane.setInput(fInput);
    }
  }

  private void feed1(ISelection selection) {
    if (selection == null || selection.isEmpty()) {
      Object input = fStructureInputPane == null ? null : fStructureInputPane.getInput();
      if (input != null && !isStructured(input) && !isCustomStructureInputPane())
        fContentInputPane.setInput(input);
      else
        fContentInputPane.setInput(null);
    } else {
      fContentInputPane.setInput(getElement(selection));
    }
  }

  private static Object getElement(ISelection selection) {
    if (selection instanceof IStructuredSelection && ((IStructuredSelection) selection).size() == 1)
      return ((IStructuredSelection) selection).getFirstElement();
    return null;
  }

  /** Callback after the controls were created; {@link #handleDispose()} is called when they are disposed. */
  protected void contentsCreated() {
  }

  protected void handleDispose() {
    fComposite = null;
    fStructureInputPane = null;
    fContentInputPane = null;
    fFocusPane = null;
  }

  public boolean setFocus2() {
    if (fFocusPane != null && !fFocusPane.isDisposed())
      return fFocusPane.setFocus();
    return fComposite != null && !fComposite.isDisposed() && fComposite.setFocus();
  }

  public Viewer createDiffViewer(Composite parent) {
    return new DiffTreeViewer(parent, fCompareConfiguration);
  }

  public Viewer findStructureViewer(Viewer oldViewer, ICompareInput input, Composite parent) {
    return CompareUI.findStructureViewer(oldViewer, input, parent, fCompareConfiguration);
  }

  public Viewer findContentViewer(Viewer oldViewer, ICompareInput input, Composite parent) {
    return CompareUI.findContentViewer(oldViewer, input, parent, fCompareConfiguration);
  }

  public boolean isSaveNeeded() {
    return isLeftSaveNeeded() || isRightSaveNeeded();
  }

  protected boolean isLeftSaveNeeded() {
    return fLeftDirty;
  }

  protected boolean isRightSaveNeeded() {
    return fRightDirty;
  }

  public boolean isDirty() {
    return isSaveNeeded();
  }

  public void setDirty(boolean dirty) {
    boolean oldDirty = isSaveNeeded();
    fLeftDirty = dirty;
    fRightDirty = dirty;
    if (oldDirty != isSaveNeeded())
      firePropertyChange(new PropertyChangeEvent(this, DIRTY_STATE, oldDirty, isSaveNeeded()));
  }

  protected void setLeftDirty(boolean dirty) {
    boolean oldDirty = isSaveNeeded();
    fLeftDirty = dirty;
    if (oldDirty != isSaveNeeded())
      firePropertyChange(new PropertyChangeEvent(this, DIRTY_STATE, oldDirty, isSaveNeeded()));
  }

  protected void setRightDirty(boolean dirty) {
    boolean oldDirty = isSaveNeeded();
    fRightDirty = dirty;
    if (oldDirty != isSaveNeeded())
      firePropertyChange(new PropertyChangeEvent(this, DIRTY_STATE, oldDirty, isSaveNeeded()));
  }

  public synchronized void addPropertyChangeListener(IPropertyChangeListener listener) {
    if (!fListenerList.contains(listener))
      fListenerList.add(listener);
  }

  public synchronized void removePropertyChangeListener(IPropertyChangeListener listener) {
    fListenerList.remove(listener);
  }

  protected void firePropertyChange(PropertyChangeEvent event) {
    List<IPropertyChangeListener> ls;
    synchronized (this) {
      ls = new ArrayList<>(fListenerList);
    }
    for (IPropertyChangeListener l : ls)
      l.propertyChange(event);
  }

  /** Writes the changes of the viewers to the inputs; subclasses save them. */
  public void saveChanges(IProgressMonitor monitor) throws CoreException {
    flushViewers(monitor);
  }

  protected void flushViewers(IProgressMonitor monitor) {
    for (CompareViewerPane pane : new CompareViewerPane[] { fStructureInputPane, fContentInputPane })
      if (pane instanceof CompareViewerSwitchingPane
          && ((CompareViewerSwitchingPane) pane).getViewer() instanceof IFlushable)
        ((IFlushable) ((CompareViewerSwitchingPane) pane).getViewer()).flush(monitor);
  }

  public void setStatusMessage(String message) {
    if (fContainer instanceof CompareDialog)
      ((CompareDialog) fContainer).setStatusMessage(message);
  }

  /** The container (the compare dialog) the input is shown in, set when it is opened. */
  public void setContainer(Object container) {
    fContainer = container;
  }

  public final Object getContainer() {
    return fContainer;
  }

  public boolean canRunAsJob() {
    return false;
  }

  public boolean isEditionSelectionDialog() {
    return false;
  }

  public String getOKButtonLabel() {
    if (isEditable())
      return "C&ommit";
    if (isEditionSelectionDialog())
      return "&Select";
    return IDialogConstants.OK_LABEL;
  }

  public String getCancelButtonLabel() {
    return IDialogConstants.CANCEL_LABEL;
  }

  private boolean isEditable() {
    return getCompareConfiguration().isLeftEditable() || getCompareConfiguration().isRightEditable();
  }

  public boolean okPressed() {
    if (isEditable())
      return saveChanges();
    return true;
  }

  public void cancelPressed() {
  }

  private boolean saveChanges() {
    try {
      new ProgressMonitorDialog(fComposite == null ? null : fComposite.getShell()).run(false, false, monitor -> {
        try {
          saveChanges(monitor);
        } catch (CoreException e) {
          throw new InvocationTargetException(e);
        }
      });
      return true;
    } catch (InterruptedException x) {
      // canceled
    } catch (InvocationTargetException x) {
      ErrorDialog.openError(fComposite == null ? null : fComposite.getShell(), "Error Saving Changes", null,
          new Status(IStatus.ERROR, CompareUI.PLUGIN_ID, 0,
              "The changes were not saved: " + x.getTargetException().getMessage(), x.getTargetException()));
    }
    return false;
  }

  /** The edition selected in the structure input pane (edition selection dialogs), or null. */
  public Object getSelectedEdition() {
    if (fStructureInputPane != null) {
      ISelection selection = fStructureInputPane.getSelection();
      if (selection instanceof IStructuredSelection && !selection.isEmpty())
        return ((IStructuredSelection) selection).getFirstElement();
    }
    return null;
  }

  public void setHelpContextId(String helpContextId) {
    fHelpContextId = helpContextId;
  }

  /** The content pane (for subclasses and tests). */
  protected CompareViewerSwitchingPane getContentPane() {
    return fContentInputPane;
  }
}
