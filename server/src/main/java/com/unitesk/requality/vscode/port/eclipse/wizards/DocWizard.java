package com.unitesk.requality.vscode.port.eclipse.wizards;

import java.util.Collections;
import java.util.Map;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.port.eclipse.core.ReqSettings;

public abstract class DocWizard extends Wizard {
  private ReqSettings rs = null;
  /**
   * * Set dialog settings. Custom wizard needs to define "keys" attribute here as a set of names
   * for returning attributes
   *
   * @param ds
   */
  public void setReqSettings(ReqSettings rs) {
    this.rs = rs;
  }

  public ReqSettings getReqSettings() {
    return rs;
  }

  public Map<String, Object> getDefaultSettings() {
    return Collections.emptyMap();
  }
}
