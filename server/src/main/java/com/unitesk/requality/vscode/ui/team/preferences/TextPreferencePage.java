package com.unitesk.requality.vscode.ui.team.preferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.IInputValidator;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.team.core.FileContentManager;
import com.unitesk.requality.vscode.ui.team.core.Team;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.team.internal.ui.preferences.TextPreferencePage}: the Team &gt; File Content page, the name and
 * extension patterns of the files with their content (ASCII text or binary). The patterns of the platform can be changed
 * but not removed. Add Extension... and Add Name... add a pattern, Change toggles the content of the selected patterns
 * (double-clicking the content of a pattern edits it in place), and Remove removes the selected patterns that are not
 * built in.
 * VSCODE: the content mappings are saved with the Team UI file content manager; the file types changed event of Eclipse is
 * not broadcast (no view listens to it).
 */
public class TextPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  /** {@code TextPreferencePage.ExtensionValidator}: an extension without wildcards or the dot. */
  private static final class ExtensionValidator implements IInputValidator {
    @Override
    public String isValid(String newText) {
      if (newText.trim().isEmpty())
        return "";
      if (!isComplete(newText))
        return "The extension pattern is not completed";
      newText = strip(newText);
      if (newText.indexOf('*') >= 0)
        return "The extension cannot contain any wildcards";
      if (newText.indexOf('.') >= 0)
        return "The extension cannot contain the . character.";
      return null;
    }

    String strip(String newText) {
      newText = newText.trim();
      if (newText.startsWith("*"))
        newText = newText.substring(1);
      if (newText.startsWith("."))
        newText = newText.substring(1);
      return newText;
    }

    boolean isComplete(String text) {
      return !(text.equals("*") || text.equals("*.") || text.equals("."));
    }
  }

  /** {@code TextPreferencePage.FilenameValidator}: a file name without wildcards. */
  private static final class FilenameValidator implements IInputValidator {
    @Override
    public String isValid(String newText) {
      if (newText.trim().isEmpty())
        return "";
      if (newText.indexOf('*') >= 0)
        return "The file name cannot contain *";
      return null;
    }
  }

  private final List<FileTypeTable.Item> items = new ArrayList<>();
  private Button removeButton;
  private Button changeButton;
  private FileTypeTable table;

  public TextPreferencePage() {
    initializeItems();
  }

  /** {@code TextPreferencePage.initializeItems}: the extension mappings, then the name mappings of the manager. */
  private void initializeItems() {
    items.clear();
    FileContentManager manager = Team.getFileContentManager();
    Set<String> pluginNames = strings(manager.getDefaultNameMappings());
    Set<String> pluginExtensions = strings(manager.getDefaultExtensionMappings());
    for (FileContentManager.Mapping m : manager.getExtensionMappings()) {
      FileTypeTable.Item item = new FileTypeTable.Extension(m.getString(), pluginExtensions.contains(m.getString()));
      item.mode = m.getType();
      items.add(item);
    }
    for (FileContentManager.Mapping m : manager.getNameMappings()) {
      FileTypeTable.Item item = new FileTypeTable.Name(m.getString(), pluginNames.contains(m.getString()));
      item.mode = m.getType();
      items.add(item);
    }
  }

  private static Set<String> strings(FileContentManager.Mapping[] mappings) {
    Set<String> set = new HashSet<>();
    for (FileContentManager.Mapping m : mappings)
      set.add(m.getString());
    return set;
  }

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(2, false));
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    table = new FileTypeTable(composite, items);
    table.getViewer().getControl().addListener(SWT.Selection, e -> handleSelection());
    table.getViewer().addDoubleClickListener(event -> {
      if (event.getSelection() instanceof IStructuredSelection selection)
        table.getViewer().editElement(selection.getFirstElement(), 1);
    });

    Composite buttons = new Composite(composite, SWT.NONE);
    buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    buttons.setLayout(new GridLayout(1, false));
    Button addExtensionButton = new Button(buttons, SWT.PUSH);
    addExtensionButton.setText("Add &Extension...");
    addExtensionButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    addExtensionButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        addExtension();
      }
    });
    Button addNameButton = new Button(buttons, SWT.PUSH);
    addNameButton.setText("Add &Name...");
    addNameButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    addNameButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        addName();
      }
    });
    changeButton = new Button(buttons, SWT.PUSH);
    changeButton.setText("&Change");
    changeButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    changeButton.setEnabled(false);
    changeButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        changePattern();
      }
    });
    removeButton = new Button(buttons, SWT.PUSH);
    removeButton.setText("&Remove");
    removeButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    removeButton.setEnabled(false);
    removeButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        removePattern();
      }
    });

    Label note = new Label(composite, SWT.WRAP);
    note.setText("Note that some of these entries are built in and can only be changed, but not removed.");
    note.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1));
    return composite;
  }

  /** The selection of the table changed: Change needs a selection, Remove a selection of patterns not built in. */
  private void handleSelection() {
    IStructuredSelection selection = table.getSelection();
    boolean empty = selection.isEmpty();
    changeButton.setEnabled(!empty);
    FileTypeTable.Item selectedItem = empty ? null : (FileTypeTable.Item) selection.getFirstElement();
    removeButton.setEnabled(!empty && !selectedItem.contributed);
  }

  @Override
  protected void performDefaults() {
    super.performDefaults();
    initializeItems();
    if (table != null)
      table.getViewer().refresh();
  }

  @Override
  public boolean performOk() {
    List<String> extensions = new ArrayList<>();
    List<Integer> extensionModes = new ArrayList<>();
    List<String> names = new ArrayList<>();
    List<Integer> nameModes = new ArrayList<>();
    for (FileTypeTable.Item item : items) {
      if (item instanceof FileTypeTable.Extension) {
        extensions.add(item.name);
        extensionModes.add(item.mode);
      } else if (item instanceof FileTypeTable.Name) {
        names.add(item.name);
        nameModes.add(item.mode);
      }
    }
    Team.getFileContentManager().setExtensionMappings(extensions.toArray(new String[0]), toInts(extensionModes));
    Team.getFileContentManager().setNameMappings(names.toArray(new String[0]), toInts(nameModes));
    return true;
  }

  private static int[] toInts(List<Integer> values) {
    int[] r = new int[values.size()];
    for (int i = 0; i < r.length; i++)
      r[i] = values.get(i);
    return r;
  }

  /** Adds a new extension to the table with the default type of Binary ({@code TextPreferencePage.addExtension}). */
  void addExtension() {
    ExtensionValidator validator = new ExtensionValidator();
    InputDialog dialog = new InputDialog(getShell(), "Enter File Extension", "Please enter a file extension:", null,
        validator);
    if (dialog.open() != Window.OK)
      return;
    String extension = validator.strip(dialog.getValue());
    for (FileTypeTable.Item item : items)
      if (item instanceof FileTypeTable.Extension && item.name.equals(extension)) {
        MessageDialog.openWarning(getShell(), "Extension Already Exists", "The entered extension already exists.");
        return;
      }
    items.add(new FileTypeTable.Extension(extension, false));
    table.getViewer().refresh();
  }

  /** Adds a new file name to the table with the default type of Binary ({@code TextPreferencePage.addName}). */
  void addName() {
    InputDialog dialog = new InputDialog(getShell(), "New File Type", "Enter a file name:", null, new FilenameValidator());
    if (dialog.open() != Window.OK)
      return;
    String name = dialog.getValue();
    for (FileTypeTable.Item item : items)
      if (item instanceof FileTypeTable.Name && item.name.equals(name)) {
        MessageDialog.openWarning(getShell(), "Extension Already Exists", "The entered extension already exists.");
        return;
      }
    items.add(new FileTypeTable.Name(name, false));
    table.getViewer().refresh();
  }

  /** Removes the selected patterns, except the ones built in ({@code TextPreferencePage.removePattern}). */
  void removePattern() {
    IStructuredSelection selection = table.getSelection();
    if (selection == null)
      return;
    for (Object element : selection.toList()) {
      FileTypeTable.Item item = (FileTypeTable.Item) element;
      if (!item.contributed)
        items.remove(item);
    }
    table.getViewer().refresh();
    handleSelection();
  }

  /** Toggles the content of the selected patterns between Binary and ASCII Text ({@code changePattern}). */
  void changePattern() {
    IStructuredSelection selection = table.getSelection();
    if (selection == null)
      return;
    for (Object element : selection.toList()) {
      FileTypeTable.Item item = (FileTypeTable.Item) element;
      item.mode = item.mode == Team.TEXT ? Team.BINARY : Team.TEXT;
      table.getViewer().refresh(item);
    }
  }
}
