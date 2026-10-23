package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.ArchiveFileExportOperation}: writes the selected resources, and the
 * members of the selected folders, into an archive file. A destination ending in {@code .tar.gz} or {@code .tgz} is a
 * gzipped tar archive, any other is a zip archive.
 */
public class ArchiveFileExportOperation implements IRunnableWithProgress {
  private final List<IResource> resources;
  private final File destination;
  private final boolean createDirectoryStructure;
  private final boolean compress;
  private final List<String> problems = new ArrayList<>();

  /**
   * @param resources the resources to export (folders export their members)
   * @param destination the archive file
   * @param createDirectoryStructure whether the entries keep the workspace paths (else the entries are the file names)
   * @param compress whether the contents are compressed (zip only; tar.gz is always compressed)
   */
  public ArchiveFileExportOperation(List<IResource> resources, File destination, boolean createDirectoryStructure,
      boolean compress) {
    this.resources = resources;
    this.destination = destination;
    this.createDirectoryStructure = createDirectoryStructure;
    this.compress = compress;
  }

  /** The problems of the export (one message per resource that was not exported). */
  public List<String> getProblems() {
    return problems;
  }

  /** Whether the destination is a gzipped tar archive. */
  public static boolean isTarGz(File file) {
    String n = file.getName().toLowerCase(java.util.Locale.ROOT);
    return n.endsWith(".tar.gz") || n.endsWith(".tgz");
  }

  @Override
  public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    monitor.beginTask("Exporting", 100);
    try {
      Map<String, IFile> files = new LinkedHashMap<>();
      Map<String, Boolean> folders = new LinkedHashMap<>();
      for (IResource r : resources)
        collect(r, files, folders);
      if (monitor.isCanceled())
        throw new OperationCanceledException();
      if (destination.getParentFile() != null)
        Files.createDirectories(destination.getParentFile().toPath());
      try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(destination.toPath()))) {
        if (isTarGz(destination))
          writeTar(raw, files, folders, monitor);
        else
          writeZip(raw, files, folders, monitor);
      }
    } catch (OperationCanceledException e) {
      throw new InterruptedException();
    } catch (IOException e) {
      problems.add(destination + ": " + e.getMessage());
    } finally {
      monitor.done();
    }
  }

  /** The entry name of a resource: its workspace path, or its name without the directory structure. */
  private String entryName(IResource r) {
    if (createDirectoryStructure)
      return r.getFullPath().toString().replaceFirst("^/", "");
    return r.getName();
  }

  private void collect(IResource r, Map<String, IFile> files, Map<String, Boolean> folders) {
    try {
      if (r instanceof IProject p && !p.isOpen())
        return;
      if (r instanceof IContainer c) {
        if (createDirectoryStructure)
          folders.put(entryName(r), Boolean.TRUE);
        for (IResource m : c.members())
          collect(m, files, folders);
      } else if (r instanceof IFile f) {
        files.put(entryName(r), f);
      }
    } catch (CoreException e) {
      problems.add(r.getFullPath() + ": " + e.getMessage());
    }
  }

  private void writeZip(OutputStream raw, Map<String, IFile> files, Map<String, Boolean> folders, IProgressMonitor monitor)
      throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
      zip.setLevel(compress ? java.util.zip.Deflater.DEFAULT_COMPRESSION : java.util.zip.Deflater.NO_COMPRESSION);
      for (String folder : folders.keySet()) {
        zip.putNextEntry(new ZipEntry(folder + "/"));
        zip.closeEntry();
      }
      for (Map.Entry<String, IFile> e : files.entrySet()) {
        if (monitor.isCanceled())
          throw new OperationCanceledException();
        zip.putNextEntry(new ZipEntry(e.getKey()));
        copy(e.getValue(), zip);
        zip.closeEntry();
        monitor.worked(1);
      }
    } catch (CoreException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  private void writeTar(OutputStream raw, Map<String, IFile> files, Map<String, Boolean> folders, IProgressMonitor monitor)
      throws IOException {
    try (GZIPOutputStream gz = new GZIPOutputStream(raw)) {
      for (String folder : folders.keySet())
        writeTarHeader(gz, folder + "/", 0, '5');
      for (Map.Entry<String, IFile> e : files.entrySet()) {
        if (monitor.isCanceled())
          throw new OperationCanceledException();
        byte[] data;
        try (InputStream in = e.getValue().getContents()) {
          data = in.readAllBytes();
        } catch (CoreException ex) {
          throw new IOException(ex.getMessage(), ex);
        }
        writeTarHeader(gz, e.getKey(), data.length, '0');
        gz.write(data);
        int pad = (512 - data.length % 512) % 512;
        gz.write(new byte[pad]);
        monitor.worked(1);
      }
      gz.write(new byte[1024]); // two zero blocks end the archive
    }
  }

  /** A ustar header of one entry: the name (split into prefix and name when longer than 100 bytes). */
  private static void writeTarHeader(OutputStream out, String name, long size, char type) throws IOException {
    byte[] name100 = new byte[100];
    byte[] prefix = new byte[155];
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length <= 100) {
      System.arraycopy(nameBytes, 0, name100, 0, nameBytes.length);
    } else {
      int split = name.lastIndexOf('/', 155);
      if (split < 0 || name.length() - split - 1 > 100)
        throw new IOException("Name too long for the archive: " + name);
      byte[] p = name.substring(0, split).getBytes(StandardCharsets.UTF_8);
      byte[] n = name.substring(split + 1).getBytes(StandardCharsets.UTF_8);
      System.arraycopy(p, 0, prefix, 0, p.length);
      System.arraycopy(n, 0, name100, 0, n.length);
    }
    byte[] h = new byte[512];
    System.arraycopy(name100, 0, h, 0, 100);
    octal(h, 100, 8, type == '5' ? 0755 : 0644);
    octal(h, 108, 8, 0);
    octal(h, 116, 8, 0);
    octal(h, 124, 12, size);
    octal(h, 136, 12, System.currentTimeMillis() / 1000);
    for (int i = 148; i < 156; i++)
      h[i] = ' ';
    h[156] = (byte) type;
    byte[] magic = "ustar\u000000".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(magic, 0, h, 257, magic.length);
    System.arraycopy(prefix, 0, h, 345, 155);
    long sum = 0;
    for (byte b : h)
      sum += b & 0xff;
    octal(h, 148, 7, sum);
    h[155] = ' ';
    out.write(h);
  }

  private static void octal(byte[] h, int off, int len, long value) {
    String s = Long.toOctalString(value);
    while (s.length() < len - 1)
      s = "0" + s;
    byte[] b = s.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(b, 0, h, off, Math.min(b.length, len - 1));
    h[off + len - 1] = 0;
  }

  private static void copy(IFile file, OutputStream out) throws IOException, CoreException {
    try (InputStream in = file.getContents()) {
      in.transferTo(out);
    }
  }
}
