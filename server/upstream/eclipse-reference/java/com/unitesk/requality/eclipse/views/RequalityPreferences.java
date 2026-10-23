package com.unitesk.requality.eclipse.views;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.events.IHyperlinkListener;
import org.eclipse.ui.forms.widgets.Hyperlink;

import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.tool.IMMagic;
import com.unitesk.requality.tool.cli.LibreCLI;

public class RequalityPreferences extends PreferencePage implements IWorkbenchPreferencePage {
  public static String BROWSER_ENGINE = "browser_engine";
  public static String NOT_OPEN_EDITORS = "no_engines_editors";
  public static String OPEN_NO_ENGINES_DIALOGS = "no_engines_dialog";
  private Combo list;
  private Label activeEngine;
  private Button openNE;
  private Button notOpenEditors;
  private Text librePath, cachePath, immagicPath;
  private SortedMap<Integer, Short> engState;
  private Map<Integer, Integer> mappingIndex = new HashMap<>();
  private String old = null;
  //text on top of preference page
  private static String desciption_text =
      "Requality requires browser engine to be integrated to Eclipse."
          + "\nForm below shows settings on possible engines."
          + "\nWarning: changes on this page causes restart of eclipse."
          + "\nMore information about engines can be found in official manual:";
  //information about link to manual
  private static String link_text = "Manual";
  private static String link_href = "/com.unitesk.requality/docs/manual/general.en.html";
  private static String cacheTitle = "Global cache location";

  public RequalityPreferences() {}

  public RequalityPreferences(String title) {
    super(title);
  }

  public RequalityPreferences(String title, ImageDescriptor image) {
    super(title, image);
    // TODO Auto-generated constructor stub
  }

  @Override
  public void init(IWorkbench workbench) {}

  @Override
  protected Control createContents(Composite parent) {
    //panel for texts
    Composite desccompose = new Composite(parent, SWT.FILL);
    desccompose.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    desccompose.setLayout(new GridLayout());
    Label descl = new Label(desccompose, SWT.HORIZONTAL);
    descl.setText(desciption_text);
    //Hyperlink to manual
    final Hyperlink desc = new Hyperlink(desccompose, SWT.NONE);
    desc.setHref(link_href);
    desc.setUnderlined(true);
    desc.setForeground(new org.eclipse.swt.graphics.Color(EclipseAPI.getDisplay(), 0, 0, 255));
    desc.setText(link_text);
    //actions in click
    desc.addHyperlinkListener(
        new IHyperlinkListener() {

          @Override
          public void linkExited(HyperlinkEvent e) {
            // TODO Auto-generated method stub

          }

          @Override
          public void linkEntered(HyperlinkEvent e) {
            // TODO Auto-generated method stub

          }

          @Override
          public void linkActivated(HyperlinkEvent e) {
            PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(link_href);
          }
        });
    //panel for browser engine selection
    Group container = new Group(desccompose, SWT.NONE);
    container.setText("Browser Engine:");
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    container.setLayout(gridLayout);
    container.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    //obtaining information about possible engine
    //and their states
    SortedMap<Integer, String> engNames = RequalityPlugin.getBrowserEngineNames();
    engState = RequalityPlugin.getBrowserEnginesState();
    int sel = getSelectedBrowserEngine();
    //current state
    Label l2 = new Label(container, SWT.NONE);
    l2.setText("Active browser engine: ");
    activeEngine = new Label(container, SWT.NONE);
    String engname = engNames.getOrDefault(RequalityPlugin.getBrowserType(),"unknown engine");
    activeEngine.setText(engname);
    //list of browsers
    Label l = new Label(container, SWT.NONE);
    l.setText("Browser engine: ");
    list = new Combo(container, SWT.BORDER);
    list.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    //One special element for automatic browser selection
    list.add("Automatic");
    Iterator<Integer> engIt = engState.keySet().iterator();
    int i = 1;
    //get currently selected engine
    while (engIt.hasNext()) {
      int key = engIt.next();
      String adds =
          " - "
              + ((engState.get(key)) == 2
                  ? "error"
                  : (engState.get(key)) == 1 ? "available" : "not available");
      list.add(engNames.get(key) + adds);
      mappingIndex.put(i++, key);
      if (sel != -1 && sel == key) list.setText(engNames.get(key) + adds);
    }
    list.addModifyListener(
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
            int index = list.getSelectionIndex();
            RequalityPreferences.this.setValid(
                index < 1 || engState.get(mappingIndex.get(index)) != 0);
          }
        });
    if (sel == -1) list.setText(list.getItem(0));

    Group g = new Group(desccompose, SWT.None);
    g.setLayoutData(new GridData(GridData.FILL_BOTH));
    g.setLayout(new GridLayout(1, false));

    IPreferenceStore pref = Activator.getDefault().getPreferenceStore();
    openNE = new Button(g, SWT.CHECK);
    openNE.setText("Show warning dialog \"No available browser engines\"");
    openNE.setSelection(pref.getBoolean(OPEN_NO_ENGINES_DIALOGS));

    notOpenEditors = new Button(g, SWT.CHECK);
    notOpenEditors.setText("Do not open editors if browser engines are not available");
    notOpenEditors.setSelection(pref.getBoolean(NOT_OPEN_EDITORS));
    notOpenEditors.addSelectionListener(
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            openNE.setEnabled(!notOpenEditors.getSelection());
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {}
        });

    openNE.setEnabled(!notOpenEditors.getSelection());
    Group g2 = new Group(desccompose, SWT.NONE);
    g2.setLayoutData(new GridData(GridData.FILL_BOTH));
    g2.setLayout(new GridLayout(1, false));

    l = new Label(g2, SWT.NONE);
    l.setText("Path to libreoffice(v5+) soffice:");

    Composite fsel = new Composite(g2, SWT.NONE);
    fsel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    fsel.setLayout(new GridLayout(2, false));

    librePath = new Text(fsel, SWT.NONE);
    librePath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Button bsel = new Button(fsel, SWT.NONE);
    bsel.setText("Select");
    bsel.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
            dialog.setFilterExtensions(new String[] {"*soffice*"});
            dialog.setFilterPath("");
            String result = dialog.open();
            librePath.setText(result);
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });
    String def = LibreCLI.getDefault().getPath();
    if (def == null || def.isEmpty()) def = "soffice";
    librePath.setText(def);
    l = new Label(g2, SWT.NONE);
    l.setText("Path to ImageMagick:");

    fsel = new Composite(g2, SWT.NONE);
    fsel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    fsel.setLayout(new GridLayout(2, false));
    immagicPath = new Text(fsel, SWT.NONE);
    immagicPath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    bsel = new Button(fsel, SWT.NONE);
    bsel.setText("Select");
    bsel.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.OPEN);
            dialog.setFilterPath("");
            String result = dialog.open();
            immagicPath.setText(result);
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });
    def = IMMagic.getDefault().getPath();
    if (def == null || def.isEmpty()) def = "";
    immagicPath.setText(def);

    l = new Label(g2, SWT.NONE);
    l.setText(cacheTitle);

    fsel = new Composite(g2, SWT.NONE);
    fsel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    fsel.setLayout(new GridLayout(2, false));
    cachePath = new Text(fsel, SWT.NONE);
    cachePath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    bsel = new Button(fsel, SWT.NONE);
    bsel.setText("Select");
    bsel.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.OPEN);
            dialog.setFilterPath("");
            String result = dialog.open();
            cachePath.setText(result);
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });
    DialogSettings ds = new DialogSettings("globCache");
    //ds.put("path", val);
    try {
      ds.load("globCache.ini");
    } catch (IOException e) {
    }
    old = ds.get("path");
    if(old == null)
      old = "";
    cachePath.setText(old);
    //		inkscapePath = new Text(fsel,SWT.NONE);
    //		inkscapePath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    //		bsel = new Button(fsel,SWT.NONE);
    //		bsel.setText("Select");
    //		bsel.addSelectionListener(new SelectionListener() {
    //
    //			@Override
    //			public void widgetSelected(SelectionEvent e) {
    //				DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.OPEN);
    //				   dialog.setFilterPath("");
    //				   String result = dialog.open();
    //				   inkscapePath.setText(result);
    //			}
    //
    //			@Override
    //			public void widgetDefaultSelected(SelectionEvent e) {
    //				// TODO Auto-generated method stub
    //
    //			}
    //		});
    //		def = Inkscape.getDefault().getPath();
    //		if(def == null || def.isEmpty())
    //			def = "inkscape";
    //		inkscapePath.setText(def);
    return desccompose;
  }
  //programmatically call restart
  private void callRestart() {
    Activator.getDefault().getWorkbench().getDisplay();
    EclipseAPI.getDisplay()
        .asyncExec(
            new Runnable() {
              public void run() {
                Activator.getDefault().getWorkbench().restart();
              }
            });
  }
  //actions when new selection is performed
  @Override
  public boolean performOk() {
    IPreferenceStore pref = Activator.getDefault().getPreferenceStore();
    int lsel = list.getSelectionIndex();
    boolean isOk = true, callrestart = false;
    LibreCLI.getDefault().setTempPath(librePath.getText());
    LibreCLI.getDefault().saveSettings();

    IMMagic.getDefault().setTempPath(immagicPath.getText());
    IMMagic.getDefault().saveSettings();

    //        Inkscape.getDefault().setTempPath(inkscapePath.getText());
    //        Inkscape.getDefault().saveSettings();
    if (lsel > 0){
        int sel = mappingIndex.get(lsel);
        if ((!pref.contains(BROWSER_ENGINE)||lsel!=pref.getInt(BROWSER_ENGINE))&&engState.get(sel)!=0){
            pref.setValue(BROWSER_ENGINE, sel);
            callrestart =  mappingIndex.get(lsel)!=RequalityPlugin.getBrowserType();
        }
        if(engState.get(sel)==0)
        	isOk = false;
    }else{
        if((!pref.contains(BROWSER_ENGINE)||pref.getInt(BROWSER_ENGINE)!=-1) &&lsel == 0)
        {
            pref.setValue(BROWSER_ENGINE, -1);
            int targ = -1;
            if (engState.get(SWT.MOZILLA)==1) {
            	targ = SWT.MOZILLA;
            } else if (engState.get(SWT.WEBKIT)==1) {
                targ = SWT.WEBKIT;
            }
            callrestart = RequalityPlugin.getBrowserType()!=targ;
        }
    }
    if (notOpenEditors.getSelection() != pref.getBoolean(NOT_OPEN_EDITORS)) {
      pref.setValue(NOT_OPEN_EDITORS, notOpenEditors.getSelection());
    }
    if (openNE.getSelection() != pref.getBoolean(OPEN_NO_ENGINES_DIALOGS)) {
      pref.setValue(OPEN_NO_ENGINES_DIALOGS, openNE.getSelection());
    }
    callrestart = callrestart || setGC(cachePath.getText());
    if (callrestart) {
      if (MessageDialog.openConfirm(
          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
          "Requality Preferences",
          "You will need to restart Eclipse for the changes to take effect. Would you like to restart now?"))
        callRestart();
    }
    return isOk;
  }
  private boolean setGC(String val){
    DialogSettings ds = new DialogSettings("globCache");
    ds.put("path", val);
    try {
      ds.save("globCache.ini");
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return !val.equals(old);
  }
  //Changing settings to default
  @Override
  protected void performDefaults() {
    IPreferenceStore pref = Activator.getDefault().getPreferenceStore();
    pref.setValue(BROWSER_ENGINE, pref.getDefaultInt(BROWSER_ENGINE));
    pref.setValue(NOT_OPEN_EDITORS, pref.getDefaultBoolean(NOT_OPEN_EDITORS));
    pref.setValue(OPEN_NO_ENGINES_DIALOGS, pref.getDefaultBoolean(OPEN_NO_ENGINES_DIALOGS));
    list.select(0);
    notOpenEditors.setSelection(false);
    cachePath.setText("");
    setGC("");
    openNE.setSelection(true);
    LibreCLI.getDefault().setTempPath(null);
    LibreCLI.getDefault().saveSettings();
    openNE.setEnabled(!notOpenEditors.getSelection());
    super.performDefaults();
  }

  public static int getSelectedBrowserEngine() {
    return (Activator.getDefault().getPreferenceStore().contains(BROWSER_ENGINE))
        ? Activator.getDefault().getPreferenceStore().getInt(BROWSER_ENGINE)
        : -1;
  }

  static {
    Activator.getDefault().getPreferenceStore().setDefault(BROWSER_ENGINE, -1);
    Activator.getDefault().getPreferenceStore().setDefault(NOT_OPEN_EDITORS, false);
    Activator.getDefault().getPreferenceStore().setDefault(OPEN_NO_ENGINES_DIALOGS, true);
  }

  public static void setSelectedBrowserEngine(int i) {
    Activator.getDefault().getPreferenceStore().setValue(BROWSER_ENGINE, i);
  }
}
