package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Moves of files and folders that wait for other programs on Windows. A virus scanner or an indexer opens a new file
 * for a short time; while the file is open, Windows does not rename it, a folder that contains it, or a file that
 * replaces it ("The process cannot access the file because it is being used by another process"). The move is tried
 * again for some seconds. On other systems an open file does not stop a move, and a failure is final.
 */
public final class FileMoves {
  /** The attempts of a move: about 12 seconds in all. */
  static final int ATTEMPTS = 30;

  /** A move of the file system ({@link Files#move}). */
  interface Mover {
    void move(Path from, Path to, CopyOption... options) throws IOException;
  }

  private FileMoves() {
  }

  /** {@link Files#move}, tried again on Windows while another program has a file open. */
  public static void move(Path from, Path to, CopyOption... options) throws IOException {
    move(Files::move, File.separatorChar == '\\', 50, from, to, options);
  }

  static void move(Mover mover, boolean windows, long pauseMs, Path from, Path to, CopyOption... options)
      throws IOException {
    for (int attempt = 1;; attempt++) {
      try {
        mover.move(from, to, options);
        return;
      } catch (FileSystemException e) {
        if (!windows || attempt >= ATTEMPTS || !mayBeBusy(e))
          throw e;
        try {
          Thread.sleep(pauseMs * Math.min(attempt, 10));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  /** The errors that do not depend on other programs are final. */
  private static boolean mayBeBusy(FileSystemException e) {
    return !(e instanceof NoSuchFileException || e instanceof FileAlreadyExistsException
        || e instanceof DirectoryNotEmptyException || e instanceof AtomicMoveNotSupportedException);
  }
}
