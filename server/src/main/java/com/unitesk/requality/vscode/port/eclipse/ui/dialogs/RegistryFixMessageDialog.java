package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.runtime.FileLocator;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.tool.CoreUtils;

public class RegistryFixMessageDialog extends MessageDialog {

  protected static final String NAME = Consts.CONNECTION_LIMIT_DIALOG_NAME;
  private static final String SETTS_FILE_NAME = "registryfix";
  private Map<String, URL> links = new HashMap<>();
  private String buttonOk;
  private String buttonLater;
  private String buttonIgnore;
  private String link;
  private String ignoreName;
  
  private URL file;
  public static String refineURLPath(String base) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for(String s:base.split("/")){
      if(first) {
        if(!s.isEmpty()) {
          sb.append("/");
          sb.append(s);
          first = false;
        }
      }else {
        sb.append("/");
        sb.append(CoreUtils.URLEncode(s));
      }
    }
    return sb.toString();
  }
  public RegistryFixMessageDialog(
      Shell parentShell,
      String dialogTitle,
      String dialogMessage,
      String ... linksa) {
    super(
        parentShell,
        dialogTitle,
        null,
        dialogMessage,
        CONFIRM,
        new String[] {IDialogConstants.OK_LABEL},
        0);
    buttonOk=linksa[0];
    buttonLater=linksa[1];
    buttonIgnore=linksa[2];
    link = linksa[3];
    try {
      file = FileLocator.toFileURL(Activator.getDefault().getBundle().getEntry(link));
      file = new URL(file.getProtocol(),file.getHost(),file.getPort(), refineURLPath(file.getFile()));
      
    } catch (IOException e) {
    }
    ignoreName = linksa[4];
    message = dialogMessage;
  }
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, buttonOk, false);
    createButton(parent, IDialogConstants.CANCEL_ID, buttonLater, false);
    createButton(parent, IDialogConstants.CLIENT_ID + 1, buttonIgnore, false);
  }
  @Override
  protected void buttonPressed(int buttonId) {
    super.buttonPressed(buttonId);
    switch (buttonId) {
      case IDialogConstants.OK_ID:{ 
        try {
          File f = new File(file.toURI());//TODO eclipse version allowing to use on archives?
          //fileStore = fileStore.getChild(names[i]);
          if (!f.isDirectory() && f.exists()) {
            Desktop.getDesktop().open(new File(file.toURI()));
          }
        } catch (URISyntaxException e1) {
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }break;
      case IDialogConstants.CANCEL_ID:{
        //do nothing
      }break;
      case IDialogConstants.CLIENT_ID+1:{
        DialogSettings dss = new DialogSettings("registryfix");
        try {
          dss.put(ignoreName, true);
          dss.save(SETTS_FILE_NAME);
        }catch(IOException e) {
        }
      }break;
    }
  }

  protected Control createCustomArea(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    composite.setLayout(layout);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 2;
    composite.setLayoutData(data);
    DialogSettings ds = new DialogSettings("com.unitesk.requality.registryfix");
    try { 
      ds.load(SETTS_FILE_NAME);
    } catch (IOException e1) {
    }
    return composite;
  }

}
