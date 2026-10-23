package com.unitesk.requality.vscode.ui.team.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.team.registry.TeamContentProviderDescriptor;
import com.unitesk.requality.vscode.ui.team.ui.TeamUI;
import com.unitesk.requality.vscode.ui.team.ui.mapping.ITeamContentProviderDescriptor;
import com.unitesk.requality.vscode.ui.team.ui.mapping.ITeamContentProviderManager;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.team.internal.ui.mapping.ModelEnablementPreferencePage}: the Team &gt; Models page, the checkboxes of
 * the model types (the team content providers) that are available when synchronizing. The rows are the team content
 * providers of the platform: the workspace (resource) model and the patch model. The enablement is kept with the
 * descriptors ({@code TeamContentProviderDescriptor.isEnabled}).
 * The Models page has no link to the preference page of a model (Eclipse shows none on this page).
 */
public class ModelEnablementPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  private Set<ITeamContentProviderDescriptor> previouslyEnabled = new HashSet<>();
  private CheckboxTableViewer tableViewer;

  public ModelEnablementPreferencePage() {
    setTitle("Models");
  }

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    Label label = new Label(composite, SWT.NONE);
    label.setText("&Select the model types that should be available when synchronizing");
    label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    tableViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER);
    tableViewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
    tableViewer.setContentProvider(new IStructuredContentProvider() {
      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      }

      @Override
      public void dispose() {
      }

      @Override
      public Object[] getElements(Object element) {
        return element instanceof ITeamContentProviderManager manager
            ? manager.getDescriptors()
            : new Object[0];
      }
    });
    tableViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return element instanceof ITeamContentProviderDescriptor desc ? getTextFor(desc) : super.getText(element);
      }
    });
    tableViewer.setComparator(new ViewerComparator() {
      @Override
      public int compare(Viewer viewer, Object e1, Object e2) {
        if (e1 instanceof ITeamContentProviderDescriptor d1 && e2 instanceof ITeamContentProviderDescriptor d2)
          return getTextFor(d1).compareTo(getTextFor(d2));
        return super.compare(viewer, e1, e2);
      }
    });
    tableViewer.setInput(TeamUI.getTeamContentProviderManager());
    updateChecks();
    return composite;
  }

  /** The label of a row: the name of the contribution, or the label of its model provider. */
  static String getTextFor(ITeamContentProviderDescriptor desc) {
    String name = desc.getName();
    if (name != null && !name.isEmpty())
      return name;
    return TeamContentProviderDescriptor.modelLabel(desc.getModelProviderId());
  }

  private void updateChecks() {
    previouslyEnabled = new HashSet<>();
    for (ITeamContentProviderDescriptor descriptor : TeamUI.getTeamContentProviderManager().getDescriptors())
      if (descriptor.isEnabled())
        previouslyEnabled.add(descriptor);
    tableViewer.setCheckedElements(previouslyEnabled.toArray());
  }

  @Override
  public boolean performOk() {
    List<ITeamContentProviderDescriptor> nowEnabled = new ArrayList<>();
    for (Object checked : tableViewer.getCheckedElements())
      nowEnabled.add((ITeamContentProviderDescriptor) checked);
    if (hasDescriptorEnablementChanged(nowEnabled)) {
      TeamUI.getTeamContentProviderManager().setEnabledDescriptors(nowEnabled.toArray(new ITeamContentProviderDescriptor[0]));
      previouslyEnabled = new HashSet<>(nowEnabled);
    }
    return true;
  }

  private boolean hasDescriptorEnablementChanged(List<ITeamContentProviderDescriptor> checked) {
    for (ITeamContentProviderDescriptor descriptor : TeamUI.getTeamContentProviderManager().getDescriptors()) {
      boolean enable = false;
      for (ITeamContentProviderDescriptor c : checked)
        if (c.getModelProviderId().equals(descriptor.getModelProviderId())) {
          enable = true;
          break;
        }
      if (descriptor.isEnabled() != enable)
        return true;
    }
    return false;
  }

  @Override
  protected void performDefaults() {
    tableViewer.setCheckedElements(TeamUI.getTeamContentProviderManager().getDescriptors());
  }
}
