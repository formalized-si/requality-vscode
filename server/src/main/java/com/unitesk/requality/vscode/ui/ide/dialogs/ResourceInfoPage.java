package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyPage;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.resources.ProjectSettings;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.ResourceInfoPage}: the Resource page of the Properties dialog of a file,
 * a folder or a project: path, type, location, size, last modification, attributes (read-only, executable on
 * Unix-like systems, archive on Windows, derived), the text file encoding (of the file or the default of the
 * container) and, for a project, the new text file line delimiter. The encodings are kept in the project's
 * {@code .settings/org.eclipse.core.resources.prefs}, the line delimiter in {@code .settings/org.eclipse.core.runtime.prefs}.
 */
public class ResourceInfoPage extends PropertyPage {
  // VSCODE: the executable and archive attributes follow the platform (no EFS permission table of the file system).
  private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  /** The line delimiters of the Eclipse platform (Platform.knownPlatformLineSeparators). */
  static final String[] LINE_DELIMITER_KEYS = { "Windows", "Unix", "Classic Mac OS" };
  static final String[] LINE_DELIMITER_VALUES = { "\r\n", "\n", "\r" };
  private static final String LINE_DELIMITER_KEY = "line.separator";

  /** A change of the attributes of a folder, applied to its children after the confirmation. */
  private interface IResourceChange {
    String getMessage();

    void performChange(IResource resource) throws CoreException;
  }

  private Button editableBox;
  private Button executableBox;
  private Button archiveBox;
  private Button derivedBox;
  private boolean previousReadOnlyValue;
  private boolean previousExecutableValue;
  private boolean previousArchiveValue;
  private boolean previousDerivedValue;

  private Button defaultEncodingButton;
  private Button otherEncodingButton;
  private Combo encodingCombo;

  private Button defaultLineDelimiterButton;
  private Button otherLineDelimiterButton;
  private Combo lineDelimiterCombo;

  /** The resource of the element (the element itself, or what it adapts to). */
  static IResource resourceOf(IAdaptable element) {
    if (element == null)
      return null;
    if (element instanceof IResource)
      return (IResource) element;
    return element.getAdapter(IResource.class);
  }

  @Override
  protected Control createContents(Composite parent) {
    IResource resource = resourceOf(getElement());
    if (resource == null) {
      Label label = new Label(parent, SWT.NONE);
      label.setText("Resource information is not available for the current selection.");
      return label;
    }
    if (resource.getType() != IResource.PROJECT) {
      ResourceAttributes attrs = resource.getResourceAttributes();
      previousReadOnlyValue = attrs != null && attrs.isReadOnly();
      previousExecutableValue = attrs != null && attrs.isExecutable();
      previousArchiveValue = attrs != null && attrs.isArchive();
      previousDerivedValue = resource.isDerived();
    }
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    GridData data = new GridData(GridData.FILL);
    data.grabExcessHorizontalSpace = true;
    composite.setLayoutData(data);

    createBasicInfoGroup(composite, resource);
    if (resource.getType() != IResource.PROJECT) {
      createSeparator(composite);
      createStateGroup(composite, resource);
    }
    if (resource.getProject().isOpen()) {
      createEncodingGroup(composite, resource);
      if (resource.getType() == IResource.PROJECT)
        createLineDelimiterGroup(composite, (ProjectRef) resource.getProject());
    }
    return composite;
  }

  private void createBasicInfoGroup(Composite parent, IResource resource) {
    Composite info = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 4;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    info.setLayout(layout);
    GridData data = new GridData();
    data.verticalAlignment = GridData.FILL;
    data.horizontalAlignment = GridData.FILL;
    info.setLayoutData(data);

    createValueRow(info, "&Path:", resource.getFullPath().toString());
    createValueRow(info, "T&ype:", IDEResourceInfoUtils.getTypeString(resource));
    createValueRow(info, "&Location:", IDEResourceInfoUtils.getLocationText(resource));
    if (resource.getType() == IResource.FILE)
      createValueRow(info, "&Size:", IDEResourceInfoUtils.getSizeString(resource));
    createValueRow(info, "Last &modified:", IDEResourceInfoUtils.getDateStringValue(resource));
  }

  private static void createValueRow(Composite parent, String title, String value) {
    Label label = new Label(parent, SWT.LEFT);
    label.setText(title);
    GridData gd = new GridData();
    gd.verticalAlignment = GridData.BEGINNING;
    label.setLayoutData(gd);

    Text text = new Text(parent, SWT.WRAP | SWT.READ_ONLY);
    text.setText(value);
    gd = new GridData();
    gd.widthHint = 400;
    gd.grabExcessHorizontalSpace = true;
    gd.horizontalAlignment = GridData.FILL;
    gd.horizontalSpan = 3;
    text.setLayoutData(gd);
  }

  private static void createSeparator(Composite parent) {
    Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
    GridData gd = new GridData();
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    separator.setLayoutData(gd);
  }

  private void createStateGroup(Composite parent, IResource resource) {
    Composite composite = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    GridData data = new GridData();
    data.horizontalAlignment = GridData.FILL;
    composite.setLayoutData(data);

    Label attributes = new Label(composite, SWT.LEFT);
    attributes.setText("Attributes:");
    editableBox = checkBox(composite, "&Read-only", previousReadOnlyValue);
    if (!WINDOWS)
      executableBox = checkBox(composite, "E&xecutable", previousExecutableValue);
    else
      archiveBox = checkBox(composite, "Ar&chive", previousArchiveValue);
    derivedBox = checkBox(composite, "Deri&ved", previousDerivedValue);
    if (executableBox != null && resource.getType() == IResource.FOLDER) {
      Label note = new Label(composite, SWT.WRAP);
      note.setText("Note: Removing the executable flag on a folder will cause its children to become unreadable.");
      GridData gd = new GridData();
      gd.widthHint = 400;
      gd.grabExcessHorizontalSpace = true;
      gd.horizontalAlignment = GridData.FILL;
      note.setLayoutData(gd);
    }
  }

  private static Button checkBox(Composite parent, String text, boolean selected) {
    Button button = new Button(parent, SWT.CHECK | SWT.RIGHT);
    button.setAlignment(SWT.LEFT);
    button.setText(text);
    button.setSelection(selected);
    return button;
  }

  /** The encoding of the resource: the default (inherited from its container) or the one set on it. */
  private void createEncodingGroup(Composite parent, IResource resource) {
    Group group = new Group(parent, SWT.NONE);
    group.setText(resource instanceof IContainer ? "Default encoding for &text files" : "&Text file encoding");
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    group.setLayout(layout);
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    group.setLayoutData(data);

    String inherited = inheritedEncoding(resource);
    defaultEncodingButton = radioButton(group, defaultEncodingText(resource, inherited), 2);
    otherEncodingButton = radioButton(group, "&Other:", 1);
    defaultEncodingButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectEncodingDefault(true);
      }
    });
    otherEncodingButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectEncodingDefault(false);
      }
    });
    encodingCombo = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
    encodingCombo.setItems(availableCharsets(inherited));
    encodingCombo.setLayoutData(new GridData());

    String stored = ProjectSettings.explicitCharset(resource);
    if (stored == null) {
      selectEncodingDefault(true);
      selectItem(encodingCombo, inherited);
    } else {
      if (encodingCombo.indexOf(stored) < 0)
        encodingCombo.add(stored);
      selectItem(encodingCombo, stored);
      selectEncodingDefault(false);
    }
  }

  private void selectEncodingDefault(boolean useDefault) {
    defaultEncodingButton.setSelection(useDefault);
    otherEncodingButton.setSelection(!useDefault);
    encodingCombo.setEnabled(!useDefault);
  }

  private void selectEncodingDefault() {
    selectEncodingDefault(true);
  }

  private static String defaultEncodingText(IResource resource, String inherited) {
    if (resource instanceof IFile)
      return "D&efault (inherited from container: " + inherited + ")";
    return "&Inherited from container (" + inherited + ")";
  }

  /** The charset of the container of the resource (the workspace default for a project). */
  private static String inheritedEncoding(IResource resource) {
    try {
      IContainer parent = resource.getParent();
      if (parent != null)
        return parent.getDefaultCharset();
    } catch (CoreException e) {
      // the workspace default applies
    }
    return ResourcesPlugin.getEncoding();
  }

  /** The charset names, sorted, with the inherited one among them. */
  private static String[] availableCharsets(String inherited) {
    TreeSet<String> names = new TreeSet<>(Charset.availableCharsets().keySet());
    names.add(inherited);
    return names.toArray(new String[0]);
  }

  /** The line delimiter of a project: the default (inherited from the workspace) or the one set on the project. */
  private void createLineDelimiterGroup(Composite parent, ProjectRef project) {
    Group group = new Group(parent, SWT.NONE);
    group.setText("New text &file line delimiter");
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    group.setLayout(layout);
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    group.setLayoutData(data);

    String defaultKey = keyOf(System.getProperty("line.separator"));
    defaultLineDelimiterButton = radioButton(group, "Inh&erited from container (" + defaultKey + ")", 2);
    otherLineDelimiterButton = radioButton(group, "Ot&her:", 1);
    defaultLineDelimiterButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectLineDelimiterDefault(true);
      }
    });
    otherLineDelimiterButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectLineDelimiterDefault(false);
      }
    });
    lineDelimiterCombo = new Combo(group, SWT.NONE | SWT.READ_ONLY);
    lineDelimiterCombo.setItems(LINE_DELIMITER_KEYS);
    lineDelimiterCombo.setLayoutData(new GridData());

    String stored = ProjectSettings.get(project, ProjectSettings.RUNTIME_PREFS, LINE_DELIMITER_KEY);
    if (stored == null) {
      selectLineDelimiterDefault(true);
      selectItem(lineDelimiterCombo, defaultKey);
    } else {
      String key = keyOf(stored);
      selectItem(lineDelimiterCombo, key == null ? defaultKey : key);
      selectLineDelimiterDefault(false);
    }
  }

  /** Selects the item of a read-only combo. */
  private static void selectItem(Combo combo, String item) {
    int index = combo.indexOf(item);
    if (index >= 0)
      combo.select(index);
  }

  private void selectLineDelimiterDefault(boolean useDefault) {
    defaultLineDelimiterButton.setSelection(useDefault);
    otherLineDelimiterButton.setSelection(!useDefault);
    lineDelimiterCombo.setEnabled(!useDefault);
  }

  /** The Eclipse name of a line delimiter value (Unix for the platform's own on Linux and macOS). */
  static String keyOf(String value) {
    for (int i = 0; i < LINE_DELIMITER_VALUES.length; i++)
      if (LINE_DELIMITER_VALUES[i].equals(value))
        return LINE_DELIMITER_KEYS[i];
    return LINE_DELIMITER_KEYS[1];
  }

  private static String valueOf(String key) {
    for (int i = 0; i < LINE_DELIMITER_KEYS.length; i++)
      if (LINE_DELIMITER_KEYS[i].equals(key))
        return LINE_DELIMITER_VALUES[i];
    return null;
  }

  private static Button radioButton(Composite parent, String text, int span) {
    Button b = new Button(parent, SWT.RADIO);
    b.setText(text);
    GridData gd = new GridData();
    gd.horizontalSpan = span;
    b.setLayoutData(gd);
    return b;
  }

  @Override
  protected void performDefaults() {
    IResource resource = resourceOf(getElement());
    if (resource == null)
      return;
    if (editableBox != null)
      editableBox.setSelection(false);
    if (executableBox != null)
      executableBox.setSelection(false);
    if (archiveBox != null)
      archiveBox.setSelection(true);
    if (derivedBox != null)
      derivedBox.setSelection(false);
    if (encodingCombo != null)
      selectEncodingDefault();
    if (lineDelimiterCombo != null) {
      selectLineDelimiterDefault(true);
    }
  }

  @Override
  public boolean performOk() {
    IResource resource = resourceOf(getElement());
    if (resource == null)
      return true;
    try {
      if (lineDelimiterCombo != null)
        storeLineDelimiter((ProjectRef) resource.getProject());
      if (resource.getType() != IResource.PROJECT)
        storeAttributes(resource);
      if (derivedBox != null) {
        boolean localDerivedValue = derivedBox.getSelection();
        if (previousDerivedValue != localDerivedValue) {
          resource.setDerived(localDerivedValue, null);
          boolean isDerived = resource.isDerived();
          previousDerivedValue = isDerived;
          derivedBox.setSelection(isDerived);
        }
      }
      if (encodingCombo != null)
        storeEncoding(resource);
    } catch (CoreException exception) {
      ErrorDialog.openError(getShell(), "Internal Error", exception.getLocalizedMessage(), exception.getStatus());
      return false;
    }
    return true;
  }

  private void storeEncoding(IResource resource) throws CoreException {
    String value = otherEncodingButton.getSelection() ? encodingCombo.getText() : null;
    if (value != null && value.isEmpty())
      value = null;
    if (Objects.equals(value, ProjectSettings.explicitCharset(resource)))
      return;
    if (resource instanceof IFile)
      ((IFile) resource).setCharset(value, null);
    else
      ((IContainer) resource).setDefaultCharset(value, null);
  }

  private void storeLineDelimiter(ProjectRef project) throws CoreException {
    String value = otherLineDelimiterButton.getSelection() ? valueOf(lineDelimiterCombo.getText()) : null;
    try {
      ProjectSettings.put(project, ProjectSettings.RUNTIME_PREFS, LINE_DELIMITER_KEY, value);
    } catch (java.io.IOException e) {
      throw new CoreException(new com.unitesk.requality.vscode.ui.runtime.Status(
          com.unitesk.requality.vscode.ui.runtime.IStatus.ERROR, "org.eclipse.ui.ide", e.getMessage(), e));
    }
  }

  /** The attributes that changed are stored; a folder's change is applied to its children when confirmed. */
  private void storeAttributes(IResource resource) throws CoreException {
    ResourceAttributes attrs = resource.getResourceAttributes();
    if (attrs == null)
      return;
    boolean[] finalValues = { false, false, false };
    boolean[] changedAttrs = { false, false, false };
    if (editableBox != null && editableBox.getSelection() != previousReadOnlyValue) {
      attrs.setReadOnly(editableBox.getSelection());
      finalValues[0] = editableBox.getSelection();
      changedAttrs[0] = true;
    }
    if (executableBox != null && executableBox.getSelection() != previousExecutableValue) {
      attrs.setExecutable(executableBox.getSelection());
      finalValues[1] = executableBox.getSelection();
      changedAttrs[1] = true;
    }
    if (archiveBox != null && archiveBox.getSelection() != previousArchiveValue) {
      attrs.setArchive(archiveBox.getSelection());
      finalValues[2] = archiveBox.getSelection();
      changedAttrs[2] = true;
    }
    if (!(changedAttrs[0] || changedAttrs[1] || changedAttrs[2]))
      return;
    resource.setResourceAttributes(attrs);
    attrs = resource.getResourceAttributes();
    if (attrs == null)
      return;
    previousReadOnlyValue = attrs.isReadOnly();
    previousExecutableValue = attrs.isExecutable();
    previousArchiveValue = attrs.isArchive();
    if (editableBox != null)
      editableBox.setSelection(attrs.isReadOnly());
    if (executableBox != null)
      executableBox.setSelection(attrs.isExecutable());
    if (archiveBox != null)
      archiveBox.setSelection(attrs.isArchive());
    if (resource.getType() == IResource.FOLDER) {
      List<IResourceChange> changes = new ArrayList<>();
      changes.add(attributesChange(changedAttrs, finalValues));
      if (confirmRecursiveChanges(changes))
        performRecursively(resource, changes);
    }
  }

  private static IResourceChange attributesChange(boolean[] changedAttrs, boolean[] finalAttrs) {
    return new IResourceChange() {
      @Override
      public String getMessage() {
        String message = "";
        if (changedAttrs[0])
          message += simpleChangeName(finalAttrs[0], "Read-only");
        if (changedAttrs[1])
          message += simpleChangeName(finalAttrs[1], "Executable");
        if (changedAttrs[2])
          message += simpleChangeName(finalAttrs[2], "Archive");
        return message;
      }

      @Override
      public void performChange(IResource resource) throws CoreException {
        ResourceAttributes attrs = resource.getResourceAttributes();
        if (attrs != null) {
          if (changedAttrs[0])
            attrs.setReadOnly(finalAttrs[0]);
          if (changedAttrs[1])
            attrs.setExecutable(finalAttrs[1]);
          if (changedAttrs[2])
            attrs.setArchive(finalAttrs[2]);
          resource.setResourceAttributes(attrs);
        }
      }
    };
  }

  private static String simpleChangeName(boolean isSet, String name) {
    return "\t" + (isSet ? "set" : "unset") + " " + name + "\n";
  }

  private boolean confirmRecursiveChanges(List<IResourceChange> changes) {
    if (changes.isEmpty())
      return false;
    StringBuilder message = new StringBuilder("The following changes have been made:\n");
    for (IResourceChange change : changes)
      message.append(change.getMessage());
    message.append("Do you want to apply these changes to subfolders and files?");
    return MessageDialog.openQuestion(getShell(), "Confirm recursive changes", message.toString());
  }

  /** Applies the changes to every member of the folder, recursively. */
  private static void performRecursively(IResource resource, List<IResourceChange> changes) throws CoreException {
    List<IResource> members = new ArrayList<>();
    collect(resource, members);
    for (IResource child : members)
      for (IResourceChange change : changes)
        change.performChange(child);
  }

  private static void collect(IResource resource, List<IResource> out) throws CoreException {
    if (!(resource instanceof IContainer))
      return;
    for (IResource member : ((IContainer) resource).members()) {
      out.add(member);
      collect(member, out);
    }
  }
}
