package com.unitesk.requality.eclipse.editors.browser;

import java.util.Map;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionConverter;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.tool.Const;

public class NoLinkKeepSelectionConverter implements ISelectionConverter{
  private static NoLinkKeepSelectionConverter oneWatching = new NoLinkKeepSelectionConverter(true);
  private static NoLinkKeepSelectionConverter oneNotWatching = new NoLinkKeepSelectionConverter(false);
  private boolean watchLink;
  public NoLinkKeepSelectionConverter(boolean watch) {
    watchLink = watch;
  }
  @Override
  public Pair<Boolean,SelectionRecord> convertSelection(FSId fsida, SelectionRecord sel, SelectionRecord oldTargSel) {
    boolean nolink = Const.NO_EXPLORER_EDITOR_LINK && !EditorUtils.LINKED_PROVIDER.isLinked();
    Map<String, TreeNode[]> ret = null;
    if(nolink && watchLink) {
      return new Pair<>(false,oldTargSel);
    }
    return new Pair<>(true, sel);
  }
  public static NoLinkKeepSelectionConverter getOne(boolean watchLink){
    if(watchLink)
      return oneWatching;
    return oneNotWatching;
  }
}
