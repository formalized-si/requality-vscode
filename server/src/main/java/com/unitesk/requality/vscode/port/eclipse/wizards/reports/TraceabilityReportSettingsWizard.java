package com.unitesk.requality.vscode.port.eclipse.wizards.reports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.ScrolledComposite;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.report.TraceabilityDataSource;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TraceabilityReportSettingsWizard extends Wizard {
  TreeNode req;
  Object filter;
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

  class TraceabilitySettPage extends WizardPage {
    protected TraceabilitySettPage() {
      super("TraceabilityReportSettings");
    }

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
    final Map<String, Integer> cnt = new HashMap<>();
    
    @Override
    public void createControl(Composite parent) {
      if (settings != null && settings.size() > 0) groupsnum = settings.size();
      p = new Composite(parent, SWT.NONE);
      parent.setLayout(new GridLayout(1, true));
      GridData gd = new GridData(GridData.FILL_BOTH);
      gd.minimumWidth = 200;
      gd.widthHint = 400;
      parent.setLayoutData(gd);
      p.setLayoutData(gd);
      p.setLayout(new GridLayout(2, false));
      new Label(p, SWT.NONE).setText("Select target Reference name:");
      ;

      Composite c2 = new Composite(p, SWT.NONE);
      gd = new GridData(GridData.FILL_BOTH);
      gd.verticalSpan = 2;
      c2.setLayoutData(gd);
      c2.setLayout(new GridLayout(1, true));

      refs = new List(p, SWT.NONE);
      gd = new GridData(GridData.FILL_BOTH);
      gd.minimumWidth = 200;
      gd.minimumHeight = 400;
      refs.setLayoutData(gd);
      refs.setEnabled(false);
      final TreeNode req = TraceabilityReportSettingsWizard.this.req;
      final List refs = this.refs;
      final Map<String, Integer> cnt = this.cnt;
      WorkspaceJob j = new WorkspaceJob("Update refs list"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(req == null)
            return Status.CANCEL_STATUS;
          DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(req, true, true, false);
          Iterator<TreeNode> iter = dftw.iterator();
          int sel = -1;
          while (iter.hasNext()) {
            TreeNode node = iter.next();
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
          for (String s : cnt.keySet()) refs.add(s + "(" + cnt.get(s) + ")");
          if (sel > -1)
            EclipseAPI.getDisplay().asyncExec(new Runnable(){
              @Override
              public void run() {
                if(refs.isDisposed())
                  return;
                refs.select(new ArrayList<>(cnt.keySet()).indexOf(filter));
              }
            });
          EclipseAPI.getDisplay()
          .asyncExec(new Runnable(){
            @Override
            public void run() {
              if(refs.isDisposed())
                return;
              refs.setEnabled(true); 
            }
          });
          return Status.OK_STATUS;
        }
        
      };
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
      Image im = Activator.getImageDescriptor("/icons/traceabilityhelp.png").createImage();
      l.setImage(im);
      l.addDisposeListener(new DisposeListener() {
        
        @Override
        public void widgetDisposed(DisposeEvent e) {
          im.dispose();
        }
      });
      (new Label(c2, SWT.NONE))
          .setText(
              "Hyperlinks options. Numbered options below are related to\n elements marked on image.");

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

      setControl(p);
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
  }

  TreeNode rep;

  public TraceabilityReportSettingsWizard(TreeNode rep) {
    super();
    this.rep = rep;
    Attribute ft = rep.attrs().getAttribute("filter");
    if (ft != null && ft.getType() == AttributeType.STRING)
      filter = ft.getValue().toString();
    req =
        rep.getTreeDB()
            .getNode(((ReportSettings) rep).getStartRequirementQId(), Path.QID | Path.UV);
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

  TraceabilitySettPage page;

  @Override
  public void addPages() {
    page = new TraceabilitySettPage();
    addPage(page);
  };

  @Override
  public boolean performFinish() {
    ArrayList<Object> k = new ArrayList<>(), m;
    m = new ArrayList<>();
    m.add(page.templ1.getText());
    m.add(page.templ2.getText());
    k.add(new ListValue(AttributeType.STRING, m));
    for (int i = 0; i < groupsnum; i++) {
      m = new ArrayList<>();
      m.add(page.groupText.get(i).getText());
      m.add(page.groupLink.get(i).getText());
      m.add(page.groupRefError.get(i).getText());
      m.add(page.groupLinkError.get(i).getText());
      k.add(new ListValue(AttributeType.STRING, m));
    }
    int ten = page.te.getSelectionIndex();
    final TreeNode rep = TraceabilityReportSettingsWizard.this.rep;
    final Object filter = this.filter; 
    WorkspaceJob j = new WorkspaceJob("Update traceability template settings for "+rep.getUserVisibleName()){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(rep == null)
          return Status.CANCEL_STATUS;
        boolean started = rep.getTreeDB().startTransaction("Change report settings");
        if (!started) return Status.CANCEL_STATUS;
        try {
          if (filter != null && filter instanceof String)
            rep.attrs().setConstAttribute("filter", filter.toString(),0);
        
          rep.attrs().setConstAttribute("traceabilitySettings", new ListValue(AttributeType.LIST, k),0);
          rep.attrs().setConstAttribute("toggleEnabled", ten == 0,0);

          rep.commitAttributes();
          if(started) {
            rep.getTreeDB().commit();
            started = false;
          }
        } catch (Exception e) {
          RLogger.logError(e);
        }finally {
          if(started)
            rep.getTreeDB().rollback();
        }
        return Status.OK_STATUS;
 
      }
    
    };
    j.schedule();
    return true;
  }
}
