package com.unitesk.requality.tool.reqif;

import java.util.Collections;

import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public abstract class ToolExtension extends ReqIFDefinition {
  public static String TAG = "REQ-IF-TOOL-EXTENSION";

  public ToolExtension() {
    super(Collections.<String, Pair<String, String>>emptyMap());
  }

  public void load(Node node) {
    super.loadAttributes(node);
  }
}
