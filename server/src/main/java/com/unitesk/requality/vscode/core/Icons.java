package com.unitesk.requality.vscode.core;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

/**
 * Node icons with Eclipse lightweight-decorator overlays. The base icon comes from the Requality image registry
 * ({@code nodetype.<Type>}, from the {@code com.unitesk.requality.nodetype} extensions); overlays are drawn into the
 * four quadrants like JFace {@code DecorationOverlayIcon} and cached as PNG files.
 */
public final class Icons {
  public enum Quadrant {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, UNDERLAY
  }

  private static final Map<String, String> NODE_TYPE_ICONS = new HashMap<>();
  static {
    // plugin.xml, extension point com.unitesk.requality.nodetype, plus Activator.createImageRegistry().
    NODE_TYPE_ICONS.put("Requirement", "icons/nodetypes/Requirement.png");
    NODE_TYPE_ICONS.put("Document", "icons/nodetypes/Document.png");
    NODE_TYPE_ICONS.put("DocFolder", "icons/nodetypes/DocFolder.png");
    NODE_TYPE_ICONS.put("Location", "icons/nodetypes/Location.png");
    NODE_TYPE_ICONS.put("ReportData", "icons/nodetypes/Report.png");
    NODE_TYPE_ICONS.put("ReportFolder", "icons/data.gif");
    NODE_TYPE_ICONS.put("Comment", "icons/nodetypes/Comment.png");
    NODE_TYPE_ICONS.put("ErrorNode", "icons/nodetypes/ErrorNode.png");
    NODE_TYPE_ICONS.put("TestPurpose", "icons/nodetypes/TestPurpose.png");
    NODE_TYPE_ICONS.put("VirtualNode", "icons/nodetypes/Instance.png");
    NODE_TYPE_ICONS.put("InstancesFolder", "icons/nodetypes/InstanceFolder.png");
    NODE_TYPE_ICONS.put("ReportSettings", "icons/nodetypes/ReportSetting.png");
    NODE_TYPE_ICONS.put("ToDoItem", "icons/unitesk.png");
    NODE_TYPE_ICONS.put("RestrictedFolder", "icons/nodetypes/DocFolder.png");
    NODE_TYPE_ICONS.put("TextNode", "icons/nodetypes/Document.png");
    NODE_TYPE_ICONS.put("NodeType", "icons/nodetypes/NodeType.png");
  }
  public static final String DEFAULT_ICON = "icons/unitesk.png";

  /** An overlay request. */
  public static final class Overlay {
    final String icon;
    final Quadrant quadrant;

    public Overlay(String icon, Quadrant quadrant) {
      this.icon = icon;
      this.quadrant = quadrant;
    }
  }

  private static File resources;
  private static File cacheDir;
  private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
  private static final Map<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();

  private Icons() {
  }

  public static void init(File resourcesDir, File storage) {
    resources = resourcesDir;
    cacheDir = new File(storage, "icons");
    cacheDir.mkdirs();
  }

  /** Resource-relative path of the registry icon for a node type. */
  public static String nodeTypeIcon(String type) {
    return NODE_TYPE_ICONS.getOrDefault(type, DEFAULT_ICON);
  }

  /** A resource-relative path ("icons/...") or an absolute path (image descriptors). */
  public static File resource(String relPath) {
    File f = new File(relPath);
    return f.isAbsolute() ? f : new File(resources, relPath);
  }

  /** Absolute path of the PNG for the base icon with the given overlays. */
  public static String compose(String base, List<Overlay> overlays) {
    if (overlays == null || overlays.isEmpty())
      return resource(base).getAbsolutePath();
    StringBuilder key = new StringBuilder(base);
    for (Overlay o : overlays)
      key.append('|').append(o.quadrant).append(':').append(o.icon);
    return CACHE.computeIfAbsent(key.toString(), k -> render(base, overlays, k));
  }

  /**
   * {@code org.eclipse.compare.internal.DiffImageDescriptor}: a PNG {@code width} pixels wide with the base image (an
   * absolute or resource-relative path, may be null) on one side and the overlay vertically centered on the other.
   */
  public static String composeBeside(String base, String overlay, int width, boolean overlayOnLeft) {
    String key = "beside|" + base + '|' + overlay + '|' + width + '|' + overlayOnLeft;
    return CACHE.computeIfAbsent(key, k -> {
      try {
        BufferedImage out = new BufferedImage(width, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        BufferedImage b = base == null ? null : load(base);
        BufferedImage o = overlay == null ? null : load(overlay);
        if (b != null)
          g.drawImage(b, overlayOnLeft ? width - b.getWidth() : 0, 0, null);
        if (o != null)
          g.drawImage(o, overlayOnLeft ? 0 : width - o.getWidth(), (16 - o.getHeight()) / 2, null);
        g.dispose();
        File f = new File(cacheDir, sha1(k) + ".png");
        if (!f.exists())
          ImageIO.write(out, "png", f);
        return f.getAbsolutePath();
      } catch (IOException e) {
        return base == null ? null : resource(base).getAbsolutePath();
      }
    });
  }

  private static String render(String base, List<Overlay> overlays, String key) {
    try {
      BufferedImage baseImg = load(base);
      int w = baseImg == null ? 16 : baseImg.getWidth();
      int h = baseImg == null ? 16 : baseImg.getHeight();
      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      List<Overlay> rest = new ArrayList<>();
      for (Overlay o : overlays) {
        if (o.quadrant == Quadrant.UNDERLAY) {
          BufferedImage u = load(o.icon);
          if (u != null)
            g.drawImage(u, 0, 0, null);
        } else {
          rest.add(o);
        }
      }
      if (baseImg != null)
        g.drawImage(baseImg, 0, 0, null);
      for (Overlay o : rest) {
        BufferedImage img = load(o.icon);
        if (img == null)
          continue;
        int x = 0, y = 0;
        switch (o.quadrant) {
          case TOP_RIGHT:
            x = w - img.getWidth();
            break;
          case BOTTOM_LEFT:
            y = h - img.getHeight();
            break;
          case BOTTOM_RIGHT:
            x = w - img.getWidth();
            y = h - img.getHeight();
            break;
          default:
            break;
        }
        g.drawImage(img, x, y, null);
      }
      g.dispose();
      File f = new File(cacheDir, sha1(key) + ".png");
      if (!f.exists())
        ImageIO.write(out, "png", f);
      return f.getAbsolutePath();
    } catch (IOException e) {
      return resource(base).getAbsolutePath();
    }
  }

  private static BufferedImage load(String rel) {
    return IMAGES.computeIfAbsent(rel, r -> {
      try {
        return ImageIO.read(resource(r));
      } catch (IOException e) {
        return null;
      }
    });
  }

  private static String sha1(String s) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : d)
        sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
