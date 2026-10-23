package com.unitesk.requality.vscode.services;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.ui.compare.CompareContents;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;

/**
 * Eclipse Compare for Requality files ({@code compare/open}): the content merge viewer that plugin.xml contributes
 * for the file extension ({@code org.eclipse.compare.contentMergeViewers}, JSONCompareViewerCreator for
 * {@code .json}) shows two versions of a node file side by side in an editor. A side is the local workspace file
 * (LocalResourceTypedElement, the node of the loaded project) or a revision given by its contents
 * (FileRevisionTypedElement: a git version, another file).
 */
public final class CompareService implements Services.Service {
  @Override
  public void register(RequalityServer server) {
    // The contents of the read-only documents of the requality-compare scheme (CompareEditor, client/openDiff).
    server.rpc().onRequest("compare/content", p -> CompareContents.get(Nodes.string(p, "id")));
    server.rpc().onRequest("compare/open", p -> {
      JsonObject left = p.getAsJsonObject("left"), right = p.getAsJsonObject("right");
      if (CompareUI.viewerFor(CompareUI.extension(Nodes.string(left, "name"))) == null)
        return false;
      ITypedElement l = element(left), r = element(right);
      if (l == null || r == null)
        return false;
      return CompareUI.openCompareEditor(Nodes.string(p, "title"), l, r, Nodes.string(left, "label"), Nodes.string(right, "label"));
    });
  }

  /** A compare side: the workspace file at {@code path} unless {@code content} is given (a revision of it). */
  static ITypedElement element(JsonObject side) {
    String path = Nodes.string(side, "path");
    String name = Nodes.string(side, "name");
    IFile file = path == null ? null : ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(new File(path).getAbsolutePath()));
    if (side.has("content") && !side.get("content").isJsonNull()) {
      byte[] bytes = side.get("content").getAsString().getBytes(StandardCharsets.UTF_8);
      IPath full = file == null ? null : file.getFullPath();
      String type = name != null && name.lastIndexOf('.') >= 0 ? name.substring(name.lastIndexOf('.') + 1) : ITypedElement.UNKNOWN_TYPE;
      return new FileRevisionTypedElement(name, type, () -> new ByteArrayInputStream(bytes), full);
    }
    return file == null ? null : new LocalResourceTypedElement(file);
  }
}
