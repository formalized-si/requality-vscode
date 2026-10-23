package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.layout.RowLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.workbench.EditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry.IEditorDescriptor;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.dialogs.EditorSelectionDialog}: the user chooses an editor for a file from the internal editors
 * (the text editor, the editors of the plug-ins and of VS Code) or from the external programs (the system editor and
 * the programs chosen with Browse...). The choice can be remembered for the file name or for its file type.
 */
public class EditorSelectionDialog extends Dialog {

  /** {@code EditorSelectionDialog.TreeArrayContentProvider}: the elements of an array, no children. */
  private static class TreeArrayContentProvider implements ITreeContentProvider {

    private static final Object[] EMPTY = new Object[0];

    @Override
    public Object[] getElements(Object inputElement) {
      if (!(inputElement instanceof Object[] orig))
        return EMPTY;
      // see bug 9262 why we can't return the same array
      return Arrays.copyOf(orig, orig.length);
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return EMPTY;
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return false;
    }
  }

  private IEditorDescriptor selectedEditor;

  private IEditorDescriptor hiddenSelectedEditor;

  private Button externalButton;

  private FilteredTree editorTable;

  private Button browseExternalEditorsButton;

  private Button internalButton;

  private Button okButton;

  /**
   * For internal use only.
   *
   * @noreference This field is not intended to be referenced by clients.
   */
  protected static final String STORE_ID_INTERNAL_EXTERNAL = "EditorSelectionDialog.STORE_ID_INTERNAL_EXTERNAL";

  private static final String STORE_ID_DESCR = "EditorSelectionDialog.STORE_ID_DESCR";

  private static final String STORE_ID_FILE_EXTENSION = "EditorSelectionDialog.STORE_ID_FILE_EXTENSION";

  private String message = "Choose an editor:";

  // collection of IEditorDescriptor
  private IEditorDescriptor[] externalEditors;

  private IEditorDescriptor[] internalEditors;

  private IEditorDescriptor[] editorsToFilter;

  private final DialogListener listener = new DialogListener();

  private TreeViewer editorTableViewer;

  private String fileName;

  private Button rememberTypeButton;

  private Button rememberEditorButton;

  private static final String[] Executable_Filters;

  private static final int TABLE_WIDTH = 200;

  static {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      Executable_Filters = new String[] { "*.exe", "*.bat", "*.*" };
    } else if (os.startsWith("mac")) {
      Executable_Filters = new String[] { "*.app", "*" };
    } else {
      Executable_Filters = new String[] { "*" };
    }
  }

  /**
   * Create an instance of this class.
   *
   * @param parentShell the parent shell
   */
  public EditorSelectionDialog(Shell parentShell) {
    super(parentShell);
  }

  /** This method is called if a button has been pressed. */
  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == IDialogConstants.OK_ID) {
      saveWidgetValues();
    }
    super.buttonPressed(buttonId);
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Editor Selection");
    // VSCODE: no context help of the workbench help system (Eclipse IWorkbenchHelpContextIds.EDITOR_SELECTION_DIALOG)
  }

  /**
   * Creates and returns the contents of the upper part of the dialog (above the button bar).
   *
   * @param parent the parent composite to contain the dialog area
   * @return the dialog area control
   */
  @Override
  protected Control createDialogArea(Composite parent) {
    Font font = parent.getFont();
    // create main group
    Composite contents = (Composite) super.createDialogArea(parent);
    ((GridLayout) contents.getLayout()).numColumns = 2;

    // begin the layout
    Label textLabel = new Label(contents, SWT.WRAP);

    textLabel.setText(message);
    GridData data = new GridData();
    data.horizontalSpan = 2;
    data.horizontalAlignment = SWT.FILL;
    data.widthHint = TABLE_WIDTH;
    textLabel.setLayoutData(data);
    textLabel.setFont(font);

    Composite group = new Composite(contents, SWT.SHADOW_NONE);
    data = new GridData();
    data.grabExcessHorizontalSpace = true;
    data.horizontalAlignment = SWT.FILL;
    data.horizontalSpan = 2;
    group.setLayout(new RowLayout(SWT.HORIZONTAL));
    group.setLayoutData(data);

    internalButton = new Button(group, SWT.RADIO | SWT.LEFT);
    internalButton.setText("&Internal editors");
    internalButton.addListener(SWT.Selection, listener);
    internalButton.setFont(font);

    externalButton = new Button(group, SWT.RADIO | SWT.LEFT);
    externalButton.setText("&External programs");
    externalButton.addListener(SWT.Selection, listener);
    externalButton.setFont(font);

    editorTable = new FilteredTree(contents, SWT.SINGLE | SWT.BORDER, new PatternFilter(), true);
    editorTableViewer = editorTable.getViewer();
    Tree tree = editorTableViewer.getTree();
    tree.addListener(SWT.Selection, listener);
    tree.addListener(SWT.DefaultSelection, listener);
    data = new GridData();
    data.widthHint = convertHorizontalDLUsToPixels(TABLE_WIDTH);
    data.horizontalAlignment = GridData.FILL;
    data.grabExcessHorizontalSpace = true;
    data.verticalAlignment = GridData.FILL;
    data.grabExcessVerticalSpace = true;
    data.horizontalSpan = 2;
    editorTable.setLayoutData(data);
    editorTable.setFont(font);
    data.heightHint = tree.getItemHeight() * 12;
    editorTableViewer.setContentProvider(new TreeArrayContentProvider());
    // VSCODE: the editors have no images (the icons of the programs are not known to the server)
    editorTableViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        IEditorDescriptor d = (IEditorDescriptor) element;
        return d.getLabel().replace("&", "");
      }
    });

    browseExternalEditorsButton = new Button(contents, SWT.PUSH);
    browseExternalEditorsButton.setText("&Browse...");
    browseExternalEditorsButton.addListener(SWT.Selection, listener);
    data = new GridData();
    int widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
    data.widthHint = Math.max(widthHint, browseExternalEditorsButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
    data.horizontalSpan = 2;
    browseExternalEditorsButton.setLayoutData(data);
    browseExternalEditorsButton.setFont(font);

    if (fileName != null) {

      rememberEditorButton = new Button(contents, SWT.CHECK | SWT.LEFT);
      rememberEditorButton.setText("&Use this editor for all '" + fileName + "' files");
      rememberEditorButton.addListener(SWT.Selection, listener);
      data = new GridData();
      data.horizontalSpan = 2;
      rememberEditorButton.setLayoutData(data);
      rememberEditorButton.setFont(font);

      String fileType = getFileType();
      if (!fileType.isEmpty()) {
        rememberTypeButton = new Button(contents, SWT.CHECK | SWT.LEFT);
        rememberTypeButton.setText("Use it for &all '*." + fileType + "' files");
        rememberTypeButton.addListener(SWT.Selection, listener);
        data = new GridData();
        data.horizontalSpan = 2;
        rememberTypeButton.setLayoutData(data);
        rememberTypeButton.setFont(font);
      }
    }

    initializeSuggestion();
    restoreWidgetValues(); // Place buttons to the appropriate state

    // Run async to restore selection on *visible* dialog - otherwise three won't scroll
    PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
      if (editorTable.isDisposed()) {
        return;
      }
      fillEditorTable();
      updateEnableState();
    });
    return contents;
  }

  private String getFileType() {
    if (fileName == null) {
      return "";
    }
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot == -1 || lastDot >= fileName.length() - 1) {
      return "";
    }
    return fileName.substring(lastDot + 1, fileName.length());
  }

  /** Fills the editor table. */
  protected void fillEditorTable() {
    IEditorDescriptor newSelection = selectedEditor;

    boolean showInternal = internalButton.getSelection();
    Object[] input = (Object[]) editorTableViewer.getInput();
    if (input != null) {
      // we are switching between external/internal editors
      boolean isShowingInternal = Arrays.equals(input, getInternalEditors());
      if (showInternal != isShowingInternal) {
        newSelection = hiddenSelectedEditor;
        if (!editorTableViewer.getSelection().isEmpty()) {
          hiddenSelectedEditor = (IEditorDescriptor) editorTableViewer.getStructuredSelection().getFirstElement();
        }
      }
    }

    editorTableViewer.setInput(showInternal ? getInternalEditors() : getExternalEditors());

    if (fileName != null && newSelection == null) {
      if (!showInternal) {
        newSelection = findBestExternalEditor();
      } else {
        newSelection = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(fileName);
      }
    }
    if (newSelection != null) {
      editorTableViewer.setSelection(new StructuredSelection(newSelection), true);
    }

    if (editorTableViewer.getSelection().isEmpty()) {
      // set focus to first element, but don't select it:
      Tree tree = editorTableViewer.getTree();
      if (tree.getItemCount() > 0) {
        tree.showItem(tree.getItem(0));
      }
    }
    editorTable.setFocus();
  }

  private static String getFileExtension(String fileName) {
    if (fileName == null) {
      return null;
    }
    int index = fileName.lastIndexOf('.');
    if (index != -1) {
      return fileName.substring(index);
    }
    return fileName;
  }

  /** Return the dialog store to cache values into. */
  protected IDialogSettings getDialogSettings() {
    IDialogSettings workbenchSettings = Activator.getDefault().getDialogSettings();
    IDialogSettings section = workbenchSettings.getSection("EditorSelectionDialog");
    if (section == null) {
      section = workbenchSettings.addNewSection("EditorSelectionDialog");
    }
    return section;
  }

  /**
   * The external editors: the system editor, and the programs chosen with Browse... in this dialog.
   * VSCODE: the programs registered with the operating system are not listed; the system editor stands for them.
   */
  protected IEditorDescriptor[] getExternalEditors() {
    if (externalEditors == null) {
      List<IEditorDescriptor> system = new ArrayList<>();
      IEditorDescriptor editor = PlatformUI.getWorkbench().getEditorRegistry()
          .findEditor(IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID);
      if (editor != null) {
        system.add(editor);
      }
      externalEditors = filterEditors(system.toArray(new IEditorDescriptor[0]));
    }
    return externalEditors;
  }

  /**
   * Returns an array of editors which have been filtered according to the array of editors in the editorsToFilter
   * instance variable.
   *
   * @param editors an array of editors to filter
   * @return a filtered array of editors
   */
  protected IEditorDescriptor[] filterEditors(IEditorDescriptor[] editors) {
    if ((editors == null) || (editors.length < 1)) {
      return editors;
    }

    if ((editorsToFilter == null) || (editorsToFilter.length < 1)) {
      return editors;
    }

    List<IEditorDescriptor> filteredList = new ArrayList<>();
    for (IEditorDescriptor editor : editors) {
      boolean add = true;
      for (IEditorDescriptor element : editorsToFilter) {
        if (editor.getId().equals(element.getId())) {
          add = false;
        }
      }
      if (add) {
        filteredList.add(editor);
      }
    }

    return filteredList.toArray(new IEditorDescriptor[filteredList.size()]);
  }

  /** Returns the internal editors: the editors of the plug-ins and of VS Code, sorted by label. */
  protected IEditorDescriptor[] getInternalEditors() {
    if (internalEditors == null) {
      List<IEditorDescriptor> internal = new ArrayList<>();
      for (IEditorDescriptor editor : PlatformUI.getWorkbench().getEditorRegistry().getSortedEditorsFromPlugins()) {
        if (!editor.isOpenExternal() && !IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID.equals(editor.getId())) {
          internal.add(editor);
        }
      }
      IEditorDescriptor[] editors = internal.toArray(new IEditorDescriptor[0]);
      Arrays.sort(editors, Comparator.comparing(IEditorDescriptor::getLabel, Collator.getInstance()));
      internalEditors = filterEditors(editors);
    }
    return internalEditors;
  }

  /**
   * Return the editor the user selected.
   *
   * @return the selected editor
   */
  public IEditorDescriptor getSelectedEditor() {
    return selectedEditor;
  }

  /** Opens the prompt to select an external editor. */
  protected void promptForExternalEditor() {
    // VSCODE: the dialog of the client (a native open dialog), not a sheet
    FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
    dialog.setFilterExtensions(Executable_Filters);
    String result = dialog.open();
    if (result != null) {
      IEditorDescriptor editor = EditorRegistry.createForProgram(result);

      /*
       * add to our collection of cached external editors in case the user flips back and forth between
       * internal/external
       */
      IEditorDescriptor[] newEditors = Arrays.copyOf(externalEditors, externalEditors.length + 1);
      newEditors[newEditors.length - 1] = editor;
      externalEditors = newEditors;
      editorTableViewer.setInput(externalEditors);
      editorTableViewer.setSelection(new StructuredSelection(editor), true);
      editorTable.setFocus();
      selectedEditor = editor;
    }
  }

  /** Handle a double click event on the list. */
  protected void handleDoubleClickEvent() {
    buttonPressed(IDialogConstants.OK_ID);
  }

  private void initializeSuggestion() {
    if (fileName == null) {
      return;
    }
    IEditorRegistry editorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
    IEditorDescriptor suggestion = editorRegistry.getDefaultEditor(fileName);
    if (suggestion != null && !suggestion.isOpenExternal()) {
      selectedEditor = suggestion;
    } else {
      selectedEditor = findBestExternalEditor();
    }
    boolean enableInternalList = selectedEditor == null || !selectedEditor.isOpenExternal();
    internalButton.setSelection(enableInternalList);
    externalButton.setSelection(!enableInternalList);
  }

  /**
   * The external program the operating system associates with the file ({@code Program.findProgram}).
   * VSCODE: the server does not know the programs of the operating system, so there is no suggestion.
   */
  private IEditorDescriptor findBestExternalEditor() {
    return null;
  }

  /**
   * Use the dialog store to restore widget values to the values that they held last time this wizard was used to
   * completion, if the previous file has same extension.
   */
  protected void restoreWidgetValues() {
    IDialogSettings settings = getDialogSettings();
    if (fileName == null || selectedEditor == null
        || getFileExtension(fileName).equals(settings.get(STORE_ID_FILE_EXTENSION))) {
      boolean wasExternal = settings.getBoolean(STORE_ID_INTERNAL_EXTERNAL);
      internalButton.setSelection(!wasExternal);
      externalButton.setSelection(wasExternal);
      String id = settings.get(STORE_ID_DESCR);
      if (id != null) {
        IEditorDescriptor[] editors;
        if (wasExternal) {
          editors = getExternalEditors();
        } else {
          editors = getInternalEditors();
        }
        for (IEditorDescriptor desc : editors) {
          if (id.equals(desc.getId())) {
            selectedEditor = desc;
          }
        }
      }
    }
  }

  /**
   * Since Finish was pressed, write widget values to the dialog store so that they will persist into the next
   * invocation of this wizard page.
   */
  protected void saveWidgetValues() {
    IDialogSettings settings = getDialogSettings();
    // record whether use was viewing internal or external editors
    settings.put(STORE_ID_FILE_EXTENSION, getFileExtension(fileName));
    settings.put(STORE_ID_INTERNAL_EXTERNAL, !internalButton.getSelection());
    String editorId = selectedEditor.getId();
    settings.put(STORE_ID_DESCR, editorId);
    // remember editor for specific file
    if (rememberEditorButton != null && rememberEditorButton.getSelection()) {
      EditorRegistry.setDefaultEditor(fileName, editorId);
    }
    // remember editor for given extension type
    if (rememberTypeButton != null && rememberTypeButton.getSelection()) {
      EditorRegistry.setDefaultEditor("*." + getFileType(), editorId);
    }
  }

  /**
   * Set the message displayed by this message dialog.
   *
   * @param aMessage the message
   */
  public void setMessage(String aMessage) {
    message = aMessage;
  }

  /**
   * Set the file name which can be used to store the selected editor preference.
   *
   * @param fileName the file name
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Set the editors which will not appear in the dialog.
   *
   * @param editors an array of editors
   */
  public void setEditorsToFilter(IEditorDescriptor[] editors) {
    editorsToFilter = editors;
  }

  /** Update enabled state. */
  protected void updateEnableState() {
    boolean enableExternal = externalButton.getSelection();
    browseExternalEditorsButton.setEnabled(enableExternal);
    updateOkButton();
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    okButton = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    // initially there is no selection so OK button should not be enabled
    okButton.setEnabled(false);
  }

  /** Update the button enablement state. */
  protected void updateOkButton() {
    // Buttons are null during dialog creation
    if (okButton == null) {
      return;
    }
    // If there is no selection, do not enable OK button
    if (editorTableViewer.getSelection().isEmpty()) {
      okButton.setEnabled(false);
      return;
    }
    // At this point, there is a selection
    okButton.setEnabled(selectedEditor != null);
  }

  private class DialogListener implements Listener {

    @Override
    public void handleEvent(Event event) {
      if (event.type == SWT.DefaultSelection) {
        handleDoubleClickEvent();
        return;
      }
      // VSCODE: the client reports the radio button that becomes selected, so the table follows either radio button
      if (event.widget == externalButton || event.widget == internalButton) {
        fillEditorTable();
      } else if (event.widget == browseExternalEditorsButton) {
        promptForExternalEditor();
      } else if (event.widget == editorTableViewer.getTree()) {
        if (!editorTableViewer.getSelection().isEmpty()) {
          selectedEditor = (IEditorDescriptor) editorTableViewer.getStructuredSelection().getFirstElement();
        } else {
          selectedEditor = null;
          okButton.setEnabled(false);
        }
      }
      // 486859 both checked: checking one box unchecks the other
      if (rememberEditorButton != null && rememberTypeButton != null && rememberEditorButton.getSelection()
          && rememberTypeButton.getSelection()) {
        if (event.widget == rememberEditorButton) {
          rememberTypeButton.setSelection(false);
        }
        if (event.widget == rememberTypeButton) {
          rememberEditorButton.setSelection(false);
        }
      }
      updateEnableState();
    }

  }

  @Override
  protected boolean isResizable() {
    return true;
  }
}
