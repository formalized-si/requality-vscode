package com.unitesk.requality.eclipse.wizards.massops;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.jface.dialogs.IDialogSettings;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.MassOppsArguments.Argument;
import com.unitesk.requality.eclipse.wizards.TreeNodeSelectionPage;

public class DialogSettingsParser implements IDialogSettingsParser {

  private IDialogSettings dialogSettings;

  public DialogSettingsParser(IDialogSettings dialogSettings) {
    this.setDialogSettings(dialogSettings);
  }

  public Map<String, String> parse() {
    Map<String, String> map = new HashMap<>();
    for (Argument arg : Argument.values()) {
      map.put(arg.name(), dialogSettings.get(arg.name()));
    }
    return map;
  }

  public IDialogSettings getDialogSettings() {
    return dialogSettings;
  }

  public void setDialogSettings(IDialogSettings dialogSettings) {
    this.dialogSettings = dialogSettings;
  }

  public TreeNode getRoot(TreeNode superRoot) {
    String rootId = dialogSettings.get(TreeNodeSelectionPage.ROOT_ID);
    return superRoot.getTreeDB().getNode(UUID.fromString(rootId));
  }

  public Map<String, String> parse(String[] strings) {
    Map<String, String> map = new HashMap<>();
    for (String s : strings) map.put(s, dialogSettings.get(s));
    return map;
  }
}
