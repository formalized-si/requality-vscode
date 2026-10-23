package com.unitesk.requality.vscode.port.eclipse.views.atypeeditors;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.viewers.DialogCellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.filter.IObjectFilter;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.vscode.port.eclipse.views.atypeeditors.ListValueEditor.ValueItem;

public class ReferenceValueEditor extends DialogCellEditor {
  private boolean updating;
  private TreeNode node;
  private static Pattern
    pdirect =
        Pattern.compile(
            "\\w\\w\\w\\w\\w\\w\\w\\w-\\w\\w\\w\\w-\\w\\w\\w\\w-\\w\\w\\w\\w-\\w\\w\\w\\w\\w\\w\\w\\w\\w\\w\\w\\w"),
    ppath = Pattern.compile("(\\/Requirements(?:\\/(?:[\\wа-яА-Я])*)*)");

  public ReferenceValueEditor(TreeNode node, Composite table, int style) {
    super(table, style);
    this.node = node;
  }
  private String filterCross(String xpathvalue) {
    int ind = xpathvalue.indexOf("::");
    if(ind>-1){
      return xpathvalue.substring(ind+2);
    }
    return xpathvalue;
  }

  @Override
  protected Object openDialogBox(Control cellEditorWindow) {
    Object o = ((Table) cellEditorWindow.getParent()).getSelection()[0].getData();
    String val = null;
    ReferenceValue rv = null;
    if (o instanceof ReferenceValue)
      rv = (ReferenceValue)o;
    else
    if (o instanceof Attribute)
      rv = (ReferenceValue) ((Attribute) o).getValue(); //TODO or not raw
    else{
      ValueItem vi = ((ValueItem) o);
      if(vi != null && vi.getValue()!= null && (vi.getValue() instanceof ReferenceValue))
        rv =  (ReferenceValue) vi.getValue();
      else
        val = "";
    }
    String key = null;
    if(val==null&&rv!=null){
      val = rv.getRaw();
      key = rv.getKey();
    }
    LinkDialog ld = new LinkDialog(cellEditorWindow.getShell(), val);
    if (ld.open() == Window.OK) {
      return ReferenceValue.getOne(null, ld.getResult(), key);
    }
    return null;
  }

  private class LinkDialog extends Dialog {
    private Text xpath;
    private String xpathvalue;
    private Text err;
    private TreeViewer nodeTree;
    private int choice;
   
    public LinkDialog(Shell shell, String string) {
      super(shell);
      xpathvalue = string;
      setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    public Object getResult() {
      return xpathvalue;
    }

    private Button o1;
    private Button o2;
    private Button o3;

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite container = createClientContainer(parent, 1);
      container.setLayoutData(new GridData(GridData.FILL_BOTH));
      Composite topPanel = new Composite(container, SWT.NONE);
      //Flow fl = new FillLayout();
      GridLayout gl = new GridLayout(1, false);
      gl.marginHeight = gl.marginWidth = 0;

      topPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      topPanel.setLayout(gl);

      Composite checkcom = new Composite(topPanel, SWT.NONE);
      checkcom.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      checkcom.setLayout(new GridLayout(3, false));
      o1 = new Button(checkcom, SWT.RADIO);
      o2 = new Button(checkcom, SWT.RADIO);
      o3 = new Button(checkcom, SWT.RADIO);
      //final Button o3 = new Button(checkcom, SWT.RADIO);
      SelectionListener check =
          new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
              choice = 0;
              if (o1.getSelection()) {
                choice = 1;
              }
              if (o2.getSelection()) {
                choice = 2;
              }
              if (o3.getSelection()) {
                choice = 3;
              }
              updateXpath();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
              // TODO Auto-generated method stub

            }
          };

      o1.setText("By element");
      o1.addSelectionListener(check);
      o2.setText("By path");
      o2.addSelectionListener(check);
      o3.setText("By UVName");
      o3.addSelectionListener(check);
      //o3.setText("Manual");
      Label valueLabel = new Label(topPanel, SWT.WRAP);
      valueLabel.setText("Expression:");
      valueLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, true));
      xpath = new Text(topPanel, SWT.NONE);
      xpath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      
      final Button b = new Button(topPanel, SWT.CHECK);
      b.setText("Advanced settings");

      final Composite c = new Composite(topPanel, SWT.NONE);
      c.setVisible(false);
      c.setLayoutData(new GridData(0, 0));
      c.setLayout(new GridLayout(1, true));

      b.addSelectionListener(
          new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
              if (b.getSelection()) c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
              else c.setLayoutData(new GridData(0, 0));
              c.setVisible(b.getSelection());
              c.getShell().pack();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
              // TODO Auto-generated method stub

            }
          });

      valueLabel = new Label(c, SWT.NONE);
      valueLabel.setText("Error log:");
      valueLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, true));

      err = new Text(c, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
      GridData gd = new GridData(GridData.FILL_HORIZONTAL);
      gd.heightHint = 100;
      err.setLayoutData(gd);
      //   	        final Table basetable = new Table(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);

      nodeTree = new TreeViewer(container);
      GridData ld = new GridData(GridData.FILL_BOTH);
      ld.heightHint = 400;
      ld.widthHint = 300;
      nodeTree.getControl().setLayoutData(ld);
      nodeTree.setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
      nodeTree.setComparator(new RequalitySorter());
      nodeTree.setAutoExpandLevel(2);
      nodeTree.setComparator(new RequalitySorter());
      RequalityContentProvider rcp = new RequalityContentProvider(true);
      rcp.addResFilter(new IObjectFilter<Object>(){
        @Override
        public boolean filter(Object object) {
          if(object instanceof IProject){
            IProject p = (IProject)object;
            try {
              EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
              if(!p.isOpen() || !p.hasNature(ReqCatalogue.ID) ||
            		  et.getProjectLoadingState(p.getLocationURI())!=ProjectLoadingState.LOADED||
            		  p.hasNature(Consts.CATALOG_BROKEN_NATURE))
                return false;
            } catch (CoreException e) {
              return false;
            }
          }
          return true;
        }
      });
      nodeTree.setContentProvider(rcp);
      nodeTree.setInput(ResourcesPlugin.getWorkspace().getRoot());
      nodeTree.addSelectionChangedListener(
          new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
              if (updating) return;
              updating = true;
              try{
                if (choice == 0) {
                  choice = 1;
                  o1.setSelection(true);
                }
                updateXpath();
              }finally{
                updating = false;
              }
            }
          });
      xpath.addModifyListener(
          new ModifyListener() {

            @Override
            public void modifyText(ModifyEvent e) {
              xpathvalue = xpath.getText().trim();
              if (updating) return;
              choice = 0;
              err.setText("");
              //o3.setSelection(true);
              updating = true;
              try {
                int pos = xpathvalue.indexOf("::");
                if (pos > -1) {
                  String name = xpathvalue.substring(0, pos).trim();
                  if (name != null) {
                    TreeDB db = Global.getRepoManager(node.getTreeDB()).getTreeDB(node.getTreeDB(),null, name, false, node.getTreeDB().getMonitor());//TODO IDE monitor
                    if (db == null) err.setText("Project '" + name + "' is not found!");
                  }
                }
                List<TreeNode> nde =
                    node.getTreeDB()
                        .getLinkResolver()
                        .findNodesByPathIgnoreDisposed(
                            node,
                            null,
                            node.getTreeDB(),
                            xpathvalue,
                            Path.QID | Path.UV | Path.UUID | Path.XPATH);
                if (nde != null && nde.size() > 0)
                  nodeTree.setSelection(new StructuredSelection(nde));
                else nodeTree.setSelection(new StructuredSelection());
              } catch (Exception ee) {
                err.setText(ee.toString());
              }
              o1.setSelection(false);
              String fxpath = filterCross(xpathvalue);
              if (pdirect.matcher(fxpath).matches()) {
                o1.setSelection(true);
                choice = 1;
              }
              o2.setSelection(false);
              if (ppath.matcher(fxpath).matches()) {
                o2.setSelection(true);
                choice = 2;
              }
              o3.setSelection(false);
              try {
                if (choice != 2
                    && node.getTreeDB()
                            .getNode(xpathvalue, Path.QID | Path.UV | Path.UUID | Path.XPATH)
                        != null) {
                  o3.setSelection(true);
                  choice = 3;
                }
              } catch (Exception ex) {
              }
              updating = false;
            }

          });
      if (xpathvalue != null) xpath.setText(xpathvalue);

      //updateXpath();
      //nodeTree.addSelectionChangedListener(new NodeSelectionListener());

      //         nodeTree.getTree().addMouseListener(mouseList);
      //       nodeTree.getTree().addKeyListener(keyList);
      getShell().setText("Link editor");
      this.getShell()
          .setImage(
              PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
      return container;
    }

    protected void updateXpath() {
      if (nodeTree.getSelection().isEmpty() || choice == 0) return;
      updating = true;
      try{
        Object sel = ((StructuredSelection) nodeTree.getSelection()).getFirstElement();
        if(sel instanceof IProject)
        {
          IProject p = (IProject)sel;
          TreeDB db = null;
		  try {
		    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
			db = et.getTreeDB(p, null, et.getLocalName(p));
		  } catch (IOException e) {
			// TODO Auto-generated catch block
			RLogger.logError(e);
		  }
          if(db!=null)
            sel = db.getRootNode();
        }
        if(!(sel instanceof TreeNode))
          return;
        TreeNode node2 = (TreeNode) sel;
        String add = "";
        if(!node2.getTreeDB().getProjectName().equals(node.getTreeDB().getProjectName()))
          add = node2.getTreeDB().getProjectName()+"::";
        if (choice == 1) xpath.setText(add+node2.getSUUId());
        else if (choice == 2) {
          //					TreeNode nde = node2;
          //					String path = "";
          //					while(nde!=null && !"TreeNode".equals(nde.getType())){
          //						path = nde.getType()+"[@id=\""+nde.getId()+"\"]/"+path;
          //						nde = nde.getWorkParent();
          //					}
          xpath.setText(add+node2.getQualifiedId());
        } else xpath.setText(add+node2.getUserVisibleName(true));
      }
      finally{
        updating = false;
      }
    }

    private Composite createClientContainer(Composite parent, int span) {
      Composite container = new Composite(parent, SWT.NONE);
      container.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      GridLayout layout = new GridLayout();
      layout.marginWidth = layout.marginHeight = 10;
      layout.verticalSpacing = 2;
      layout.numColumns = span;
      container.setLayout(layout);
      return container;
    }
  }
}
