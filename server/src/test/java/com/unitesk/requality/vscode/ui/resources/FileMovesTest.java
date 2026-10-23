package com.unitesk.requality.vscode.ui.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The moves of files and folders wait on Windows while another program has a file open. */
class FileMovesTest {
  static final String BUSY = "The process cannot access the file because it is being used by another process";

  /** A move that fails while the file is open in another program, then succeeds. */
  static FileMoves.Mover busyFor(int failures, AtomicInteger calls) {
    return (from, to, options) -> {
      if (calls.incrementAndGet() <= failures)
        throw calls.get() % 2 == 0 ? new AccessDeniedException(from.toString())
            : new FileSystemException(from.toString(), to.toString(), BUSY);
      Files.move(from, to, options);
    };
  }

  @Test
  void aMoveOnWindowsWaitsForTheOtherProgram(@TempDir Path dir) throws IOException {
    Path from = Files.createDirectories(dir.resolve(".copy.copying"));
    Files.writeString(from.resolve("node.json"), "{}");
    Path to = dir.resolve("copy");
    AtomicInteger calls = new AtomicInteger();
    FileMoves.move(busyFor(3, calls), true, 1, from, to);
    assertEquals(4, calls.get());
    assertTrue(Files.isRegularFile(to.resolve("node.json")));
    assertFalse(Files.exists(from));
  }

  @Test
  void aMoveOnWindowsStopsAfterTheLastAttempt(@TempDir Path dir) throws IOException {
    Path from = Files.createDirectories(dir.resolve("a"));
    AtomicInteger calls = new AtomicInteger();
    assertThrows(FileSystemException.class,
        () -> FileMoves.move(busyFor(Integer.MAX_VALUE, calls), true, 0, from, dir.resolve("b")));
    assertEquals(FileMoves.ATTEMPTS, calls.get());
    assertTrue(Files.isDirectory(from));
  }

  @Test
  void aMoveOnOtherSystemsFailsAtOnce(@TempDir Path dir) throws IOException {
    Path from = Files.createDirectories(dir.resolve("a"));
    AtomicInteger calls = new AtomicInteger();
    assertThrows(FileSystemException.class, () -> FileMoves.move(busyFor(1, calls), false, 0, from, dir.resolve("b")));
    assertEquals(1, calls.get());
  }

  @Test
  void anExistingTargetIsAFinalError(@TempDir Path dir) throws IOException {
    Path from = Files.createDirectories(dir.resolve("a"));
    Path to = Files.createDirectories(dir.resolve("b"));
    Files.writeString(to.resolve("x"), "x");
    AtomicInteger calls = new AtomicInteger();
    assertThrows(FileAlreadyExistsException.class, () -> FileMoves.move(busyFor(0, calls), true, 0, from, to));
    assertEquals(1, calls.get());
  }
}
