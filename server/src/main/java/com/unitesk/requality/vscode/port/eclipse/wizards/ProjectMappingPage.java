package com.unitesk.requality.vscode.port.eclipse.wizards;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.vscode.port.eclipse.wizards.massops.AbstractExtraSettingsPage;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.Specification;

public class ProjectMappingPage extends AbstractExtraSettingsPage implements IUpdatablePage {
  public static String SPEC = "com.unitesk.pm.specifications", TREEDBS = "com.unitesk.pm.treedbs";
  private boolean opt = false;
  private TreeDB db = null;
  private TreeViewer specifications, projects, mappings;
  private DialogSettings ds;
  private Map<Specification, TreeDB> mapp = new HashMap<>();

  public ProjectMappingPage(DialogSettings ds, Set<String> names, TreeDB db) {
    super("ReqIF specification to project mapping", ds, names, db);
    this.db = db;
    this.ds = ds;
    setTitle("Map reqif specifications to requality projects");
    opt = false;
  }

  protected ProjectMappingPage(boolean opti, TreeDB db, DialogSettings ds) {
    super(
        "com.unitesk.requality.mapreqifproj",
        ds,
        new HashSet<>(Arrays.asList(getSDialogNames())),
        db);
    setTitle("Map reqif specifications to requality projects");
    opt = opti;
    this.db = db;
    this.ds = ds;
  }

  protected ReqIFModel getModel(String path) {
    ReqIFModel m = ExportToReqIFWizard.getModel(path);
    if (m != null && mapp.isEmpty()) {
      Specification best = null;
      if (db != null) {
        for (Specification s : m.getSpecifications())
          if (best == null || isBetter(best, s, db)) best = s;
        addMapping(best, db);
      }
      ResManager rm = ResManager.getManager();
      for (TreeDB db : rm.getTreeDBs())
        if (db != this.db)
          for (Specification s : m.getSpecifications())
            if (db.getProjectName().toLowerCase().equals(s.getName().toLowerCase())) addMapping(s, db);
    }
    if(m==null)
      setErrorMessage("Cannot load a ReqIF model from given file");
    return m;
  }

  private ReqIFModel getModel() {
    String s = ds.get("com.unitesk.inRFFile");
    if (s != null && s.length() > 0) return getModel(s);
    s = ds.get("com.unitesk.requality.reqIFFIleName");
    if (s != null && s.length() > 0) return getModel(s);
    return null;
  }

  private class SpecsContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      if (inputElement == null) return new Object[0];
      if (inputElement instanceof DialogSettings) {
        try {
          ReqIFModel model = getModel();
          if (model != null) return model.getSpecifications().toArray(new Specification[0]);
        } catch (Exception e) {
          setErrorMessage(e.getMessage());
          RLogger.logError(e);
        }
        //try to load model
      }
      if (inputElement instanceof ReqIFModel) {
        ReqIFModel model = (ReqIFModel) inputElement;
        return model.getSpecifications().toArray(new Specification[0]);
      }
      return new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return getElements(parentElement);
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      Object[] r = getChildren(element);
      return r != null && r.length > 0;
    }
  }

  private class MappingContentProvider implements ITreeContentProvider {

    @Override
    public Object[] getElements(Object inputElement) {
      if (inputElement instanceof Map<?, ?>) {
        Map<Specification, TreeDB> map = (Map<Specification, TreeDB>) inputElement;
        Object[] ret = new Object[map.size()];
        int i = 0;
        for (Specification s : map.keySet()) {
          ret[i++] = new Object[] {s, map.get(s)};
        }
        return ret;
      }
      return new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return getElements(parentElement);
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      Object[] r = getChildren(element);
      return r != null && r.length > 0;
    }
  }

  @Override
  public void createControl(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(3, true));
    specifications = new TreeViewer(c);
    specifications.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    specifications.setContentProvider(new SpecsContentProvider());
    specifications.setInput(ds);
    specifications.setLabelProvider(
        new ILabelProvider() {

          @Override
          public void removeListener(ILabelProviderListener listener) {
            // TODO Auto-generated method stub

          }

          @Override
          public boolean isLabelProperty(Object element, String property) {
            // TODO Auto-generated method stub
            return true;
          }

          @Override
          public void dispose() {
            // TODO Auto-generated method stub

          }

          @Override
          public void addListener(ILabelProviderListener listener) {
            // TODO Auto-generated method stub

          }

          @Override
          public String getText(Object element) {

            if (element instanceof Specification) {
              Specification s = ((Specification) element);
              return s.getName();
            }
            return null;
          }

          @Override
          public Image getImage(Object element) {
            // TODO Auto-generated method stub
            return null;
          }
        });

    projects = new TreeViewer(c);
    projects.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    projects.setFilters(
        new ViewerFilter[] {
          new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
              if (element instanceof IProject || element instanceof TreeDB) return true;
              return false;
            }
          }
        });
    projects.setContentProvider(new RequalityContentProvider());
    projects.setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
    projects.setInput(ResourcesPlugin.getWorkspace().getRoot());
    if (db != null) projects.setSelection(new StructuredSelection(db));

    mappings = new TreeViewer(c);
    mappings.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    mappings.setContentProvider(new MappingContentProvider());
    mappings.setLabelProvider(
        new LabelProvider() {
          @Override
          public String getText(Object el) {
            if (el instanceof Object[]) {
              Object[] element = (Object[]) el;
              Specification s = (Specification) element[0];
              TreeDB d = (TreeDB) element[1];
              String name = ResManager.getManager().getProjectName(d);
              if (name == null) name = "none";
              return s.getName() + " -> " + name;
            }
            return super.getText(el);
          }
        });
    mappings.setInput(mapp);

    Composite buttons = new Composite(c, SWT.NONE);
    buttons.setLayout(new GridLayout(2, false));
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 3;
    buttons.setLayoutData(gd);

    Button anew = new Button(buttons, SWT.PUSH);
    anew.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    anew.setText("Add new mapping");
    anew.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            Specification s = null;
            TreeDB db = null;
            StructuredSelection ss = (StructuredSelection) specifications.getSelection();
            if (ss != null) {
              if (ss.getFirstElement() != null) s = (Specification) ss.getFirstElement();
            }
            ss = (StructuredSelection) projects.getSelection();
            if (ss != null) {
              if (ss.getFirstElement() != null) db = getTreeDB(ss.getFirstElement());
            }

            if (s != null) {
              addMapping(s, db);
            }
          }
        });

    Button arem = new Button(buttons, SWT.PUSH);
    arem.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    arem.setText("Remove mapping");
    arem.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            StructuredSelection ss = (StructuredSelection) mappings.getSelection();
            if (ss != null) {
              if (ss.getFirstElement() != null) {
                Object[] mp = (Object[]) ss.getFirstElement();
                mapp.remove(mp[0]);
                mappings.refresh();
                checkOneMapping();
              }
            }
          }
        });

    setControl(c);
  }

  private boolean isBetter(Specification best, Specification s, TreeDB db2) {
    String name = db2.getProjectName();
    return CoreUtils.calcDist(name, s.getName()) < CoreUtils.calcDist(name, best.getName());
  }

  private void addMapping(Specification s, TreeDB db2) {
    mapp.put(s, db2);
    if (mappings != null) mappings.refresh();
    checkOneMapping();
  }

  protected TreeDB getTreeDB(Object firstElement) {
    if (firstElement instanceof IProject) {
      IProject p = (IProject) firstElement;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
		return et.getTreeDB(p, null, et.getLocalName(p));
	  } catch (IOException e) {
		RLogger.logError(e);
	  }
    }
    if (firstElement instanceof TreeDB) return (TreeDB) firstElement;
    if (firstElement instanceof TreeNode) return ((TreeNode) firstElement).getTreeDB();
    return null;
  }

  @Override
  public boolean isPageComplete() {
    return opt ? true : super.isPageComplete();
  }

  @Override
  public void update() {
    specifications.refresh();
    projects.refresh();
    mappings.refresh();
    checkOneMapping();
  }

  public Map<Specification, TreeDB> getMapping() {
    return mapp;
  }

  private void checkOneMapping() {
    setPageComplete(false);
    boolean ret = false;
    if (!mapp.isEmpty()) {
      for (Specification s : mapp.keySet()) {
        TreeDB db = mapp.get(s);
        if (db != null) {
          ret = true;
          break;
        }
      }
    }
    StringBuffer sp = new StringBuffer(), treedbs = new StringBuffer();
    int i = 0;
    for (Specification s : mapp.keySet()) {
      if (sp.length() > 0) {
        sp.append("|||");
        treedbs.append("|||");
      }
      sp.append(s.getId());
      TreeDB db = mapp.get(s);
      if (db != null) treedbs.append(db.getProjectName());
      i++;
    }
    ds.put(SPEC, sp.toString());
    ds.put(TREEDBS, treedbs.toString());
    setPageComplete(ret);
  }

  @Override
  public String[] getDialogNames() {
    return getSDialogNames();
  }

  public static String[] getSDialogNames() {
    return new String[] {SPEC, TREEDBS};
  }

  @Override
  public void reloadSettingsFromDS() {}

  @Override
  protected boolean additianalConds() {
    return true;
  }
}
