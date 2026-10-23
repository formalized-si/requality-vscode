package com.unitesk.requality.eclipse.wizards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.SpecObjectType;

public class ReqMappingPage extends WizardPage {
  DialogSettings ds = null;
  ImportFromReqIFWizard wiz;

  public ReqMappingPage(DialogSettings dialogSettings, ImportFromReqIFWizard wizard) {
    super("MapReqIFTypes");
    setTitle("Types mapping");
    setDescription("Map Requality types to ReqIF elements to be imported");
    ds = dialogSettings;
    wiz = wizard;
  }

  List type, reqtype, result;
  Control con;
  protected boolean firstPass = true;
  private Map<String, String> mapping = new HashMap<>();
  private Map<String, String> mappingIndex = new HashMap<>();

  @Override
  protected org.eclipse.jface.dialogs.IDialogSettings getDialogSettings() {
    return ds;
  };

  private boolean pressed = false;

  @Override
  public void createControl(Composite parent) {
    con = parent = new Composite(parent, SWT.NONE);
    con.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setLayout(new GridLayout(1, true));
    GridLayout gl = new GridLayout(2, true);
    gl.marginBottom = gl.marginRight = gl.marginLeft = gl.marginTop = 0;
    Composite lists = new Composite(parent, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalIndent = gd.verticalIndent = 0;
    lists.setLayoutData(gd);
    lists.setLayout(gl);
    Composite sublist = new Composite(lists, SWT.NONE);
    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalIndent = gd.verticalIndent = 0;
    sublist.setLayoutData(gd);
    gl = new GridLayout(2, true);
    gl.marginBottom =
        gl.marginRight = gl.marginLeft = gl.marginTop = gl.marginHeight = gl.marginWidth = 0;
    sublist.setLayout(gl);

    type = new List(sublist, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalIndent = gd.verticalIndent = 0;
    type.setLayoutData(gd);
    reqtype = new List(sublist, SWT.H_SCROLL | SWT.V_SCROLL);
    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalIndent = gd.verticalIndent = 0;
    reqtype.setLayoutData(gd);
    updateSecondField();
    result = new List(lists, SWT.H_SCROLL | SWT.V_SCROLL);
    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalIndent = gd.verticalIndent = 0;
    result.setLayoutData(gd);
    Composite buttons = new Composite(parent, SWT.NONE);
    buttons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    buttons.setLayout(new RowLayout());
    Button addLink = new Button(buttons, SWT.NONE);
    addLink.setText("Add mapping");
    addLink.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            int sel2 = reqtype.getSelectionIndex();
            int[] sel1 = type.getSelectionIndices();
            String[] sels = type.getSelection();
            if (sel2 > -1 && sel1.length > 0) {
              for (String i : sels) {
                if (mapping.containsKey(i)) {
                  mapping.remove(i);
                  String ind = mappingIndex.get(i);
                  mappingIndex.remove(i);
                  result.remove(ind);
                }
                addLink(i, reqtype.getSelection()[0]);
              }
            }
          }
        });
    final Button removeLink = new Button(buttons, SWT.NONE);
    removeLink.setText("Remove mapping");
    removeLink.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            int sel = result.getSelectionIndex();
            if (sel > -1) {
              String val = result.getSelection()[0];
              result.remove(val);
              String mi = null;
              for (String s : mappingIndex.keySet()) if (mappingIndex.get(s).equals(val)) mi = s;
              mappingIndex.remove(mi);
              mapping.remove(mi);
            }
          }
        });
    type.addListener(
        SWT.KeyDown,
        new Listener() {

          @Override
          public void handleEvent(Event event) {
            if (event.keyCode == 262144) pressed = true;
          }
        });
    type.addListener(
        SWT.KeyUp,
        new Listener() {

          @Override
          public void handleEvent(Event event) {
            if (event.keyCode == 262144) pressed = false;
          }
        });
    type.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            result.deselectAll();
            removeLink.setEnabled(false);
            if (type.getSelectionCount() == 1 && pressed) {
              String sel = type.getSelection()[0];
              ArrayList<String> tosel = new ArrayList<>();
              for (String s : type.getItems()) {
                if (isFirstColumnSame(s, sel)) tosel.add(s);
              }
              type.setSelection(tosel.toArray(new String[0]));
            }
          }
        });
    reqtype.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            result.deselectAll();
            removeLink.setEnabled(false);
          }
        });
    result.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            reqtype.deselectAll();
            type.deselectAll();
            removeLink.setEnabled(true);
          }
        });

    removeLink.setEnabled(false);
    setControl(con);
    updateFirstField();
    updateSecondField();
  }

  public void initFirstField(Set<String> str) {
    for (String s : type.getItems())
      if (!str.contains(s)) {
        type.remove(s);
        mapping.remove(s);
        String s2 = mappingIndex.get(s);
        if (s2 != null) result.remove(s2);
        mappingIndex.remove(s);
      }
    Set<String> s2 = new HashSet<>();
    for (String s : type.getItems()) s2.add(s);
    Set<String> reqtypes = new HashSet<>();
    for (String s : reqtype.getItems()) reqtypes.add(s);
    if (str == null) return;
    for (String s : str)
      if (!s2.contains(s)) {
        type.add(s);
        firstPass = true;
        for (String k : reqtypes) if (isClose(s, k)) addLink(s, k);
        firstPass = false;
        for (String k : reqtypes) if (!mappingIndex.containsKey(s) && isClose(s, k)) addLink(s, k);
      }
  }

  protected boolean isFirstColumnSame(String s, String s2) {
    if (s.indexOf(".") == -1 || s2.indexOf(".") == -1) return false;
    return s.substring(s.indexOf(".")).equals(s2.substring(s2.indexOf(".")));
  }

  protected boolean isClose(String s, String k) {
    return firstPass ? s.equals(k) : k.equals(Requirement.TYPE_NAME);
  }

  public void initSecondField(Set<String> set) {
    for (String s : reqtype.getItems())
      if (!set.contains(s)) {
        for (String s2 : mapping.keySet())
          if (mapping.get(s2).equals(s)) {
            type.remove(s2);
            mapping.remove(s2);
            String s3 = mappingIndex.get(s2);
            if (s3 != null) result.remove(s3);
            mappingIndex.remove(s2);
          }
      }
    Set<String> s2 = new HashSet<>();
    for (String s : type.getItems()) s2.add(s);
    Set<String> reqtypes = new HashSet<>();
    for (String s : reqtype.getItems()) reqtypes.add(s);
    for (String s : set)
      if (!reqtypes.contains(s)) {
        reqtype.add(s);
        firstPass = true;
        for (String k : s2) if (isClose(k, s)) addLink(k, s);
        firstPass = false;
        for (String k : s2) if (!mappingIndex.containsKey(s) && isClose(k, s)) addLink(k, s);
        //if(s2.contains(s))
        //addLink(s, s);
      }
  }

  protected void addLink(String i, String string) {
    if (mapping.containsKey(i) && mapping.get(i).equals(string)) return;
    if (mapping.containsKey(i)) result.remove(mappingIndex.get(i));
    mapping.put(i, string);
    String ret = i + " -> " + string;
    mappingIndex.put(i, ret);
    result.add(ret);
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    updateFirstField();
    updateSecondField();
  };

  public void updateFirstField() {
    IFile f = ((ImportFromReqIFWizard) getWizard()).getSelectedFile();
    if (f != null) {
      ReqIFModel d = wiz.getModel();
      if (d != null) {
        Set<String> str = new HashSet<>();
        for (SpecObjectType sot : d.getSpecObjectTypes()) str.add(sot.getNameOrId());
        initFirstField(str);
      } else ;
      //TODO
    }
  }

  public void updateSecondField() {
    initSecondField(RequalityPlugin.getAdditionalTypes().keySet());
  }

  public Map<String, String> getMapping() {
    return mapping;
  }
}
