package com.unitesk.requality.tool;

import java.net.URI;
import com.unitesk.requality.core.attribute.valueprovider.IRelatedFilesProvider;
import com.unitesk.requality.core.model.TreeNode;

public interface IPathHandler {
  String handlePath(URI dpath, String string, TreeNode object, IRelatedFilesProvider added);
}
