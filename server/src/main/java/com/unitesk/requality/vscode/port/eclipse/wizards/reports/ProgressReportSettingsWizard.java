package com.unitesk.requality.vscode.port.eclipse.wizards.reports;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.DateTime;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportSettings;

public class ProgressReportSettingsWizard extends Dialog {
  String dateFrom, dateTo, step;
  DateTime dtfrom, dtto, dtfromtime, dttotime;
  Text stepText;
  Composite p;
  boolean changed = false;
  String def = "";

  @Override
  protected Control createDialogArea(Composite parent) {
    getShell().setText("Настройки отчета о прогрессе");
    parent.setLayout(new GridLayout(1, true));
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    p = new Composite(parent, SWT.NONE);
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
    helpStep.setText(
        "Step in format of pairs number - period. For example, '1 week 2 day'. Allowed "
            + " periods are 'day', 'week', 'year', 'month'. Shorten names can be used instead"
            + " ('d','w','y','m'). If not defifed, this parameter will be selected automatically.");
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
    return p;
  }

  private Shell shell;
  TreeNode rep;

  public ProgressReportSettingsWizard(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = rep;
    Attribute df = rep.attrs().getAttribute("dateFrom");
    if (df != null && df.getType().equals(AttributeType.STRING))
      dateFrom = df.getValue().toString();
    Attribute dt = rep.attrs().getAttribute("dateTo");
    if (dt != null && dt.getType().equals(AttributeType.STRING))
      dateTo = dt.getValue().toString();
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
      Calendar c = Calendar.getInstance();
      c.set(
          dtfrom.getYear(),
          dtfrom.getMonth(),
          dtfrom.getDay(),
          dtfromtime.getHours(),
          dtfromtime.getMinutes(),
          dtfromtime.getSeconds());
      String dval1 = ReportSettings.dataFormat.format(c.getTime());
      if (dateFrom == null || !dateFrom.equals(dval1)) rep.attrs().setConstAttribute("dateFrom", dval1,0);
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
        rep.attrs().setConstAttribute("step", stepText.getText(), 0);
      rep.commitAttributes();
      if(started) {
        rep.getTreeDB().commit();
        started = false;
      }
      super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    }finally {
      if(started)
        rep.getTreeDB().rollback();
    }
  }
}
