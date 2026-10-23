package com.unitesk.requality.eclipse.ui.dialogs;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportSettings;

public class ProgressReportSettingsDialog extends Dialog {
  String dateFrom, dateTo, step;
  DateTime dtfrom, dtto, dtfromtime, dttotime;
  Text stepText;
  Composite p;
  Group container1;
  Button c1, c2;
  boolean changed = false;
  String def = "";

  private void setEnabled(Control c22, boolean enabled) {
    if (c22 == null) return;
    c22.setEnabled(enabled);
    if (c22 instanceof Composite)
      for (Control c2 : ((Composite) c22).getChildren()) setEnabled(c2, enabled);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    getShell().setText("Настройки отчета по прогрессу");
    parent.setLayout(new GridLayout(1, true));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    c1 = new Button(parent, SWT.RADIO);
    c1.setText("Progress for selected dates");
    container1 = new Group(parent, SWT.NONE);
    container1.setLayoutData(new GridData(GridData.FILL_BOTH));
    container1.setLayout(new GridLayout(1, true));

    c2 = new Button(parent, SWT.RADIO);
    c2.setText("Progress for all available dates");

    SelectionListener sl =
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            setEnabled(container1, c1.getSelection());
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        };
    c1.addSelectionListener(sl);
    c2.addSelectionListener(sl);

    p = new Composite(container1, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_BOTH);
    p.setLayoutData(gd);
    p.setLayout(new GridLayout(2, true));
    (new Label(p, SWT.NONE)).setText("Date from:");
    (new Label(p, SWT.NONE)).setText("Date to:");
    dtfrom = new DateTime(p, SWT.CALENDAR);
    dtto = new DateTime(p, SWT.CALENDAR);
    dtfromtime = new DateTime(p, SWT.TIME);
    dttotime = new DateTime(p, SWT.TIME);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    gd.widthHint = 400;
    Text helpStep = new Text(p, SWT.MULTI | SWT.WRAP);
    helpStep.setEditable(false);
    helpStep.setLayoutData(gd);
    helpStep.setBackground(helpStep.getParent().getBackground());
    helpStep.setText(
        "Step in format of pairs number - period. For example, '1 week 2 day'. Allowed "
            + " periods are 'day', 'week', 'year', 'month'. Shorten names can be used instead"
            + " ('d','w','y','m'). If not defined, this parameter will be selected automatically.");
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    stepText = new Text(p, SWT.NONE);
    stepText.setLayoutData(gd);
    DateFormat df = ReportSettings.dataFormat;
    Calendar c = Calendar.getInstance();
    if (dateFrom != null) {
      try {
        c.setTime(df.parse(dateFrom));
      } catch (ParseException e) {
        e.printStackTrace();
      }
      dtfrom.setDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
      dtfromtime.setTime(c.get(Calendar.HOUR), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
    }

    if (dateTo != null) {
      try {
        c.setTime(df.parse(dateTo));
      } catch (ParseException e) {
        e.printStackTrace();
      }
      dtto.setDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
      dttotime.setTime(c.get(Calendar.HOUR), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
    }
    if (step != null) {
      stepText.setText(step);
      changed = true;
    } else stepText.setText("1 week");
    //setControl(p);
    //shell.layout(true, true);
    //final Point newSize = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
    //shell.setSize(parent.getSize());

    if (fsel) {
      c1.setSelection(true);
      setEnabled(container1, c1.getSelection());
    } else {
      c2.setSelection(true);
      setEnabled(container1, c1.getSelection());
    }
    return p;
  }

  private Shell shell;
  TreeNode rep;
  private boolean fsel = false;

  public ProgressReportSettingsDialog(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = rep;
    Attribute df = rep.attrs().getAttribute("dateFrom");
    if (df != null && df.getType().equals(AttributeType.STRING)) {
      fsel = true;
      dateFrom = df.getValue().toString();
    }
    Attribute dt = rep.attrs().getAttribute("dateTo");
    if (dt != null && dt.getType().equals(AttributeType.STRING)) {
      fsel = true;
      dateTo = dt.getValue().toString();
    }
    Attribute st = rep.attrs().getAttribute("step");
    if (st != null && st.getType().equals(AttributeType.STRING))
      step = st.getValue().toString();
  }

  @Override
  protected void okPressed() {
    boolean started = false;
    try {
      started = rep.getTreeDB().startTransaction("Change report settings");
      if (!started) return;
      if (c2.getSelection()) {
        rep.attrs().removeAttribute("dateFrom");
        rep.attrs().removeAttribute("dateTo");
        rep.attrs().removeAttribute("step");
      } else {
        Calendar c = Calendar.getInstance();
        c.set(
            dtfrom.getYear(),
            dtfrom.getMonth(),
            dtfrom.getDay(),
            dtfromtime.getHours(),
            dtfromtime.getMinutes(),
            dtfromtime.getSeconds());
        String dval1 = ReportSettings.dataFormat.format(c.getTime());
        if (dateFrom == null || !dateFrom.equals(dval1))
          rep.attrs().setConstAttribute("dateFrom", dval1,0);
        c.set(
            dtto.getYear(),
            dtto.getMonth(),
            dtto.getDay(),
            dttotime.getHours(),
            dttotime.getMinutes(),
            dttotime.getSeconds());
        String dval2 = ReportSettings.dataFormat.format(c.getTime());
        if (dateTo == null || !dateTo.equals(dval2)) rep.attrs().setConstAttribute("dateTo", dval2,0);
        if (step == null || !stepText.getText().equals(step))
          rep.attrs().setConstAttribute("step", stepText.getText(),0);
      }
      rep.commitAttributes();
      if(started) {
        rep.getTreeDB().commit();
        started = false;
      }
      super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if(started)
        rep.getTreeDB().rollback();
    }
  }
}
