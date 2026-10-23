package com.unitesk.requality.eclipse.views.projectSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class EditorsProps extends RequalityPropertyPage {
  private static final String ATTR_PROFILES = "_editorsProfiles";
  private static final String DESC_TEXT_1 = "Editors may show one or more attributes. \n"
      + "Each editor may have one or more profile.";
  private static final String DEFAULT = "<default>"; 
  private static final char SEP = (char)0xE2;
  private static final String
  DESC_TEXT_2 =
      "It is possible to select several attributes to be shown in corresponding editor.\n"
          + "Default value may be specified for those attributes.";
  private String selEd,selProf;

  private TreeNode nodeToStore;
  private List names;
  private IProject p;
  private Group container3;
  private TableViewer attrSelList;
  private List eds;
  private List attrList;
  //text on top of preference page
  private Map<String,Map<String, Map<String,String>>> selected = new HashMap<>();
  private Map<String,Map<String, Set<String>>> markNoDef = new HashMap<>();
  
  private Set<String> attributes;
  private String[] edsItems;
  
  public EditorsProps() {
    setTitle("Editors profiles properties");
  }
  class AttributeSetts {
    public String name;
    public String defvalue;
    public boolean hideDef;
    public AttributeSetts(String key, String text,boolean hd) {
      this.name = key;
      this.defvalue = text;
      this.hideDef = hd;
    }
    public String toString(){
      return name+(hideDef?"":"["+defvalue+"]");
    }
  }
 
  private final class EditorAttributeParamContentProvider implements IStructuredContentProvider {
    @Override
    public Object[] getElements(Object input) {
      if (input instanceof Object[]) {
        return (Object[])input;
      }
      return null;
    }

    @Override
    public void dispose() {}

    @Override
    public void inputChanged(Viewer arg0, Object arg1, Object arg2) {}
  }

  @Override
  protected Control createContents(Composite parent) {

    Composite c = new Composite(parent, SWT.NONE);
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    c.setLayout(new GridLayout(1, true));

    final Group container2 = new Group(c, SWT.NONE);
    container2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
    container2.setText("Profiles:");
    container2.setLayout(new GridLayout(2, true));
    //container2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label l = new Label(container2, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    l.setLayoutData(gd);
    l.setText(DESC_TEXT_1);
    
    eds = new List(container2, SWT.NONE);
    eds.setLayoutData(new GridData(GridData.FILL_BOTH));
    eds.add("Unieditor");
    eds.add("Module editor");
    eds.add("Review");
    selEd = "Unieditor";
    
    eds.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        int ind = eds.getSelectionIndex();
        if(ind==-1)
            selEd = "Unieditor";
        else
            selEd = eds.getItem(ind);
        if(!selected.containsKey(selEd))
          selected.put(selEd, new HashMap<>());
        if(!markNoDef.containsKey(selEd))
          markNoDef.put(selEd, new HashMap<>());
        names.removeAll();
        names.add(DEFAULT);
        for(String s:selected.get(selEd).keySet())
            names.add(s);
        names.select(0);
        
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    names = new List(container2, SWT.NONE);
    names.setLayoutData(new GridData(GridData.FILL_BOTH));
    names.add(DEFAULT);
    
    
    
    Composite bts = new Composite(container2, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    bts.setLayoutData(gd);
    bts.setLayout(new FillLayout());
    
    Text t = new Text(bts, SWT.NONE);
    t.setText("New name");
    
    Button cre = new Button(bts,SWT.NONE);
    cre.setText("Add profile");
    cre.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        String text = t.getText();
        for(String s:names.getItems())
          if(s.equals(text))
            return;
        if(!selected.containsKey(selEd))
          selected.put(selEd, new HashMap<>());
        if(!markNoDef.containsKey(selEd))
          markNoDef.put(selEd, new HashMap<>());
        names.add(text);
        selected.get(selEd).put(text, new HashMap<String, String>());
        markNoDef.get(selEd).put(text, new HashSet<>());
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    final Button rem = new Button(bts,SWT.NONE);
    rem.setText("Remove profile");
    rem.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        if(names.getSelectionIndex()>-1)
        {
          String name = names.getItem(names.getSelectionIndex());
          if(name.equals(DEFAULT))
            return;
          names.remove(names.getSelectionIndex());
          selProf = DEFAULT;
          selected.get(selEd).remove(name);
          markNoDef.get(selEd).remove(name);
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    names.addSelectionListener(new SelectionListener(){
      
      @Override
      public void widgetSelected(SelectionEvent e) {
        if(names.getSelectionIndex()>-1){
          String text = names.getItem(names.getSelectionIndex());
          selProf = text;
          container3.setText(getPSText(text));
          rem.setEnabled(true);
          if(text.equals(DEFAULT))
            rem.setEnabled(false);
          Set<AttributeSetts> trem = new HashSet<>();
          for(TableItem tas:attrSelList.getTable().getItems())
          {
            AttributeSetts as = (AttributeSetts) tas.getData();
            if(as!=null){
              attrList.add(as.name);
              trem.add(as);
            }
          }
          attrSelList.setInput(new AttributeSetts[0]);
          for(String s:selected.get(selEd).get(selProf).keySet())
          {
            attrList.remove(s);
            attrSelList.add(new AttributeSetts(s, selected.get(selEd).get(selProf).get(s),false));
          }
          for(String s:markNoDef.get(selEd).get(selProf))
          {
            attrList.remove(s);
            attrSelList.add(new AttributeSetts(s, "",true));
          }
        }
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    
    container3 = new Group(c, SWT.NONE);
    container3.setLayoutData(new GridData(GridData.FILL_BOTH));
    container3.setText(getPSText(DEFAULT));
    container3.setLayout(new GridLayout(3, false));
    //container3.setLayoutData(new GridData(GridData.FILL_BOTH));

    l = new Label(container3, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 3;
    l.setLayoutData(gd);
    l.setText(DESC_TEXT_2);
    
    
    //ScrollableCom

    //Composite al = new Composite(container3, SWT.V_SCROLL);
    //al.setLayout(new FillLayout(SWT.HORIZONTAL));
    
    attrList = new List(container3,  SWT.V_SCROLL);
    
    GridData gd_al = new GridData(GridData.FILL_BOTH);
    gd_al.grabExcessVerticalSpace=false;
    gd_al.heightHint=200;
    gd_al.widthHint=200;
    attrList.setLayoutData(gd_al);
    
    Composite buts2 = new Composite(container3,SWT.NONE);
    buts2.setLayout(new GridLayout(1,true));
    buts2.setLayoutData(new GridData(GridData.FILL_VERTICAL));
    
    Button ra = new Button(buts2,SWT.NONE);
    ra.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    ra.setText("->");
   
    Button la = new Button(buts2,SWT.NONE);
    la.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    la.setText("<-");
    
    //Composite al2 = new Composite(container3, SWT.V_SCROLL);
    //al2.setLayout(new FillLayout(SWT.HORIZONTAL));
    
    
    attrSelList = new TableViewer(container3,  SWT.V_SCROLL);
    attrSelList.setContentProvider(new EditorAttributeParamContentProvider());
    attrSelList.setComparator(new ViewerComparator(){
      @Override
      public int compare(Viewer viewer, Object e1, Object e2) {
        AttributeSetts a1 = (AttributeSetts) e1,
                       a2 = (AttributeSetts) e2;
        return super.compare(viewer,a1.name,a2.name);
      }
    });
    //attrSelList.setLabelProvider(new EditorAttributeParamLabelProvider());
    GridData gd_al2 = new GridData(GridData.FILL_BOTH);
    gd_al2.grabExcessVerticalSpace=false;
    gd_al2.heightHint=200;
    gd_al2.widthHint=200;
    attrSelList.getTable().setLayoutData(gd_al2);
    
    Composite valsc = new Composite(container3, SWT.NONE);
    valsc.setLayout(new GridLayout(3,false));
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan=3;
    valsc.setLayoutData(gd);
    
    l = new Label(valsc, SWT.NONE);
    l.setText("Default value");
    
    Text valc = new Text(valsc,SWT.NONE);
    valc.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
   
    Button ch = new Button(valsc, SWT.CHECK);
    ch.setText("Hide if have no value");
    ch.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        valc.setEnabled(!ch.getSelection());
        StructuredSelection ts = (StructuredSelection) attrSelList.getSelection();
        if(ts!=null){
          AttributeSetts as = (AttributeSetts) (ts.getFirstElement());
          if(as!=null)
            as.hideDef= ch.getSelection();
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    valc.addModifyListener(new ModifyListener(){
      
      @Override
      public void modifyText(ModifyEvent e) {
        StructuredSelection ts = (StructuredSelection) attrSelList.getSelection();
        if(ts!=null){
          AttributeSetts as = (AttributeSetts) (ts.getFirstElement());
          if(as!=null){
            String key = as.name,val = as.defvalue;
            if(!val.equals(valc.getText())){
              attrSelList.remove(as);
              attrSelList.add(new AttributeSetts(key,valc.getText(),ch.getSelection()));
              //attrSelList.add(key+SEP+"["+valc.getText()+"]", sel);
            }
          }
        }
      }
    });
    
    attrSelList.addSelectionChangedListener(new ISelectionChangedListener(){
    
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        StructuredSelection ts = (StructuredSelection) attrSelList.getSelection();
        if(ts!=null){
          AttributeSetts as = (AttributeSetts) ts.getFirstElement();
          if(as!=null){
            if(attrList.getSelectionIndex()!=-1)
              attrList.setSelection(-1);
            String val = as.defvalue;
            valc.setText(val);
            ch.setSelection(as.hideDef);
          }
        }
      }
    });
    
    ch.addSelectionListener(new SelectionListener(){
      @Override
      public void widgetSelected(SelectionEvent e) {
        
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    
    ra.addSelectionListener(new SelectionListener(){
      
      @Override
      public void widgetSelected(SelectionEvent e) {
        int sel = attrList.getSelectionIndex();
        if(sel>-1){
          String s = attrList.getItem(sel);
          attrList.remove(s);
          attrSelList.add(new AttributeSetts(s, valc.getText(),ch.getSelection()));
          if(ch.getSelection())
            markNoDef.get(selEd).get(selProf).add(s);
          else
            selected.get(selEd).get(selProf).put(s, valc.getText());
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    la.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        StructuredSelection ts = (StructuredSelection) attrSelList.getSelection();
        if(ts!=null){
          AttributeSetts as = (AttributeSetts) ts.getFirstElement();
          int ind = Arrays.binarySearch(attrList.getItems(), as.name);
          if(ind<0){
            attrList.add(as.name,Math.abs(ind)-1);
            attrSelList.remove(as);
          }
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    attrList.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        int sel = attrList.getSelectionIndex();
        if(sel>-1){
          StructuredSelection ts = (StructuredSelection) attrSelList.getSelection();
          if(ts!=null){
            attrSelList.setSelection(null);
            valc.setText("");
          }
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    String sel = DEFAULT;
    String esel = "Unieditor";
    if(names.getSelectionIndex()>-1)
      sel = names.getItem(names.getSelectionIndex());
    Map<String, Map<String, String>> vale = selected.getOrDefault(esel,new HashMap<>());
    Map<String, String> val = vale.getOrDefault(sel, new HashMap<>());
    Set<String> val2 = markNoDef.getOrDefault(esel, new HashMap<>()).
        getOrDefault(sel,new HashSet<>());
    String[]attrs = attributes.toArray(new String[0]);
    Arrays.sort(attrs);
    
    for(String s:attrs){
      if(!val.containsKey(s))
        attrList.add(s);
    }
    
    for(String s:val.keySet()){
      attrSelList.add(new AttributeSetts(s,val.get(s),val2.contains(s)));
    }
    return c;
  }

  private String getPSText(String string) {
    return "Profile settings for '"+string+"':";
  }

  @Override
  public void setElement(org.eclipse.core.runtime.IAdaptable element) {
    if (element instanceof IProject) {
      p = (IProject) element;
      TreeDB db = null;
	  try {
	    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
		db = et.getTreeDB(p, null, et.getLocalName(p));
	  } catch (IOException e) {
		RLogger.logError(e);
	  }
      if (db != null) {
        nodeToStore = db.getRootNode();
        //TODO add job before the use of this class
        DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(nodeToStore,false,false,true,true,false);
        Iterator<TreeNode> it = dftw.iterator();
        attributes = new HashSet<>();
        while(it.hasNext()){
          TreeNode node = it.next();
          for(String s:node.attrs().getAllAttributeKeys())
            if(!s.startsWith("_"))
              attributes.add(s);
        }
        loadProfilesDescription(nodeToStore);
      }
    }
  };

  private void loadProfilesDescription(TreeNode nodeToStore2) {
    markNoDef.clear();
    selected.clear();
    Attribute a = nodeToStore2.attrs().getAttribute(ATTR_PROFILES);
    if(a!=null){
      java.util.List<Object> l = ((ListValue)a.getGValue()).getValues();
      String ename = null;
      for(Object o:l){
        java.util.List<Object> lp = ((ListValue)o).getValues();//editor
        if(lp.size()==1 && lp.get(0) instanceof String)
            ename = lp.get(0).toString();
        else
        {
          java.util.List<Object> meta = ((ListValue) lp.get(0)).getValues();
          java.util.List<Object> names = null;
          if(lp.size()>1)
            names = ((ListValue) lp.get(1)).getValues();
          java.util.List<Object> vals = null; 
          if(lp.size()>2)
            vals = ((ListValue) lp.get(2)).getValues();
          String name = meta.get(0).toString();
          if(!markNoDef.containsKey(ename)){
              markNoDef.put(ename, new HashMap<>());
              selected.put(ename, new HashMap<>());
          }
          if(!markNoDef.get(ename).containsKey(name)){
              markNoDef.get(ename).put(name, new HashSet<>());
              selected.get(ename).put(name, new HashMap<>());
          }
          if(names !=null && vals!=null)
            for(int i = 0;i<names.size();i++)
            {
                String key = names.get(i).toString();
                String aval = i<vals.size()?vals.get(i).toString():null;
                if(aval == null)
                   markNoDef.get(ename).get(name).add(key);
                else
                   selected.get(ename).get(name).put(key, aval);
            }
        }
      }
    }
  }
  
  private void saveProfilesDescription(TreeNode nodeToStore2) {
   
    ArrayList<Object> lg = new ArrayList<>();
    for(String s:edsItems)//foreach editor
    {
      ArrayList<Object> eo = new ArrayList<>();
      ArrayList<Object> meta = new ArrayList<>();
      meta.add(s);
      eo.add(new ListValue(AttributeType.STRING, meta));
      Map<String,Map<String,String>> map = selected.get(s);
      if(map!=null)
        for(String p:map.keySet()){//foreach profile name
          ArrayList<Object> al = new ArrayList<>(),al2 = new ArrayList<>();
          Map<String,String>ap =  selected.get(s).get(p);//Attributes with def value
          Set<String>ad =  markNoDef.get(s).get(p);//Attributes with no def value
          for(String sa:ap.keySet()){
            al.add(sa);
            al2.add(ap.getOrDefault(sa,""));
          }
          for(String sd:ad)
            al.add(sd);
          ArrayList<Object> metai = new ArrayList<>();
          metai.add(p);//profile name
          ArrayList<Object> named = new ArrayList<>();
          named.add(new ListValue(AttributeType.STRING, metai));
          named.add(new ListValue(AttributeType.STRING, al));//names
          named.add(new ListValue(AttributeType.STRING, al2));//defvalues
          eo.add(new ListValue(AttributeType.LIST, named));
        }
      lg.add(new ListValue(AttributeType.LIST, eo));
    }
    nodeToStore2.attrs().setConstAttribute(ATTR_PROFILES,new ListValue(AttributeType.LIST, lg),Flag.HIDDEN);
    nodeToStore2.commitAttributes();
  }

  //Changing settings to default
  @Override
  protected void performDefaults() {
    if(inParallelUpdate())
      return;
    super.performDefaults();
  }

  @Override
  public boolean isOkOperationInTransaction() {
    // TODO Auto-generated method stub
    return false;
  }
  @Override
  public void prepareVars() {
    edsItems = eds.getItems();
  }
  @Override
  public IStatus okPressedAction(IProgressMonitor monitor) {
    if(actionsStarted)
      return Status.CANCEL_STATUS;
    try {
      saveProfilesDescription(nodeToStore);
      return Status.OK_STATUS;
    }finally {
      actionsStarted = false;
    }
  }

  @Override
  public boolean settingsChanged() {
    String[] items = eds.getItems();
    Attribute a = nodeToStore.attrs().getAttribute(ATTR_PROFILES);
    if(a == null && items.length!=0)
      return true;
    if(a!=null && a.getType() == AttributeType.LIST){
      ListValue l = a.getGValue();
      if(l.getType() == AttributeType.LIST)
        if(((ListValue)l.getValues().get(0)).getValues().size()!=items.length)
          return true;//TODO more checks;
    }
    return false;
  }

  @Override
  public TreeDB getTreeDB() {
    return nodeToStore.getTreeDB();
  }

  @Override
  public boolean inputCorrect() {
    return true;//no correctness checks
  }
}
