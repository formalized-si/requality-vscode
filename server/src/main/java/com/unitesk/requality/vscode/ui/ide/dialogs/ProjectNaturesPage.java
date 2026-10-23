package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.ElementListSelectionDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyPage;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IExtension;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.ProjectNaturesPage}: the natures of the project (the natures of its
 * {@code .project} file), with Add... and Remove. The natures that can be added are the ones of the
 * {@code org.eclipse.core.resources.natures} extension point.
 */
public class ProjectNaturesPage extends PropertyPage {
  static final String NATURES_POINT = "org.eclipse.core.resources.natures";

  private IProject project;
  private List<String> naturesIdsWorkingCopy;
  private TableViewer activeNaturesList;
  private boolean warningAlreadyShown = false;

  public ProjectNaturesPage() {
    setDescription("Projects may have natures assigned to enable additional functionalities. "
        + "Use this page to specify which natures should be assigned to the project.");
  }

  @Override
  protected Control createContents(final Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    initialize();

    Label warningLabel = new Label(composite, SWT.WRAP);
    warningLabel.setText(WARNING_MESSAGE);
    GridData warningData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    warningData.widthHint = 400;
    warningLabel.setLayoutData(warningData);

    Composite naturesComposite = new Composite(composite, SWT.NONE);
    naturesComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    naturesComposite.setLayout(twoColumns());

    activeNaturesList = new TableViewer(naturesComposite);
    activeNaturesList.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    activeNaturesList.setLabelProvider(new NatureLabelProvider());
    activeNaturesList.setContentProvider(ArrayContentProvider.getInstance());
    try {
      naturesIdsWorkingCopy = new ArrayList<>(Arrays.asList(project.getDescription().getNatureIds()));
    } catch (CoreException ex) {
      naturesIdsWorkingCopy = new ArrayList<>();
    }
    activeNaturesList.setInput(naturesIdsWorkingCopy);

    Composite buttonComposite = new Composite(naturesComposite, SWT.NONE);
    buttonComposite.setLayout(new GridLayout());
    buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

    Button addButton = new Button(buttonComposite, SWT.PUSH);
    addButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    addButton.setText("&Add...");
    addButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (!confirmChange())
          return;
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(parent.getShell(), new NatureLabelProvider());
        dialog.setMessage("Select a Nature to add to the Project");
        dialog.setTitle("Select Nature");
        List<String> natures = new ArrayList<>();
        for (String id : availableNatures())
          if (!naturesIdsWorkingCopy.contains(id))
            natures.add(id);
        dialog.setElements(natures.toArray());
        if (dialog.open() == Window.OK) {
          for (Object item : dialog.getResult())
            naturesIdsWorkingCopy.add((String) item);
          activeNaturesList.refresh();
        }
      }
    });

    Button removeButton = new Button(buttonComposite, SWT.PUSH);
    removeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    removeButton.setText("&Remove");
    removeButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (!confirmChange())
          return;
        IStructuredSelection selection = (IStructuredSelection) activeNaturesList.getSelection();
        for (Object item : selection.toList())
          naturesIdsWorkingCopy.remove((String) item);
        activeNaturesList.refresh();
      }
    });
    activeNaturesList.addSelectionChangedListener(
        event -> removeButton.setEnabled(!activeNaturesList.getSelection().isEmpty()));
    activeNaturesList.setSelection(new StructuredSelection());
    return composite;
  }

  private static GridLayout twoColumns() {
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    return layout;
  }

  /** The natures change needs the confirmation of the user (once per page). */
  private boolean confirmChange() {
    if (warningAlreadyShown)
      return true;
    if (!MessageDialog.openConfirm(getShell(), "Confirm Project Nature update", WARNING_MESSAGE + "\n\nContinue?"))
      return false;
    warningAlreadyShown = true;
    return true;
  }

  private static final String WARNING_MESSAGE = "Modifying natures is an advanced operation that can leave the project "
      + "in the state requiring further extensive and non-obvious configuration changes before normal function is "
      + "achieved. Do not attempt without a recent backup of the project.";

  /** The ids of the natures the platform knows (the extensions of the natures point). */
  static List<String> availableNatures() {
    List<String> ids = new ArrayList<>();
    for (IExtension e : ExtensionRegistry.get().getExtensionPoint(NATURES_POINT).getExtensions())
      ids.add(e.getUniqueIdentifier());
    return ids;
  }

  /** The label of a nature: its name, its id when it has none, "{0} (missing)" for an unknown one. */
  static String natureLabel(String natureId) {
    for (IExtension e : ExtensionRegistry.get().getExtensionPoint(NATURES_POINT).getExtensions())
      if (natureId.equals(e.getUniqueIdentifier())) {
        String label = e.getLabel();
        return label == null || label.trim().isEmpty() ? natureId : label;
      }
    return natureId + " (missing)";
  }

  private static class NatureLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      return element instanceof String ? natureLabel((String) element) : String.valueOf(element);
    }
  }

  private void initialize() {
    IResource resource = ResourceInfoPage.resourceOf(getElement());
    project = resource == null ? null : resource.getProject();
    noDefaultAndApplyButton();
  }

  @Override
  public boolean performOk() {
    List<String> originalNatureIds;
    try {
      originalNatureIds = Arrays.asList(project.getDescription().getNatureIds());
    } catch (CoreException ex) {
      originalNatureIds = new ArrayList<>();
    }
    if (naturesIdsWorkingCopy.size() == originalNatureIds.size() && naturesIdsWorkingCopy.containsAll(originalNatureIds))
      return true;
    try {
      IProjectDescription description = project.getDescription();
      description.setNatureIds(naturesIdsWorkingCopy.toArray(new String[0]));
      project.setDescription(description, null);
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), null, null, e.getStatus());
      return false;
    }
    return true;
  }
}
