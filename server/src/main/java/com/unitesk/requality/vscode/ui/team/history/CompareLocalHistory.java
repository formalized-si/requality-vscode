package com.unitesk.requality.vscode.ui.team.history;

/**
 * {@code org.eclipse.team.internal.ui.history.CompareLocalHistory}: Compare With &gt; Local History... of a file: the local
 * history page opens in Compare mode, and the selected state is compared with the file.
 */
public class CompareLocalHistory extends ShowLocalHistory {
  @Override
  protected boolean isCompare() {
    return true;
  }

  @Override
  protected String getPromptTitle() {
    return "Compare with Local History";
  }
}
