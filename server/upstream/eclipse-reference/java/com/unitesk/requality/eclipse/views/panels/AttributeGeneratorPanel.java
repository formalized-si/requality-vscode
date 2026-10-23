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
package com.unitesk.requality.eclipse.views.panels;

import java.util.UUID;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.AttributeValueIteratorType;
import com.unitesk.requality.core.nodeiterator.RequalityIteratorType;

public class AttributeGeneratorPanel {
  private Composite panel;

  private Composite panel_typed, panel_typed_add, general;

  //    static private int i = 0;
  private AttributeGenerator iter;
  //additional type list(for example - for id)
  private Combo addtype;

  private Text max, min, itr;
  private org.eclipse.swt.widgets.List values;
  private Button to_gen;

  private SelectionListener addtypelistener;
//
//  private TreeNode currentNode;

  private void updatePanels(TreeNode node, final AttributeGenerator iter, boolean readOnly) {
    //for(Control c:panel_typed.getChildren())
    //c.dispose();
    this.iter = iter;
    if (iter != null && iter.getType().equals(RequalityIteratorType.ATTRIBUTE)) {
      if (panel_typed_add == null) {
        panel_typed_add = new Composite(panel_typed, SWT.NONE);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.horizontalSpan = 2;
        panel_typed_add.setLayoutData(gridData);
        panel_typed_add.setLayout(new GridLayout(2, false));
      }
      rebuildAttributeIteratorPanel(node, iter, readOnly);
      panel_typed.pack();
      panel_typed.update();
      panel_typed.getParent().pack();
      setEnabled(panel_typed_add,!readOnly);
      addtype.setEnabled(!readOnly);
    }
  }

  private void setEnabled(Control com, boolean b) {
    com.setEnabled(b);
    if(com instanceof Composite)
      for(Control c:((Composite)com).getChildren())
        setEnabled(c, b);
  }

  private String activePanel = null;

  private void constructPanelComponents(TreeNode currentNode, final AttributeGenerator nit, final boolean readOnly) {
    for (Control c : panel_typed_add.getChildren()) c.dispose();
    String id = iter.getGeneratorType().getId();
    if (id.equals(AttributeValueIteratorType.CYCLE_ID)) {
      new Label(panel_typed_add, SWT.NONE).setText("From:");
      min = new Text(panel_typed_add, SWT.NONE);
      min.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      min.addFocusListener(
          new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
              int val;
              try {
                val = Integer.valueOf(min.getText());
              } catch (NumberFormatException nfe) {
                val = 0;
              }

              if (nit != null && iter.getMin() != val) {
                iter.setMin(val);
                setIterator(currentNode, iter, readOnly);
              }
            }

            @Override
            public void focusGained(FocusEvent e) {}
          });

      new Label(panel_typed_add, SWT.NONE).setText("To:");
      max = new Text(panel_typed_add, SWT.NONE);
      max.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      max.addFocusListener(
          new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
              int val;
              try {
                val = Integer.valueOf(max.getText());
              } catch (NumberFormatException nfe) {
                val = 0;
              }
              if (nit != null && iter.getMax() != val) {
                iter.setMax(val);
                setIterator(currentNode, iter, readOnly);
              }
            }

            @Override
            public void focusGained(FocusEvent e) {
              // TODO Auto-generated method stub

            }
          });

      new Label(panel_typed_add, SWT.NONE).setText("Step:");
      itr = new Text(panel_typed_add, SWT.NONE);
      itr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      itr.addFocusListener(
          new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
              int val = 1;
              try {
                val = Integer.valueOf(itr.getText());
              } catch (NumberFormatException nfe) {
                val = 1;
              }

              if (nit != null && iter.getIteration() != val)
                try {
                  iter.setIteration(Integer.valueOf(itr.getText()));
                  setIterator(currentNode, iter, readOnly);
                } catch (NumberFormatException e1) {
                  e1.printStackTrace();

                  e1.printStackTrace();
                }
            }

            @Override
            public void focusGained(FocusEvent e) {}
          });
    } else if (id.equals(AttributeValueIteratorType.RANDOM_ID)) {
      new Label(panel_typed_add, SWT.NONE).setText("Min:");

      min = new Text(panel_typed_add, SWT.NONE);
      min.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      min.addFocusListener(
          new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
              int val;
              try {
                val = Integer.valueOf(min.getText());
              } catch (NumberFormatException nfe) {
                val = 0;
              }
              if (iter != null && iter.getMin() != val) {
                try {
                  iter.setMin(Integer.valueOf(min.getText()));
                  iter.getRandValues(true);
                  setIterator(currentNode, iter, readOnly);
                } catch (NumberFormatException e1) {
                  e1.printStackTrace();

                  e1.printStackTrace();
                }
              }
            }

            @Override
            public void focusGained(FocusEvent e) {
              // TODO Auto-generated method stub

            }
          });

      new Label(panel_typed_add, SWT.NONE).setText("Max:");
      max = new Text(panel_typed_add, SWT.NONE);
      max.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      max.addFocusListener(
          new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
              int val = 0;
              try {
                val = Integer.valueOf(max.getText());
              } catch (NumberFormatException nfe) {
                val = 0;
              }

              if (nit != null && iter.getMax() != val) {
                iter.setMax(Integer.valueOf(max.getText()));
                iter.getRandValues(true);
                setIterator(currentNode, iter, readOnly);
              }
            }

            @Override
            public void focusGained(FocusEvent e) {}
          });

      new Label(panel_typed_add, SWT.NONE).setText("Count:");
      itr = new Text(panel_typed_add, SWT.NONE);
      itr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      itr.addFocusListener(
          new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
              try {
                Text t = (Text) e.getSource();
                int val;
                try {
                  val = Integer.valueOf(t.getText());
                } catch (NumberFormatException nfe) {
                  val = 1;
                }

                if (iter != null && iter.getIteration() != val) {
                  iter.setIteration(val);
                  iter.getRandValues(true);
                  setIterator(currentNode, iter, readOnly);
                }
              } catch (NumberFormatException nfe) {
              }
            }

            @Override
            public void focusGained(FocusEvent e) {}
          });

      to_gen = new Button(panel_typed_add, SWT.NONE);
      GridData gd = new GridData(GridData.FILL_BOTH);
      gd.horizontalSpan = 2;
      to_gen.setLayoutData(gd);
      to_gen.setText("Generate new set");
      to_gen.addListener(
          SWT.MouseDown,
          new Listener() {
            @Override
            public void handleEvent(Event event) {
              if (iter != null) {
                iter.getRandValues(true);
                setIterator(currentNode, iter, readOnly);
              }
            }
          });
    } else if (id.equals(AttributeValueIteratorType.FORMULA_ID)) {
      GridData gd = new GridData(GridData.FILL_VERTICAL);
      gd.verticalAlignment = GridData.BEGINNING;
      Label l = new Label(panel_typed_add, SWT.NONE);
      l.setLayoutData(gd);
      l.setText("Formula:");
      itr = new Text(panel_typed_add, SWT.SINGLE);
      gd = new GridData(GridData.FILL_BOTH);
      gd.verticalAlignment = GridData.BEGINNING;
      itr.setLayoutData(gd);
      itr.addFocusListener(
          new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
              if (iter != null && !itr.getText().equals(iter.getPredicate())) {
                iter.setFormula(itr.getText(), false);
                setIterator(currentNode, iter, readOnly);
              }
            }

            @Override
            public void focusGained(FocusEvent e) {}
          });
    }
  }

  private void rebuildAttributeIteratorPanel(TreeNode currentNode, final AttributeGenerator nit, boolean readOnly) {
    String id = iter.getGeneratorType().getId();
    if (!id.equals(activePanel)) constructPanelComponents(currentNode, nit, readOnly);
    activePanel = id;
    if (id.equals(AttributeValueIteratorType.CYCLE_ID)) {
      min.setText("");
      try {
        if (iter != null) min.setText(Integer.toString(iter.getMin()));
      } catch (NumberFormatException nfe) {
        min.setText("0");
      }
      max.setText("");
      try {
        if (iter != null) max.setText(Integer.toString(iter.getMax()));
      } catch (NumberFormatException nfe) {
        max.setText("0");
      }
      itr.setText("");
      itr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      try {
        if (iter != null) itr.setText(Integer.toString(iter.getIteration()));
      } catch (NumberFormatException nfe) {
        itr.setText("1");
      }
      itr.setEnabled(!readOnly);
      if (to_gen != null && !to_gen.isDisposed()) to_gen.setEnabled(!readOnly);
      max.setEnabled(!readOnly);
      min.setEnabled(!readOnly);
    } else if (id.equals(AttributeValueIteratorType.RANDOM_ID)) {
      min.setText("");
      try {
        if (iter != null) min.setText(Integer.toString(iter.getMin()));
      } catch (NumberFormatException nfe) {
        min.setText("0");
      }
      max.setText("");
      try {
        if (iter != null) max.setText(Integer.toString(iter.getMax()));
      } catch (NumberFormatException nfe) {
        max.setText("0");
      }

      itr.setText("");
      try {
        if (iter != null) itr.setText(Integer.toString(iter.getIteration()));
      } catch (NumberFormatException nfe) {
        itr.setText("1");
      }
    } else if (id.equals(AttributeValueIteratorType.FORMULA_ID)) {
      itr.setText("");
      if (iter != null) itr.setText(iter.getPredicate());
      itr.setEnabled(!readOnly);
    }
    values.removeAll();

    if (iter != null)
      for (int i = 0; i < iter.getItemsCount(); i++) {
        String ret = iter.getSingleValue(currentNode.attrs(), i,null).toString();
        if (ret != null) values.add(ret);
      }
  }

  public void setIterator(TreeNode currentNode, final AttributeGenerator it, boolean ro) {
    iter = it;
    if (panel.isDisposed()) {
      return;
    }
    boolean readOnly =
        iter == null
            || ro;
//    this.currentNode = currentNode;
    updatePanels(currentNode, it, readOnly);
    if (it != null) {
      UUID origin = currentNode.getUUId();
      //TODO look
      //Attribute a = currentNode.attrs().getAttribute(it.getAttributeName());
      //if (a == null) origin = a.getOrigin();
      TreeNode master = currentNode.getTreeDB().getNode(origin);
      String s = master.getUserVisibleName(true);
      if (s.equals("")) s = master.getQualifiedId();
      addtype.select(addtype.indexOf(it.getGeneratorType().getLabel()));
    } else {
      addtype.select(-1);
      values.removeAll();
    }
    panel.layout();
    panel.update();
    panel_typed.layout();
    panel_typed.update();
    panel_typed.getParent().layout();
    panel_typed.getParent().pack();
    addtype.setEnabled(!readOnly);
  }

  public AttributeGeneratorPanel(TreeNode currentNode, Composite parent, boolean readonly) {
    panel = new Composite(parent, SWT.BORDER);
    GridLayout layout = new GridLayout(2, true);
    layout.marginWidth = layout.marginHeight = 0;
    layout.horizontalSpacing = layout.verticalSpacing = 0;

    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.widthHint = 280;

    panel.setLayout(layout);
    panel.setLayoutData(gd);

    Composite p2 = new Composite(panel, SWT.NONE);
    GridData grid = new GridData(GridData.FILL_HORIZONTAL);
    grid.horizontalSpan = 2;
    p2.setLayoutData(grid);
    gd.widthHint = 280;
    p2.setLayout(new GridLayout(2, false));

    general = new Composite(panel, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.widthHint = 280;
    gd.verticalAlignment = SWT.BEGINNING;
    general.setLayoutData(gd);
    general.setLayout(new GridLayout(2, false));

    new Label(general, SWT.NONE).setText("Attr. gen. type:");

    addtype = new Combo(general, SWT.NONE);
    for (AttributeValueIteratorType nitt : AttributeValueIteratorType.values()) {
      if (!AttributeGenerator.iteratorTypeIsHidden(nitt)) addtype.add(nitt.toString());
    }
    if (addtypelistener != null) addtype.removeSelectionListener(addtypelistener);
      addtypelistener =
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            String sel = iter.getGeneratorType().getId(), seln = addtype.getText();
            if (sel != seln && seln != null && seln != "") {
              iter.setIdType(AttributeValueIteratorType.getAVIType(seln));
              setIterator(currentNode, iter, readonly);
              //addtype.select(seln);
            }
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {}
        };
    addtype.addSelectionListener(addtypelistener);
    addtype.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    gd = new GridData(GridData.FILL_BOTH);
    gd.verticalAlignment = GridData.BEGINNING;
    //gd.horizontalSpan = 2;

    panel_typed = new Composite(panel, SWT.NONE);
    panel_typed.setLayout(new GridLayout(2, false));
    //gd.heightHint = 140;
    gd.widthHint = 280;
    panel_typed.setLayoutData(gd);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.verticalAlignment = GridData.BEGINNING;
    Composite pnel = new Composite(panel, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.widthHint = 580;
    gd.horizontalSpan = 2;
    pnel.setLayoutData(gd);
    pnel.setLayout(new GridLayout(2, false));

    Label l = new Label(pnel, SWT.SEPARATOR | SWT.HORIZONTAL);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    l.setLayoutData(gd);

    l = new Label(pnel, SWT.NONE);
    l.setText("Preview: ");
    gd = new GridData(GridData.BEGINNING);
    gd.verticalAlignment = SWT.TOP;
    l.setLayoutData(gd);

    values = new List(pnel, SWT.READ_ONLY | SWT.V_SCROLL);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.heightHint = 100;
    gd.grabExcessVerticalSpace = false;
    values.setLayoutData(gd);
  }

  public Control getControl() {
    return panel;
  }

  public void dispose() {
    panel.dispose();
  }

  public AttributeGenerator getGenerator() {
    return iter;
  }
}
