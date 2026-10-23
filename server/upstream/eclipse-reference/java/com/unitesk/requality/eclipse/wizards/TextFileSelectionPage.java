package com.unitesk.requality.eclipse.wizards;

import java.io.File;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.wizard.WizardPage;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class TextFileSelectionPage extends WizardPage implements IUpdatablePage {

  public static String KEY = "com.unitesk.requality.reqIFFIleName";
  private DialogSettings ds;
  public static byte ONLY_EXISTS = 1, ONLY_NOT_EXISTS = 2, DOESNT_MATTER = 4, SAVE = 8, LOAD = 16;
  private byte activefilter;
  private String desc;
  private Text path;
  private String[] filt;
  private Button pathB;
  private String name;

  public String getVarName() {
    return name;
  }

  public TextFileSelectionPage(
      String name, String title, String desc, DialogSettings ds, String[] names, byte checkexists) {
    super(name);
    setDescription("");
    this.desc = desc;
    if(this.desc != null){
      StringBuilder sb = new StringBuilder();
      int start = 0;
      while(start < this.desc.length()){
        int mind = this.desc.substring(start+1).indexOf('\n');
        int targ = Math.min(start + 110, this.desc.length());
        if(mind!=-1)
          targ = Math.min(mind+start+1, targ);
        if(targ!=this.desc.length())
          targ = closest(targ ,desc.substring(0, targ).lastIndexOf(" ")+1, desc.indexOf(" ",targ));
        String add = desc.substring(start, targ);
        if(add.startsWith("\n"))
          sb.append(add.substring(1));
        else
          sb.append(add);
        if(sb.charAt(sb.length()-1)!='\n')
          sb.append("\n");
        start = targ;
      }
      sb.setLength(sb.length()-1);
      this.desc = sb.toString();
    }
    this.name = name;
    //super("ReqIF file import",ds, names, db);
    this.filt = names;
    this.ds = ds;
    activefilter = checkexists;
    setTitle(title);
  }

  private static int closest(int targ, int lastIndexOf, int indexOf) {
    if((indexOf-targ)<(targ-lastIndexOf))
      return indexOf;
    return lastIndexOf;
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
            update();
          }
        });

    pathB = new Button(fc, SWT.NONE);
    pathB.addSelectionListener(
        new SelectionListener() {
          public void widgetDefaultSelected(SelectionEvent arg0) {}

          public void widgetSelected(SelectionEvent arg0) {
            FileDialog fd =
                new FileDialog(
                    getShell(),
                    (activefilter & SAVE) == SAVE
                        ? SWT.SAVE
                        : (activefilter & LOAD) == LOAD ? SWT.OPEN : SWT.NONE);
            if (filt != null) {
              String[] cln = new String[filt.length];
              for (int i = 0; i < filt.length; i++) cln[i] = "*." + filt[i];
              fd.setFilterExtensions(cln);
              fd.setFileName("*." + filt[0]);
            }
            String paths = ds.get(name);
            if (paths != null) {
              File f = new File(paths);
              if (f.getParentFile() != null && f.getParentFile().exists())
                fd.setFilterPath(f.getParent());
            }
            String name = fd.open();
            path.setText(name);
          }
        });
    pathB.setText("Browse...");
    Label d = new Label(fc, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    d.setLayoutData(gd);
    if(desc != null)
      d.setText(desc);
    setControl(fc);
  }

  private void save() {
    ds.put(name, path.getText());
  }

  @Override
  public void update() {
    setPageComplete(false);
    String p = path.getText();
    File f = new File(p);
    ds.put(name, (String) null);
    if ((activefilter & ONLY_EXISTS) == ONLY_EXISTS && f.exists()
        || (activefilter & ONLY_NOT_EXISTS) == ONLY_NOT_EXISTS && !f.exists()
        || (activefilter & DOESNT_MATTER) == DOESNT_MATTER) {
      ds.put(name, p);
      setPageComplete(true);
      save();
    }
  }
}
