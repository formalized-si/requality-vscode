package com.unitesk.requality.vscode.ui.ide.wizards;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.unitesk.requality.vscode.ui.ide.dialogs.FileSystemElement;
import com.unitesk.requality.vscode.ui.ide.dialogs.IElementFilter;
import com.unitesk.requality.vscode.ui.jface.dialogs.ListSelectionDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.DirectoryDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchContentProvider;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardFileSystemResourceImportPage1}: imports files from a
 * directory of the local file system. The page has the source directory, the folder/file selection with the types
 * filter, the destination container and the options.
 */
public abstract class WizardFileSystemResourceImportPage1 extends WizardResourceImportPage implements Listener {
  protected static final int SIZING_TEXT_FIELD_WIDTH = 250;
  protected static final String STORE_SOURCE_NAMES_ID = "WizardFileSystemResourceImportPage1.STORE_SOURCE_NAMES_ID";
  protected static final String STORE_SELECTED_TYPES_ID = "WizardFileSystemResourceImportPage1.STORE_SELECTED_TYPES_ID";

  protected Combo sourceNameField;
  protected Button sourceBrowseButton;
  protected Button selectTypesButton;
  protected Button selectAllButton;
  protected Button deselectAllButton;
  /** The file extensions to import; null means all extensions. */
  protected Set selectedTypes = new HashSet();
  protected FileSystemSelectionGroup selectionGroup;
  private boolean entryChanged;

  public WizardFileSystemResourceImportPage1(String name, IWorkbench aWorkbench, IStructuredSelection selection) {
    super(name, aWorkbench, selection);
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    setControl(composite);

    createSourceGroup(composite);
    createContainerGroup(composite);
    createOptionsGroup(composite);

    restoreWidgetValues();
    updateWidgetEnablements();
    setErrorMessage(null);
    setMessage(null);
  }

  /** Creates the source directory, the file selection and the selection buttons. */
  protected void createSourceGroup(Composite parent) {
    createSourceDirectoryGroup(parent);
    createFileSelectionGroup(parent);
    createButtonsGroup(parent);
  }

  private void createSourceDirectoryGroup(Composite parent) {
    Composite sourceContainerGroup = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    sourceContainerGroup.setLayout(layout);
    sourceContainerGroup.setFont(parent.getFont());
    sourceContainerGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

    Label groupLabel = new Label(sourceContainerGroup, SWT.NONE);
    groupLabel.setText("From directory:");
    groupLabel.setFont(parent.getFont());

    sourceNameField = new Combo(sourceContainerGroup, SWT.BORDER);
    GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
    data.widthHint = SIZING_TEXT_FIELD_WIDTH;
    sourceNameField.setLayoutData(data);
    sourceNameField.setFont(parent.getFont());

    sourceNameField.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateFromSourceField();
      }
    });
    sourceNameField.addKeyListener(new KeyListener() {
      private String sourceValue;

      @Override
      public void keyPressed(KeyEvent e) {
        sourceValue = sourceNameField.getText();
        setPageComplete(false);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (!sourceNameField.getText().equals(sourceValue))
          entryChanged = true;
        if (e.keyCode == '\r') {
          File sourcepath = new File(sourceNameField.getText());
          if (sourcepath.exists() && sourcepath.isDirectory())
            updateFromSourceField();
        }
      }
    });
    sourceNameField.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (entryChanged) {
          entryChanged = false;
          updateFromSourceField();
        }
        updatePageCompletion();
      }
    });

    sourceBrowseButton = new Button(sourceContainerGroup, SWT.PUSH);
    sourceBrowseButton.setText("Browse...");
    sourceBrowseButton.addListener(SWT.Selection, this);
    sourceBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    sourceBrowseButton.setFont(parent.getFont());
    setButtonLayoutData(sourceBrowseButton);
  }

  /** Creates the two-pane folder/file selection. */
  protected void createFileSelectionGroup(Composite parent) {
    selectionGroup = new FileSystemSelectionGroup(parent, getFolderProvider(), getFileProvider());
  }

  /** Creates the "Select types", "Select all" and "Deselect all" buttons. */
  protected void createButtonsGroup(Composite parent) {
    Composite buttonComposite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    layout.makeColumnsEqualWidth = true;
    buttonComposite.setLayout(layout);
    buttonComposite.setFont(parent.getFont());
    GridData buttonData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    buttonData.horizontalSpan = 2;
    buttonComposite.setLayoutData(buttonData);

    selectTypesButton = createButton(buttonComposite, IDialogConstants.SELECT_TYPES_ID, "Select types", false);
    selectTypesButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        handleTypesEditButtonPressed();
        resetSelection();
      }
    });
    setButtonLayoutData(selectTypesButton);

    selectAllButton = createButton(buttonComposite, IDialogConstants.SELECT_ALL_ID, "Select all", false);
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        setAllSelections(true);
        updateWidgetEnablements();
      }
    });
    setButtonLayoutData(selectAllButton);

    deselectAllButton = createButton(buttonComposite, IDialogConstants.DESELECT_ALL_ID, "Deselect all", false);
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        setAllSelections(false);
        updateWidgetEnablements();
      }
    });
    setButtonLayoutData(deselectAllButton);
  }

  /** Creates a push button with the given id and label in the composite; grows the composite's grid by one column. */
  protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    ((GridLayout) parent.getLayout()).numColumns++;

    Button button = new Button(parent, SWT.PUSH);
    button.setFont(parent.getFont());

    GridData buttonData = new GridData(GridData.FILL_HORIZONTAL);
    button.setLayoutData(buttonData);

    button.setData(Integer.valueOf(id));
    button.setText(label);

    if (defaultButton) {
      Shell shell = parent.getShell();
      if (shell != null) {
        shell.setDefaultButton(button);
      }
      button.setFocus();
    }
    return button;
  }

  @Override
  protected void createOptionsGroupButtons(Group optionsGroup) {
    // the base page has no options; the subclasses add theirs
  }

  @Override
  protected boolean validateOptionsGroup() {
    return true;
  }

  @Override
  protected boolean validateSourceGroup() {
    if (!ensureSourceIsValid())
      return false;
    return !getSelectedResources().isEmpty();
  }

  /** The source directory is valid if it exists and is a directory. */
  protected boolean ensureSourceIsValid() {
    return getSourceDirectory() != null;
  }

  /** Shows an error dialog for the page. */
  protected void displayErrorDialog(String message) {
    MessageDialog.openError(getShell(), "Import", message);
  }

  /** The source directory of the import, or null if it does not exist. */
  protected File getSourceDirectory() {
    File sourceDirectory = new File(sourceNameField.getText());
    if (!sourceDirectory.exists() || !sourceDirectory.isDirectory())
      return null;
    return sourceDirectory;
  }

  /** Adds the directory to the source combo and selects it. */
  protected void setSourceName(String selectedDirectory) {
    if (selectedDirectory.length() > 0) {
      String[] currentItems = this.sourceNameField.getItems();
      int selectionIndex = -1;
      for (int i = 0; i < currentItems.length; i++) {
        if (currentItems[i].equals(selectedDirectory))
          selectionIndex = i;
      }
      if (selectionIndex < 0) {
        int oldLength = currentItems.length;
        String[] newItems = new String[oldLength + 1];
        System.arraycopy(currentItems, 0, newItems, 0, oldLength);
        newItems[oldLength] = selectedDirectory;
        this.sourceNameField.setItems(newItems);
        selectionIndex = oldLength;
      }
      this.sourceNameField.select(selectionIndex);

      resetSelection();
    }
  }

  protected void updateFromSourceField() {
    setSourceName(sourceNameField.getText());
    updateWidgetEnablements();
  }

  /** Opens the directory chooser for the source directory. */
  protected void handleSourceBrowseButtonPressed() {
    String currentSource = this.sourceNameField.getText();
    DirectoryDialog dialog = new DirectoryDialog(sourceNameField.getShell(), SWT.SAVE);
    dialog.setMessage("Select source folder");
    dialog.setFilterPath(currentSource);
    String selectedDirectory = dialog.open();

    if (selectedDirectory != null) {
      if (!new File(selectedDirectory).isDirectory() || selectedDirectory.equals(currentSource))
        return;
      setErrorMessage(null);
      setSourceName(selectedDirectory);
    }
  }

  @Override
  public void handleEvent(Event event) {
    if (event.widget == sourceBrowseButton)
      handleSourceBrowseButtonPressed();
  }

  /** The source tree as a root element (its children are the folders of the source directory). */
  protected MinimizedFileSystemElement getFileSystemTree() {
    File sourceDirectory = getSourceDirectory();
    if (sourceDirectory == null)
      return null;
    return createRootElement(sourceDirectory, FileSystemStructureProvider.INSTANCE);
  }

  protected MinimizedFileSystemElement createRootElement(File sourceDirectory,
      FileSystemStructureProvider instance) {
    boolean isContainer = instance.isFolder(sourceDirectory);
    String elementLabel = instance.getLabel(sourceDirectory);

    MinimizedFileSystemElement dummyParent = new MinimizedFileSystemElement("", null, true);
    dummyParent.setPopulated();
    MinimizedFileSystemElement result = new MinimizedFileSystemElement(elementLabel, dummyParent, isContainer);
    result.setFileSystemObject(sourceDirectory);
    result.getFiles(instance);

    return dummyParent;
  }

  /** The content provider of the folder pane: the folders of a folder. */
  protected ITreeContentProvider getFolderProvider() {
    return new WorkbenchContentProvider() {
      @Override
      public Object[] getChildren(Object o) {
        if (o instanceof MinimizedFileSystemElement) {
          MinimizedFileSystemElement element = (MinimizedFileSystemElement) o;
          return element.getFolders(FileSystemStructureProvider.INSTANCE).getChildren(element);
        }
        return new Object[0];
      }

      @Override
      public boolean hasChildren(Object o) {
        if (o instanceof MinimizedFileSystemElement) {
          MinimizedFileSystemElement element = (MinimizedFileSystemElement) o;
          if (element.getFiles().size() != 0)
            return getChildren(element).length > 0;

          return true;
        }
        return false;
      }
    };
  }

  /** The content provider of the file pane: the files of a folder. */
  protected ITreeContentProvider getFileProvider() {
    return new WorkbenchContentProvider() {
      @Override
      public Object[] getChildren(Object o) {
        if (o instanceof MinimizedFileSystemElement) {
          MinimizedFileSystemElement element = (MinimizedFileSystemElement) o;
          return element.getFiles(FileSystemStructureProvider.INSTANCE).getChildren(element);
        }
        return new Object[0];
      }
    };
  }

  /** Whether the files with this extension are imported (the selected types). */
  protected boolean isExportableExtension(String extension) {
    if (selectedTypes == null) // ie.- all extensions are acceptable
      return true;

    for (Object type : selectedTypes) {
      if (extension.equalsIgnoreCase((String) type))
        return true;
    }

    return false;
  }

  /** Checks the files whose extension is selected, and only them, in the selection of the source tree. */
  protected void setupSelectionsBasedOnSelectedTypes() {
    ProgressMonitorDialog dialog = new ProgressMonitorDialog(getContainer().getShell());
    final Map<FileSystemElement, List<FileSystemElement>> selectionMap = new Hashtable<>();

    final IElementFilter filter = new IElementFilter() {
      @Override
      public void filterElements(Collection files, IProgressMonitor monitor) throws InterruptedException {
        if (files == null) {
          throw new InterruptedException();
        }
        for (Object file : files) {
          if (monitor.isCanceled())
            throw new InterruptedException();
          checkFile(file);
        }
      }

      @Override
      public void filterElements(Object[] files, IProgressMonitor monitor) throws InterruptedException {
        if (files == null) {
          throw new InterruptedException();
        }
        for (int i = 0; i < files.length; i++) {
          if (monitor.isCanceled())
            throw new InterruptedException();
          checkFile(files[i]);
        }
      }

      private void checkFile(Object fileElement) {
        MinimizedFileSystemElement file = (MinimizedFileSystemElement) fileElement;
        if (isExportableExtension(file.getFileNameExtension())) {
          List<FileSystemElement> elements = new ArrayList<>();
          FileSystemElement parent = file.getParent();
          if (selectionMap.containsKey(parent))
            elements = selectionMap.get(parent);
          elements.add(file);
          selectionMap.put(parent, elements);
        }
      }
    };

    IRunnableWithProgress runnable = new IRunnableWithProgress() {
      @Override
      public void run(final IProgressMonitor monitor) throws InterruptedException {
        monitor.beginTask("Selectioning monitoring processing", IProgressMonitor.UNKNOWN);
        getSelectedResources(filter, monitor);
      }
    };

    try {
      dialog.run(true, true, runnable);
    } catch (InvocationTargetException exception) {
      return;
    } catch (InterruptedException exception) {
      return;
    }
    getShell().update();
    updateSelections(selectionMap);
  }

  /** Shows the file types of the source tree and replaces the selected types by the chosen ones. */
  protected void handleTypesEditButtonPressed() {
    Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    FileSystemElement root = selectionGroup.getRoot();
    if (root instanceof MinimizedFileSystemElement)
      collectFileTypes((MinimizedFileSystemElement) root, types);

    ListSelectionDialog dialog = new ListSelectionDialog(getShell(), types.toArray(),
        ArrayContentProvider.getInstance(), new LabelProvider(), "Select the types of files to import");
    dialog.setTitle("Select types");
    dialog.setInitialElementSelections(new ArrayList<>(selectedTypes));
    if (dialog.open() == Window.OK) {
      selectedTypes.clear();
      for (Object type : dialog.getResult())
        selectedTypes.add(type);
    }
  }

  private void collectFileTypes(MinimizedFileSystemElement folder, Set<String> types) {
    for (Object file : folder.getFiles(FileSystemStructureProvider.INSTANCE).getChildren(folder)) {
      String extension = ((MinimizedFileSystemElement) file).getFileNameExtension();
      if (!extension.isEmpty())
        types.add(extension);
    }
    for (Object subfolder : folder.getFolders(FileSystemStructureProvider.INSTANCE).getChildren(folder))
      collectFileTypes((MinimizedFileSystemElement) subfolder, types);
  }

  /** Rebuilds the source tree, reapplies the selected types and updates the page. */
  protected void resetSelection() {
    selectionGroup.setRoot(getFileSystemTree());
    setupSelectionsBasedOnSelectedTypes();
    updateWidgetEnablements();
  }

  /** The files checked in the selection of the page. */
  protected List getSelectedResources() {
    return selectionGroup.getCheckedElements();
  }

  /** Applies the filter to all the files of the source tree and returns the selected resources. */
  protected List getSelectedResources(IElementFilter filter, IProgressMonitor monitor) throws InterruptedException {
    FileSystemElement current = selectionGroup.getRoot();
    if (current instanceof MinimizedFileSystemElement)
      filterTree((MinimizedFileSystemElement) current, filter, monitor);
    return getSelectedResources();
  }

  private void filterTree(MinimizedFileSystemElement folder, IElementFilter filter, IProgressMonitor monitor)
      throws InterruptedException {
    filter.filterElements(folder.getFiles(FileSystemStructureProvider.INSTANCE).getChildren(folder), monitor);
    for (Object subfolder : folder.getFolders(FileSystemStructureProvider.INSTANCE).getChildren(folder))
      filterTree((MinimizedFileSystemElement) subfolder, filter, monitor);
  }

  /** Replaces the checked files by the elements of the given lists. */
  protected void updateSelections(Map<?, ?> selectionMap) {
    selectionGroup.updateSelections(selectionMap);
  }

  /** Checks or unchecks all the files of the displayed folder. */
  protected void setAllSelections(boolean state) {
    selectionGroup.setAllSelections(state);
  }

  protected void enableButtonGroup(boolean enable) {
    selectTypesButton.setEnabled(enable);
    selectAllButton.setEnabled(enable);
    deselectAllButton.setEnabled(enable);
  }

  protected boolean determinePageCompletion() {
    return validateSourceGroup() && validateDestinationGroup() && validateOptionsGroup();
  }

  protected void updatePageCompletion() {
    setPageComplete(determinePageCompletion());
  }

  protected void updateWidgetEnablements() {
    boolean pageComplete = determinePageCompletion();
    setPageComplete(pageComplete);
    if (pageComplete)
      setErrorMessage(null);
    enableButtonGroup(getSourceDirectory() != null);
  }

  /** Restores the source directories and the selected types from the dialog settings of the wizard, if any. */
  protected void restoreWidgetValues() {
    IDialogSettings settings = getDialogSettings();
    if (settings == null)
      return;
    String[] sourceNames = settings.getArray(STORE_SOURCE_NAMES_ID);
    if (sourceNames != null)
      for (String name : sourceNames)
        if (sourceNameField.indexOf(name) < 0)
          sourceNameField.add(name);
    String[] types = settings.getArray(STORE_SELECTED_TYPES_ID);
    if (types != null) {
      selectedTypes.clear();
      for (String type : types)
        selectedTypes.add(type);
    }
  }

  /** Saves the source directories and the selected types to the dialog settings of the wizard, if any. */
  protected void saveWidgetValues() {
    IDialogSettings settings = getDialogSettings();
    if (settings == null)
      return;
    settings.put(STORE_SOURCE_NAMES_ID, sourceNameField.getItems());
    settings.put(STORE_SELECTED_TYPES_ID, (String[]) selectedTypes.toArray(new String[0]));
  }
}
