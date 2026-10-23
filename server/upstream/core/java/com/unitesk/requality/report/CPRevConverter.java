package com.unitesk.requality.report;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.repo.DBSlice;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.template.IReportSettingsConverter;

public class CPRevConverter implements IReportSettingsConverter {

  @Override
  public Map<String, Object> getUpdatedSettings(ReportGenerationContext context, Map<String, Object> allData, TreeDB db, TreeDB genWhere) {
    Map<String, Object> mapO = new HashMap<>();
    if(context==null)
    {
      RLogger.logError("null context during report generation settings update");
      return mapO;
    }
    for(Entry<String, Object> en:allData.entrySet()){
      String s = en.getKey(); 
      String sl = s.toLowerCase();
      if(sl.contains("revision")){
        boolean sa = false;
        String rev = getStringVariableValue(context.getReportData(), en.getValue());
        if(sl.contains("prev")){
          DBSlice slice = context.storedSlices.get("old");
          if(slice!=null && slice.isDeleted())
            slice = null;
          if(slice!=null&& slice!=DBSlice.EMPTY){
            TreeDB targ = getTree(slice, genWhere);
            if(targ!=null) {
              mapO.put(s, targ.revision.getRevision(targ));
              sa = true;
            }
          }
        }else if (sl.contains("cur")){
          DBSlice slice = context.storedSlices.get("new");
          if(slice!=null && slice.isDeleted())
            slice = null;
          if(slice!=null && slice!=DBSlice.EMPTY){
            TreeDB targ = getTree(slice, genWhere);
            if(targ!=null) {
              mapO.put(s, targ.revision.getRevision(targ));
              sa = true;
            }
          }
        }
        RevisionInfo ri = null;
        if(!rev.equals(""))
          try {
            ri = Global.getRepoManager(db).getRevisionInfoByRev(rev, db.getMonitor());//TODO IDE monitor
          } catch (Exception e) {
            RLogger.logError(e);
          }
        if(!sa && ri!=null){
          mapO.put(s, "D|"+RevisionInfo.getDateFormat().format(ri.getDate()));
          sa = true;
        }
        if(!sa)
          mapO.put(s, en.getValue());
      }else
        mapO.put(s, en.getValue());
    }
    return mapO;
  }

  private String getStringVariableValue(ReportData rdata, Object value) {
    if(value instanceof AttributeDefinition) {
      AttributeDefinition ad = (AttributeDefinition) value;
      return (String) ad.getValue(rdata.attrs());
    }
	return value.toString();
  }

  @Override
  public Map<String, AttributeDefinition> getUpdatedDefinitionSettings(
      ReportGenerationContext context,
      Map<String, AttributeDefinition> allData, TreeDB baseDB, TreeDB genWhere) {
    Map<String, AttributeDefinition> mapO = new HashMap<>();
    RepoManager rm = Global.getRepoManager(baseDB);
    for(Entry<String, AttributeDefinition> en:allData.entrySet()){
      String s = en.getKey();
      String sl = s.toLowerCase();
      AttributeDefinition def = en.getValue();
      boolean sa = false;
      if(sl.contains("revision")){
        if(sl.contains("prev")){
          AttributeType at = AttributeType.STRING;
          UUID origin = (context==null || context.getReportData() == null)?null:context.getReportData().getUUId();
          if(def != null) {
            at = def.getRawValue().getType();
            origin = def.getOrigin();
          }
          if(context!=null) {
            DBSlice slice = context.storedSlices.get("old");
            if(slice.isDeleted())
              slice = null;
            if(slice!=null && origin!=null){
              TreeDB targ = getTree(slice, genWhere);
              if(targ!=null) {
                mapO.put(s, new AttributeDefinition(origin, sl, 
                      at,  targ.getLocalRevisionString()));
                sa = true;
              }
            }
          }
        }else if (sl.contains("cur")){
          AttributeType at = AttributeType.STRING;
          UUID origin = (context == null || context.getReportData() == null)?null:context.getReportData().getUUId();
          if(def != null) {
            at = def.getRawValue().getType();
            origin = def.getOrigin();
          }
          if(context!=null) {
            DBSlice slice = context.storedSlices.get("new");
            if(slice.isDeleted())
              slice = null;
            if(slice!=null && origin!=null){
              TreeDB targ = getTree(slice, genWhere);
              if(targ!=null) {
                mapO.put(s, new AttributeDefinition(origin, sl, 
                  at,  targ.getLocalRevisionString()));
                sa = true;
              }
            }
          }
        }
        String valOld = null;
        if(def!=null && !sa){
          valOld = def.getRawValue().getGValue();

          RevisionInfo ri = null;
          if(valOld!=null && !valOld.equals(""))
            try {
              ri = rm.getRevisionInfoByRev(valOld, baseDB.getMonitor());
            } catch (Exception e) {
              RLogger.logError(e);
            }
          if(ri!=null){
            mapO.put(s, new AttributeDefinition(def.getOrigin(), def.getKey(), 
                def.getRawValue().getType(),  "D|"+RevisionInfo.getDateFormat().format(ri.getDate())));
            sa = true;
          }
        }
        if(!sa)
          mapO.put(s, allData.get(s));
      }else
        mapO.put(s, allData.get(s));
    }
    return mapO;
  }

  private TreeDB getTree(DBSlice slice, TreeDB db) {
    return slice.getTreeDB(db, db.getLocalName(), db.getProjectName(), false, true);
  }
}
