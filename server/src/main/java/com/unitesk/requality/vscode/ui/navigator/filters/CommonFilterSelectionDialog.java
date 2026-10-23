package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentDescriptor;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.CTabFolder;
import com.unitesk.requality.vscode.ui.swt.widgets.CTabItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.CommonFilterSelectionDialog} ("Filters and Customization"): the
 * tabs "Pre-set filters", "User filters" (when the viewer shows resources) and "Content", and the description of
 * the selected item; Apply activates the checked filters and contents.
 */
public class CommonFilterSelectionDialog extends Dialog {
  private static final int TAB_WIDTH_IN_DLUS = 300;
  private static final int TAB_HEIGHT_IN_DLUS = 150;
  /** The resource content of the navigator: the user filters apply to resources. */
  private static final String RESOURCE_CONTENT = "org.eclipse.ui.navigator.resourceContent";
  /** The id of the user defined filter (NavigatorPlugin.RESOURCE_REGEXP_FILTER_FILTER_ID). */
  public static final String RESOURCE_REGEXP_FILTER_FILTER_ID = "org.eclipse.ui.navigator.resources.filters.userDefined";

  private final CommonViewer commonViewer;
  private final INavigatorContentService contentService;
  private CTabFolder customizationsTabFolder;
  private CommonFiltersTab commonFiltersTab;
  private ContentExtensionsTab contentExtensionsTab;
  private UserFiltersTab userFiltersTab;
  private Label descriptionText;
  private ISelectionChangedListener updateDescriptionSelectionListener;

  public CommonFilterSelectionDialog(CommonViewer aCommonViewer) {
    super(aCommonViewer.getControl().getShell());
    commonViewer = aCommonViewer;
    contentService = commonViewer.getNavigatorContentService();
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Apply", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    getShell().setText("Filters and Customization");
    Composite superComposite = (Composite) super.createDialogArea(parent);
    createCustomizationsTabFolder(superComposite);
    commonFiltersTab = new CommonFiltersTab(customizationsTabFolder, contentService);
    createTabItem(customizationsTabFolder, "Pre-set filters", commonFiltersTab);
    boolean resources = false;
    for (INavigatorContentDescriptor d : contentService.getVisibleExtensions())
      resources |= RESOURCE_CONTENT.equals(d.getId());
    if (resources) {
      userFiltersTab = new UserFiltersTab(customizationsTabFolder, commonViewer);
      createTabItem(customizationsTabFolder, "User filters", userFiltersTab);
    }
    contentExtensionsTab = new ContentExtensionsTab(customizationsTabFolder, contentService);
    createTabItem(customizationsTabFolder, "Content", contentExtensionsTab);
    createDescriptionText(superComposite);
    commonFiltersTab.addSelectionChangedListener(getSelectionListener());
    commonFiltersTab.setInitialFocus();
    contentExtensionsTab.addSelectionChangedListener(getSelectionListener());
    if (customizationsTabFolder.getItems().length > 0)
      customizationsTabFolder.setSelection(0);
    return customizationsTabFolder;
  }

  private void createCustomizationsTabFolder(Composite superComposite) {
    customizationsTabFolder = new CTabFolder(superComposite, SWT.RESIZE | SWT.BORDER);
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    gd.widthHint = convertHorizontalDLUsToPixels(TAB_WIDTH_IN_DLUS);
    gd.heightHint = convertVerticalDLUsToPixels(TAB_HEIGHT_IN_DLUS);
    customizationsTabFolder.setLayout(new GridLayout());
    customizationsTabFolder.setLayoutData(gd);
    customizationsTabFolder.setSimple(true);
    customizationsTabFolder.addListener(SWT.Selection, e -> {
      if (descriptionText != null)
        descriptionText.setText("");
    });
  }

  private CTabItem createTabItem(CTabFolder aTabFolder, String label, Composite composite) {
    CTabItem item = new CTabItem(aTabFolder, SWT.BORDER);
    item.setText(label);
    item.setControl(composite);
    return item;
  }

  private void createDescriptionText(Composite composite) {
    descriptionText = new Label(composite, SWT.WRAP);
    GridData descriptionTextGridData = new GridData(GridData.FILL, GridData.BEGINNING, true, false);
    descriptionTextGridData.heightHint = convertHeightInCharsToPixels(3);
    descriptionText.setLayoutData(descriptionTextGridData);
  }

  private ISelectionChangedListener getSelectionListener() {
    if (updateDescriptionSelectionListener == null)
      updateDescriptionSelectionListener = new FilterDialogSelectionListener(descriptionText);
    return updateDescriptionSelectionListener;
  }

  @Override
  protected void okPressed() {
    if (contentExtensionsTab != null) {
      List<String> checkedExtensions = new ArrayList<>();
      for (Object o : contentExtensionsTab.getTableViewer().getCheckedElements())
        checkedExtensions.add(((INavigatorContentDescriptor) o).getId());
      contentService.activateExtensions(checkedExtensions.toArray(new String[0]));
    }
    List<String> filterIdsToActivate = new ArrayList<>();
    if (commonFiltersTab != null)
      for (Object descriptor : commonFiltersTab.getCheckedItems())
        filterIdsToActivate.add(((ICommonFilterDescriptor) descriptor).getId());
    if (userFiltersTab != null) {
      commonViewer.setData(UserFiltersTab.RESOURCE_REGEXP_FILTER_DATA, userFiltersTab.getUserFilters());
      if (!userFiltersTab.getUserFilters().isEmpty())
        filterIdsToActivate.add(RESOURCE_REGEXP_FILTER_FILTER_ID);
    }
    // UpdateActiveFiltersOperation
    contentService.getFilterService().activateFilterIdsAndUpdateViewer(filterIdsToActivate.toArray(new String[0]));
    if (userFiltersTab != null)
      commonViewer.refresh();
    super.okPressed();
  }

  protected ICommonFilterDescriptor[] getFilterDescriptorChangeHistory() {
    return commonFiltersTab != null ? commonFiltersTab.getFilterDescriptorChangeHistory() : new ICommonFilterDescriptor[0];
  }
}
