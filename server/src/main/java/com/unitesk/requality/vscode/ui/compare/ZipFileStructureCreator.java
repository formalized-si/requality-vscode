package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureComparator;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureCreator;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.compare.ZipFileStructureCreator}: the structure creator of zip archives. The entries of the archive
 * are the members of the structure (folders for the directories of the entries); the contents of an entry are its
 * text. The members filtered by the Compare/Patch preferences are left out.
 */
public class ZipFileStructureCreator implements IStructureCreator {
  /** {@code ZipFileStructureCreator.ZipResource}: an entry of the archive, a folder or a file. */
  static abstract class ZipResource implements IStructureComparator, ITypedElement {
    private final String fName;

    ZipResource(String name) {
      fName = name;
    }

    @Override
    public String getName() {
      return fName;
    }

    @Override
    public Image getImage() {
      return CompareUI.getImage(getType());
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof ITypedElement)
        return fName.equals(((ITypedElement) other).getName());
      return super.equals(other);
    }

    @Override
    public int hashCode() {
      return fName.hashCode();
    }
  }

  /** {@code ZipFileStructureCreator.ZipFolder}: a directory of the archive. */
  static class ZipFolder extends ZipResource {
    private final HashMap<String, ZipResource> fChildren = new HashMap<>(10);

    ZipFolder(String name) {
      super(name);
    }

    @Override
    public String getType() {
      return ITypedElement.FOLDER_TYPE;
    }

    @Override
    public Object[] getChildren() {
      Object[] children = new Object[fChildren.size()];
      Iterator<ZipResource> iter = fChildren.values().iterator();
      for (int i = 0; iter.hasNext(); i++)
        children[i] = iter.next();
      return children;
    }

    /** Creates the file of the path (and its folders); null when the entry is filtered. */
    ZipFile createContainer(String path) {
      String entry = path;
      int pos = path.indexOf('/');
      if (pos < 0)
        pos = path.indexOf('\\');
      if (pos >= 0) {
        entry = path.substring(0, pos);
        path = path.substring(pos + 1);
      } else if (entry.length() > 0) {
        if (CompareUIPlugin.filter(path, false, true))
          return null;
        ZipFile ze = new ZipFile(entry);
        fChildren.put(entry, ze);
        return ze;
      } else {
        return null;
      }
      ZipFolder folder = null;
      Object o = fChildren.get(entry);
      if (o instanceof ZipFolder)
        folder = (ZipFolder) o;
      if (folder == null) {
        if (path.length() > 0 && CompareUIPlugin.filter(path, true, true))
          return null;
        folder = new ZipFolder(entry);
        fChildren.put(entry, folder);
      }
      return folder.createContainer(path);
    }
  }

  /** {@code ZipFileStructureCreator.ZipFile}: a file (entry) of the archive, with its contents. */
  static class ZipFile extends ZipResource implements IStreamContentAccessor {
    private byte[] fContents;

    ZipFile(String name) {
      super(name);
    }

    @Override
    public String getType() {
      String s = this.getName();
      int pos = s.lastIndexOf('.');
      if (pos >= 0)
        return s.substring(pos + 1);
      return ITypedElement.UNKNOWN_TYPE;
    }

    @Override
    public Object[] getChildren() {
      return null;
    }

    @Override
    public InputStream getContents() {
      if (fContents == null)
        fContents = new byte[0];
      return new ByteArrayInputStream(fContents);
    }

    byte[] getBytes() {
      return fContents;
    }

    void setBytes(byte[] buffer) {
      fContents = buffer;
    }

    void appendBytes(byte[] buffer, int length) {
      if (length > 0) {
        int oldLen = 0;
        if (fContents != null)
          oldLen = fContents.length;
        byte[] newBuf = new byte[oldLen + length];
        if (oldLen > 0)
          System.arraycopy(fContents, 0, newBuf, 0, oldLen);
        System.arraycopy(buffer, 0, newBuf, oldLen, length);
        fContents = newBuf;
      }
    }
  }

  private final String fTitle;

  public ZipFileStructureCreator() {
    this("Zip Archive Compare");
  }

  public ZipFileStructureCreator(String title) {
    fTitle = title;
  }

  @Override
  public String getName() {
    return fTitle;
  }

  @Override
  public IStructureComparator getStructure(Object input) {
    InputStream is = null;
    if (input instanceof IStreamContentAccessor sca) {
      try {
        is = sca.getContents();
      } catch (CoreException ex) {
        // no structure
      }
    }
    if (is == null)
      return null;
    ZipInputStream zip = new ZipInputStream(is);
    ZipFolder root = new ZipFolder("");
    try {
      for (;;) {
        ZipEntry entry = zip.getNextEntry();
        if (entry == null)
          break;
        ZipFile ze = root.createContainer(entry.getName());
        if (ze != null) {
          int length = (int) entry.getSize();
          if (length >= 0) {
            byte[] buffer = new byte[length];
            int offset = 0;
            do {
              int n = zip.read(buffer, offset, length);
              offset += n;
              length -= n;
            } while (length > 0);
            ze.setBytes(buffer);
          } else {
            byte[] buffer = new byte[1024];
            int n;
            do {
              n = zip.read(buffer, 0, 1024);
              ze.appendBytes(buffer, n);
            } while (n >= 0);
          }
        }
        zip.closeEntry();
      }
    } catch (IOException ex) {
      return null;
    } finally {
      try {
        zip.close();
      } catch (IOException ex) {
        // ignore
      }
    }
    if (root.fChildren.size() == 1) {
      Iterator<ZipResource> iter = root.fChildren.values().iterator();
      return iter.next();
    }
    return root;
  }

  @Override
  public String getContents(Object o, boolean ignoreWhitespace) {
    if (o instanceof ZipFile) {
      byte[] bytes = ((ZipFile) o).getBytes();
      if (bytes != null)
        return new String(bytes);
      return "";
    }
    return null;
  }

  public boolean canSave() {
    return false;
  }

  @Override
  public void save(IStructureComparator structure, Object input) {
    throw new IllegalStateException("Cannot update zip archive");
  }

  @Override
  public IStructureComparator locate(Object path, Object source) {
    return null;
  }

  public boolean canRewriteTree() {
    return false;
  }

  public void rewriteTree(Differencer differencer, IDiffContainer root) {
  }
}
