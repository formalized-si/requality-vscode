package com.unitesk.requality.vscode.ui.team.wizards;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.team.preferences.IPreferenceIds;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetGroup;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ImportProjectSetMainPage}: the page of the Team Project Set import, the file
 * (with Browse...) or the URL to read the project set from, the working sets of the imported projects, and the choice to
 * run the import in the background (the Team UI preference {@code RUN_IMPORT_IN_BACKGROUND} is its initial value).
 */
public class ImportProjectSetMainPage extends WizardPage {
  /** The input is a file. */
  public static final int InputType_file = 0;
  /** The input is a URL. */
  public static final int InputType_URL = 1;

  private Combo fileCombo;
  private Button browseButton;
  private Combo urlCombo;
  private Button fileInputButton;
  private Button urlInputButton;
  private String file = "";
  private String urlString = "";
  private int inputType = InputType_file;
  private boolean runInBackground = isRunInBackgroundPreferenceOn();
  private int messageType = IMessageProvider.NONE;
  private WorkingSetGroup workingSetGroup;

  public ImportProjectSetMainPage(String pageName, String title, Object titleImage) {
    super(pageName, title, titleImage);
    setDescription("Import the team project file.");
  }

  private void setInputType(int inputTypeSelected) {
    this.inputType = inputTypeSelected;
    messageType = IMessageProvider.NONE;
    fileInputButton.setSelection(inputType == InputType_file);
    fileCombo.setEnabled(inputType == InputType_file);
    browseButton.setEnabled(inputType == InputType_file);
    urlInputButton.setSelection(inputType == InputType_URL);
    urlCombo.setEnabled(inputType == InputType_URL);
    if (inputType == InputType_file)
      updateFileEnablement();
    if (inputType == InputType_URL)
      updateUrlEnablement();
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite inner = new Composite(composite, SWT.NONE);
    inner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    GridLayout layout = new GridLayout(3, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    inner.setLayout(layout);

    fileInputButton = new Button(inner, SWT.RADIO);
    fileInputButton.setText("F&ile");
    fileInputButton.setEnabled(true);
    fileInputButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        setInputType(InputType_file);
      }
    });
    fileCombo = new Combo(inner, SWT.DROP_DOWN | SWT.BORDER);
    fileCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    file = PsfHistory.suggestedFilename();
    fileCombo.setItems(PsfHistory.get(PsfHistory.FILENAMES));
    fileCombo.setText(file);
    fileCombo.addModifyListener(e -> {
      file = fileCombo.getText();
      updateFileEnablement();
    });
    browseButton = new Button(inner, SWT.PUSH);
    browseButton.setText("B&rowse...");
    GridData data = new GridData();
    data.horizontalAlignment = GridData.FILL;
    data.widthHint = browseButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x;
    browseButton.setLayoutData(data);
    browseButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        browse();
      }
    });

    urlInputButton = new Button(inner, SWT.RADIO);
    urlInputButton.setText("&URL");
    urlInputButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        setInputType(InputType_URL);
      }
    });
    urlCombo = new Combo(inner, SWT.DROP_DOWN | SWT.BORDER);
    GridData urlData = new GridData(GridData.FILL_HORIZONTAL);
    urlData.horizontalSpan = 2;
    urlCombo.setLayoutData(urlData);
    String[] urls = PsfHistory.get(PsfHistory.URLS);
    urlString = urls.length > 0 ? urls[0] : "";
    urlCombo.setItems(urls);
    urlCombo.setText(urlString);
    urlCombo.addModifyListener(e -> {
      urlString = urlCombo.getText();
      updateUrlEnablement();
    });

    workingSetGroup = new WorkingSetGroup(composite, null,
        new String[] { "org.eclipse.ui.resourceWorkingSetPage", "org.eclipse.jdt.ui.JavaWorkingSetPage" });

    Button runInBackgroundCheckbox = new Button(composite, SWT.CHECK);
    runInBackgroundCheckbox.setText("Run the import in the bac&kground");
    runInBackgroundCheckbox.setSelection(runInBackground);
    runInBackgroundCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        runInBackground = !runInBackground;
      }
    });

    setControl(composite);
    setDefaultInputType();
  }

  /** The input type of the page: the URL when the clipboard holds a URL, else the file ({@code setDefaultInputType}). */
  private void setDefaultInputType() {
    Clipboard clipboard = new Clipboard(getControl().getDisplay());
    Object o = clipboard.getContents(TextTransfer.getInstance());
    clipboard.dispose();
    if (o instanceof String text && isUrl(text)) {
      setInputType(InputType_URL);
      urlCombo.setText(text);
      return;
    }
    setInputType(InputType_file);
  }

  private static boolean isUrl(String text) {
    try {
      new URL(text);
      return true;
    } catch (MalformedURLException e) {
      return false;
    }
  }

  private void browse() {
    FileDialog d = new FileDialog(getShell());
    d.setFilterExtensions(new String[] { "*.psf", "*" });
    d.setFilterNames(new String[] { "Team Project Set Files (*.psf)", "All Files (*.*)" });
    String dir = file;
    if (dir != null && !dir.isEmpty()) {
      int separator = dir.lastIndexOf(File.separatorChar);
      dir = separator != -1 ? dir.substring(0, separator) : "";
    } else {
      dir = "";
    }
    d.setFilterPath(dir);
    String f = d.open();
    if (f != null) {
      fileCombo.setText(f);
      file = f;
    }
  }

  private void updateUrlEnablement() {
    boolean complete = false;
    setMessage(null);
    setErrorMessage(null);
    if (urlString.isEmpty()) {
      setMessage("Please specify an URL to import.", messageType);
    } else {
      try {
        new URL(urlString);
        complete = true;
      } catch (MalformedURLException e) {
        messageType = IMessageProvider.ERROR;
        setMessage("Malformed URL", messageType);
      }
    }
    if (complete)
      setDescription("Import the team project file.");
    setPageComplete(complete);
  }

  private void updateFileEnablement() {
    boolean complete = false;
    setMessage(null);
    setErrorMessage(null);
    if (file.isEmpty()) {
      setMessage("Please specify a file to import.", messageType);
    } else {
      File f = new File(file);
      if (!f.exists()) {
        messageType = IMessageProvider.ERROR;
        setMessage("The specified file does not exist", messageType);
      } else if (f.isDirectory()) {
        messageType = IMessageProvider.ERROR;
        setMessage("You have specified a folder", messageType);
      } else if (!isValidProjectSetFile(f)) {
        messageType = IMessageProvider.ERROR;
        setMessage("The specified file is not a valid Team Project Set file.", messageType);
      } else {
        complete = true;
      }
    }
    if (complete)
      setDescription("Import the team project file.");
    setPageComplete(complete);
  }

  /** The file name of the import. */
  public String getFileName() {
    return file;
  }

  /** The URL of the import. */
  public String getUrl() {
    return urlString;
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible && fileCombo != null)
      fileCombo.setFocus();
  }

  /** The working sets selected on the page, or an empty array. */
  public IWorkingSet[] getWorkingSets() {
    return workingSetGroup.getSelectedWorkingSets();
  }

  private static boolean isRunInBackgroundPreferenceOn() {
    return TeamUIPlugin.getPreferenceStore().getBoolean(IPreferenceIds.RUN_IMPORT_IN_BACKGROUND);
  }

  /** Whether the import runs in a background job (the check box). */
  public boolean isRunInBackgroundOn() {
    return runInBackground;
  }

  /** The kind of the input: {@link #InputType_file} or {@link #InputType_URL}. */
  public int getInputType() {
    return inputType;
  }

  /**
   * {@code ImportProjectSetMainPage.getURLContents}: the project set read from the URL, or null when it cannot be loaded or
   * is not a Team Project Set (the page shows the reason).
   */
  public String getURLContents() {
    PsfHistory.remember(PsfHistory.URLS, urlString);
    try (InputStream in = new URL(urlString).openStream()) {
      String urlContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (isValidProjectSetString(urlContent))
        return urlContent;
      messageType = IMessageProvider.ERROR;
      setMessage("The specified file is not a valid Team Project Set file.", messageType);
      setPageComplete(false);
    } catch (MalformedURLException e) {
      // the URL was checked when it was entered
    } catch (IOException e) {
      messageType = IMessageProvider.ERROR;
      setMessage("File from given URL cannot be loaded", messageType);
      setPageComplete(false);
    }
    return null;
  }

  /** {@code ProjectSetImporter.isValidProjectSetFile}: the file is an XML document with a psf root element. */
  static boolean isValidProjectSetFile(File f) {
    try {
      return isRootPsf(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f));
    } catch (Exception e) {
      return false;
    }
  }

  /** {@code ProjectSetImporter.isValidProjectSetString}: the content is an XML document with a psf root element. */
  static boolean isValidProjectSetString(String content) {
    try {
      return isRootPsf(DocumentBuilderFactory.newInstance().newDocumentBuilder()
          .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isRootPsf(Document document) {
    return document.getDocumentElement() != null && "psf".equals(document.getDocumentElement().getNodeName());
  }
}
