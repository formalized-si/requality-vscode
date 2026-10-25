package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.unitesk.requality.vscode.ui.runtime.FileLocator;

/**
 * FileLocator.toFileURL gives a file URL with the path not encoded, as in Eclipse: the plug-in takes its path as a file
 * path (the report templates of a server installed in a folder with a space were not found).
 */
class FileLocatorTest {
  @Test
  void aPathWithASpaceStaysAFilePath() throws Exception {
    File dir = new File(Files.createTempDirectory("requality locator").toFile(), "report templates");
    dir.mkdirs();
    URL url = FileLocator.toFileURL(dir.toURI().toURL());
    assertEquals("file", url.getProtocol());
    assertTrue(new File(url.getPath()).isDirectory(), url.getPath());
    assertEquals(dir.getCanonicalFile(), new File(url.getPath()).getCanonicalFile());
    // a URL that is not encoded stays as it is
    assertEquals(url, FileLocator.toFileURL(url));
  }
}
