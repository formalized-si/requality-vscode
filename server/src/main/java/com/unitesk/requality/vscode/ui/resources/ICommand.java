package com.unitesk.requality.vscode.ui.resources;

import java.util.Map;

/** {@code org.eclipse.core.resources.ICommand}: a builder entry of a .project file. */
public interface ICommand {
  String getBuilderName();

  void setBuilderName(String builderName);

  Map<String, String> getArguments();

  void setArguments(Map<String, String> args);
}
