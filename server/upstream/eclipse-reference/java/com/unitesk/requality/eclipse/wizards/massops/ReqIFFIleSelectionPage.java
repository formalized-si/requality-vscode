package com.unitesk.requality.eclipse.wizards.massops;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Text;
import com.unitesk.requality.core.model.TreeDB;

public class ReqIFFIleSelectionPage extends AbstractExtraSettingsPage {

  public static String KEY = "com.unitesk.requality.reqIFFIleName";
  private DialogSettings ds;
  private Text path;
  private Button pathB;

  public ReqIFFIleSelectionPage(DialogSettings ds, Set<String> names, TreeDB db) {
    super("ReqIF file import", ds, names, db);
    this.ds = dialogSettings = ds;
    setTitle("ReqIF file selection page");
  }

  @Override
  public boolean isPageComplete() {
    if (path == null) return false;
    File f = new File(path.getText());
    return f.exists();
  }

  @Override
  public void createControl(Composite parent) {
    //super.createControl(parent);
    Composite fc = new Composite(parent, SWT.NONE);
    fc.setLayout(new GridLayout(2, false));

    path = new Text(fc, SWT.NONE);
    path.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    path.addModifyListener(
        new ModifyListener() {

          @Override
          public void modifyText(ModifyEvent e) {
            String p = path.getText();
            File f = new File(p);
            setPageComplete(false);
            ds.put(KEY, (String) null);
            if (f.exists()) {
              ds.put(KEY, p);
              setPageComplete(true);
              save();
            }
          }
        });

    pathB = new Button(fc, SWT.NONE);
    pathB.addSelectionListener(
        new SelectionListener() {
          public void widgetDefaultSelected(SelectionEvent arg0) {}

          public void widgetSelected(SelectionEvent arg0) {
            FileDialog fd = new FileDialog(getShell());
            fd.setFilterExtensions(new String[] {"*.xml;*.reqif"});
            String name = fd.open();
            path.setText(name);
          }
        });
    pathB.setText("Browse...");
    setControl(fc);
  }

  private void save() {
    try {
      dialogSettings.save(MassOppsWizard.FILE_NAME);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static String[] dnames = new String[] {KEY};

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
    return true;
  }
}
