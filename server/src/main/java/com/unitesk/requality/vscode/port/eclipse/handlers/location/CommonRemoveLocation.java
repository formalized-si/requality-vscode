package com.unitesk.requality.vscode.port.eclipse.handlers.location;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

public class CommonRemoveLocation
    extends com.unitesk.requality.core.operation.CommonRemoveLocation {

  //parameterized removing locations:
  //if req != null, then from requirement req, else from everywhere;
  //if reqMarker != null, then from Markup Editor reqMarker, else search any Markup Editor
  //if batch, then asks, else TRANSACTION MUST BE STARTED!
  
  @Override
  public boolean openConfirm(String title, String message) {
    return MessageDialog.openConfirm(
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), title, message);
  }

  @Override
  public void openError(String title, String message) {
    MessageDialog.openError(
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), title, message);
  }
//  @Override
//  public void removeFragments(Map<Location, List<Requirement>> locs, boolean b) {
//    Map<Document, List<Location>> torem = new HashMap<>();
//    for(Location loc : locs.keySet()){
//      List<Requirement> reqs = locs.get(loc);
//      if (reqs == null || loc.getRequirements().length == 0 || Arrays.equals(loc.getRequirements(),reqs.toArray())) {
//        Document doc = (Document) loc.getWorkParent();
//        //else doc = (Document) db.getNode(locID.substring(0, locID.lastIndexOf("/")), Path.QID);
//        if (doc != null) {
//          if(!torem.containsKey(doc))
//            torem.put(doc, new LinkedList<>());
//          torem.get(doc).add(loc);
//          //String[] rem = new String[] {loc.getSUUId(), loc.getRawId()};
//          
//          // }
//        }
//      }
//    }
//    for(Document doc:torem.keySet()){
//      ReqMarker reqMarker = null;
//      //if (reqMarker == null) {
//        IEditorReference eRef = null;
//        try {
//          eRef = RequalityPlugin.findMarkEditorForDocument(doc.getQualifiedId(true));
//        } catch (Exception e) {
//          e.printStackTrace();
//        }
//        if (eRef != null
//            && eRef.getEditor(true) != null
//            && (eRef.getEditor(true) instanceof ReqMarker)) {
//          reqMarker = (ReqMarker) eRef.getEditor(true);
//        }
//      //}
//      //if (reqMarker != null) {
//        if(reqMarker!=null){
//          try{
//            reqMarker.getBrowser().getDocService()
//                      .deleteLocations(new HashSet<Location>(torem.get(doc)));
//            //js = doc.removeLocationRegion(locID.substring(locID.lastIndexOf("/")+1));
//          }catch(Exception e){
//            e.printStackTrace();
//          }
//        }
//      try {
//        doc.getDOM();
//      } catch (Exception e) {
//        e.printStackTrace();
//      }
//      Set<Location> trem = new HashSet<>(torem.get(doc));
//      //String js = doc.removeLocationRegions(trem);
//      Set<UUID> tremu = new HashSet<>();
//      //doc.removeLocationFromUUIdToIndex(trem.toArray(new Location[0]));//TODO change it
//      //doc.saveDOM(js, doc.getXmlDoc());
//      //doc.reloadLocationUUIdToIndex();//TODO make more core
//    }
//  }
  
}
