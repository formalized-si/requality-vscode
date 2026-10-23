package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.report.TraceabilityDataSource;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TraceabilityReportSettingsDialog extends Dialog {
  TreeNode req;
  Object filter;
  final Map<String, Integer> cnt = new HashMap<>();
  Map<Integer, String[]> settings;
  boolean showHideMech = true;
  String ftpl, stpl;
  int groupsnum = -1;
  private static String[]
      texts =
          new String[] {
            "{user-visible-name}",
            "{id}",
            "{name-or-id}",
            "{attribute-name}",
            "{revert-attribute-name}"
          },
      links =
          new String[] {"", "{this-page}#{value}", "{next-page}#{value}", "{requality-cnf}{value}"},
      referror =
          new String[] {
            "<span style='margin-left:40px;color: red;'>{value}</span>",
            "",
            "{user-visible-name}",
            "{id}",
            "{name-or-id}",
            "{attribute-name}",
            "{revert-attribute-name}",
            "{skip-line}"
          },
      linkerror =
          new String[] {
            "<span style='margin-left:40px;color: red;'>{value}</span>",
            "",
            "{user-visible-name}",
            "{id}",
            "{name-or-id}",
            "{attribute-name}",
            "{revert-attribute-name}",
            "{skip-line}"
          };
  List refs;
  Composite p;
  Composite setts;
  boolean changed = false;
  String def = "";
  Map<Integer, Composite> groupComp = new HashMap<>();
  Map<Integer, Label> groupLabel = new HashMap<>();
  public Map<Integer, Combo> groupText = new HashMap<>(),
      groupLink = new HashMap<>(),
      groupRefError = new HashMap<>(),
      groupLinkError = new HashMap<>();
  public Combo te;
  public Text templ1, templ2;
  private Image lImage;

  @Override
  protected org.eclipse.swt.widgets.Control createContents(Composite parent) {
    parent.getShell().setText("Настройки отчета о трассируемости");
    
    if (settings != null && settings.size() > 0) groupsnum = settings.size();
    p = new Composite(parent, SWT.NONE);
    p.setLayout(new GridLayout(2, false));
    GridData gd = new GridData();
    gd.widthHint = 700;
    gd.heightHint = 600;
    parent.setLayoutData(gd);
    p.setLayoutData(gd);
    new Label(p, SWT.NONE).setText("Select target Reference name:");
    ;

    Composite c2 = new Composite(p, SWT.NONE);
    gd = new GridData(GridData.FILL_BOTH);
    gd.verticalSpan = 2;
    c2.setLayoutData(gd);
    c2.setLayout(new GridLayout(1, true));

    refs = new List(p, SWT.NONE);
    gd = new GridData(GridData.FILL_BOTH);
    refs.setLayoutData(gd);

    final TreeNode req = this.req;
    final Map<String, Integer> cnt = this.cnt;
    final List refs = this.refs;
    WorkspaceJob j = new WorkspaceJob("Init refs data"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
         if(req == null)
          return Status.CANCEL_STATUS;
        DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(req, true, true, false);
        Iterator<TreeNode> iter = dftw.iterator();
        
        int sel = -1;
        while (iter.hasNext()) {
          TreeNode node = iter.next();
          if(node!=null)
            for (Attribute a : node.attrs()) {
              if (a.getType().equals(AttributeType.REFERENCE)) {
                if (!cnt.containsKey(a.getKey())) {
                  if (filter != null && filter instanceof String && filter.equals(a.getKey()))
                    //TODO list
                    sel = cnt.keySet().size();
                  cnt.put(a.getKey(), 0);
                }
                cnt.put(a.getKey(), cnt.get(a.getKey()) + 1);
              }
            }
        }
        int fsel = sel;
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
          @Override
          public void run() {
            if(refs.isDisposed())
              return;
            for (String s : cnt.keySet()) refs.add(s + "(" + cnt.get(s) + ")");
            if (fsel > -1) refs.select(new ArrayList<>(cnt.keySet()).indexOf(filter));
            refs.setEnabled(true);
          }
        });
        return Status.OK_STATUS;
      }
      
    };
    refs.setEnabled(false);
    j.schedule();
    refs.addSelectionListener(
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            if(refs.isDisposed())
              return;
            if (refs.getSelectionIndex() > -1)
              filter = cnt.keySet().toArray()[refs.getSelectionIndex()];
            //TODO list
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });

    Label l = new Label(c2, SWT.NONE);
    l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    //Display.getCurrent();
    lImage = Activator.getImageDescriptor("/icons/traceabilityhelp.png").createImage();
    l.setImage(lImage);
    
    l.addDisposeListener(new DisposeListener(){

      @Override
      public void widgetDisposed(DisposeEvent e) {
        lImage.dispose();
      }
    });
    
    (new Label(c2, SWT.NONE))
        .setText(
            "Template options for traceability report. Please select required \n number of groups and edit settings for groups.");

    Composite cn = new Composite(c2, SWT.BORDER);
    cn.setLayoutData(new GridData(GridData.FILL_BOTH));
    cn.setLayout(new GridLayout(1, true));

    l = new Label(cn, SWT.NONE);
    l.setText("Primary template:");
    templ1 = new Text(cn, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    templ1.setLayoutData(gd);
    templ1.setText(TraceabilityDataSource.defaultTemplates[0]);
    if (ftpl != null) templ1.setText(ftpl);

    l = new Label(cn, SWT.NONE);
    l.setText("Secondaty template:");
    templ2 = new Text(cn, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    templ2.setLayoutData(gd);
    templ2.setText(TraceabilityDataSource.defaultTemplates[1]);
    if (stpl != null) templ2.setText(stpl);
    l = new Label(cn, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    l.setLayoutData(gd);
    l.setText("Groups options. Number of groups:");

    if (groupsnum == -1) groupsnum = 4;
    final Text tm = new Text(cn, SWT.NONE);
    tm.setText(Integer.toString(groupsnum));
    tm.addModifyListener(
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
            if(tm.isDisposed())
              return;
            int newnum = Integer.parseInt(tm.getText());
            if (newnum > groupsnum) {
              for (int i = groupsnum; i < newnum; i++) newComposite(i);
            } else if (newnum < groupsnum) {
              for (int i = newnum; i < groupsnum; i++) {
                removeComposite(i);
              }
            }
            groupsnum = newnum;
          }
        });

    ScrolledComposite sc = new ScrolledComposite(cn, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    sc.setAlwaysShowScrollBars(true);
    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;
    sc.setLayoutData(gd);
    sc.setLayout(new GridLayout(1, true));
    sc.setExpandHorizontal(true);
    sc.setExpandVertical(true);

    setts = new Composite(sc, SWT.NONE);
    setts.setLayout(new GridLayout(2, false));
    gd = new GridData(GridData.FILL_VERTICAL);
    gd.widthHint = sc.getSize().x;
    setts.setLayoutData(gd);
    sc.setContent(setts);

    new Label(setts, SWT.NONE).setText("N");
    Composite comp = new Composite(setts, SWT.NONE);
    comp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    comp.setLayout(new GridLayout(4, true));

    l = new Label(comp, SWT.NONE);
    l.setText("Text");
    l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    l = new Label(comp, SWT.NONE);
    l.setText("Link");
    l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    l = new Label(comp, SWT.NONE);
    l.setText("Text on\nmissing ref.");
    l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    l = new Label(comp, SWT.NONE);
    l.setText("Text on\nwrong link");
    l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    for (int i = 0; i < groupsnum; i++) newComposite(i);
    te = new Combo(cn, SWT.READ_ONLY);
    te.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    te.add("Enable Hide\\Show mechanism");
    te.add("Disable Hide\\Show mechanism");
    te.select(showHideMech ? 0 : 1);
    //parent.getShell().layout(true,true);
    Composite bc = new Composite(c2, SWT.NONE);
    bc.setLayout(new GridLayout(2, false));
    bc.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
    Button cancelB = new Button(bc, SWT.NONE), okB = new Button(bc, SWT.NONE);

    okB.setText("Ok");
    gd = new GridData();
    gd.widthHint = 100;
    okB.setLayoutData(gd);
    okB.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(!okB.isDisposed())
              okPressed();
          }
        });

    cancelB.setText("Cancel");
    gd = new GridData();
    gd.widthHint = 100;
    cancelB.setLayoutData(gd);
    cancelB.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(!cancelB.isDisposed())
              cancelPressed();
          }
        });

    parent.layout(true, true);
    return p;
  }

  private void removeComposite(int i) {
    groupComp.get(i).dispose();
    groupLabel.get(i).dispose();
    groupComp.remove(i);
    groupRefError.remove(i);
    groupLink.remove(i);
    groupText.remove(i);
    groupLabel.remove(i);

    ((ScrolledComposite) setts.getParent())
        .setMinSize(setts.getSize().x, setts.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);
    setts.getParent().layout(true, true);
  }

  private void newComposite(int i) {
    Label l = new Label(setts, SWT.NONE);
    l.setText("Group " + Integer.toString(i + 1) + ":");
    groupLabel.put(i, l);

    Composite comp = new Composite(setts, SWT.NONE);
    comp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    comp.setLayout(new GridLayout(4, true));
    groupComp.put(i, comp);

    Combo text = new Combo(comp, SWT.NONE);
    text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    for (String s : texts) text.add(s);
    groupText.put(i, text);

    Combo link = new Combo(comp, SWT.NONE);
    link.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    for (String s : links) link.add(s);
    groupLink.put(i, link);

    Combo error = new Combo(comp, SWT.NONE);
    error.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    for (String s : referror) error.add(s);
    groupRefError.put(i, error);

    Combo lerror = new Combo(comp, SWT.NONE);
    lerror.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    for (String s : linkerror) lerror.add(s);
    groupLinkError.put(i, lerror);

    ((ScrolledComposite) setts.getParent())
        .setMinSize(setts.getSize().x, setts.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);
    setts.getParent().layout(true, true);
    if (settings.containsKey(i)) {
      text.setText(settings.get(i)[0]);
      link.setText(settings.get(i)[1]);
      error.setText(settings.get(i)[2]);
      lerror.setText(settings.get(i)[3]);
    } else {
      if (i < TraceabilityDataSource.defaultSettings.length) {
        text.setText(TraceabilityDataSource.defaultSettings[i][0]);
        link.setText(TraceabilityDataSource.defaultSettings[i][1]);
        error.setText(TraceabilityDataSource.defaultSettings[i][2]);
        lerror.setText(TraceabilityDataSource.defaultSettings[i][3]);
      } else {
        text.select(0);
        link.select(0);
        error.select(0);
        lerror.select(0);
      }
    }
  }

  TreeNode rep;
  Shell shell;

  public TraceabilityReportSettingsDialog(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = rep;
    req =
        rep.getTreeDB()
            .getNode(((ReportSettings) rep).getStartRequirementQId(), Path.QID | Path.UV);
    Attribute ft = rep.attrs().getAttribute("filter");
    if (ft != null && ft.getType() == AttributeType.STRING)
      filter = ft.getValue().toString();
    settings = new HashMap<>();
    stpl = ftpl = null;
    Attribute a = rep.attrs().getAttribute("traceabilitySettings");
    if (a != null) {
      if (a.getListType() == AttributeType.LIST) {
        for (Object o : ((ListValue) a.getValue()).getValues()) {
          if (o instanceof ListValue) {
            ListValue oa = (ListValue) o;
            if (oa.getType() == AttributeType.STRING) {
              if (oa.getValues().size() > 2) {
                java.util.List<String> l = new ArrayList<>();
                for (Object o2 : oa.getValues()) l.add(o2.toString());
                settings.put(settings.keySet().size(), l.toArray(new String[0]));
              } else {
                ftpl = (String) oa.getValues().get(0);
                stpl = (String) oa.getValues().get(1);
              }
            }
          }
        }
      }
    }
    showHideMech = true;
    a = rep.attrs().getAttribute("toggleEnabled");
    if (a != null)
      showHideMech = a.getValue().toString().toLowerCase().equals("true");
  }

  @Override
  protected void okPressed() {
    try {
      if(rep == null)
        return;
      ArrayList<Object> k = new ArrayList<>(), m;
      m = new ArrayList<>();
      m.add(templ1.getText());
      m.add(templ2.getText());
      k.add(new ListValue(AttributeType.STRING, m));
      for (int i = 0; i < groupsnum; i++) {
        m = new ArrayList<>();
        m.add(groupText.get(i).getText());
        m.add(groupLink.get(i).getText());
        m.add(groupRefError.get(i).getText());
        m.add(groupLinkError.get(i).getText());
        k.add(new ListValue(AttributeType.STRING, m));
      }
      boolean tesel = te.getSelectionIndex() == 0;
      final TreeNode rep = this.rep;
      final Object filter = this.filter;
      
      WorkspaceJob j = new WorkspaceJob("Change traceability settings"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(rep == null)
             return Status.CANCEL_STATUS;
          boolean started = rep.getTreeDB().startTransaction("Change report settings");
          try {
            if (!started) return Status.CANCEL_STATUS;
            if (filter != null && filter instanceof String)
              rep.attrs().setConstAttribute("filter", filter.toString(),0);
            
            rep.attrs().setConstAttribute("traceabilitySettings", new ListValue(AttributeType.LIST, k),0);
            rep.attrs().setConstAttribute("toggleEnabled", tesel,0);
  
            rep.commitAttributes();
            if(started) {
              rep.getTreeDB().commit();
              started = false;
            }
          }finally {
            if(started)
              rep.getTreeDB().rollback();
          }

          return Status.OK_STATUS;
        }
        
      };
      j.schedule();
      super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    }
  };
}
