package com.unitesk.requality.vscode.port.eclipse.views.panels;

import com.unitesk.requality.vscode.ui.swt.awt.Frame;
import java.awt.event.FocusListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecorationRegistry;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.awt.SWT_AWT;
import com.unitesk.requality.vscode.ui.swt.events.FocusAdapter;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeModification;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.IAttributeFilter;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.document.DiffProcessor;
import com.unitesk.requality.core.operation.document.DiffProcessor.Diff;
import com.unitesk.requality.core.operation.document.DiffProcessor.Operation;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributesPanel.APanelWorkMode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class ElementComparePanel {
  public interface IProblemListener {
    void problemAppears(String problem);

    void problemResolved(String problem);
  }

  private List<IProblemListener> listeners = new ArrayList<>();

  public void addProblemListener(IProblemListener pl) {
    listeners.add(pl);
  }
  private Label nameL, idL, versL, parentL, storeL, typeL, descL;
  private NameEditPanel name;
  private Text id;
  private ControlDecoration nodeIdDecorator;
  //private Text desc;
  private Composite panec;
  private JTextPane pane;
  private AttributesPanel ap;
  private TreeNode node;
  private Composite c;
  private boolean transactionIgnore = false;
  private boolean readOnly;
  private Map<String, String> labels;
  private static Map<String, String> defl = null;

  public static Map<String, String> getDefaultLabels() {
    if (defl == null) {
      defl = new HashMap<>();
      defl.put("idLabel", "Id:");
      defl.put("versionLabel", "Version:");
      defl.put("storageLabel", "Stored format:");
      defl.put("parentLabel", "Parent:");
      defl.put("nameLabel", "Name:");
      defl.put("typeLabel", "Type:");
      defl.put("descriptionLabel", "Description:");
    }
    return Collections.unmodifiableMap(defl);
  }

  public boolean hasProblems() {
    return nodeIdDecorator.isVisible();
  }
  public ElementComparePanel(Composite parent, int columns, boolean readOnly, boolean parentvis, boolean versvis) {
    this(parent, columns, getDefaultLabels(), null, readOnly, parentvis, versvis);
  }
  public ElementComparePanel(Composite parent, int columns, boolean parentvis, boolean versvis) {
    this(parent, columns, getDefaultLabels(), null, parentvis, versvis);
  }

  public ElementComparePanel(Composite parent, int columns, TreeNode node, boolean parentvis, boolean versvis) {
    this(parent, columns, getDefaultLabels(), node, parentvis, versvis);
  }

  public void setTransactionIgnore(boolean ti) {
    transactionIgnore = ti;
    if (name != null) name.setTransactionIgnore(ti);
    if (ap != null) ap.setTransactionIgnore(ti);
  }

  public ElementComparePanel(Composite parent, int columns, Map<String, String> labels, TreeNode nde, boolean parentvis, boolean versvis) {
    this(parent, columns, labels, nde, false, parentvis, versvis);
  }
  public Composite getControl(){
    return c;
  }
  public ElementComparePanel(Composite parent, int columns, Map<String, String> labels, TreeNode nde, boolean readOnly,
      boolean parentVis, boolean versvis) {
    this.labels = labels;
    c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(2,false));
    new Label(c, SWT.NONE).setText(getLabel("versionLabel"));
    versL = new Label(c, SWT.NONE);
    if(parentVis){
      new Label(c, SWT.NONE).setText(getLabel("parentLabel"));
      parentL = new Label(c, SWT.NONE);
    }
    if(versvis){
      new Label(c, SWT.NONE).setText(getLabel("storageLabel"));
      storeL = new Label(c, SWT.NONE);
    }
    new Label(c, SWT.NONE).setText(getLabel("typeLabel"));
    typeL = new Label(c, SWT.NONE);
//    leftv = new Label(lc, SWT.NONE);
//    leftv.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL));
//    rightv = new Label(rc, SWT.NONE);
//    rightv.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL));
    idL=new Label(c, SWT.READ_ONLY);
    idL.setText(getLabel("idLabel"));
    this.readOnly = readOnly;
    id = new Text(c, readOnly?SWT.READ_ONLY:SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 1;
    id.setLayoutData(gd);
    nodeIdDecorator = createDecorator(id);
    nodeIdDecorator.hide();
    id.addKeyListener(
        new KeyListener() {

          @Override
          public void keyReleased(KeyEvent e) {
            try {
              if (node == null) {
                return;
              }

              if (node.getTreeDB().getTreeLogic().isIdValidExc(node, id.getText().trim())) {
                unshowIdProblem();
              } else {
                nodeIdDecorator.setDescriptionText(
                    "Element Id is not correct and Element Id should not be empty");
                showIdProblem();
              }
            } catch (SameIdExistsInChildren e1) {
              nodeIdDecorator.setDescriptionText("Element with the same Id already exists");
              showIdProblem();
            } catch (IdEmpty e1) {
              showIdProblem();
              nodeIdDecorator.setDescriptionText("Element Id is empty");
            } catch (IdContainsWrongChars e1) {
              showIdProblem();
              nodeIdDecorator.setDescriptionText(
                  "Element Id contains wrong character - " + e1.getWrongCharacter());
            }
          }

          @Override
          public void keyPressed(KeyEvent e) {}
        });
    id.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            unshowIdProblem();
            if (!id.getText().equals(id.getText().trim())) {
              id.setText(id.getText().trim());
            }
            if (node == null) {
              return;
            }
            if (node.getId().equals(id.getText())) {
              return;
            }
            if (!node.getQualifiedId().equals(Requirement.getTypeRootQId())) {
              String newId = id.getText().trim();
              final Text id = ElementComparePanel.this.id;
              final TreeNode node = ElementComparePanel.this.node;
              final boolean transactionIgnore = ElementComparePanel.this.transactionIgnore;
              WorkspaceJob j = new WorkspaceJob("Change node id for "+node.getUserVisibleName()){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if (node.getWorkParent()!=null && node.getTreeDB().getTreeLogic().isIdValid(node, newId)){
                    boolean cont = transactionIgnore;
                    boolean started = !cont && node.getTreeDB().startTransaction(
                            "Change " + CoreUtils.splitWords(node.getType()) + " Id", true);
                    cont = cont || started;
                    try {
                      if (cont) {
                        node.setId(newId.trim());
                        node.commitAttributes();
                        if (started) {
                          started = false;
                          node.getTreeDB().commit();
                        }
                      } else {
                        EclipseAPI.getDisplay().asyncExec(new Runnable(){
                        
                          @Override
                          public void run() {
                            if(id.isDisposed() || node == null)
                              return;
                            id.setText(node.getId());
                          }
                        });
                      }
                    }finally {
                      if(started)
                        node.getTreeDB().rollback();
                    }
                  }
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
            }
          }
        });
    nameL = new Label(c, SWT.READ_ONLY);
    nameL.setText(getLabel("nameLabel"));
    
    name = new NameEditPanel(c, readOnly?SWT.READ_ONLY:SWT.NONE);
    name.setTransactionIgnore(transactionIgnore);
    name.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
//    gd = new GridData(GridData.FILL_HORIZONTAL);
//    gd.horizontalSpan = 1;
    //name.getControl().setLayoutData(gd);

    //new Label(parent, SWT.NONE).setText(getLabel("descriptionLabel"));
    descL =(new Label(c, SWT.NONE));
    descL.setText(getLabel("descriptionLabel"));
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 1;
    descL.setLayoutData(gd);
    //desc = new Text(c, SWT.READ_ONLY | SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    panec = new Composite(c, SWT.EMBEDDED | (readOnly?SWT.READ_ONLY:SWT.NONE));
    com.unitesk.requality.vscode.ui.swt.awt.SWT_AWT sa = new SWT_AWT();

    Frame f = sa.new_Frame(panec);
    
    gd = new GridData();
    gd.heightHint = 80;
    gd.widthHint = 300;
    //gd.horizontalSpan = 0;
    panec.setLayoutData(gd);
    
    pane = new JTextPane(new DefaultStyledDocument());
    pane.setEditable(!readOnly);
    //pane.setContentType("text/plain");
    //pane.setSize(new Dimension(panec.getSize().x,panec.getSize().y));
    JScrollPane sp = new JScrollPane(pane);
    //f.setSize(new Dimension(panec.getSize().x,panec.getSize().y));
    f.add(sp);
    //sp.add(pane);
//    pane.
    //desc.setLayoutData(gd);
//    desc =
//        new Text(
//            null, ,c, (readOnly?SWT.READ_ONLY:SWT.NONE) |SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    gd = new GridData(400, 80);
    gd.heightHint = 80;
    gd.horizontalSpan = 1;
    //desc.setLayoutData(gd);
    setTreeNode(nde);
    pane.addFocusListener(new FocusListener(){
    
      @Override
      public void focusLost(java.awt.event.FocusEvent e) {
        updateText();
      }
    
      @Override
      public void focusGained(java.awt.event.FocusEvent e) {
        // TODO Auto-generated method stub
        
      }
      private void updateText(){
        if(ElementComparePanel.this.readOnly)
          return;
        try {
          String s = pane.getDocument().getText(0, pane.getDocument().getLength());
          if(!toString(node.getRawDescription()).equals(s)){
            final TreeNode node = ElementComparePanel.this.node;
//            System.out.println(2 ); 
            WorkspaceJob j = new WorkspaceJob("Update description for "+node.getUserVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                boolean started = node.getTreeDB().startTransaction("Update '"+node.getUserVisibleName()+"' description");
                if(started)
                {
                  try {
                    node.setDescription(s);
                    if(node.attrs().getChanged().contains(TreeNode.ATTR_DESCRIPTION)){
                      node.commitAttributes();
                      if(started) {
                        node.getTreeDB().commit();
                        started = false;
                      }
                    }else{
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                        @Override
                        public void run() {
                          if(getControl().isDisposed())
                            return;
                          setDescription(node.getRawDescription());                        
                        }
                      });
                    }
                  }finally {
                    if(started)
                      node.getTreeDB().rollback();
                  }
    //              node.commitAttributes();
    //              node.getTreeDB().commit();
                }else
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      @Override
                      public void run() {
                        if(getControl().isDisposed())
                          return;
                        setDescription(node.getRawDescription());                        
                      }
                    });
                return Status.OK_STATUS;
              }
            };
            j.schedule();
          }
        } catch (BadLocationException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      }

	  private Object toString(String desc) {
        return desc == null?"":desc;
     }
    });
    if (node == null || node.canEditAttributesInTable()) {
      ap = new AttributesPanel(null, c, APanelWorkMode.LOCAL, readOnly);
      ap.setAttributeFilter(new IAttributeFilter(){
      
        @Override
        public boolean filter(Attribute a) {
          if (a.getKey().startsWith("_")||a.isVirtual())
            return false;
          if(!a.getOrigin().equals(node.getUUId()))
          {
            TreeNode n = node.getCloneParent();
            while(n!=null){
              if(a.getOrigin().equals(n.getUUId()))
                return true;
              n = n.getCloneParent();
            }
            return false;
          }
          return true;
        }
      }, 
      new IAttributeNameFilter(){
      
        @Override
        public boolean filter(String key) {
          return !key.startsWith("_");
        }
      });
      gd = new GridData(GridData.FILL_BOTH);
      gd.heightHint = 200;
      gd.horizontalSpan = 2;
      ap.getControl().setLayoutData(gd);
    }
  }

  private String getLabel(String string) {
    return labels.getOrDefault(string, "<"+string+">");
  }

//  public boolean isDirty() {
//    String newDescr = "";
//    boolean descEqual = true;
//    if (desc != null) {
//      newDescr = desc.getText();
//      newDescr = CoreUtils.htmlEncapsulate(newDescr);
//      if (oldDescr == null) oldDescr = "";
//      descEqual = !desc.isDirty()&&newDescr.equals(oldDescr);
//    }
//    if (oldName == null) oldName = "";
//    return (ap != null && ap.isDirty())
//        || !descEqual
//        || !name.getControl().getText().equals(oldName)
//        || !id.getText().equals(oldId);
//  }

  protected void showIdProblem() {
    nodeIdDecorator.show();
    for (IProblemListener pl : listeners) pl.problemAppears("id_problem");
  }

  protected void unshowIdProblem() {
    nodeIdDecorator.hide();
    for (IProblemListener pl : listeners) pl.problemResolved("id_problem");
  }

  private ControlDecoration createDecorator(Text text) {
    ControlDecoration controlDecoration = new ControlDecoration(text, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration =
        FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    controlDecoration.setImage(fieldDecoration.getImage());
    return controlDecoration;
  }

  private String oldDescr, oldName, oldId;
  public void setTreeNode(TreeNode node) {
    if (nodeIdDecorator != null) unshowIdProblem();
    this.node = node;
    if (node != null) id.setText(node.getId());
    if(parentL!=null)
      parentL.setText("");
    versL.setText("");
    typeL.setText("");;
    
    name.setTreeNode(node);
    if (node != null){
      if(versL!=null)
        versL.setText(node.getNodeVersion());
      typeL.setText(node.getTypeLabel());
      String text = node.getRawDescription();
      //pane.setText(text == null ? "": text);
      
      setDescription(text);
//      StyledDocument styleDoc = pane.getStyledDocument();
//      try {
//        styleDoc.insertString(styleDoc.getLength(), text == null ? "": text, null);
//      } catch (BadLocationException e) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      }

//      pane.replaceSelection();
      //pane.setContentType("text/plain");
      oldDescr = node.getRawDescription();
      oldName = node.getName();
      oldId = node.getId();
//      if(node.getShownParent()!=null){
//        setParent(node.getQualifiedId());
//      }
    }
    if (ap != null) {
      ap.getControl().setVisible(true);
      if (node!=null && node.canEditAttributesInTable()) ap.setNode(node, false);
      else
        //TODO remove
        ap.getControl().setVisible(false);
    }
  }
  
  private void setDescription(String text) {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    pane.setContentType("text/plain");
    text = text == null ? "": text;
    try {
      pane.read(new ByteArrayInputStream(text.getBytes()), doc);
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  static Color add = new Color(EclipseAPI.getDisplay(), 204, 255, 153),
      remove= new Color(EclipseAPI.getDisplay(), 255, 204, 204), 
      modify= new Color(EclipseAPI.getDisplay(), 255, 255, 50);
  private Color toColor(AttributeModification ad){
//    if(!defc.containsKey(k) && id2 != null)
//      defc.put(k, id2.getBackground());
    if(ad!=null)
      switch (ad) {
        case ADD:
          return add;
        case REMOVE:
          return remove;
        case MODIFY:
          return modify;
      }
    return null;//defc.get(k);
  }

  public void markAttribute(String k, AttributeModification add) {
    switch (k) {
      case TreeNode.ATTR_ID:
        idL.setBackground(toColor(add));
        break;
      case TreeNode.ATTR_NAME:
        nameL.setBackground(toColor(add));
        break;
      case TreeNode.ATTR_TYPE:
        typeL.setBackground(toColor(add));
        break;
      case TreeNode.ATTR_DESCRIPTION:
        descL.setBackground(toColor(add));
        break;
      case AttributesVersioningRule.ATTR_NODE_VERSION:
        versL.setBackground(toColor(add));
        break;
      default:
        if(ap!=null)
          ap.setAttributeMarkColor(ap.getViewer(), k, toColor(add));
        break;
    }
  }
  public void resetColors(){
    markAttribute(TreeNode.ATTR_ID, null);
    markAttribute(TreeNode.ATTR_NAME, null);
    markAttribute(TreeNode.ATTR_DESCRIPTION, null);
    markAttribute(TreeNode.ATTR_TYPE, null);
    markAttribute(AttributesVersioningRule.ATTR_NODE_VERSION, null);
    if(storeL!=null)
      storeL.setBackground(null);
    ap.resetColors(ap.getViewer());
    if(parentL!=null)
      parentL.setBackground(null);
    
  }
  private static void appendToPane(JTextPane pane, int red, int green, int blue, int alph, int pos, int end)
  {
      StyleContext sc = StyleContext.getDefaultStyleContext();
      AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Background, new java.awt.Color(
        red,green,blue,alph));

      aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Arial");
      aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);

      pane.setCaretPosition(pos);
      pane.setSelectionEnd(end);
      pane.setCharacterAttributes(aset, false);
      pane.setCaretPosition(0);
      pane.setSelectionEnd(0);
      //pane.replaceSelection(msg);
  }
  private static void appendToPane(JTextPane pane, Color c, int pos, int end)
  {
      appendToPane(pane,
        c.getRed(),c.getGreen(),c.getBlue(),c.getAlpha(),pos,end);
  }
  private static void appendToPane(JTextPane pane, java.awt.Color c, int pos, int end) {
    appendToPane(pane,
      c.getRed(),c.getGreen(),c.getBlue(),c.getAlpha(),pos,end);
  }
  public static void setDiffColors(JTextPane one, JTextPane another){
    DiffProcessor dp = new DiffProcessor();
    appendToPane(one,one.getBackground(),0, one.getDocument().getLength());
    appendToPane(another,another.getBackground(),0, another.getDocument().getLength());
    LinkedList<Diff> l = dp.diff_main(one.getText(), another.getText());
    //dp.diff_charsToLines(l, dp.linearray);
    // Eliminate freak matches (e.g. blank lines)
    //dp.diff_cleanupSemantic(l);
//    dp.diff_cleanupMerge(l);
//    dp.diff_cleanupSemanticLossless(l);
//    dp.d
    for(Diff d:l){
      if(d.operation == Operation.EQUAL)
        continue;
      int stl = d.originalOffset, str = d.replacementOffset;
      if(d.operation == Operation.DELETE){
        appendToPane(one,add,stl-d.text.length(), stl);
      }
      if(d.operation == Operation.INSERT){
        appendToPane(another,remove,str-d.text.length(), str);
      }
      //int end = st+d.text.length();
    }
    //pane.setSelectionStart(selectionStart);
  }
  
 

  public void setEditable(boolean b) {
    //if(ap!=null)
     // ap.setEnabled(b);
    //desc.setEnabled(b);
    //nameL.setEnabled(b);
    //idL.setEnabled(false);//TODO support
  }

  public void setVersion(String nodeVersion) {
    versL.setText(nodeVersion);
  }
  public void setFormatVersion(String nodeVersion) {
    if(storeL != null)
      storeL.setText(nodeVersion);
  }

  public JTextPane getDescPanel() {
    return pane;
  }

  public void setVisible(boolean b) {
    c.setVisible(b);
    ap.getControl().setVisible(b);
  }
  public void setParent(String nodePath) {
    if(parentL == null)
      return;
    parentL.setText("");
    if(nodePath!=null){
      nodePath = filterPath(nodePath);
      parentL.setText(nodePath);
    }
  }

  public static String filterPath(String nodePath) {
    if(nodePath == null)
      return null;
    int ind = nodePath.indexOf("/Requirements");
    if(ind>-1 ){
      nodePath = nodePath.substring(ind+1);
      if(nodePath.length()>13&&(nodePath.lastIndexOf("/")!=nodePath.indexOf("/")))
        nodePath = nodePath.substring(13);
    }
    if(nodePath.startsWith("/"))
      nodePath = nodePath.substring(1);
    ind = nodePath.lastIndexOf("/");
    if(ind>-1)
      nodePath = nodePath.substring(0,ind);
    else
      nodePath = "/";
    int end = nodePath.toLowerCase().indexOf(".json");
    if(end>-1)
      nodePath = nodePath.substring(0, end-1);
    return nodePath;
  }

  public void markParentChange(AttributeModification modify2) {
    if(parentL!=null)
      parentL.setBackground(toColor(modify2));
  }
  public void markStoredVersionChange(AttributeModification modify2) {
    if(storeL!=null)
      storeL.setBackground(toColor(modify2));
  }
}
