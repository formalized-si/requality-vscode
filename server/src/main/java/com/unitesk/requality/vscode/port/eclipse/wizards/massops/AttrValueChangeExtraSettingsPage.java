package com.unitesk.requality.vscode.port.eclipse.wizards.massops;

import java.io.IOException;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.mass.MassOppsArguments;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;

public class AttrValueChangeExtraSettingsPage extends AbstractExtraSettingsPage {

  private static final String LBL_CH_VALUE = "Type new value:	";
  private Text newAttrValue;
  private boolean valsel = false;

  public AttrValueChangeExtraSettingsPage(DialogSettings ds, Set<String> names, TreeDB db) {
    super("Attribute value change", ds, names, db);
    setTitle("Attribute Value Change Settings");
    dialogSettings = ds;
  }

  @Override
  public void createControl(Composite parentP) {
    //		//super.createControl(parent);
    container = new Composite(parentP, SWT.None);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);
    layout.verticalSpacing = 2;
    container.setLayout(layout);
    container.setLayoutData(new GridData(GridData.FILL_BOTH));
    leftright = new Composite(container, SWT.NONE);
    leftright.setLayoutData(new GridData(GridData.FILL_BOTH));
    leftright.setLayout(new GridLayout(2, true));

    left = new Composite(leftright, SWT.NONE);
    left.setLayout(new GridLayout(1, true));
    left.setLayoutData(new GridData(GridData.FILL_BOTH));

    right = new Composite(leftright, SWT.NONE);
    right.setLayout(new GridLayout(1, true));
    right.setLayoutData(new GridData(GridData.FILL_BOTH));

    down = new Composite(container, SWT.NONE);

    (new Label(left, SWT.None)).setText(LBL_ATTR_NAME);
    attrList = new List(left, SWT.V_SCROLL | SWT.H_SCROLL);
    attrList.addSelectionListener(listener);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 300;
    attrList.setLayoutData(gd);
    if (attrNames == null || attrNames.isEmpty()) {
      attrList.setItems(new String[]{"There are no attributes"});
      attributesExist = false;
    } else {
      attrList.setItems(attrNames.toArray(new String[0]));
    }

    (new Label(right, SWT.NONE)).setText(LBL_ATTR_VALUE);
    attrValList = new List(right, SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
    attrValList.setBackground(
        EclipseAPI.getDisplay().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND));
    gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 300;
    attrValList.setLayoutData(gd);
    down.setLayout(new GridLayout());
    if (getAttributesExist()) attrList.addSelectionListener(listener);
    Label chValueLabel = new Label(down, SWT.None);
    chValueLabel.setText(LBL_CH_VALUE);
    chValueLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL, GridData.CENTER, true, true));
    addAttributeSelectionListener(
      new SelectionListener(){
      
        @Override
        public void widgetSelected(SelectionEvent e) {
          setPageComplete(false);;
          if(getAttributeChoose())
            if(valsel)
              setPageComplete(true);
        }
      
        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
          // TODO Auto-generated method stub
          
        }
      }
    );
    newAttrValue = new Text(down, SWT.BORDER);
    newAttrValue.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    newAttrValue.addModifyListener(
        new ModifyListener() {

          @Override
          public void modifyText(ModifyEvent e) {
            //	setPageComplete(true);
            setPageComplete(false);
            valsel = false;
            String newVal = newAttrValue.getText();

            getDialogSettings().put(MassOppsArguments.Argument.ATTRIBUTE_TYPE.name(), (String)null);
            getDialogSettings().put(MassOppsArguments.Argument.NEW_VALUE.name(), newVal);
            try {
              dialogSettings.save(MassOppsWizard.FILE_NAME);
            } catch (IOException e1) {
              RLogger.logInfo(e1);
            }
            if(newVal!=null && !newVal.isEmpty()){
              valsel = true;
              if (getAttributeChoose()) setPageComplete(true);
            }
          }
        });
    setControl(container);
  }


  private static String[] dnames = new String[] {};

  @Override
  public String[] getDialogNames() {
    return dnames;
  }

  @Override
  public void reloadSettingsFromDS() {
    // TODO Auto-generated method stub

  }

  @Override
  protected boolean additianalConds() {
    return valsel;
  }
}
