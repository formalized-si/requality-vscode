package com.unitesk.requality.eclipse.wizards.reports;

class VarProc{
  public String var,proc,form;
  public Double d;
  public VarProc(String v, String p, String formula){
    var = v;
    proc = p;
    form = formula;
    d = TVReportSettingsWizard.max_ind++;
  }
  @Override
  public String toString() {
    return var+(proc!=null&&!proc.isEmpty()?": ("+proc+")":"")+(form!=null&&!form.isEmpty()?" ["+form+"]":"");
  }
  public double getId() {
    return d;
  }
  public void setId(double e) {
    d = e;
  }
}