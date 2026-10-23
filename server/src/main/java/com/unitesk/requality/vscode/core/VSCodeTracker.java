package com.unitesk.requality.vscode.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ErrorNode;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.nodetype.VirtualNode;

/**
 * The node types of the workspace trackers. The trees tracker of the server is the upstream
 * {@code EclipseTracker} (over the compatibility workspace, see {@link Workspace}); this class keeps the node type
 * table the server registers for the image registry.
 */
public final class VSCodeTracker {
  private VSCodeTracker() {
  }

  /**
   * Node classes of the plugin.xml {@code com.unitesk.requality.nodetype} extensions, as the Eclipse tracker registers
   * them (RequalityPlugin.getAdditionalTypes).
   */
  public static final Map<String, Class<? extends TreeNode>> NODE_TYPES;
  static {
    Map<String, Class<? extends TreeNode>> m = new LinkedHashMap<>();
    m.put(Requirement.TYPE_NAME, Requirement.class);
    m.put(Document.TYPE_NAME, Document.class);
    m.put(DocFolder.TYPE_NAME, DocFolder.class);
    m.put(Location.TYPE_NAME, Location.class);
    m.put(ReportData.TYPE_NAME, ReportData.class);
    m.put(ReportFolder.TYPE_NAME, ReportFolder.class);
    m.put(Comment.TYPE_NAME, Comment.class);
    m.put(ErrorNode.TYPE_NAME, ErrorNode.class);
    m.put(TestPurpose.TYPE_NAME, TestPurpose.class);
    m.put(VirtualNode.TYPE_NAME, VirtualNode.class);
    m.put(InstancesFolder.TYPE_NAME, InstancesFolder.class);
    m.put(ReportSettings.TYPE_NAME, ReportSettings.class);
    m.put(ToDoItem.TYPE_NAME, ToDoItem.class);
    m.put(RestrictedFolder.TYPE_NAME, RestrictedFolder.class);
    m.put(TextNode.TYPE_NAME, TextNode.class);
    NODE_TYPES = Collections.unmodifiableMap(m);
  }
}
