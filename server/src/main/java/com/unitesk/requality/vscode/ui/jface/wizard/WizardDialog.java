package com.unitesk.requality.vscode.ui.jface.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.TitleAreaDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.jface.wizard.WizardDialog}: Back/Next/Finish/Cancel around the current page, and the progress
 * monitor part below the pages that shows the operations run by the wizard.
 *
 * <p>
 * VSCODE: the operations run in a thread (fork) while the display thread keeps dispatching events, as
 * {@code ModalContext} does; the progress updates of the thread are shown on the display thread by the
 * {@link ProgressMonitorPart}. Without fork the operation runs on the display thread.
 * </p>
 */
public class WizardDialog extends TitleAreaDialog implements IWizardContainer {
  private static final String FOCUS_CONTROL = "focusControl";
  private static boolean dialogHelpAvailable = true;

  /** The current wizard: the wizard of the current page (a nested wizard shows its pages after a page of its parent). */
  private IWizard wizard;
  /** {@code WizardDialog.createdWizards}: every wizard this dialog has shown, disposed when the dialog closes. */
  private final List<IWizard> createdWizards = new ArrayList<>();
  /** {@code WizardDialog.nestedWizards}: the chain of nested wizards; the current wizard is the last one. */
  private final List<IWizard> nestedWizards = new ArrayList<>();
  private IWizardPage current;
  private Composite pageContainer;
  private ProgressMonitorPart progressMonitorPart;
  /** the number of operations running in the wizard, the dialog cannot be closed meanwhile */
  private int activeRunningOperations = 0;

  public WizardDialog(Shell parentShell, IWizard newWizard) {
    super(parentShell);
    setWizard(newWizard);
  }

  /**
   * {@code WizardDialog.setWizard}: makes the wizard the current one. A new wizard (a nested wizard whose page is shown)
   * gets this dialog as its container and its page controls, and joins the nested chain; going back to the previous
   * wizard of the chain removes the last one.
   */
  protected void setWizard(IWizard newWizard) {
    wizard = newWizard;
    wizard.setContainer(this);
    if (!createdWizards.contains(wizard)) {
      createdWizards.add(wizard);
      nestedWizards.add(wizard);
      if (pageContainer != null) {
        wizard.createPageControls(pageContainer);
        for (IWizardPage p : wizard.getPages())
          if (p.getControl() != null)
            hide(p);
        pageContainer.layout(true);
      }
    } else {
      int size = nestedWizards.size();
      if (size >= 2 && nestedWizards.get(size - 2) == wizard)
        nestedWizards.remove(size - 1);
      else
        nestedWizards.add(wizard);
    }
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText(wizard.getWindowTitle() == null ? "" : wizard.getWindowTitle());
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite) super.createDialogArea(parent);
    pageContainer = new Composite(area, SWT.NONE);
    GridLayout gl = new GridLayout();
    gl.marginWidth = 10;
    gl.marginHeight = 10;
    pageContainer.setLayout(gl);
    pageContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
    // Insert a progress monitor
    progressMonitorPart = createProgressMonitorPart(area, new GridLayout());
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    if (!wizard.needsProgressMonitor()) {
      gridData.exclude = true;
    }
    progressMonitorPart.setLayoutData(gridData);
    progressMonitorPart.setVisible(false);
    // Build the separator line
    Label separator = new Label(area, SWT.HORIZONTAL | SWT.SEPARATOR);
    separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    wizard.addPages();
    wizard.createPageControls(pageContainer);
    for (IWizardPage p : wizard.getPages())
      hide(p);
    IWizardPage start = wizard.getStartingPage();
    if (start != null)
      showPage(start);
    return area;
  }

  /**
   * Hook method for subclasses to create a custom progress monitor part. The default part has a stop button that cancels
   * the running operation.
   *
   * @param composite the parent composite
   * @param pmlayout the layout
   * @return the progress monitor part
   */
  protected ProgressMonitorPart createProgressMonitorPart(Composite composite, GridLayout pmlayout) {
    return new ProgressMonitorPart(composite, pmlayout, true);
  }

  private void hide(IWizardPage p) {
    Control c = p.getControl();
    if (c == null)
      return;
    Object ld = c.getLayoutData();
    if (!(ld instanceof GridData)) {
      ld = new GridData(GridData.FILL_BOTH);
      c.setLayoutData(ld);
    }
    ((GridData) ld).exclude = true;
    p.setVisible(false);
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    if (wizard.needsPreviousAndNextButtons()) {
      createButton(parent, IDialogConstants.BACK_ID, IDialogConstants.BACK_LABEL, false);
      createButton(parent, IDialogConstants.NEXT_ID, IDialogConstants.NEXT_LABEL, false);
    }
    createButton(parent, IDialogConstants.FINISH_ID, IDialogConstants.FINISH_LABEL, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    updateButtons();
  }

  @Override
  protected void buttonPressed(int id) {
    switch (id) {
      case IDialogConstants.BACK_ID:
        backPressed();
        break;
      case IDialogConstants.NEXT_ID:
        nextPressed();
        break;
      case IDialogConstants.FINISH_ID:
        finishPressed();
        break;
      case IDialogConstants.CANCEL_ID:
        cancelPressed();
        break;
      default:
        break;
    }
  }

  protected void backPressed() {
    IWizardPage p = current.getPreviousPage();
    if (p != null)
      showPage(p, false);
  }

  protected void nextPressed() {
    IWizardPage p = current.getNextPage();
    if (p != null)
      showPage(p);
  }

  /**
   * {@code WizardDialog.finishPressed}: the current wizard finishes first (it may refuse), then the outer wizards of the
   * nested chain (to save their state).
   */
  protected void finishPressed() {
    if (wizard.performFinish()) {
      for (int i = 0; i < nestedWizards.size() - 1; i++)
        nestedWizards.get(i).performFinish();
      setReturnCode(OK);
      hardClose();
    }
  }

  /**
   * The Cancel button closes the dialog. While an operation runs it is disabled: the stop button of the progress monitor
   * part cancels the operation.
   */
  @Override
  protected void cancelPressed() {
    if (activeRunningOperations <= 0) {
      setReturnCode(CANCEL);
      close();
    } else {
      Button cancel = getButton(IDialogConstants.CANCEL_ID);
      if (cancel != null)
        cancel.setEnabled(false);
    }
  }

  @Override
  public IWizardPage getCurrentPage() {
    return current;
  }

  @Override
  public void showPage(IWizardPage page) {
    showPage(page, true);
  }

  private void showPage(IWizardPage page, boolean forward) {
    if (page == null || page == current)
      return;
    if (forward && current != null)
      page.setPreviousPage(current);
    if (page.getWizard() == null)
      page.setWizard(wizard);
    // the page belongs to the current wizard (WizardDialog.updateForPage)
    if (wizard != page.getWizard())
      setWizard(page.getWizard());
    if (page.getControl() == null)
      page.createControl(pageContainer);
    IWizardPage old = current;
    current = page;
    if (old != null)
      hide(old);
    Control c = page.getControl();
    Object ld = c.getLayoutData();
    if (ld instanceof GridData)
      ((GridData) ld).exclude = false;
    else
      c.setLayoutData(new GridData(GridData.FILL_BOTH));
    page.setVisible(true);
    pageContainer.layout();
    updateWindowTitle();
    updateTitleBar();
    updateMessage();
    updateButtons();
  }

  @Override
  public void updateButtons() {
    if (current == null)
      return;
    Button back = getButton(IDialogConstants.BACK_ID);
    Button next = getButton(IDialogConstants.NEXT_ID);
    Button finish = getButton(IDialogConstants.FINISH_ID);
    if (back != null)
      back.setEnabled(current.getPreviousPage() != null);
    if (next != null)
      next.setEnabled(current.canFlipToNextPage());
    if (finish != null)
      finish.setEnabled(wizard.canFinish());
    Shell s = getShell();
    if (s != null) {
      if (next != null && current.canFlipToNextPage() && !wizard.canFinish())
        s.setDefaultButton(next);
      else if (finish != null)
        s.setDefaultButton(finish);
    }
  }

  @Override
  public void updateMessage() {
    if (current == null)
      return;
    setErrorMessage(current.getErrorMessage());
    String m = current.getMessage();
    if (m == null)
      setMessage(current.getDescription(), IMessageProvider.NONE);
    else
      setMessage(m, current.getMessageType());
  }

  @Override
  public void updateTitleBar() {
    if (current != null)
      setTitle(current.getTitle());
    updateMessage();
  }

  @Override
  public void updateWindowTitle() {
    if (getShell() != null)
      getShell().setText(wizard.getWindowTitle() == null ? "" : wizard.getWindowTitle());
  }

  /**
   * Runs the operation with the progress part of the dialog as its monitor. The Back, Next, Finish and the page controls
   * are disabled meanwhile, and the progress part is shown with its stop button if the operation is cancelable.
   */
  @Override
  public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
      throws InvocationTargetException, InterruptedException {
    Display display = Display.getDefault();
    if (!display.isUIThread()) {
      // VSCODE: the dialog is shown and updated by the display thread: the operation is run there
      Object[] failure = new Object[1];
      display.syncExec(() -> {
        try {
          run(fork, cancelable, runnable);
        } catch (InvocationTargetException | InterruptedException | RuntimeException | Error e) {
          failure[0] = e;
        }
      });
      if (failure[0] instanceof InvocationTargetException)
        throw (InvocationTargetException) failure[0];
      if (failure[0] instanceof InterruptedException)
        throw (InterruptedException) failure[0];
      if (failure[0] instanceof RuntimeException)
        throw (RuntimeException) failure[0];
      if (failure[0] instanceof Error)
        throw (Error) failure[0];
      return;
    }
    Map<String, Object> state = null;
    if (activeRunningOperations++ == 0)
      state = aboutToStart(fork && cancelable);
    IProgressMonitor progressMonitor = getProgressMonitor();
    if (progressMonitor == null)
      progressMonitor = new NullProgressMonitor();
    try {
      if (fork)
        runForked(runnable, progressMonitor);
      else
        runnable.run(progressMonitor);
    } finally {
      // explicitly invoke done() on our progress monitor so that its label does not spill over to the next invocation
      progressMonitor.done();
      // Stop if this is the last one
      if (state != null)
        stopped(state);
      activeRunningOperations--;
    }
  }

  /**
   * Runs the operation in a thread while the display thread dispatches events until it is done (the nested event loop of
   * {@code ModalContext}). The updates the thread posted to the display are processed before the end of the run.
   */
  private void runForked(IRunnableWithProgress runnable, IProgressMonitor progressMonitor)
      throws InvocationTargetException, InterruptedException {
    Display display = Display.getDefault();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean finished = new AtomicBoolean();
    Thread thread = new Thread(() -> {
      try {
        runnable.run(progressMonitor);
      } catch (Throwable e) {
        failure.set(e);
      } finally {
        display.asyncExec(() -> finished.set(true));
      }
    }, "requality-wizard-operation");
    thread.setDaemon(true);
    thread.start();
    display.runUntil(finished::get);
    Throwable e = failure.get();
    if (e instanceof InvocationTargetException)
      throw (InvocationTargetException) e;
    if (e instanceof InterruptedException)
      throw (InterruptedException) e;
    if (e != null)
      throw new InvocationTargetException(e);
  }

  /**
   * Saves the UI state and shows the progress part before a long running operation.
   *
   * @param enableCancelButton {@code true} if the operation can be canceled
   * @return the saved UI state
   */
  private Map<String, Object> aboutToStart(boolean enableCancelButton) {
    Map<String, Object> savedState = null;
    if (getShell() != null) {
      // Save focus control
      Control focusControl = getShell().getDisplay().getFocusControl();
      if (focusControl != null && focusControl.getShell() != getShell())
        focusControl = null;
      boolean needsProgressMonitor = wizard.needsProgressMonitor();

      // Deactivate shell. VSCODE: the default progress monitor part has a stop button, which is the cancel of the
      // operation, so the Cancel button stays disabled (as Eclipse).
      savedState = saveUIState(false);
      if (focusControl != null)
        savedState.put(FOCUS_CONTROL, focusControl);
      // Activate cancel behavior.
      if (needsProgressMonitor && progressMonitorPart != null) {
        if (enableCancelButton)
          progressMonitorPart.attachToCancelComponent(getButton(IDialogConstants.CANCEL_ID));
        progressMonitorPart.setVisible(true);
      }
    }
    return savedState;
  }

  /**
   * A long running operation was stopped, by the user or by its end: hides the progress monitor and restores the enable
   * state of the buttons and of the page.
   */
  private void stopped(Map<String, Object> savedState) {
    if (getShell() != null && !getShell().isDisposed()) {
      if (wizard.needsProgressMonitor() && progressMonitorPart != null) {
        progressMonitorPart.setVisible(false);
        progressMonitorPart.removeFromCancelComponent(getButton(IDialogConstants.CANCEL_ID));
      }
      restoreUIState(savedState);
      Control focusControl = (Control) savedState.get(FOCUS_CONTROL);
      if (focusControl != null && !focusControl.isDisposed())
        focusControl.setFocus();
    }
  }

  /** Saves the enable state of the buttons and of the current page, and disables them. */
  private Map<String, Object> saveUIState(boolean keepCancelEnabled) {
    Map<String, Object> savedState = new HashMap<>(10);
    saveEnableStateAndSet(getButton(IDialogConstants.BACK_ID), savedState, "back", false);
    saveEnableStateAndSet(getButton(IDialogConstants.NEXT_ID), savedState, "next", false);
    saveEnableStateAndSet(getButton(IDialogConstants.FINISH_ID), savedState, "finish", false);
    saveEnableStateAndSet(getButton(IDialogConstants.CANCEL_ID), savedState, "cancel", keepCancelEnabled);
    if (current != null && current.getControl() != null)
      savedState.put("page", ControlEnableState.disable(current.getControl()));
    return savedState;
  }

  /** Restores the enable state of the buttons and of the page. */
  private void restoreUIState(Map<String, Object> saveState) {
    restoreEnableState(getButton(IDialogConstants.BACK_ID), saveState, "back");
    restoreEnableState(getButton(IDialogConstants.NEXT_ID), saveState, "next");
    restoreEnableState(getButton(IDialogConstants.FINISH_ID), saveState, "finish");
    restoreEnableState(getButton(IDialogConstants.CANCEL_ID), saveState, "cancel");
    Object pageValue = saveState.get("page");
    if (pageValue != null)
      ((ControlEnableState) pageValue).restore();
  }

  private static void saveEnableStateAndSet(Control control, Map<String, Object> saveState, String key,
      boolean enabled) {
    if (control != null) {
      saveState.put(key, control.getEnabled());
      control.setEnabled(enabled);
    }
  }

  private static void restoreEnableState(Control control, Map<String, Object> saveState, String key) {
    Boolean enabled = (Boolean) saveState.get(key);
    if (control != null && enabled != null)
      control.setEnabled(enabled);
  }

  /** {@code ControlEnableState}: the enable state of a control tree, saved to be restored. */
  private static final class ControlEnableState {
    private final Map<Control, Boolean> states = new LinkedHashMap<>();

    static ControlEnableState disable(Control control) {
      ControlEnableState s = new ControlEnableState();
      s.save(control);
      return s;
    }

    private void save(Control control) {
      states.put(control, control.getEnabled());
      control.setEnabled(false);
      if (control instanceof Composite)
        for (Control child : ((Composite) control).getChildren())
          save(child);
    }

    void restore() {
      for (Map.Entry<Control, Boolean> e : states.entrySet())
        if (!e.getKey().isDisposed())
          e.getKey().setEnabled(e.getValue());
    }
  }

  /**
   * The progress monitor of the dialog: the progress part below the pages, or {@code null} if there is none.
   */
  protected IProgressMonitor getProgressMonitor() {
    return progressMonitorPart;
  }

  public IWizard getWizard() {
    return wizard;
  }

  /** {@code org.eclipse.jface.wizard.WizardDialog.setDialogHelpAvailable(boolean)}: static flag, help is not rendered. */
  public static void setDialogHelpAvailable(boolean b) {
    dialogHelpAvailable = b;
  }

  /** {@code org.eclipse.jface.wizard.WizardDialog.isDialogHelpAvailable()}. */
  public static boolean isDialogHelpAvailable() {
    return dialogHelpAvailable;
  }

  /** Closes the dialog unless an operation runs (then the user is told to cancel it) or the wizard cancel refuses. */
  @Override
  public boolean close() {
    if (okToClose())
      return hardClose();
    return false;
  }

  private boolean okToClose() {
    if (activeRunningOperations > 0) {
      MessageDialog closing = new MessageDialog(getShell(), "Wizard Closing", null,
          "Wizard can not be closed due to an active operation. You must cancel the operation before you can close the wizard.",
          MessageDialog.QUESTION, new String[] { IDialogConstants.OK_LABEL }, 0);
      closing.open();
      return false;
    }
    return wizard.performCancel();
  }

  private boolean hardClose() {
    // WizardDialog.hardClose: every wizard shown is disposed, then loses this dialog as its container
    for (IWizard createdWizard : createdWizards) {
      createdWizard.dispose();
      createdWizard.setContainer(null);
    }
    return super.close();
  }
}
