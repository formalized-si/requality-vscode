package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;

/** {@link IProjectDescription} read from and written to a {@code .project} file. */
final class ProjectDescription implements IProjectDescription {
  private String name;
  private String comment = "";
  private String[] natures = new String[0];
  private List<ICommand> buildSpec = new ArrayList<>();
  private URI location;
  private IProject[] referenced = new IProject[0];

  static final class Command implements ICommand {
    private String builder;
    private Map<String, String> args = new LinkedHashMap<>();

    @Override
    public String getBuilderName() {
      return builder;
    }

    @Override
    public void setBuilderName(String builderName) {
      builder = builderName;
    }

    @Override
    public Map<String, String> getArguments() {
      return new LinkedHashMap<>(args);
    }

    @Override
    public void setArguments(Map<String, String> a) {
      args = a == null ? new LinkedHashMap<>() : new LinkedHashMap<>(a);
    }
  }

  static ProjectDescription read(File dotProject) throws CoreException {
    ProjectDescription d = new ProjectDescription();
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      Element root = f.newDocumentBuilder().parse(dotProject).getDocumentElement();
      d.name = text(root, "name");
      d.comment = text(root, "comment");
      List<String> ns = new ArrayList<>();
      NodeList natures = root.getElementsByTagName("nature");
      for (int i = 0; i < natures.getLength(); i++)
        ns.add(natures.item(i).getTextContent().trim());
      d.natures = ns.toArray(new String[0]);
      // <projects><project>name</project></projects>: the referenced projects (kept on every rewrite).
      List<IProject> refs = new ArrayList<>();
      for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling())
        if (n instanceof Element && n.getNodeName().equals("projects"))
          for (Node p = n.getFirstChild(); p != null; p = p.getNextSibling())
            if (p instanceof Element && p.getNodeName().equals("project") && !p.getTextContent().trim().isEmpty())
              refs.add(ResourcesPlugin.getWorkspace().getRoot().getProject(p.getTextContent().trim()));
      d.referenced = refs.toArray(new IProject[0]);
      NodeList cmds = root.getElementsByTagName("buildCommand");
      for (int i = 0; i < cmds.getLength(); i++) {
        Element c = (Element) cmds.item(i);
        Command cmd = new Command();
        cmd.builder = text(c, "name");
        NodeList dict = c.getElementsByTagName("dictionary");
        for (int j = 0; j < dict.getLength(); j++) {
          Element e = (Element) dict.item(j);
          cmd.args.put(text(e, "key"), text(e, "value"));
        }
        d.buildSpec.add(cmd);
      }
      d.location = dotProject.getAbsoluteFile().getParentFile().toURI();
      return d;
    } catch (Exception e) {
      throw FsResource.error("Could not read " + dotProject, e);
    }
  }

  private static String text(Element parent, String child) {
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
      if (n instanceof Element && n.getNodeName().equals(child))
        return n.getTextContent().trim();
    return "";
  }

  private static String esc(String s) {
    return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  void write(File dotProject) throws CoreException {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription>\n");
    sb.append("\t<name>").append(esc(name)).append("</name>\n");
    sb.append("\t<comment>").append(esc(comment)).append("</comment>\n");
    sb.append("\t<projects>\n");
    for (IProject p : referenced)
      sb.append("\t\t<project>").append(esc(p.getName())).append("</project>\n");
    sb.append("\t</projects>\n\t<buildSpec>\n");
    for (ICommand c : buildSpec) {
      sb.append("\t\t<buildCommand>\n\t\t\t<name>").append(esc(c.getBuilderName())).append("</name>\n");
      sb.append("\t\t\t<arguments>\n");
      for (Map.Entry<String, String> e : c.getArguments().entrySet())
        sb.append("\t\t\t\t<dictionary>\n\t\t\t\t\t<key>").append(esc(e.getKey())).append("</key>\n\t\t\t\t\t<value>")
            .append(esc(e.getValue())).append("</value>\n\t\t\t\t</dictionary>\n");
      sb.append("\t\t\t</arguments>\n\t\t</buildCommand>\n");
    }
    sb.append("\t</buildSpec>\n\t<natures>\n");
    for (String n : natures)
      sb.append("\t\t<nature>").append(esc(n)).append("</nature>\n");
    sb.append("\t</natures>\n</projectDescription>\n");
    try {
      if (dotProject.getParentFile() != null)
        dotProject.getParentFile().mkdirs();
      // Written to a temporary file and moved over the old one, so a concurrent reader never sees a partial file.
      byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
      java.nio.file.Path tmp = Files.createTempFile(dotProject.getAbsoluteFile().getParentFile().toPath(), ".project", ".tmp");
      Files.write(tmp, bytes);
      replace(tmp, dotProject.toPath(), bytes);
    } catch (IOException e) {
      throw FsResource.error("Could not write " + dotProject, e);
    }
  }

  /**
   * Moves the new contents over the description. Windows does not replace a file that a program has open (Java opens
   * the files it reads without the share mode that allows it): the move is tried again for a while, then the file is
   * written in place, as {@code IFile.setContents} writes it.
   */
  private static void replace(java.nio.file.Path tmp, java.nio.file.Path target, byte[] bytes) throws IOException {
    for (int attempt = 1;; attempt++) {
      try {
        try {
          Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
          Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return;
      } catch (java.nio.file.AccessDeniedException e) {
        if (attempt < 20) {
          try {
            Thread.sleep(50);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
          continue;
        }
        Files.write(target, bytes);
        Files.deleteIfExists(tmp);
        return;
      }
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String projectName) {
    name = projectName;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public void setComment(String c) {
    comment = c == null ? "" : c;
  }

  @Override
  public String[] getNatureIds() {
    return natures.clone();
  }

  @Override
  public void setNatureIds(String[] n) {
    natures = n == null ? new String[0] : n.clone();
  }

  @Override
  public boolean hasNature(String natureId) {
    return Arrays.asList(natures).contains(natureId);
  }

  @Override
  public ICommand[] getBuildSpec() {
    return buildSpec.toArray(new ICommand[0]);
  }

  @Override
  public void setBuildSpec(ICommand[] spec) {
    buildSpec = spec == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(spec));
  }

  @Override
  public ICommand newCommand() {
    return new Command();
  }

  @Override
  public IPath getLocation() {
    return location == null ? null : Path.fromOSString(new File(location).getAbsolutePath());
  }

  @Override
  public void setLocation(IPath l) {
    location = l == null ? null : l.toFile().getAbsoluteFile().toURI();
  }

  @Override
  public URI getLocationURI() {
    return location;
  }

  @Override
  public void setLocationURI(URI l) {
    location = l;
  }

  @Override
  public IProject[] getReferencedProjects() {
    return referenced.clone();
  }

  @Override
  public void setReferencedProjects(IProject[] projects) {
    referenced = projects == null ? new IProject[0] : projects.clone();
  }
}
