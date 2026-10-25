package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;

import org.junit.jupiter.api.Test;

import com.unitesk.requality.core.operation.ReportExportHandler;

/** The report export copies only the images in the report folder to the export folder (VSCODE-PATCH). */
class ReportExportPatchTest {
  @Test
  void anImagePathWithParentFoldersStaysOut() throws Exception {
    Method inside = ReportExportHandler.class.getDeclaredMethod("inside", URI.class, URI.class);
    inside.setAccessible(true);
    URI folder = new File(System.getProperty("java.io.tmpdir"), "report").toURI();
    assertTrue((Boolean) inside.invoke(null, folder, URI.create(folder + "/images/a.png")));
    assertFalse((Boolean) inside.invoke(null, folder, URI.create(folder + "/../secret.txt")));
    assertFalse((Boolean) inside.invoke(null, folder, URI.create(folder + "/images/../../../etc/passwd")));
  }
}
