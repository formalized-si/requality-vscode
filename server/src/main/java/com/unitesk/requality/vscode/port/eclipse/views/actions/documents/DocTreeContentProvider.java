/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.vscode.port.eclipse.views.actions.documents;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;

public class DocTreeContentProvider implements ITreeContentProvider {
  @Override
  public Object[] getElements(Object inputElement) {
    return EclipseTracker.getTracker(DBNamesManager.DEFAULT).getTrees();
  }

  @Override
  public boolean hasChildren(Object element) {
    if (element instanceof TreeDB) {
      TreeDB db = (TreeDB) element;
      if (db.getNode(Document.getTypeRootQId(), Path.QID) == null) {
        return false;
      }
      return db.getNode(Document.getTypeRootQId(), Path.QID).sizeShownChildren() > 0;
    }

    if (element instanceof DocFolder) {
      DocFolder dir = (DocFolder) element;
      return dir.sizeShownChildren() > 0;
    }
    return false;
  }

  @Override
  public Object getParent(Object element) {
    return null;
  }

  @Override
  public Object[] getChildren(Object parentElement) {
    Set<Object> result = new HashSet<>();

    if (parentElement instanceof TreeDB) {
      TreeDB db = (TreeDB) parentElement;
      TreeNode docs = db.getNode(Document.getTypeRootQId(), Path.QID);
      if (docs == null) {
        return result.toArray();
      }
      for (int i = 0; i < docs.sizeShownChildren(); i++) {
        result.add(docs.getShownChild(i));
      }
      return result.toArray(new Object[0]);
    }

    if (parentElement instanceof DocFolder) {
      //            DocFolder dir = (DocFolder) parentElement;
      //            for (String fname : dir.getDirectory().list()) {
      //                String path = dir.getDirectory().getAbsolutePath()
      //                        + File.separator + fname;
      //                File f = new File(path);
      //                if(f.isHidden() ) {
      //                    continue;
      //                }
      //                if (f.isDirectory()) {
      //                    result.add(new DocFolder(dir.getTreeDB(), f));
      //                } else {
      //
      //                    String docRelativePath = dir.getRelativePath() + fname;
      //                    Document doc = dir.getTreeDB().getDocument(docRelativePath);
      //
      //                    if (isFileAcceptable(doc.getId()))
      //                        result.add(doc);
      //                }
      //            }

      return result.toArray(new Object[0]);
    }

    return null;
  }

  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

  @Override
  public void dispose() {}
}
