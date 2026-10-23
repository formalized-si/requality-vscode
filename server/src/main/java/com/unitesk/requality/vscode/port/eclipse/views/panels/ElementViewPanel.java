package com.unitesk.requality.vscode.port.eclipse.views.panels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecorationRegistry;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.FocusAdapter;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

import com.unitesk.requality.core.attribute.AttributeModification;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributesPanel.APanelWorkMode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class ElementViewPanel {
  public interface IProblemListener {
    void problemAppears(String problem);

    void problemResolved(String problem);
  }

  private List<IProblemListener> listeners = new ArrayList<>();

  public void addProblemListener(IProblemListener pl) {
    listeners.add(pl);
  }
  private Label nameL, idL;
  private NameEditPanel name;
  private Text id;
  private ControlDecoration nodeIdDecorator;
  private ElementDescriptionPanel desc;
  private AttributesPanel ap;
  private TreeNode node;
  private boolean transactionIgnore = false;
  private boolean readOnly;
  private static Map<String, String> defl = null;

  public static Map<String, String> getDefaultLabels() {
    if (defl == null) {
      defl = new HashMap<>();
      defl.put("idLabel", "Id:");
      defl.put("nameLabel", "Name:");
      defl.put("descriptionLabel", "Description");
    }
    return Collections.unmodifiableMap(defl);
  }

  public boolean hasProblems() {
    return nodeIdDecorator.isVisible();
  }
  public ElementViewPanel(Composite parent, int columns, boolean readOnly) {
    this(parent, columns, getDefaultLabels(), null, readOnly);
  }
  public ElementViewPanel(Composite parent, int columns) {
    this(parent, columns, getDefaultLabels(), null);
  }

  public ElementViewPanel(Composite parent, int columns, TreeNode node) {
    this(parent, columns, getDefaultLabels(), node);
  }

  public void setTransactionIgnore(boolean ti) {
    transactionIgnore = ti;
    if (name != null) name.setTransactionIgnore(ti);
    if (ap != null) ap.setTransactionIgnore(ti);
  }

  public ElementViewPanel(Composite parent, int columns, Map<String, String> labels, TreeNode nde) {
    this(parent, columns, labels, nde, false);
  }
  public ElementViewPanel(Composite parent, int columns, Map<String, String> labels, TreeNode nde, boolean readOnly) {
    idL=new Label(parent, SWT.NONE);
    idL.setText(labels.get("idLabel"));
    this.readOnly = readOnly;
    id = new Text(parent, readOnly?SWT.READ_ONLY:SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = columns < 3 ? 1 : columns - 1;
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
              if(id.isDisposed())
                  return;

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
            if(id.isDisposed())
              return;
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
              final TreeNode node = ElementViewPanel.this.node;
              final boolean transactionIgnore = ElementViewPanel.this.transactionIgnore;
              final Text id = ElementViewPanel.this.id;
              WorkspaceJob j = new WorkspaceJob("Change id for "+node.getUserVisibleName()){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  boolean cont = false;
                  if (node.getWorkParent()!=null && node.getTreeDB().getTreeLogic().isIdValid(node, newId)) {
                    cont = transactionIgnore;
                    boolean started = !cont && node.getTreeDB().startTransaction(
                            "Change " + CoreUtils.splitWords(node.getType()) + " Id", true);
                    cont = cont || started;
                    try {
                      if(cont) {
                        node.setId(newId.trim());
                        node.commitAttributes();
                        if (started) {
                          node.getTreeDB().commit();
                          started = false;
                        }
                      }
                    }finally {
                      if(started)
                        node.getTreeDB().rollback();
                    }
                  }
                  if(!cont){
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(id.isDisposed()||node == null)
                          return;
                        id.setText(node.getId());
                      }
                    });
                  }
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
            }
          }
        });
    nameL = new Label(parent, SWT.NONE);
    nameL.setText(labels.get("nameLabel"));
    
    name = new NameEditPanel(parent, readOnly?SWT.READ_ONLY:SWT.NONE);
    name.setTransactionIgnore(transactionIgnore);
    name.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = columns < 3 ? 1 : columns;
    name.getControl().setLayoutData(gd);

    //new Label(parent, SWT.NONE).setText(labels.get("descriptionLabel"));
    desc =
        new ElementDescriptionPanel(
            null, labels.get("descriptionLabel"),parent, (readOnly?SWT.READ_ONLY:SWT.NONE) |SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    gd = new GridData(600, 80);
    gd.heightHint = 80;
    desc.getControl().setLayoutData(gd);
    desc.getEditControl()
        .addFocusListener(
            new FocusAdapter() {
              public void focusLost(FocusEvent e) {
            	if (node == null || desc.getControl().isDisposed()) {
                  return;
                }
                String newText = desc.getEditControl().getText();
                if (newText == null) return;
                String oldDescr = node.getRawDescription();
                if (oldDescr == null) {
                  oldDescr = "";
                }
                String newDescr = "";
                newDescr = newText;
                newDescr = CoreUtils.htmlEncapsulate(newDescr);
                if (oldDescr.equals(newDescr)) {
                  return;
                }
                String fnewDescr = newDescr;
                final TreeNode node = ElementViewPanel.this.node;
                final boolean transactionIgnore = ElementViewPanel.this.transactionIgnore;
                
                final ElementDescriptionPanel desc = ElementViewPanel.this.desc;
                WorkspaceJob j = new WorkspaceJob("Update description of "+node.getQualifiedId()){

                  @Override
                  public IStatus runInWorkspace(IProgressMonitor monitor) {
                    if(node == null)
                      return Status.CANCEL_STATUS;
                  //if new attribute text is "", then remove this attribute rather than set it to "",
                    //because otherwise we get modified .json-files with empty attribute added,
                    //when simply navigating nodes in Requality Explorer and Properties View,
                    //and this is affecting .svn
                   
                boolean cont = transactionIgnore;
                boolean started = !cont && node.getTreeDB().startTransaction("Update " + node.getTypeLabel(), true);
                cont = cont || started;
                if (cont) {
                  try {
                	node.setDescription(fnewDescr);
                    if(node.attrs().getChanged().contains(TreeNode.ATTR_DESCRIPTION)){
                      node.commitAttributes();
                      if(started) {
                        node.getTreeDB().commit();
                        started = false;
                      }
                    }
                  }finally {
                    if(started) {
                      node.getTreeDB().rollback();
                      started = false;
                    }
                  }
                  //node.commitAttributes();
                  //if (!transactionIgnore) node.getTreeDB().commit();
                }
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(desc.getControl().isDisposed() || node == null)
                      return;
                    desc.setDocument(node.getRawDescription(),node.getDescription(true,true, false));
                  }
                });
                return Status.OK_STATUS;
              }
            }; 
            j.schedule();
                //desc.setDocument(docu, rval);
                //TODO else cancel
           }
         });
    setTreeNode(nde);
    if (node == null || node.canEditAttributesInTable()) {
      ap = new AttributesPanel(null, parent, APanelWorkMode.LOCAL,readOnly);
      gd = new GridData(GridData.FILL_BOTH);
      gd.heightHint = 200;
      gd.horizontalSpan = columns < 2 ? 1 : columns;
      ap.getControl().setLayoutData(gd);
    }
  }

  public boolean isDirty() {
    String newDescr = "";
    boolean descEqual = true;
    if (desc != null) {
      newDescr = desc.getEditControl().getText();
      newDescr = CoreUtils.htmlEncapsulate(newDescr);
      if (oldDescr == null) oldDescr = "";
      descEqual = !desc.isDirty()&&newDescr.equals(oldDescr);
    }
    if (oldName == null) oldName = "";
    return (ap != null && ap.isDirty())
        || !descEqual
        || !name.getControl().getText().equals(oldName)
        || !id.getText().equals(oldId);
  }

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
  public void setVisible(boolean b){
    name.getControl().getParent().setVisible(b);
  }
  public void setTreeNode(TreeNode node) {
    if (nodeIdDecorator != null) unshowIdProblem();
    this.node = node;
    if (node != null) id.setText(node.getId());
    name.setTreeNode(node);
    if (node != null){
      desc.setDocument(node.getRawDescription(),node.getDescription(true,true, false));
      oldDescr = node.getRawDescription();
      oldName = node.getName();
      oldId = node.getId();
    }
    if (ap != null) {
      ap.getControl().setVisible(true);
      if (node!=null && node.canEditAttributesInTable()) ap.setNode(node, false);
      else
        //TODO remove
        ap.getControl().setVisible(false);
    }
  }
  
  Color add = new Color(EclipseAPI.getDisplay(), 204, 255, 153),
      remove= new Color(EclipseAPI.getDisplay(), 255, 204, 204), 
      modify= new Color(EclipseAPI.getDisplay(), 255, 255, 50);
  private Map<String, Color> defc = new HashMap<>();
  private Color toColor(Control id2, AttributeModification ad, String k){
    if(!defc.containsKey(k) && id2 != null)
      defc.put(k, id2.getBackground());
    if(ad!=null)
      switch (ad) {
        case ADD:
          return add;
        case REMOVE:
          return remove;
        case MODIFY:
          return modify;
      }
    return null;
  }

  public void markAttribute(String k, AttributeModification add) {
    switch (k) {
      case TreeNode.ATTR_ID:
        idL.setBackground(toColor(id,add,k));
        break;

      case TreeNode.ATTR_NAME:
        nameL.setBackground(toColor(name.getControl(),add,k));
        break;

      case TreeNode.ATTR_DESCRIPTION:
        desc.getControl().setBackground(toColor(desc.getControl(),add,k));
        break;
      default:
        if(ap!=null)
          ap.setAttributeMarkColor(ap.getViewer(),k, toColor(null, add, k));
        break;
    }
  }
  public void resetColors(){
    markAttribute(TreeNode.ATTR_ID, null);
    markAttribute(TreeNode.ATTR_NAME, null);
    markAttribute(TreeNode.ATTR_DESCRIPTION, null);
    ap.resetColors(ap.getViewer());
  }

  public void setEditable(boolean b) {
    if(ap!=null)
      ap.setEnabled(ap.getViewer(),b);
    desc.getControl().setEnabled(b);
    nameL.setEnabled(b);
    idL.setEnabled(false);//TODO support
  }
}
