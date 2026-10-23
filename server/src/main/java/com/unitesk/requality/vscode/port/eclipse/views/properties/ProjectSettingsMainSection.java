/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.vscode.port.eclipse.views.properties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.IAttributeFilter;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.EnumDefExtractDialog;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributesPanel;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributesPanel.APanelWorkMode;
import com.unitesk.requality.vscode.port.eclipse.views.panels.IAttributeNameFilter;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ProjectSettingsMainSection extends TreeNodePropertiesTab {

  private AttributesPanel attrPanel;
  private Button enumDefFromAttr;
  private static String TEXT_ENUM_FROM_ATTR = "Extract enum definition from attributes";
  public static IAttributeFilter filter =
      new IAttributeFilter() {

        @Override
        public boolean filter(Attribute a) {
          return !a.getKey().startsWith("_") && !a.getKey().startsWith("@");
        }
      };
  public static IAttributeNameFilter namefilter =
          new IAttributeNameFilter() {

            @Override
            public boolean filter(String key) {
              return !key.startsWith("_") && !key.startsWith("@");
            }
          };

  private TreeNode selectedRoot;

  private boolean isData() {
    return ReportData.TYPE_NAME.equals(node.getType());
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    selectedRoot = node;
    if(node!=null && (attrPanel.getControl().isDisposed() || !node.isRoot()))
      return;
    attrPanel.setNode(node, isDisposed);
    attrPanel.setEnabled(attrPanel.getViewer(), !isData());
  }

  @Override
  public void createControls(
      final Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    parent.setLayout(new GridLayout(2, false));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors

    Composite buttons = new Composite(parent, SWT.NONE);
    buttons.setLayout(new FillLayout());

    enumDefFromAttr = new Button(buttons, SWT.NONE);
    enumDefFromAttr.setText(TEXT_ENUM_FROM_ATTR);
    enumDefFromAttr.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            if(enumDefFromAttr.isDisposed())
              return;
            TreeDB db = selectedRoot.getTreeDB();
            final IAttributeFilter filter = ProjectSettingsMainSection.this.filter;
            final TreeNode selectedRoot = ProjectSettingsMainSection.this.selectedRoot;
            WorkspaceJob j = new WorkspaceJob("Gather enum definitions for "+(db==null?"empty tree":db.getVisibleName())){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(db == null)
                  return Status.CANCEL_STATUS;
                DeepFirstTreeWalker walker = new DeepFirstTreeWalker(db.getNode(Requirement.getTypeRootQId()), true, true, false);
                Iterator<TreeNode> iter = walker.iterator();
                Set<String> names = new HashSet<>();
                while (iter.hasNext()) {
                  TreeNode node = iter.next();
                  for (Attribute a : node.attrs())
                    if (EnumDefExtractDialog.filter.filter(a)) names.add(a.getKey());
                }
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                
                  @Override
                  public void run() {
                    if(isDisposed || parent.isDisposed())
                      return;
                    EnumDefExtractDialog d = new EnumDefExtractDialog(parent.getShell(), db, names);
                    if (d.open() == EnumDefExtractDialog.OK) {
                      String[] res = d.getResult();
                      String defName = res[1], attrName = res[0];
                      WorkspaceJob j = new WorkspaceJob("Change enum definition "+defName+" settings for "+(db!=null?db.getVisibleName():"null tree")){

                        @Override
                        public IStatus runInWorkspace(IProgressMonitor monitor) {
                          if(db == null)
                            return Status.CANCEL_STATUS;
                          boolean started = db.startTransaction("Create enum definition '" + defName + "'");
                          if (started) {
                            try {
                              EnumDefinitionValue edv =
                                  new EnumDefinitionValue(AttributeType.STRING, new ArrayList<EnumValue>());
                              DeepFirstTreeWalker walker = new DeepFirstTreeWalker(selectedRoot);
                              Iterator<TreeNode> iter = walker.iterator();
                              Set<Object> vals = new HashSet<>();
                              while (iter.hasNext()) {
                                TreeNode node = iter.next();
                                Attribute a = node.attrs().getAttribute(attrName);
                                if (a != null
                                    && filter.filter(a)
                                    && a.getType() != AttributeType.RESOURCE
                                    && a.getType() != AttributeType.ENUM
                                    && a.getType() != AttributeType.ENUM_DEFINITION)
                                  vals.add(a.getValue().toString());
                              }
                              for (Object o : vals) edv.addEnumValue(o, null);
  
                              //new Object[]{AttributeType.STRING,vals});
                              selectedRoot.attrs().setConstAttribute(defName, edv,AttributeDefinition.detectFlags(defName));
                              selectedRoot.commitAttributes();
                              iter = walker.iterator();
                              while (iter.hasNext()) {
                                TreeNode node = iter.next();
                                Attribute a = node.attrs().getAttribute(attrName);
                                if (a != null && EnumDefExtractDialog.filter.filter(a)) {
                                  String val = Attribute.valueToString(node.attrs(), a.getType(), a.getValue());
                                  node.attrs().setAttribute(new AttributeDefinition(a.getOrigin(), attrName, a.getAvailability(),
                                      new ConstValueProvider(AttributeType.ENUM, new AppliedEnumValue(val, null, edv.getType(), defName)),a.getDefinition().getFlags()));//TODO flags?
  //                                    Attribute newa =
  //                                        node.attrs()
  //                                            .createAttribute(
  //                                                attrName,
  //                                                new AppliedEnumValue(val, null, edv.getType(), defName),
  //                                                a.getAvailability());
  //                                    node.attrs().storeAttribute(newa);
                                  node.commitAttributes();
                                }
                              }
                              if(started) {
                                db.commit();
                                started = false;
                              }
                            }finally {
                              if(started)
                                db.rollback();
                            }
                          }
                          return Status.OK_STATUS;
                        }
                        
                      };
                      j.schedule();
                    }                    
                  }
                });
                
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        });

    Composite panelmid = new Composite(parent, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    panelmid.setLayoutData(gd);
    GridLayout gl = new GridLayout(5, false);
    gl.horizontalSpacing = gl.verticalSpacing = 0;
    panelmid.setLayout(gl);

    attrPanel = new AttributesPanel(fsid, parent, APanelWorkMode.GLOBAL);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 2;
    attrPanel.getControl().setLayoutData(data);
    attrPanel.setAttributeFilter(filter, namefilter);
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return true;
  };

  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s: what.getAll())
      if(!s.startsWith("_"))
        return true;
    return false;
  };
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
