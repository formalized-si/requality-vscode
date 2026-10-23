package com.unitesk.requality.eclipse.views.panels;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

public class ElementDescriptionPanel{
  private CTabFolder ctf=null;
  private TabFolder ctfa;
  private Text adesc,adescprev;
  CTabItem d1,d2;
  TabItem d1a=null,d2a=null;
  private boolean isDirty = false;
  
  private ModifyListener ml = new ModifyListener(){
    @Override
    public void modifyText(ModifyEvent e) {
      isDirty = true;
    }
  };
  public boolean isDirty(){
    return isDirty;
  }
  public ElementDescriptionPanel(TabbedPropertySheetWidgetFactory wf,String text, Composite parent, int i) {
    if (wf != null){
      ctf = wf.createTabFolder(parent, SWT.NONE);
      d1 = wf.createTabItem(ctf, SWT.NONE);
      d1.setText(text);
      Composite c1 = new Composite(ctf, SWT.NONE);
      c1.setLayout(new FillLayout());
      adesc = wf.createText(c1, "", i);
      adesc.addModifyListener(ml);
      d1.setControl(c1);
      
      //Group d2 = wf.createGroup(parent, "Preview");
      d2 = wf.createTabItem(ctf, SWT.NONE);
      d2.setText("Preview");
      Composite c2 = new Composite(ctf, SWT.NONE);
      c2.setLayout(new FillLayout());
      adescprev = wf.createText(c2, "", i|SWT.READ_ONLY);
      d2.setControl(c2);
      ctf.setSelection(0);
    }
    else{
      ctfa = new TabFolder(parent, SWT.NONE);
      d1a = new TabItem(ctfa, SWT.NONE);
      d1a.setText(text);
      Composite c1 = new Composite(ctfa, SWT.NONE);
      c1.setLayout(new FillLayout());
      adesc = new Text(c1, i);
      adesc.addModifyListener(ml);
      d1a.setControl(c1);
      
      //Group d2 = wf.createGroup(parent, "Preview");
      d2a = new TabItem(ctfa, SWT.NONE);
      d2a.setText("Preview");
      Composite c2 = new Composite(ctfa, SWT.NONE);
      c2.setLayout(new FillLayout());
      adescprev = new Text(c2, i|SWT.READ_ONLY);
      d2a.setControl(c2);
      ctfa.setSelection(0);
    }
  }

  public Control getControl() {
    return ctf!=null?ctf:ctfa;
  }

  public void setDocument(String docu,String rval) {
    adesc.removeModifyListener(ml);
    isDirty = false;
    if (docu != null) adesc.setText(docu);
    else adesc.setText("");
    if (rval != null) adescprev.setText(rval);
    else adescprev.setText("");
    adesc.addModifyListener(ml);
  }

  public Text getEditControl() {
    // TODO Auto-generated method stub
    return adesc;
  }

  public void setFirstLabel(String string) {
    if(d1!=null)
      d1.setText(string);
    else
      d1a.setText(string); 
  }
  public ModifyListener getDirtyListener() {
    return ml;
  }
}
