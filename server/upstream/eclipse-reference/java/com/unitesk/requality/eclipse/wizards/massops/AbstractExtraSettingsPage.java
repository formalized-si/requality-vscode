package com.unitesk.requality.eclipse.wizards.massops;

import java.util.Collections;
import java.util.Set;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.List;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.mass.MassOppsArguments;

public abstract class AbstractExtraSettingsPage extends WizardPage {
  Composite container;
  Composite left;
  Composite right;
  Composite down;
  Composite leftright;
  Set<String> attrNames;
  protected boolean attributesExist = true;
  static final String MES_WRONG_TYPE = "Attributes of selected name has no valid value";
  static final String LBL_ATTR_NAME = "Select a name of the attribute:";
  static final String LBL_ATTR_VALUE = "Values of selected attributes:";
  static final String MES_NOT_SEL = "Attribute name is not selected";
  DialogSettings dialogSettings = null;
  protected boolean attributeChoose = false;
  protected String name;

  TreeDB db;

  List attrList;
  List attrValList;

  public abstract void reloadSettingsFromDS();

  public AbstractExtraSettingsPage(String name, DialogSettings ds, Set<String> names, TreeDB db) {
    super("Extra Settings Page " + name);
    this.db = db;
    this.attrNames = Collections.unmodifiableSet(names);
    setPageComplete(false);
    dialogSettings = ds;
  }

  protected boolean getAttributesExist() {
    return attributesExist;
  }

  protected SelectionListener listener =
      new SelectionListener() {

        @Override
        public void widgetSelected(SelectionEvent e) {
          setPageComplete(false);
          int ind = attrList.getSelectionIndex();
          setAttributeChoose(false);
          dialogSettings.put(MassOppsArguments.Argument.ATTRIBUTE_NAME.name(), "");
          if (ind > -1) {
            setAttributeChoose(true);
            name = attrList.getItem(ind);
            dialogSettings.put(MassOppsArguments.Argument.ATTRIBUTE_NAME.name(), name);
            attrValList.removeAll();
            attrValList.setItems(Attribute.getAttributeValues(db, name));
            if(additianalConds())
              setPageComplete(true);
          }
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
          // TODO Auto-generated method stub

        }
      };

  protected boolean getAttributeChoose() {
    return attributeChoose;
  }

  protected abstract boolean additianalConds();

  private void setAttributeChoose(boolean flag) {
    attributeChoose = flag;
  }

  @Override
  public IWizardPage getPreviousPage() {
    return getWizard().getPage(SelectOppsPage.NAME);
  }
  //private static Set<String> dns = new HashSet<String>(){{{add(Sel)}}};
  public abstract String[] getDialogNames();
  protected void addAttributeSelectionListener(SelectionListener selectionListener) {
    attrValList.addSelectionListener(selectionListener);
  }
}
