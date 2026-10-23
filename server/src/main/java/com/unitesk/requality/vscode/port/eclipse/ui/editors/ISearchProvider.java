package com.unitesk.requality.vscode.port.eclipse.ui.editors;

public interface ISearchProvider {

  boolean findText(String aString, boolean aCaseSensitive, boolean aBackwards, boolean aWrapAround);

  String getSelectionText();
}
