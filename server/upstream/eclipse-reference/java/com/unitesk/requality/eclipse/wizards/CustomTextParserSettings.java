package com.unitesk.requality.eclipse.wizards;

import java.util.Map;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import com.unitesk.requality.core.operation.document.CustomTextParser;
import com.unitesk.requality.eclipse.core.ReqSettings;

public class CustomTextParserSettings extends DocWizard {
  TextWP setts;
  private static String TEXT =
      "Text parser works in <body></body> part of document. To determine a start of requirements\n"
          + " list (skipping Introduction etc) it uses section pattern. To gather requirements from just\n"
          + " after <body> tag it is possiblte to define it as '^(.*)$'.\n"
          + "Requirements pattern is used for\n"
          + " requirements gathering. This pattern should return id of new requirement in first group and\n"
          + " its name in second. All text in between located requirements is threated as description of\n"
          + " last gathered one.\n"
          + "Attribute pattern is used to define attributes withing requirement\n"
          + " description. It should return two groups - first for name and second for value. If value is\n"
          + " equal to '' then all text below match is used as value.\n"
          + "Level pattern is used to define depth\n"
          + " of new requirement. The number of its maches in requirement id means the depth of new\n"
          + " requirement. By default it uses all non-symbolic and non-digit characters in id.";

  public CustomTextParserSettings() {
    super();
    setts = new TextWP();
    setts.setTitle("Text parser settings");
    setts.setDescription("Text parser settings.");
  }

  private class TextWP extends WizardPage {
    protected TextWP() {
      super("Settings of document parser");
    }

    private Text sp, rp, ap, lp;

    @Override
    public void createControl(Composite parent) {
      Composite par = new Composite(parent, SWT.NONE);
      par.setLayout(new GridLayout(1, true));
      new Label(par, SWT.NONE).setText("Parse section start pattern:");
      sp = new Text(par, SWT.NONE);
      sp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      String v = getReqSettings().getString(CustomTextParser.START_SECTION_PATTERN);
      if (v != null) sp.setText(v);
      new Label(par, SWT.NONE).setText("Requirement id pattern:");
      rp = new Text(par, SWT.NONE);
      rp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      v = getReqSettings().getString(CustomTextParser.REQ_ID_PATTERN);
      if (v != null) rp.setText(v);
      new Label(par, SWT.NONE).setText("Attirbute pattern:");
      ap = new Text(par, SWT.NONE);
      ap.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      v = getReqSettings().getString(CustomTextParser.ATTRIB_PATTERN);
      if (v != null) ap.setText(v);

      new Label(par, SWT.NONE).setText("Level pattern:");
      lp = new Text(par, SWT.NONE);
      lp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      v = getReqSettings().getString(CustomTextParser.LEVEL_PATTERN);
      if (v != null) lp.setText(v);

      new Label(par, SWT.NONE).setText(TEXT);

      setControl(par);
    }

    public String getSectionPattern() {
      return sp.getText();
    }

    public String getRequirementPattern() {
      return rp.getText();
    }

    public String getAttributePattern() {
      return ap.getText();
    }

    public String getLevelPattern() {
      return lp.getText();
    }
  }

  @Override
  public void setDialogSettings(org.eclipse.jface.dialogs.IDialogSettings ds) {
    ds.put(
        "keys",
        new String[] {
          CustomTextParser.START_SECTION_PATTERN,
          CustomTextParser.REQ_ID_PATTERN,
          CustomTextParser.ATTRIB_PATTERN,
          CustomTextParser.LEVEL_PATTERN
        });
    super.setDialogSettings(ds);
  };

  @Override
  public Map<String, Object> getDefaultSettings() {
    return CustomTextParser.defaultSettings;
  }

  @Override
  public void addPages() {
    addPage(setts);
  };

  @Override
  public boolean performFinish() {
    ReqSettings ds = getReqSettings();
    ds.put(CustomTextParser.START_SECTION_PATTERN, setts.getSectionPattern());
    ds.put(CustomTextParser.REQ_ID_PATTERN, setts.getRequirementPattern());
    ds.put(CustomTextParser.ATTRIB_PATTERN, setts.getAttributePattern());
    ds.put(CustomTextParser.LEVEL_PATTERN, setts.getLevelPattern());
    return true;
  }
}
