package com.unitesk.requality.eclipse.ui.editors;

public interface ISearchProvider {

  boolean findText(String aString, boolean aCaseSensitive, boolean aBackwards, boolean aWrapAround);

  String getSelectionText();
}
