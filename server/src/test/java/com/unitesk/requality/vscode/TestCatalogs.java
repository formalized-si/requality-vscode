package com.unitesk.requality.vscode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Real Requality catalogs used by the tests. The public ETSI ITS catalog TS103096 (storage format 0.15, written by
 * the Eclipse plug-in) is downloaded once into {@code build/test-catalogs} and verified by a digest of its files.
 */
public final class TestCatalogs {
  private static final String ETSI_COMMIT = "a1bb254a580929cabb769de2a597879c2d5a799b";
  private static final String ETSI_URL = "https://forge.etsi.org/rep/ITS/ITS/-/archive/" + ETSI_COMMIT + "/ITS-"
      + ETSI_COMMIT + ".tar.gz?path=requality";
  /** sha256 over the sorted lines "path\0sha256(file)\n" of the 598 catalog files. */
  private static final String ETSI_DIGEST = "f3840e419eb2aed5632c118cba3e880554a280f7a6b92fa289d4daf9de1d5cbc";

  private TestCatalogs() {
  }

  private static File cacheRoot() {
    return new File(System.getProperty("user.dir"), "build/test-catalogs");
  }

  /** The pristine ETSI TS103096 catalog (do not modify; copy it with {@link #copyEtsi}). */
  public static synchronized File etsi() throws Exception {
    File dir = new File(cacheRoot(), "etsi/TS103096");
    if (dir.isDirectory() && ETSI_DIGEST.equals(digest(dir.toPath())))
      return dir;
    deleteTree(dir.getParentFile().toPath());
    dir.getParentFile().mkdirs();
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(ETSI_URL)).build(),
        HttpResponse.BodyHandlers.ofInputStream());
    if (r.statusCode() != 200)
      throw new IOException("Cannot download " + ETSI_URL + ": HTTP " + r.statusCode());
    String prefix = "ITS-" + ETSI_COMMIT + "-requality/requality/TS103096/";
    try (TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(r.body()))) {
      TarArchiveEntry e;
      while ((e = tar.getNextTarEntry()) != null) {
        if (!e.getName().startsWith(prefix) || e.isDirectory())
          continue;
        Path target = dir.toPath().resolve(e.getName().substring(prefix.length())).normalize();
        if (!target.startsWith(dir.toPath()))
          throw new IOException("Bad archive entry " + e.getName());
        Files.createDirectories(target.getParent());
        Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    String d = digest(dir.toPath());
    if (!ETSI_DIGEST.equals(d))
      throw new IOException("Downloaded ETSI catalog digest " + d + " differs from " + ETSI_DIGEST);
    return dir;
  }

  /** Copies the ETSI catalog into {@code parent}, optionally under another folder name. */
  public static File copyEtsi(File parent, String folderName) throws Exception {
    File src = etsi();
    File dst = new File(parent, folderName);
    copyTree(src.toPath(), dst.toPath());
    return dst;
  }

  public static String digest(Path root) throws Exception {
    List<String> paths = new ArrayList<>();
    try (Stream<Path> s = Files.walk(root)) {
      s.filter(Files::isRegularFile).forEach(p -> paths.add(root.relativize(p).toString().replace(File.separatorChar, '/')));
    }
    Collections.sort(paths);
    MessageDigest all = MessageDigest.getInstance("SHA-256");
    for (String p : paths) {
      byte[] fh = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(p)));
      all.update((p + "\0" + hex(fh) + "\n").getBytes(StandardCharsets.UTF_8));
    }
    return hex(all.digest());
  }

  private static String hex(byte[] b) {
    StringBuilder sb = new StringBuilder();
    for (byte x : b)
      sb.append(String.format("%02x", x));
    return sb.toString();
  }

  public static void copyTree(Path src, Path dst) throws IOException {
    try (Stream<Path> s = Files.walk(src)) {
      for (Path p : (Iterable<Path>) s::iterator) {
        Path t = dst.resolve(src.relativize(p).toString());
        if (Files.isDirectory(p))
          Files.createDirectories(t);
        else
          Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  public static void deleteTree(Path p) throws IOException {
    if (!Files.exists(p))
      return;
    try (Stream<Path> s = Files.walk(p)) {
      List<Path> all = new ArrayList<>();
      s.forEach(all::add);
      Collections.reverse(all);
      for (Path x : all)
        deleteWithRetries(x);
    }
  }

  /** Windows releases a file some time after the program closed it: a file still in use is deleted again a few times. */
  private static void deleteWithRetries(Path x) throws IOException {
    for (int attempt = 1;; attempt++) {
      try {
        Files.deleteIfExists(x);
        return;
      } catch (java.nio.file.FileSystemException e) {
        if (attempt == 20 || e instanceof java.nio.file.DirectoryNotEmptyException)
          throw e;
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }
}
