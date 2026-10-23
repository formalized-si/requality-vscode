package com.unitesk.requality.eclipse.views.panels;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableItem;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.FileSpaceTimePoint;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.repo.RepoManager;

public class RevisionsPanel {
  public static final String FIRST = null;
  private TableViewer revisions;
  private TreeDB db;
  boolean first = true;
  private TreeNode node;
  private int[] sind;
  private String[]slist;
//  int b = 0;
  private java.util.List<FileSpaceTimePoint> points;
  public static String WS = "Workspace", HEAD = "HEAD";
  Map<String,Integer> index = new HashMap<>();
  
  public RevisionsPanel(final Composite parent, int selCount, int swt) {
    sind = new int[selCount];
    slist = new String[selCount];
    Arrays.fill(slist, "");
    index.put(WS, 0);
    index.put(HEAD, 1);
    revisions = new TableViewer(parent, swt);
    revisions.setSelection(StructuredSelection.EMPTY);
    revisions.setContentProvider(new IStructuredContentProvider(){
    
      @Override
      public Object[] getElements(Object inputElement) {
        synchronized (inputElement) {
          if(inputElement instanceof java.util.List){
            FileSpaceTimePoint[] l = ((java.util.List<FileSpaceTimePoint>)inputElement).toArray(new FileSpaceTimePoint[0]);
            Object[]obj = new Object[l.length+2];
            obj[0] = WS;
            obj[1] = HEAD;
            //Iterator<FileSpaceTimePoint> it = l.iterator();
            int i = 2;
            for(FileSpaceTimePoint it:l)
            //while(it.hasNext())
              obj[i++] = it;
            return obj;
          }
        }
        return null;
      }
    });
    revisions.setLabelProvider(new ITableLabelProvider(){
      @Override
      public void removeListener(ILabelProviderListener listener) {
        // TODO Auto-generated method stub
        
      }
    
      @Override
      public boolean isLabelProperty(Object element, String property) {
        return true;
      }
    
      @Override
      public void dispose() {
        // TODO Auto-generated method stub
        
      }
    
      @Override
      public void addListener(ILabelProviderListener listener) {
        // TODO Auto-generated method stub
        
      }

      @Override
      public Image getColumnImage(Object element, int columnIndex) {
        // TODO Auto-generated method stub
        return null;
      }

      @Override
      public String getColumnText(Object element, int columnIndex) {
        FileSpaceTimePoint fstp = null;
        String ret = null, sel = element.toString();
        if(element instanceof FileSpaceTimePoint){
          fstp = (FileSpaceTimePoint)element;
          ret = toLabel(fstp);
          sel = fstp.getRevision();
        }else
          ret = sel;
        for(String s:slist)
          if(sel.equals(s))
            return "●"+ret;
        return "○"+ret;
      }
    });
    revisions.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    revisions.addSelectionChangedListener(new ISelectionChangedListener(){
    
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        if(revisions.getControl().isDisposed())
          return;
        ISelection is = revisions.getSelection();
        if(!is.isEmpty())
        {
          Object s = ((StructuredSelection)is).getFirstElement();
          String selected = null;
          if(s instanceof FileSpaceTimePoint){
            selected = ((FileSpaceTimePoint) s).getRevision();
            if(selected!=null && ((FileSpaceTimePoint)s).isBroken())
              return;//ignore selection of broken revs
          }else{
            selected = s.toString();
          }
          boolean was = false;
          for(int i = 0;i<slist.length;i++)
            if(slist[i].equals(selected))
              was = true;
          if(!was)
            updateSelection(selected);
        }
        revisions.refresh(true);
      }

    });
  }

  protected String toLabel(FileSpaceTimePoint fstp) {
    return (fstp.isBroken()?"[ERR]":"") + fstp.getRevision().substring(0,6)+" - "+fstp.getMessage().replace("\n", " ").replace("  ", " ");
  }

  public void setTreeNode(IProgressMonitor monitor, Display d, TreeNode nde, String select, String select2) {
    node = nde;
    d.syncExec(new Runnable(){
    
      @Override
      public void run() {
	    if(revisions.getControl().isDisposed())
          return;
        revisions.getTable().clearAll();
      }
    });
    if (nde != null) {
      db = node.getTreeDB();
      renewValues(monitor, d, select, select2);
    }else
      d.syncExec(new Runnable(){
      
        @Override
        public void run() {
  	    if(revisions.getControl().isDisposed())
            return;
          revisions.refresh();
        }
      });
  }
  private void updateSelection(String sel) {
    String selected = "";
    
    TableItem[]items = revisions.getTable().getItems();
    int i = 0;
    for(i=0;i<items.length;i++)
    {
      Object o = items[i].getData();
      String sela = null;
      if(o instanceof FileSpaceTimePoint)
        sela = ((FileSpaceTimePoint)o).getRevision();
      else
        sela = o.toString();
      index.put(sela, i);
      if(sel == null)
        if(i == 0){
          selected = sela;
          break;
        }else
          return;
      else{
        if(sela.toLowerCase().startsWith(sel.toLowerCase())){
          selected = sela;
          //revisions.setSelection(new StructuredSelection(p));
          break;
        }
      }
    }
    if(selected.equals(""))
      selected = sel;
    boolean was = false; 
    for(int j = 0;j<slist.length;j++)
      if(slist[j].equals(selected))
        was = true;
    if(!was && slist.length>0){
      int k = 0;
      if(first){
        slist[slist.length-1] = selected;
      }else
        slist[0] = selected;
      first = !first;
//      for(;k<slist.length;k++)
//        if(slist[k].equals("") || index.getOrDefault(slist[k],Integer.MAX_VALUE)>=i)
//          break;
//      if(k>=(slist.length-1))
//        slist[k-1] = selected;
//      else{
//        
//        if(k==0||slist[k].equals("") || index.get(slist[k-1])<i){
//          for(int l = slist.length-2;l>=k;l--)
//            slist[l+1] = slist[l];
//          slist[k>0?k-1:0] = selected;
//        }else
//          slist[slist.length-1] = selected;
    //  }
    }
    revisions.setSelection(new StructuredSelection(selected));
//    b++;
//    if(b>=slist.length)
//      b = 0;
  }
  Comparator<FileSpaceTimePoint> comp = new Comparator<FileSpaceTimePoint>(){

    @Override
    public int compare(FileSpaceTimePoint o1, FileSpaceTimePoint o2) {
      return o2.getDate().compareTo(o1.getDate());
    }
  };
  private void renewValues(IProgressMonitor monitor, Display d, String select, String select2) {
    final TableViewer revisions = this.revisions;
    final TreeDB db = this.db;
    WorkspaceJob j = new WorkspaceJob("Renew revisions list for project '"+db.getVisibleName()+"'"){
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(db == null)
          return Status.CANCEL_STATUS;
        RepoManager rm = ResManager.getManager().getRepoManager(db);
        points = new LinkedList<>();
        d.syncExec(new Runnable(){
          @Override
          public void run() {
            synchronized (points) {
              if(revisions.getControl().isDisposed())
                return;
              revisions.setInput(points);
            }
          }
        });
        if(rm!=null){
          Iterator<FileSpaceTimePoint> rev = rm.getRevisions(node.getTreeDB());
          while(rev.hasNext()){
            if(monitor.isCanceled())
              return Status.CANCEL_STATUS;
            synchronized (points) {
              FileSpaceTimePoint next = rev.next();
              points.add(next);
              if(next.getRevision().equals(select))
                index.put(next.getRevision(), points.size()+1);
              if(next.getRevision().equals(select2))
                index.put(next.getRevision(), points.size()+1);
            }
          }
          d.syncExec(new Runnable(){
          
            @Override
            public void run() {
              if(revisions.getControl().isDisposed())
                return;
              updateSelection(select);
              updateSelection(select2);
            }
          });
        }

        d.syncExec(new Runnable(){
        
          @Override
          public void run() {
            if(revisions.getControl().isDisposed())
              return;
            revisions.refresh();
          }
        });
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
  }

  public TableViewer getControl() {
    return revisions;
  }
  public String[] getSelected() {
    String[] sels = Arrays.copyOf(slist, slist.length);
    Arrays.sort(sels,new Comparator<String>(){
      @Override
      public int compare(String arg0, String arg1) {
        int ind = getIndex(arg0), ind2 = getIndex(arg1);
        return Integer.compare(ind, ind2);
      }
      
    });
    return sels;
  }

  protected int getIndex(String arg0) {
    if(index!=null)
      return index.getOrDefault(arg0, Integer.MAX_VALUE);//new values are added at the end
    return 0;
  }
}
