package com.unitesk.requality.eclipse.views.projectSettings;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.dialogs.FilteredList;

import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.RequiredAttributesRule;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.SetRequiredAttributes;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.views.atypeeditors.BoolValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.FloatValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.IntValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.ListValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.ReferenceValueEditor;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ReqAttrsProperties extends RequalityPropertyPage implements IWorkbenchPropertyPage {
  private TreeDB db;
  private IProject p;  
  private org.eclipse.swt.widgets.List listtypes;
  private FilteredList listattrs;
  private Button add;
  private Button remove;
  private Text name;
  private Combo type;
  private Combo exnames;
  private Button loadnames;
  private Group container4;

  private CellEditor value;
  private TableViewer valc;
  private TableViewerColumn tvc;
  private Object val;
  
  private java.util.List<String> types = new LinkedList<>();
  
  private Map<Integer,String> typeMap = new HashMap<>();
  private Map<String, Map<String,AttributeDefinition>> mapVals;
  private Map<String,Attribute> exMap;
  private String selType = null;
  
  public ReqAttrsProperties() {}
  private SelectionListener listAttrList = new SelectionListener(){
  
    @Override
    public void widgetSelected(SelectionEvent e) {
      if(listattrs.isDisposed())
        return;
      if(listattrs.getSelectionIndex()>-1){
        AttributeDefinition ad = (AttributeDefinition)listattrs.getSelection()[0];
        setActive(ad.getKey(), ad.getType(),ad.getRawValue().getValue());
      }
    }
  
    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
    }
  };
  private ModifyListener nameModList = new ModifyListener(){
    @Override
    public void modifyText(ModifyEvent e) {
      if(e.widget.isDisposed())
        return;
      name.removeModifyListener(this);
      String n = name.getText();
      String newMsg = null;
      boolean isvalid = Attribute.checkNameIsValid(n, true);
      if(!isvalid)
        newMsg = "Name '"+n+"' is not valid attribute name!";

      if(RequiredAttributesRule.isPermittedKey(db, name.getText())){
        newMsg = "Attribute '"+name.getText()+"' cannot be declared as mandatory one!";
        isvalid = false;
      }
      updateStatus(newMsg, true);
      checkEnSystemType(n);
      add.setEnabled(isvalid);
      remove.setEnabled(true);
      if(n.isEmpty())
      {
        add.setEnabled(false);
        remove.setEnabled(false);
      }
      //listattrs.removeSelectionListener(listAttrList);
      AttributeDefinition ad = mapLook(n);
      if(ad!=null){
        setActive(n, ad.getType(), ad.getRawValue().getGValue());
        listattrs.setSelection(new Object[]{ad});
      }
      name.addModifyListener(this);
    }
  };
  private void recEnabled(Control c, boolean b){
    c.setEnabled(b);
    if(c instanceof Composite){
      Composite cm = (Composite)c;
      for(Control c2:cm.getChildren())
        recEnabled(c2, b);
    }
  }
  @Override
  protected Control createContents(Composite parent) {

    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(1, true));
    c.setLayoutData(new GridData(GridData.FILL_BOTH));

    //initReferenceRenameComposite(container3);
    container4 = new Group(c, SWT.NONE);
    container4.setLayout(new GridLayout(4, false));
    container4.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    Composite c1 = new Composite(container4, SWT.NONE);
    c1.setLayout(new GridLayout(1,false));
    c1.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    Label l = new Label(c1,SWT.NONE);
    l.setText("Node type:");
    listtypes = new List(c1, SWT.NONE);
    listtypes.setLayoutData(new GridData(GridData.FILL_BOTH));
    listtypes.add("Requirement");
    listtypes.add("Text node");
    typeMap.put(0, Requirement.TYPE_NAME);
    typeMap.put(1, TextNode.TYPE_NAME);
    listtypes.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        if(listtypes.isDisposed())
          return;
        int sel = listtypes.getSelectionIndex();
        if(sel>-1){
          selType = typeMap.get(sel);
          setListAttrs();
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });

    Composite c2 = new Composite(container4, SWT.NONE);
    c2.setLayout(new GridLayout(1,false));
    c2.setLayoutData(new GridData(GridData.FILL_BOTH));

    l = new Label(c2,SWT.NONE);
    l.setText("Defined attributes:");
    listattrs = new FilteredList(c2, SWT.NONE,new ILabelProvider(){
    
      @Override
      public void removeListener(ILabelProviderListener listener) {
      }
    
      @Override
      public boolean isLabelProperty(Object element, String property) {
        return true;
      }
    
      @Override
      public void dispose() {
      }
    
      @Override
      public void addListener(ILabelProviderListener listener) {
      }
    
      @Override
      public String getText(Object element) {
        return ((AttributeDefinition)element).toString(db.getRootNode().attrs());
      }
    
      @Override
      public Image getImage(Object element) {
        return null;
      }
    },false,false,false);
    listattrs.setFilter("*");
    listattrs.setLayoutData(new GridData(GridData.FILL_BOTH));
    listattrs.addSelectionListener(listAttrList);
    
    Composite ca = new Composite(container4, SWT.NONE);
    ca.setLayout(new GridLayout(1,true));
    ca.setLayoutData(new GridData(GridData.FILL_VERTICAL));
    
    add = new Button(ca, SWT.NONE);
    add.setText("←");
    add.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) {
        if(add.isDisposed())
          return;
        if(RequiredAttributesRule.isPermittedKey(db, name.getText())){
          updateStatus("Attribute name '"+name.getText()+"' cannot be declared as mandatory!", true);//TODO false?
          name.setFocus();
          return;
        }
        if(!mapVals.containsKey(selType))
          mapVals.put(selType, new HashMap<>());
        AttributeDefinition ad = setMap(name.getText(),getSelType(),getSelValue());
        if(ad!=null)
          listattrs.setSelection(new Object[]{ad});
      }

      
    });
    remove = new Button(ca, SWT.NONE);
    remove.setText("→");
    remove.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
        if(remove.isDisposed())
          return;
        int sel = listattrs.getSelectionIndex();
        AttributeDefinition val = null;
        if(sel>-1){
          val = (AttributeDefinition)listattrs.getSelection()[0];
          if(mapVals.containsKey(selType)){
            String ind = "";
            for(Entry<String, AttributeDefinition> en:mapVals.get(selType).entrySet())
              if(en.getValue().getKey().equals(val.getKey()))
                ind = en.getKey();
            mapVals.get(selType).remove(ind);
          }
          listattrs.setElements(mapVals.get(selType).values().toArray());
        }
//        else{
//          val = name.getText();
//          if(map.containsKey(selType)&&map.get(selType).contains(val)){
//            listattrs.remove(val);
//          }
//        }
      }
    });
    
    Composite names = new Composite(container4,SWT.NONE);
    names.setLayout(new GridLayout(1,true));
    names.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    l = new Label(names,SWT.NONE);
    l.setText("Name:");
    
    name = new Text(names, SWT.NONE);
    name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    name.addModifyListener(nameModList  );
    
    l = new Label(names,SWT.NONE);
    l.setText("Type:");
    type = new Combo(names, SWT.READ_ONLY);
    type.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    type.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        if(type.isDisposed())
          return;
        int ind = type.getSelectionIndex();
        if(ind>-1){
          String val = type.getItem(ind);
          initTypedEditor(val);
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });

    l = new Label(names,SWT.NONE);
    l.setText("Default value:");
    valc =
        new TableViewer(
            names, SWT.BORDER | SWT.RESIZE | SWT.V_SCROLL);
    valc.setContentProvider(new IStructuredContentProvider(){
      @Override
      public Object[] getElements(Object inputElement) {
        return new Object[]{inputElement};
      }
    });
    //valc = new Table(names,SWT.NONE); 
   // valc.getTable().setLayout(new GridLayout(1,true));
    //
    valc.getTable().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    tvc = new TableViewerColumn(valc, SWT.NONE);
    tvc.setLabelProvider(new CellLabelProvider(){
    
      @Override
      public void update(ViewerCell cell) {
        if(tvc.getViewer().getControl().isDisposed())
          return;
        if(cell.getElement()==null)
          cell.setText("");
        else{
          if(getSelType() == AttributeType.ENUM){
            AppliedEnumValue aev = (AppliedEnumValue) cell.getElement();
            cell.setText(aev.getValue().toString());
             return;
          }
          cell.setText(cell.getElement().toString());
        }
      }
    });
   
    value = new TextCellEditor(valc.getTable(), SWT.NONE);//placeholder
  //  value.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    valc.setCellEditors(new CellEditor[]{value});
    valc.add("");
    
    l = new Label(names,SWT.NONE);
    l.setText("Attribute name:");
    
    exnames = new Combo(names, SWT.READ_ONLY);
    exnames.setEnabled(false);
    exnames.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    exnames.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        if(exnames.isDisposed())
          return;
        Attribute a = exMap.get(exnames.getText());
        name.setText(exnames.getText());
        Object val = a.getDefinition().getRawValue();
        if(val!=null)
          val = ((TypedValue)val).getValue();
        if(val==null)
          val = a.getValue();
        setActive(exnames.getText(),a.getType(),val);
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    loadnames = new Button(names,SWT.NONE);
    loadnames.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    loadnames.setText("Find attr. names");
    loadnames.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
	    final TreeDB db = ReqAttrsProperties.this.db;
        final Combo exnames = ReqAttrsProperties.this.exnames;
        WorkspaceJob j = new WorkspaceJob("Load attribute names"){

          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            if(db == null)
              return Status.CANCEL_STATUS;
            DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getNode(Requirement.getTypeRootQId()));
            Iterator<TreeNode> it = dftw.iterator();
            SortedSet<String> names = new TreeSet<>();
            String versName = SystemNames.getOne(db).getVisSysName(AttributesVersioningRule.ATTR_NODE_VERSION);
            exMap = new HashMap<>();
            while(it.hasNext()){
              TreeNode node = it.next();
            if(node.isRoot())
              continue;
            for(Entry<String, Attribute> en:node.attrs().getActiveAttributes().entrySet()){
              Attribute a = en.getValue();
              if(a != null && !a.isVirtual()){
                String name = null;
                if(a.isUserVisible())
                  name = a.getKey();
                else
                  name = SystemNames.getOne(db).getVisSysName(a.getKey());
                if(name!=null && !name.equals(versName)&&!RequiredAttributesRule.isPermittedKey(db, name)){
                  names.add(name);
                  exMap.put(name, a);
                }
              }
            }
          }
          EclipseAPI.getDisplay().asyncExec(new Runnable(){
            
            @Override
            public void run() {
              if(exnames.isDisposed())
                return;
              exnames.setItems(names.toArray(new String[0]));
              exnames.setEnabled(true); 
            }
          });
          return Status.OK_STATUS;
        }
        
      };
      j.schedule();
    }
  });

    RequiredAttributesRule rar = db.getAttributeRules().getRequiredAttrRule();
    mapVals = rar.getMap();
    if(mapVals  == null)
      mapVals = new HashMap<>();
    else 
      mapVals = clone(mapVals);
    initTypes();
    initControls();
    //recEnabled(container4, avr != null && !avr.isEnAll());
    return c;
  }

  private Map<String, Map<String, AttributeDefinition>> clone(
      Map<String, Map<String, AttributeDefinition>> mapVals2) {
    Map<String, Map<String, AttributeDefinition>> map = new HashMap<>();
    for(String s:mapVals2.keySet()){
      map.put(s, new HashMap<>());
      for(String s2:mapVals2.get(s).keySet())
        map.get(s).put(s2, mapVals2.get(s).get(s2));
    }
    return map;
  }
  protected void setActive(String text, AttributeType type2, Object value2) {
    name.setText(text);
    int i = 0;
    String tsel = type2.toString();
    for(i = 0; i<type.getItemCount(); i++)
    {
      String val = type.getItem(i);
      if(type2 == AttributeType.ENUM){
        if(val.startsWith(Attribute.ENUM_CHAR)){
          AppliedEnumValue aev = (AppliedEnumValue)value2;
          tsel = val;
          val = val.substring(1);
          if(val.equals(aev.getBaseKey()))
            break;
        }
      }else{
        if(val.equals(type2.toString()))
          break;
      }
    }
    checkEnSystemType(text);
    type.select(i);
    initTypedEditor(tsel);
    val = value2;
    valc.setInput(val);
    valc.refresh(true);
  }
  private void checkEnSystemType(String text) {
    type.setEnabled(true);
    if(SystemNames.getOne(db).getOne(db).getSysName(text)!=null || SystemNames.getOne(db).getVisSysName(text)!=null)
      type.setEnabled(false);
  }
  protected void initTypedEditor(String vala) {
    //value.getControl().dispose();
    //valc.clear(0);
    AttributeType at = AttributeType.ENUM;
    if(!vala.startsWith(Attribute.ENUM_CHAR)){
      at = AttributeType.valueOf(vala);
      val = AttributeType.getDefaultValue(at);
      if(at == AttributeType.REFERENCE)
        val = (ReferenceValue.getOne(db.getRootNode(), val, name.getText()));//null is global ref
      switch(at){
        case BOOL:{
          value = new BoolValueEditor(valc.getTable(), SWT.FULL_SELECTION);
        }break;
        case INT:{
          value = new IntValueEditor(valc.getTable(), SWT.FULL_SELECTION);
        }break;
        case FLOAT:{
          value = new FloatValueEditor(valc.getTable(), SWT.FULL_SELECTION);
        }break;
        case REFERENCE:{
          value = new ReferenceValueEditor(db.getRootNode(), valc.getTable(), SWT.FULL_SELECTION);
        }break;
        case LIST:{
          value = new ListValueEditor(db.getRootNode(), valc.getTable(), SWT.FULL_SELECTION,
              true);
        }break;
        default:{
          value = new TextCellEditor(valc.getTable(), SWT.FULL_SELECTION);
        }break;
      }
    }else
    {
      String basekey = vala.substring(1);
      Attribute a = db.getRootNode().attrs().getAttribute(basekey);
      String[]cval = new String[0];
      if(a!=null)
      {
        EnumDefinitionValue edv = a.getGValue();
        int i = 0;
        if(edv!=null && edv.getValues()!=null && !edv.getValues().isEmpty()){
          cval = new String[edv.getValues().size()];
          for(EnumValue ev:edv.getValues()){
            String enval = ev.getValue().toString();
            if(i == 0)
              val = new AppliedEnumValue(enval, ev.getComment(), edv.getType(), basekey);
            cval[i++] = enval;//TODO mb support not strings in future
          }
        }else 
          val = null;
        
      }
      value = new ComboBoxCellEditor(valc.getTable(), cval, SWT.READ_ONLY);
    }
   
    //value.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    final AttributeType fat = at;
    tvc.setEditingSupport(new EditingSupport(valc){
    
      @Override
      protected void setValue(Object element, Object value) {
        val = value;
        if(element instanceof AppliedEnumValue && value instanceof Integer){
          AppliedEnumValue aev = (AppliedEnumValue) element;
          Attribute def = db.getRootNode().attrs().getAttribute(aev.getBaseKey());
          EnumDefinitionValue edv = ((EnumDefinitionValue)def.getGValue());
          EnumValue ev = edv.getValues().get((int)val);
          val = ev.createApplied(edv.getType(), aev.getBaseKey());
        }
        valc.setInput(val);
      }
    
      @Override
      protected Object getValue(Object element) {
        if(val instanceof AppliedEnumValue){
          String basekey = type.getText().substring(1);
          Attribute a = db.getRootNode().attrs().getAttribute(basekey);
          if(a!=null)
          {
            EnumDefinitionValue edv = a.getGValue();
            int i = 0;
            if(edv!=null && edv.getValues()!=null && !edv.getValues().isEmpty()){
              for(EnumValue ev:edv.getValues()){
                String enval = ev.getValue().toString();
                if(enval.equals(((AppliedEnumValue)element).getValue()))
                  return i;
                i++;
              }
            }
          }
        }
        return element;
      }
    
      @Override
      protected CellEditor getCellEditor(Object element) {
        return value;
      }
    
      @Override
      protected boolean canEdit(Object element) {
        return true;
      }
    });
    valc.setCellEditors(new CellEditor[]{value});
    valc.setInput(val);
    tvc.getColumn().setWidth(valc.getControl().getSize().x);
   // valc.getTable().layout();
    valc.getTable().addControlListener(new ControlListener() {
		
		@Override
		public void controlResized(ControlEvent e) {
		  tvc.getColumn().setWidth(valc.getControl().getSize().x);	
		}
		
		@Override
		public void controlMoved(ControlEvent e) {
			// TODO Auto-generated method stub
			
		}
	});
    valc.refresh(true);
    //valc.getTable().pack();
  }
  private void initTypes() {
    types.clear();
    for(AttributeType at:AttributeType.visibleValues()){
      if(at!=AttributeType.ENUM_DEFINITION)
        types.add(at.name().toString());
    }
    TreeNode nd = db.getRootNode();
    for(Entry<String, Attribute> en:nd.attrs().getActiveAttributes().entrySet())
    {
      if(en.getValue()!=null && en.getValue().getType() == AttributeType.ENUM_DEFINITION 
          && (en.getValue().getGValue() != null && !((EnumDefinitionValue)en.getValue().getGValue()).getValues().isEmpty()))
        types.add(Attribute.ENUM_CHAR+en.getKey());
    }
    type.removeAll();
    for(String s:types)
      type.add(s);
    type.select(types.indexOf(AttributeType.STRING.name()));
  }
  protected Object getSelValue(){// TODO Auto-generated method stub
    return val;
  }
  protected AttributeType getSelType(){// TODO Auto-generated method stub
    int sel = type.getSelectionIndex();
    if(sel>-1){
      String tp = type.getItem(sel);
      if(!tp.startsWith(Attribute.ENUM_CHAR))
        return AttributeType.valueOf(tp);
      return AttributeType.ENUM;
    }
    return AttributeType.STRING;
  }
  private void initControls() {
    add.setEnabled(false);
    remove.setEnabled(false);
    listtypes.select(0);//sets default selection
    selType = typeMap.get(0);
    initTypedEditor(AttributeType.STRING.name());
    setListAttrs();
  }
  private AttributeDefinition mapLook(String text){
    if(!mapVals.containsKey(selType))
      mapVals.put(selType, new HashMap<>());
    String as = SystemNames.getOne(db).getSysName(text);
    if(as!=null)
      return mapVals.get(selType).get(as);
    else
      return mapVals.get(selType).get(text);
  }
  private AttributeDefinition setMap(String text, AttributeType at, Object val) {
    if(!mapVals.containsKey(selType))
      mapVals.put(selType, new HashMap<>());
    if(text.equals(AttributesVersioningRule.ATTR_NODE_VERSION))
      return null;
    String as = SystemNames.getOne(db).getSysName(text);
    IValueProvider vp = new ConstValueProvider(at, val);
    //TODO change to factory
    if(at == AttributeType.REFERENCE)
      vp = new ReferenceValueProvider(at,val);
    if(at == AttributeType.LIST && ((ListValue)val).getType()==AttributeType.REFERENCE)
      vp = new ReferenceValueProvider(at, ((ListValue)val));
      
    if(at == AttributeType.HTML
        || at == AttributeType.LIST && ((ListValue)val).getType()==AttributeType.HTML)
      vp = new HtmlValueProvider(at, ((ReferenceValue)val).getRaw(), 
        TempFolderFilesProvider.getOne(null, false));
    if(as!=null){
      if(as.equals(AttributesVersioningRule.ATTR_NODE_VERSION))
        return null;
      String key = SystemNames.getOne(db).getVisSysName(as);
      mapVals.get(selType).put(as, new AttributeDefinition(null, key, vp));
    }else{
      String vtext = SystemNames.getOne(db).getVisSysName(text);
      if(vtext==null)
        vtext = text;
      as = text;
      mapVals.get(selType).put(text, new AttributeDefinition(null, vtext, vp));
    }
    setListAttrs();
    return mapVals.get(selType).get(as);
    //listattrs.setElements(mapVals.get(selType).values().toArray());
  }
  private void setListAttrs() {
    if(!mapVals.containsKey(selType))
      mapVals.put(selType, new HashMap<>());
    Set<Object> sset = new HashSet<>();
    for(String s:mapVals.get(selType).keySet()){
      String as = SystemNames.getOne(db).getVisSysName(s);
      if(as!=null){
        AttributeDefinition ad = mapVals.get(selType).get(s); 
        sset.add(new AttributeDefinition(null, as, 
          new ConstValueProvider(ad.getType(),ad.getRawValue().getValue())));
      }else
        sset.add(mapVals.get(selType).get(s));
    }
    listattrs.setElements(sset.toArray(new Object[0]));
  }
  @Override
  public void setElement(org.eclipse.core.runtime.IAdaptable element) {
    if (element instanceof IProject) {
      p = (IProject) element;
      db = null;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
		db = et.getTreeDB(p, null, et.getLocalName(p));
	  } catch (IOException e) {
		RLogger.logError(e);
	  }
    }
  };
  
  //Changing settings to default
  @Override
  protected void performDefaults() {
    if(inParallelUpdate())
      return;
    mapVals.clear();
    exnames.setItems(new String[0]);
    initControls();
    super.performDefaults();
  }
  @Override
  public boolean isOkOperationInTransaction() {
    return true;
  }
  @Override
  public void prepareVars() {
  }
  @Override
  public IStatus okPressedAction(IProgressMonitor monitor) {
    if(db == null || actionsStarted)
      return Status.CANCEL_STATUS;
    actionsStarted = true;
    try{
      db.getAttributeRules().getRequiredAttrRule().setMap(db, new HashMap<>(mapVals));
      db.getAttributeRules().saveRules();
      SetRequiredAttributes sra = new SetRequiredAttributes();
      sra.setIgnoreTransaction(true);
      sra.execute(db.getRootNode());
    }catch(Exception e){
      RLogger.logError(e);
      return Status.CANCEL_STATUS;
    }finally {
      actionsStarted = false;
    }
    return Status.OK_STATUS;
  }
  @Override
  public boolean settingsChanged() {
    if(!db.getAttributeRules().getRequiredAttrRule().isSame(mapVals))
      return true;
    return false;
  }
  @Override
  public TreeDB getTreeDB() {
    return db;
  }
  @Override
  public boolean inputCorrect() {
    return true;//No checks for now
  }
}
