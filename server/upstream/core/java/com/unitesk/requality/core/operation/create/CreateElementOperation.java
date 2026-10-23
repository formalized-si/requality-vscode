package com.unitesk.requality.core.operation.create;

import java.util.Collection;
import java.util.HashMap;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.message.RMessages;

public abstract class CreateElementOperation{
  private static final HashMap<String, CreateElementOperation> handlers = new HashMap<>();
  private static final String NODE_TYPE = "requality.parameter.NodeType";

  public String getElementType() {
    String[] types = getElementTypes();
    if (types != null && types.length == 1) return types[0];
    return null;
  }


  public abstract String[] getElementTypes();

  public String getShowedElementType(TreeDB tree) {
    return getShowedElementType(tree, getElementType());
  }

  public String getShowedElementType(TreeDB tree, String tp) {
    if(tp == null)
      return null;
    NodeType nt = tree.getNodeType(tp);
    if(nt == null)
      return null;
    return nt.getVisibleClassName();
  }

  public Pair<String, Boolean> getNewIdentifier(TreeNode par, String type, boolean filterId) {
    return new Pair<>(RequalityCLI.getNextId(par, type, RequalityCLI.detectPrefix(par, type, filterId)),filterId);
  }

  public <type extends TreeNode> type execute(boolean ignoreTrans, TreeNode par, String id, boolean nocheck, TreeNode nodeAfter) {
    return execute(ignoreTrans, par, id, null, getElementType(),nocheck, nodeAfter);
  }

  @SuppressWarnings("unchecked")
  public <type extends TreeNode> type execute(boolean ignoreTrans, TreeNode par,String ids, String templ, String type, boolean nocheck, TreeNode nodeAfter) {
    TreeDB db = par.getTreeDB();
    TreeNode node = nodeAfter;

    Pair<String, Boolean> idp = ids == null? null: new Pair<String, Boolean>(ids, true);
    Pair<String, Boolean> name = null;
    if(idp == null) {
    	name = getNewIdentifier(par, type,false);
    	idp = getNewIdentifier(par, type,true);
    }
    if (idp == null || !idp.getSecond()) return null;
    String id = idp.getFirst();
    try {
      if (!nocheck&&!par.getTreeDB().getTreeLogic().canBeCreatedInExc(type, id, par)) {
        RMessages.openError(
            "Create Element",
            "Element of type '"
                + getShowedElementType(db)
                + "' cannot be created in "
                + par.getUserVisibleName(true),null, false);
        return null;
      }
    } catch (PastingIntoLeafException e) {
      RMessages.openError(
          "Create Element",
          "Can't add "
              + getShowedElementType(db)
              + " to "
              + par.getTypeLabel()
              + " with "
              + "Test Purpose child",null, false);
      return null;
    } catch (SameIdExistsInChildren e) {
      RMessages.openError(
          "Create Element",
          "Element with same id exists in " + par.getUserVisibleName(true),null, false);
      return null;
    } catch (IdEmpty e) {
      RMessages.openError(
          "Create Element",
          "Element id is empty",null, false);
      return null;
    } catch (IdContainsWrongChars e) {
      RMessages.openError(
          "Create Element",
          "Element id contains wrong character - " + e.getWrongCharacter(),null, false);
      return null;
    } catch (ProjectNotLoadedException e) {
      RMessages.openError(
          "Create Element",
          "Project "+db.getVisibleName()+" is not loaded",null, false);
      return null;
    }
    {
      boolean cont = ignoreTrans;
      boolean started = !cont && db.startTransaction("Create " + getShowedElementType(db, type), true);
      cont = cont || started;
      if(cont)
        try {
          Index ind = Index.getNextIndex(type, par, node, false);
          TreeNode nn = par.createChild(id, type, templ);
          if (!ind.isUndefined() && (db.getSortingSettings().getMethod() == SortingMethod.BY_INDEX)
              || db.getTreeLogic().usesIndexByDefault(nn.getType())) {
            nn.setIndex(ind,false);
          }
          setElementSpecificAttributes(nn);
          if(name != null && name.getFirst()!=null && !name.getFirst().equals(id))
            nn.setIgnoredName(name.getFirst());
          nn.commitAttributes();
          if(started) {
            db.commit();
            started = false;
          }
        //	if(Activator.getDefault()!=null)
        //	Activator.getDefault().setActiveNode(nn, type);
          return (type) nn;
        }finally {  
          if(started)
            db.rollback();
        }
    }
    return null;
  }

  protected abstract void setElementSpecificAttributes(TreeNode nn);

  public static <type extends TreeNode> type execute(boolean ignoreTrans, TreeNode parent, String id, String type, boolean nocheck, TreeNode nodeAfter){
    if(type == null || parent == null)
      return null;
    CreateElementOperation ceo = getHandlersMap().get(type);
    if(ceo!=null && (nocheck||ceo.isEnabled(parent)))
      return ceo.execute(ignoreTrans, parent,id, null, type, nocheck, nodeAfter);
    return null;
  }

//  public boolean isEnabled(TreeNode par) { 
//    if (par == null) return false;
//    if (par.isVirtual()) return false;
//    if (!checkElementSpecificConditions(par)) return false;
//    for (String s : getElementTypes())
//      if (par.getTreeDB().getTreeLogic().canBeCreatedIn(s, par))
//        return true;
//    return false;
//  }


  public boolean isEnabled(TreeNode parent) {
    if (parent == null) return false;
    if (parent.isVirtual()) return false;
    if (!checkElementSpecificConditions(parent)) return false;
    for (String s : getElementTypes())
      if (parent.getTreeDB().getTreeLogic().canBeCreatedIn(s, parent))
        return true;
    return false;
  }

  protected boolean checkElementSpecificConditions(TreeNode treeNode) {
    return true;
  }

  public static Collection<CreateElementOperation> getHandlers() {
	if(handlers.isEmpty())
      handlers.putAll(API.getCreateElementOperations());
	return handlers.values();
  }
  public static HashMap<String, CreateElementOperation> getHandlersMap() {
	if(handlers.isEmpty())
      handlers.putAll(API.getCreateElementOperations());
	return handlers;
  }
}
