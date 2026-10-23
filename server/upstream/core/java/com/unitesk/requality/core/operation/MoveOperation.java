package com.unitesk.requality.core.operation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.core.exception.NodeWithIdExistException;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.storage.cache.NewIndexesManipulator;
import com.unitesk.requality.tool.Index;

public class MoveOperation {
  int pos = 0;
  private TreeNode n, par, n2;
  public MoveOperation(TreeNode node, TreeNode newPar, TreeNode targetNode, boolean addAfter){
    n = node;
    par = newPar;
    n2 = targetNode;
    pos = addAfter?1:-1;
  }
  protected void nodeWithIdExists(TreeNode node,TreeNode exist, TreeNode newPar, String id){
    throw new NodeWithIdExistException(exist,id,par);
  }
  public void excecute(){
    String id = n.getId();
    if((par.getQualifiedId()+"/").startsWith(n.getQualifiedId()+"/"))
      throw new RuntimeException("Cannot move node "+n.getUserVisibleName()+" to itself or its accessors("+par.getUserVisibleName()+")");
    Comparator<TreeNode> comp = TreeNodeComparator.getComparator(   
        SortingSettings.init(SortingMethod.BY_INDEX, false));
    List<TreeNode>nodes = par.getSortedShownChildren(
        comp, null);
    boolean stop = false;
    int i = 0, size = nodes.size();
    Iterator<TreeNode> it = nodes.iterator();
    boolean wasN = n2!=null;
    if(wasN){
      wasN = false;
      for(TreeNode node:nodes)
        if(node.getSUUId().equals(n2.getSUUId()))
          wasN = true;
      if(!wasN)
        throw new RuntimeException("Cannot move node (1)'"+n.getUserVisibleName()+"' after (2)'"+n2.getUserVisibleName()+"' because (2) is not present in parent '"+par.getUserVisibleName()+"'");
    }
    if(!par.getUUId().equals(n.getWorkParentUUID())){
      TreeNode na = par.findWorkChild(id);
      if(na!=null&&!na.getSUUId().equals(n.getSUUId()))
        nodeWithIdExists(n, na, par, id);
      n.setParent(par);
      UUID npar = n.getWorkParentUUID();
      if(npar!=null && !npar.equals(par.getUUId()))
        throw new RuntimeException("Parent was not changed");
    }
    
    Index oldIndex = n.getIndex();
    Index ind = null;
      //if(n2!=null){
    Index start = Index.getZero(), pstart = Index.getZero(), send = null;;
    
    if(n2!=null || pos < 0)
      while(it.hasNext() && !stop){
        TreeNode nde = it.next();
        //test node is the position-related one 
        boolean isTarg = n2!=null && n2.getSUUId().equals(nde.getSUUId());
        //node will be ignored in index mechanics
        boolean ignore = isTarg && pos < 0;
        //test node is one moving
        boolean isMoving = nde.getSUUId().equals(n.getSUUId());
        //if node is position-related then stop iteration
        if(isTarg)
          stop = true;
        //if node is one moving then it will be ignored and we needs to save its index 
        //for possible NUU case where N is moved node and U are nodes with undef index
        if(isMoving){
          if(!oldIndex.isUndefined())
            send = oldIndex;
          ignore = true;
        }//if we have a node that is not one moved(and is not ignored) then clear additional bound for NUU case
        else if(!ignore && !nde.getIndex().isUndefined())
          send = null;
        Index ni = nde.getIndex();
        if(!ignore && ni.isUndefined()){
          int j = i+1;//index next to current
          Index end = null;
          if(j>=size)//if there are no more nodes then it tends then this node
          //will have start+1 index(tends to be unreal on practice)
            ni = (start.findGreaterIndex());
          else{
            //if moving node is the last one before set of undefs then its index will be used
            //as right bound
            if(send!=null && (pos >=0 || pos < 0 && !stop)){
              ni = start.findMiddleIndex(send);
              start = ni;
            }else{
              //tries to find a right bound
              for(;j<size
                  &&(
                  //if node is one we are moving
                     nodes.get(j).getSUUId().equals(n.getSUUId())||
                  //or it has no index then skip them
                     nodes.get(j).getIndex().isUndefined()
                  );j++)
                  
              //if node we select is not a moving one and
              //if its index is set 
              if(j<size&&!nodes.get(j).getSUUId().equals(n.getSUUId()) && !nodes.get(j).getIndex().isUndefined()){
                //end is setted to found right node index
                //tends to be unreachable according to current algo
                end = nodes.get(j).getIndex();
                ni = (start.findMiddleIndex(end));
              }
              else
              //if there are no nodes at the right then index was defined as start+1
                ni = (start.findGreaterIndex());
            }
          }
          if(ni!=null && !ni.isUndefined()){
            nde.setIndex(ni, false);
            nde.commitAttributes();
          }
        }
        if(ni == null)
          throw new RuntimeException("Ni is null! Not possible..");
        if(!ignore && !ni.isUndefined()){
          pstart = start;
          start = ni;
        }
        if(!isTarg || pos>=0)
          i++;
      }
  //}
    boolean addToEndOrAfter = n2 != null || n2 == null && pos < 0;
    if(addToEndOrAfter)
    {
      int j = i;
      //start = Index.getZero();
      for(;j<size && (
          nodes.get(j).getIndex().isUndefined() ||
          nodes.get(j).getSUUId().equals(n.getSUUId()));j++);
      //boolean isMoving = nodes.get(j).getSUUId().equals(n.getSUUId());
      if(j<size && !nodes.get(j).isIndexUndefined())
        ind = start.findMiddleIndex(nodes.get(j).getIndex());
      else
        ind = start.findGreaterIndex();
    }else{
      int j = 0;
      for(;j<size && nodes.get(j).getSUUId().equals(n.getSUUId());j++);
      if(j<size && !nodes.get(j).getSUUId().equals(n.getSUUId()))
      {
        ind = Index.getZero().findMiddleIndex(nodes.get(j).getIndex());
      }else
        ind = Index.getUndefined();
    }
    boolean npf = false;
    int np = -1, ip = 0, k = -1;
    nodes = par.getSortedShownChildren(
        comp, null);
    boolean indexDupe = false;
    for(TreeNode nde:nodes){
      k++;
      Index indn = nde.getIndex();
      if(nde.getUUId().equals(n.getUUId())){
        np = k;
        npf = true;
        continue;//ignore self
      }
      int c1 = indn.compareTo(oldIndex), c2=indn.compareTo(ind);
      if(c1 == 0 && !indn.isUndefined())
    	  indexDupe = true;
      if(!npf&&(c1<0|| (c1==0 && (!indn.isUndefined() || comp.compare(nde, n)<0))))
        np = k+1;
      if(c2<0||!indn.isUndefined()&&c2==0)
        ip = k+1;
    }
    boolean changeDef = false;
    if (np != ip || indexDupe || oldIndex.isUndefined()) {
      NewIndexesManipulator nim = n.getTreeDB().getCMM().getNewIndexesManipulator();
      nim.removeNewIndex(n.getTreeDB(), n.getUUId());
      if(!oldIndex.isUndefined()){
        Boolean beforeChange = null, afterChange = null;
        ArrayList<Integer> beforeList = null, afterList = null; 
        boolean wasNow = false;
        int wn = 0;
        for(TreeNode nde:nodes){
          if(wn>=ip){
            wasNow = true;
          }
          {
            if(nde.getSUUId().equals(n.getSUUId())){
              wn++;
              continue;
            }
            if(!wasNow){
              boolean change = nde.getTreeDB().getCMM().getNewIndexesManipulator().isNew(nde.getUUId());
              int ndepos = nde.getIndex().compareTo(oldIndex);
              boolean ch = false;
              if(beforeChange == null){
                ch = ndepos>=0;
                beforeChange = ch&&change || !ch;
              }else
                if(ndepos>=0&& 
                    !change)
                  beforeChange = false;
                else if (change&&ndepos>=0){
                  ch = true;
                }
              if(ch&&change && beforeChange){
                beforeChange = true;
                if(beforeList == null)
                  beforeList = new ArrayList<>();
                beforeList.add(wn);
              }
            }else{
              boolean ch = false;
              boolean change=nde.getTreeDB().getCMM().getNewIndexesManipulator().isNew(nde.getUUId());
              int ndepos = nde.getIndex().compareTo(oldIndex);
              if(afterChange == null){
                ch = ndepos<0;
                afterChange = ch&&change || !ch;
              }else
                if(ndepos<0 && 
                    !change)
                  afterChange = false;
                else if (change&&ndepos<0)
                  ch = true;
              if(ch&&change && afterChange)
              {
                afterChange = true;
                if(afterList == null)
                  afterList = new ArrayList<>();
                afterList.add(wn);
              }              
            }
            wn++;
          }
        }
        if(beforeList!=null&&!beforeList.isEmpty()&&(afterChange==null||true==afterChange)
            ||afterList!=null&&!afterList.isEmpty()&&(beforeChange==null||true==beforeChange)){
          Index end = oldIndex,starts = null;
          if(beforeList!=null){
            int bi = beforeList.size()-1;
            while(bi>=0){
              int ind1 = beforeList.get(bi);
              TreeNode node = nodes.get(ind1);
              if(starts==null)
              {
                int bi2 = ind1-1;
                while(bi2>=0 && (nodes.get(bi2).getIndex().isUndefined()||beforeList.contains(bi2)
                    || nodes.get(bi2).getSUUId().equals(n.getSUUId())))
                  bi2--;
                if(bi2<0)
                  starts = Index.getZero();
                else
                  starts = nodes.get(bi2).getIndex();
              } 
              if(starts == null)
                starts = Index.getZero();
              Index next = null;
              next = starts.findMiddleIndex(end);
              end = next;
              node.setIndex(next, false);
              node.commitAttributes();
              bi--;
            }
          }
          end = null;
          starts = oldIndex;
          int bi = 0;
          if(afterList!=null){
            while(bi<afterList.size()){
              int ind2 = afterList.get(bi);
              TreeNode node = nodes.get(ind2);
              if(end==null)
              {
                int bi2 = ind2+1;
                while(bi2<nodes.size()&&(nodes.get(bi2).getIndex().isUndefined()||afterList.contains(bi2)||
                    nodes.get(bi2).getSUUId().equals(n.getSUUId())))
                  bi2++;
                if(bi2>=nodes.size())
                  end = Index.getUndefined();
                else
                  end = nodes.get(bi2).getIndex();
              } 
              Index next = null;
              if(end == null)
                end = Index.getUndefined();
              if(end.isUndefined())
                next = starts.findGreaterIndex();
              else
                next = starts.findMiddleIndex(end);
              starts = next;
              node.setIndex(next, false);
              node.commitAttributes();
              bi++;
            }
          }
        }else{
          changeDef = true;
        }
      }else
        changeDef = true;
    }
    if(changeDef){
      n.setIndex(ind,true);
      n.commitAttributes();
    }
  }
}
