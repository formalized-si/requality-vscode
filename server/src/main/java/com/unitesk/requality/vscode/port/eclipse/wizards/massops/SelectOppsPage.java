
package com.unitesk.requality.vscode.port.eclipse.wizards.massops;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;

import com.unitesk.requality.core.api.MassOppDescriptor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.port.eclipse.wizards.TreeNodeSelectionPage;
import com.unitesk.requality.tool.CoreUtils;

public class SelectOppsPage extends WizardPage {

  private MassOppDescriptor[] obj = null;
  private List list = null;

  static String LBL_ATTR_NAME = "Select a name of the attribute:";
  static String MASS_OPP_DESCR_NAME = "Name";
  static String MASS_OPP_NAME = "Operation";
  static String ID = "Id";
  static String NAME = "Selecting opps page";

  public SelectOppsPage(MassOppDescriptor[] objs) {
    super(NAME);
    setPageComplete(false);
    setTitle("Choose name");
    setDescription("Choose object by its name for the next operations");
    this.obj = objs;
  }

  @Override
  public void createControl(Composite parent) {
    final DialogSettings dialogSettings = (DialogSettings) getDialogSettings();
    final Map<String, MassOppDescriptor> descriptors = new HashMap<>();
    for (MassOppDescriptor mod : obj) {
      if (!mod.isDebug() || CoreUtils.DEBUG_LEVEL > 0) descriptors.put(mod.getName(), mod);
    }
    Composite container = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);

    Label label = new Label(container, SWT.NONE);
    label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    label.setText(
        "You have selected the Node: "
            + dialogSettings.get(TreeNodeSelectionPage.ROOT_ID)//TODO user visible name
            + "\n"
            + "Please choose operation");

    list = new List(container, SWT.V_SCROLL | SWT.H_SCROLL);
    GridData gd = new GridData(GridData.FILL_BOTH);
    list.setLayoutData(gd);
    list.setItems(descriptors.keySet().toArray(new String[0]));
    list.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            setPageComplete(false);
            int ind = list.getSelectionIndex();
            if (ind > -1) {
              MassOppDescriptor putDesc = descriptors.get(list.getItem(ind));
              if (putDesc != null) {
                dialogSettings.put(SelectOppsPage.MASS_OPP_DESCR_NAME, putDesc.getName());
                dialogSettings.put(SelectOppsPage.ID, putDesc.getId());
              }
              try {
                setPageComplete(true);
                dialogSettings.save(MassOppsWizard.FILE_NAME);
                ((MassOppsWizard)getWizard()).updateCanFinish();
                getWizard().canFinish();
              } catch (IOException e1) {
                RLogger.logInfo("Save Dialog Settings Exception in SelectOppsPage\n");
              }
            }
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });
    setControl(container);
  }

  @Override
  public IWizardPage getPreviousPage() {
    return getWizard().getStartingPage();
  }
}
