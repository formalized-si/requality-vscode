package com.unitesk.requality.vscode.ui.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@code org.eclipse.core.runtime.Path}: an immutable path of segments with an optional device and leading slash. */
public final class Path implements IPath {
  public static final Path EMPTY = new Path(null, new String[0], false, false, false);
  public static final Path ROOT = new Path(null, new String[0], true, false, false);
  private static final boolean WINDOWS = File.separatorChar == '\\';

  private final String device;
  private final String[] segments;
  private final boolean absolute;
  private final boolean trailing;
  private final boolean unc;

  private Path(String device, String[] segments, boolean absolute, boolean trailing, boolean unc) {
    this.device = device;
    this.segments = segments;
    this.absolute = absolute;
    this.trailing = trailing && segments.length > 0;
    this.unc = unc;
  }

  public Path(String fullPath) {
    this(parse(fullPath, WINDOWS));
  }

  public Path(String device, String path) {
    this(parse(path, WINDOWS).setDeviceInternal(device));
  }

  private Path(Path p) {
    this(p.device, p.segments, p.absolute, p.trailing, p.unc);
  }

  public static Path fromOSString(String pathString) {
    return parse(pathString, WINDOWS);
  }

  public static Path fromPortableString(String pathString) {
    return parse(pathString, false);
  }

  private static Path parse(String s, boolean backslashes) {
    if (s == null)
      s = "";
    if (backslashes)
      s = s.replace('\\', '/');
    String dev = null;
    int colon = s.indexOf(DEVICE_SEPARATOR);
    if (colon > 0 && s.indexOf('/') > colon || colon > 0 && s.indexOf('/') < 0 && colon == s.length() - 1) {
      dev = s.substring(0, colon + 1);
      s = s.substring(colon + 1);
    }
    boolean isUnc = s.startsWith("//");
    boolean abs = s.startsWith("/");
    boolean trail = s.endsWith("/") && s.length() > 1;
    List<String> segs = new ArrayList<>();
    for (String part : s.split("/")) {
      if (part.isEmpty() || part.equals("."))
        continue;
      if (part.equals("..") && !segs.isEmpty() && !segs.get(segs.size() - 1).equals("..")) {
        segs.remove(segs.size() - 1);
        continue;
      }
      if (part.equals("..") && abs)
        continue;
      segs.add(part);
    }
    return new Path(dev, segs.toArray(new String[0]), abs, trail, isUnc);
  }

  private Path setDeviceInternal(String dev) {
    return new Path(dev, segments, absolute, trailing, unc);
  }

  private Path with(String[] segs, boolean abs, boolean trail) {
    return new Path(device, segs, abs, trail, unc);
  }

  @Override
  public IPath addFileExtension(String extension) {
    if (isRoot() || isEmpty() || hasTrailingSeparator())
      return this;
    String[] s = segments.clone();
    s[s.length - 1] = s[s.length - 1] + "." + extension;
    return with(s, absolute, trailing);
  }

  @Override
  public IPath addTrailingSeparator() {
    return with(segments, absolute, true);
  }

  @Override
  public IPath append(String path) {
    return append(fromPortableString(WINDOWS ? path.replace('\\', '/') : path));
  }

  @Override
  public IPath append(IPath tail) {
    if (tail == null || tail.segmentCount() == 0 && !tail.hasTrailingSeparator())
      return this;
    String[] t = tail.segments();
    List<String> segs = new ArrayList<>(Arrays.asList(segments));
    for (String x : t) {
      if (x.equals("..") && !segs.isEmpty() && !segs.get(segs.size() - 1).equals(".."))
        segs.remove(segs.size() - 1);
      else
        segs.add(x);
    }
    return with(segs.toArray(new String[0]), absolute, tail.hasTrailingSeparator());
  }

  @Override
  public String getDevice() {
    return device;
  }

  @Override
  public String getFileExtension() {
    if (hasTrailingSeparator())
      return null;
    String last = lastSegment();
    if (last == null)
      return null;
    int i = last.lastIndexOf('.');
    return i < 0 ? null : last.substring(i + 1);
  }

  @Override
  public boolean hasTrailingSeparator() {
    return trailing;
  }

  @Override
  public boolean isAbsolute() {
    return absolute;
  }

  @Override
  public boolean isEmpty() {
    return segments.length == 0 && !absolute && !unc;
  }

  @Override
  public boolean isPrefixOf(IPath other) {
    if (device == null ? other.getDevice() != null : !device.equalsIgnoreCase(String.valueOf(other.getDevice())))
      return false;
    if (isEmpty() || isRoot() && other.isAbsolute())
      return true;
    if (segments.length > other.segmentCount())
      return false;
    for (int i = 0; i < segments.length; i++)
      if (!segments[i].equals(other.segment(i)))
        return false;
    return true;
  }

  @Override
  public boolean isRoot() {
    return absolute && segments.length == 0;
  }

  @Override
  public boolean isUNC() {
    return unc;
  }

  @Override
  public boolean isValidPath(String path) {
    for (String s : fromPortableString(path).segments())
      if (!isValidSegment(s))
        return false;
    return true;
  }

  @Override
  public boolean isValidSegment(String segment) {
    if (segment.isEmpty())
      return false;
    for (char c : segment.toCharArray())
      if (c == '/' || c == '\\' && WINDOWS || c == ':' && WINDOWS)
        return false;
    return true;
  }

  @Override
  public String lastSegment() {
    return segments.length == 0 ? null : segments[segments.length - 1];
  }

  @Override
  public IPath makeAbsolute() {
    return absolute ? this : with(segments, true, trailing);
  }

  @Override
  public IPath makeRelative() {
    return absolute ? new Path(device, segments, false, trailing, false) : this;
  }

  @Override
  public IPath makeRelativeTo(IPath base) {
    if (device == null ? base.getDevice() != null : !device.equalsIgnoreCase(String.valueOf(base.getDevice())))
      return this;
    int common = matchingFirstSegments(base);
    List<String> segs = new ArrayList<>();
    for (int i = common; i < base.segmentCount(); i++)
      segs.add("..");
    for (int i = common; i < segments.length; i++)
      segs.add(segments[i]);
    return new Path(null, segs.toArray(new String[0]), false, trailing, false);
  }

  @Override
  public IPath makeUNC(boolean toUNC) {
    return new Path(toUNC ? null : device, segments, absolute || toUNC, trailing, toUNC);
  }

  @Override
  public int matchingFirstSegments(IPath other) {
    int n = Math.min(segments.length, other.segmentCount());
    int i = 0;
    while (i < n && segments[i].equals(other.segment(i)))
      i++;
    return i;
  }

  @Override
  public IPath removeFileExtension() {
    String ext = getFileExtension();
    if (ext == null || ext.isEmpty() && !lastSegment().endsWith("."))
      return this;
    String[] s = segments.clone();
    String last = s[s.length - 1];
    s[s.length - 1] = last.substring(0, last.length() - ext.length() - 1);
    return with(s, absolute, trailing);
  }

  @Override
  public IPath removeFirstSegments(int count) {
    if (count <= 0)
      return this;
    if (count >= segments.length)
      return new Path(device, new String[0], false, false, false);
    return new Path(device, Arrays.copyOfRange(segments, count, segments.length), false, trailing, false);
  }

  @Override
  public IPath removeLastSegments(int count) {
    if (count <= 0)
      return this;
    if (count >= segments.length)
      return with(new String[0], absolute, false);
    return with(Arrays.copyOf(segments, segments.length - count), absolute, trailing);
  }

  @Override
  public IPath removeTrailingSeparator() {
    return with(segments, absolute, false);
  }

  @Override
  public String segment(int index) {
    return index < 0 || index >= segments.length ? null : segments[index];
  }

  @Override
  public int segmentCount() {
    return segments.length;
  }

  @Override
  public String[] segments() {
    return segments.clone();
  }

  @Override
  public IPath setDevice(String value) {
    return new Path(value, segments, absolute, trailing, unc);
  }

  @Override
  public File toFile() {
    return new File(toOSString());
  }

  @Override
  public String toOSString() {
    String s = toString();
    return WINDOWS ? s.replace('/', '\\') : s;
  }

  @Override
  public String toPortableString() {
    return toString();
  }

  @Override
  public IPath uptoSegment(int count) {
    if (count >= segments.length)
      return this;
    return with(Arrays.copyOf(segments, Math.max(0, count)), absolute, count > 0 || !absolute ? true : false);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (device != null)
      sb.append(device);
    if (unc)
      sb.append('/');
    if (absolute)
      sb.append('/');
    for (int i = 0; i < segments.length; i++) {
      if (i > 0)
        sb.append('/');
      sb.append(segments[i]);
    }
    if (trailing)
      sb.append('/');
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Path))
      return false;
    Path p = (Path) o;
    return absolute == p.absolute && unc == p.unc && Arrays.equals(segments, p.segments)
        && (device == null ? p.device == null : device.equalsIgnoreCase(String.valueOf(p.device)));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(segments) * 31 + (absolute ? 1 : 0);
  }

  @Override
  public Path clone() {
    return this;
  }
}
