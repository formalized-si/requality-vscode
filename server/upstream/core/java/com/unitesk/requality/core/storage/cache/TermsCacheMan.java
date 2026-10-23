package com.unitesk.requality.core.storage.cache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter;
import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter.Direction;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.FNames;
import com.unitesk.requality.core.storage.ISectionModel;
import com.unitesk.requality.core.storage.TermsManipulator;

public class TermsCacheMan extends CacheManager {
  public static final String
    DEF_TERM_MODEL_VERSION = "1.2.7",
    USE_TERM_MODEL_VERSION = "1.2.7",
    CPDEF_TERM_MODEL_VERSION = "1.2.7",
    CPUSE_TERM_MODEL_VERSION = "1.2.7",
    RELS_MODEL_VERSION = "1.2.7",
    CPRELS_MODEL_VERSION = "1.2.7",
    CACHE_TABLE_NAME_CP_USEDEF = "CPUsedTerms", //lt
    CACHE_TABLE_NAME_CP_DEFTERM = "CPTermDefs", //lf
    CACHE_TABLE_NAME_USEDEF = "UsedTerms",
    CACHE_TABLE_NAME_DEFTERM = "TermDefs",
    CACHE_TABLE_NAME_RELATIONS = "TermRelations",
    CACHE_TABLE_NAME_CP_RELATIONS = "CPTermRelations",//LFLT
    
    SQL_GET_DEF_TO_USE_TERMS = "SELECT * FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "+FNames.NODE_FROM+"=?;",
    SQL_GET_DEF_TO_USE_TERMS_BY_KEY = "SELECT * FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "+FNames.NODE_FROM+"=? AND "+FNames.KEY+"=?;",
    SQL_GET_USE_TO_DEF_TERMS = "SELECT * FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "+FNames.NODE_TO+"=?;", 
    
    SQL_GET_DEF_TO_USE_TERMS_FOR_NODES = "SELECT * FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "+FNames.NODE_FROM+" IN ", 
    SQL_GET_USE_TO_DEF_TERMS_FOR_NODES = "SELECT * FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "+FNames.NODE_TO+" IN ", 
        
    SQL_GET_USES="SELECT * FROM "+CACHE_TABLE_NAME_USEDEF+" WHERE "+FNames.NODE_TO+" = ?;",
    SQL_GET_ALL_DEFS="SELECT * FROM "+CACHE_TABLE_NAME_DEFTERM+";",
    SQL_GET_DEFS_PREFIX="SELECT * FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+" IN (",
    SQL_GET_CP_DEFS_PREFIX="SELECT * FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE ",
    SQL_REMOVE_USET = "DELETE FROM "+CACHE_TABLE_NAME_USEDEF+" WHERE "+FNames.NODE_TO+" = ?;",
    SQL_CP_REMOVE_USET = "DELETE FROM "+CACHE_TABLE_NAME_CP_USEDEF+" WHERE "+FNames.LPROJ_TO+" = ? AND "+FNames.NODE_TO+" = ?;",
    
    SQL_CP_REMOVE_USET_PREF = "DELETE FROM "+CACHE_TABLE_NAME_CP_USEDEF+" WHERE "+FNames.LPROJ_TO+" = ? AND "+FNames.NODE_TO+" = ? AND "+FNames.KEY+" IN ",
    
    SQL_REMOVE_DEF_PREF = "DELETE FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.NODE_FROM+" = ? AND "+FNames.KEY+" IN ",
    SQL_REMOVE_DEFS = "DELETE FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.NODE_FROM+" = ?;",
    SQL_CP_REMOVE_DEF_PREF = "DELETE FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE "+FNames.LPROJ_FROM+" = ? AND "+FNames.NODE_FROM+" = ? AND "+FNames.KEY+" IN ",
    SQL_CP_REMOVE_DEFS = "DELETE FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE "+FNames.LPROJ_FROM+" = ? AND "+FNames.NODE_FROM+" = ?;",

    SQL_REMOVE_ALL_USET = "DELETE FROM "+CACHE_TABLE_NAME_USEDEF+";",
    SQL_CP_REMOVE_ALL_USET = "DELETE FROM "+CACHE_TABLE_NAME_CP_USEDEF+" WHERE "+FNames.LPROJ_TO+" = ?;",
    SQL_CP_REMOVE_TO_RELATIONS = "DELETE FROM "+CACHE_TABLE_NAME_CP_RELATIONS+" WHERE "+FNames.LPROJ_TO+" = ?",
    SQL_CP_UPDATE_FROM_RELATIONS = "UPDATE "+CACHE_TABLE_NAME_CP_RELATIONS+" SET "+FNames.NODE_FROM+" = NULL WHERE "+FNames.LPROJ_FROM+" = ?",
    SQL_REMOVE_ALL_DEFS = "DELETE FROM "+CACHE_TABLE_NAME_DEFTERM+";",
    SQL_CP_REMOVE_ALL_DEFS = "DELETE FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE "+FNames.LPROJ_FROM+" = ?;",
    SQL_REMOVE_ALL_RELATIONS = "DELETE FROM "+CACHE_TABLE_NAME_RELATIONS+";",
    

    SQL_REMOVE_PROJ_USES_PREF = "DELETE FROM "+CACHE_TABLE_NAME_USEDEF+" WHERE "+FNames.PROJ_FROM+" = ? AND "+FNames.NODE_TO+" = ? AND "+FNames.KEY+" IN ",
    SQL_REMOVE_USES_PREF = "DELETE FROM "+CACHE_TABLE_NAME_USEDEF+" WHERE "+FNames.NODE_TO+" = ? AND "+FNames.KEY+" IN ",
    SQL_REMOVE_USES = "DELETE FROM "+CACHE_TABLE_NAME_USEDEF+" WHERE "+FNames.NODE_TO+" = ?;",
//    SQL_GET_DEF_TO_USE_TERMS = ""
//        + "SELECT "+FNames.NODE_TO
//        +", "+FNames.NODE_FROM
//        +", "+FNames.NODE_TO_VERSION
//        +", "+CACHE_TABLE_NAME_USEDEF+"."+FNames.KEY+" AS "+FNames.KEY+","
//        + CACHE_TABLE_NAME_USEDEF+"."+FNames.PROJ_FROM+" AS "+FNames.PROJ_FROM+" FROM "
//        + CACHE_TABLE_NAME_DEFTERM 
//        + " LEFT JOIN "+CACHE_TABLE_NAME_USEDEF+" ON "
//        + CACHE_TABLE_NAME_USEDEF+"."+FNames.KEY+" = "
//        + CACHE_TABLE_NAME_DEFTERM+"."+FNames.KEY+" WHERE "+FNames.NODE_FROM+" = ? AND ("
//        + FNames.NODE_TO+" IS NOT NULL AND "+FNames.PROJ_FROM+" IS NULL);",
    SQL_CP_GET_DIR_TERMS = ""
        + "SELECT "+FNames.LPROJ_TO
        +", "+FNames.PROJ_TO
        +", "+FNames.NODE_TO
        +", "+FNames.PROJ_FROM
        +", "+FNames.NODE_FROM
        +", "+FNames.NODE_TO_VERSION
        +", "+FNames.KEY+" FROM "
        + CACHE_TABLE_NAME_CP_RELATIONS 
        + " WHERE "+FNames.LPROJ_FROM+" = ? AND "+FNames.NODE_FROM+" = ? AND ("
        + FNames.NODE_TO+" IS NOT NULL);",
    SQL_GET_CP_USE_TO_DEF_TERMS = ""
        + "SELECT "+FNames.LPROJ_TO
        +", "+FNames.PROJ_TO
        +", "+FNames.NODE_TO
        +", "+FNames.NODE_FROM
        +", "+FNames.NODE_TO_VERSION
        +", "+FNames.LPROJ_FROM
        +", "+FNames.PROJ_FROM
        +", "+FNames.KEY+" FROM "
        +  CACHE_TABLE_NAME_CP_RELATIONS
        + " WHERE "+FNames.LPROJ_TO
        +" = ? AND "+FNames.NODE_TO+" = ?;",
    SQL_GET_RELATED_PROJS = ""
        + "SELECT "+FNames.PROJ_TO
        +", "+FNames.PROJ_FROM
        +", "+FNames.KEY
        +", "+FNames.LPROJ_FROM
        +", "+FNames.LPROJ_TO
        +" FROM "
        +  CACHE_TABLE_NAME_CP_RELATIONS
        + " WHERE "+FNames.LPROJ_TO
        +" = ? OR "+FNames.PROJ_TO+" = ?"
        + " OR "+FNames.LPROJ_FROM+" = ? "
        + " OR "+FNames.PROJ_FROM+" = ?;",
    SQL_GET_CP_DEF_TO_USE_TERMS = ""
        + "SELECT "+FNames.LPROJ_TO
        +", "+FNames.PROJ_TO
        +", "+FNames.NODE_TO
        +", "+FNames.NODE_FROM
        +", "+FNames.NODE_TO_VERSION
        +", "+FNames.LPROJ_FROM
        +", "+FNames.PROJ_FROM
        +", "+FNames.KEY+" FROM "
        + CACHE_TABLE_NAME_CP_RELATIONS
        + " WHERE "+FNames.LPROJ_FROM
        +" = ? AND "+FNames.NODE_FROM+" = ?;",
    SQL_GET_CP_USE_TO_DEF_TERMS_BY_NODES_PREFIX = ""
        + "SELECT "+FNames.PROJ_TO
        +", "+FNames.NODE_TO
        +", "+FNames.NODE_FROM
        +", "+FNames.NODE_TO_VERSION
        +", "+FNames.LPROJ_FROM
        +", "+FNames.PROJ_FROM
        +", "+FNames.KEY+" FROM "
        +  CACHE_TABLE_NAME_CP_RELATIONS
        + " WHERE "+FNames.LPROJ_TO
        +" = ? AND "+FNames.NODE_TO+" IN ",
    SQL_GET_CP_DEF_TO_USE_TERMS_BY_NODES_PREFIX = ""
        + "SELECT "+FNames.LPROJ_TO
        +", "+FNames.PROJ_TO
        +", "+FNames.NODE_TO
        +", "+FNames.NODE_FROM
        +", "+FNames.NODE_TO_VERSION
        +", "+FNames.LPROJ_FROM
        +", "+FNames.PROJ_FROM
        +", "+FNames.KEY+" FROM "
        + CACHE_TABLE_NAME_CP_RELATIONS
        + " WHERE "+FNames.LPROJ_FROM
        +" = ? AND "+FNames.NODE_FROM+" IN ",
//    SQL_GET_USE_TO_DEF_TERMS = ""
//        + "SELECT "+FNames.NODE_TO
//        +", "+FNames.NODE_FROM
//        +", "+FNames.NODE_TO_VERSION
//        +", "+CACHE_TABLE_NAME_USEDEF+"."+FNames.PROJ_FROM
//        +","+CACHE_TABLE_NAME_DEFTERM+"."+FNames.KEY+" AS "+FNames.KEY+" FROM "
//        + CACHE_TABLE_NAME_USEDEF 
//        + " LEFT JOIN "+CACHE_TABLE_NAME_DEFTERM+" ON "
//        + CACHE_TABLE_NAME_DEFTERM+"."+FNames.KEY+" = "
//        + CACHE_TABLE_NAME_USEDEF+"."+FNames.KEY+" WHERE "+FNames.NODE_TO+" = ? AND "
//        + FNames.NODE_FROM+" IS NOT NULL AND "+CACHE_TABLE_NAME_USEDEF+"."+FNames.PROJ_FROM+" IS NULL;",
    SQL_ADD_PROJ_USES = "INSERT INTO "+CACHE_TABLE_NAME_USEDEF+"( "+FNames.PROJ_FROM+", "+FNames.NODE_TO+", "+FNames.KEY+", "+FNames.NODE_TO_VERSION+") VALUES (?, ?, ?, ?);",
    SQL_ADD_USES = "INSERT INTO "+CACHE_TABLE_NAME_USEDEF+"("+FNames.NODE_TO+", "+FNames.KEY+", "+FNames.NODE_TO_VERSION+") VALUES (?, ?, ?);",
    SQL_CP_ADD_USES = "INSERT INTO "+CACHE_TABLE_NAME_CP_USEDEF+"("+FNames.PROJ_FROM+", "+FNames.LPROJ_TO+", "+FNames.PROJ_TO+", "+FNames.NODE_TO+", "+FNames.KEY+", "+FNames.NODE_TO_VERSION+") VALUES (?, ?, ?, ?, ?, ?);",
    SQL_ADD_DEF = "INSERT INTO "+CACHE_TABLE_NAME_DEFTERM+"("+FNames.NODE_FROM+", "+FNames.KEY+") VALUES (?, ?);",
    SQL_CP_ADD_DEF = "INSERT INTO "+CACHE_TABLE_NAME_CP_DEFTERM+"("+FNames.LPROJ_FROM+", "+FNames.PROJ_FROM+", "+FNames.NODE_FROM+", "+FNames.KEY+") VALUES (?, ?, ?, ?);",
  TRIGGER_REMOVE_USE="CREATE TRIGGER IF NOT EXISTS useRemove AFTER DELETE ON "+CACHE_TABLE_NAME_USEDEF+" FOR EACH ROW BEGIN "+
        "DELETE FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "+FNames.KEY+"=old."+FNames.KEY+" AND old."+FNames.NODE_TO+"="+FNames.NODE_TO+"; END",
    TRIGGER_CP_REMOVE_USE="CREATE TRIGGER IF NOT EXISTS cpUseRemove AFTER DELETE ON "+CACHE_TABLE_NAME_CP_USEDEF+" FOR EACH ROW BEGIN "+
        "DELETE FROM "+CACHE_TABLE_NAME_CP_RELATIONS+" WHERE "+FNames.KEY+"=old."+FNames.KEY+" AND "+FNames.NODE_TO+"=old."+FNames.NODE_TO+""
            + " AND "+FNames.PROJ_TO+"=old."+FNames.PROJ_TO+ " AND "+FNames.LPROJ_TO+"=old."+FNames.LPROJ_TO+"; END",
    //not in use today but may be caused in future
    //TODO replace by proper implementation if update will be used
    TRIGGER_UPDATE_USE="CREATE TRIGGER IF NOT EXISTS useUpdate AFTER UPDATE ON "+CACHE_TABLE_NAME_USEDEF+" FOR EACH ROW BEGIN "+
        "DELETE FROM "+CACHE_TABLE_NAME_USEDEF+" WHERE "
            + FNames.KEY+"=old."+FNames.KEY+" AND "
            + FNames.NODE_TO+"=old."+FNames.NODE_TO+" AND "
            + FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM+";"
        +"INSERT INTO "+CACHE_TABLE_NAME_USEDEF+"("+FNames.KEY+", "+FNames.PROJ_FROM+", "+FNames.NODE_TO+", "+FNames.NODE_TO_VERSION+") "
            + "VALUES (new."+FNames.KEY+", new."+FNames.PROJ_FROM+", new."+FNames.NODE_TO+", new."+FNames.NODE_TO_VERSION+");"
     +" END",
    TRIGGER_CP_UPDATE_USE="CREATE TRIGGER IF NOT EXISTS cpUseUpdate AFTER UPDATE ON "+CACHE_TABLE_NAME_CP_USEDEF+" FOR EACH ROW BEGIN "+
              "DELETE FROM "+CACHE_TABLE_NAME_CP_USEDEF+" WHERE "
                  + FNames.KEY+"=old."+FNames.KEY+" AND "
                  + FNames.NODE_TO+"=old."+FNames.NODE_TO+" AND "
                  + FNames.LPROJ_TO+"=old."+FNames.LPROJ_TO+" AND "
                    + FNames.PROJ_TO+"=old."+FNames.PROJ_TO+" AND "
                  + FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM+";"
              +"INSERT INTO "+CACHE_TABLE_NAME_CP_USEDEF+"("+FNames.KEY+", "+FNames.PROJ_FROM+", "+FNames.LPROJ_TO+", "+FNames.PROJ_TO+", "+FNames.NODE_TO+", "+FNames.NODE_TO_VERSION+") "
                  + "VALUES (new."+FNames.KEY+", new."+FNames.PROJ_FROM+",  new."+FNames.LPROJ_TO+",  new."+FNames.PROJ_TO+", new."+FNames.NODE_TO+", new."+FNames.NODE_TO_VERSION+");"
           +" END",
    TRIGGER_UPDATE_DEF="CREATE TRIGGER IF NOT EXISTS defUpdate AFTER UPDATE ON "+CACHE_TABLE_NAME_DEFTERM+" FOR EACH ROW BEGIN "+
             "DELETE FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "
             + FNames.KEY+"=old."+FNames.KEY+" AND "
             + FNames.NODE_FROM+"=old."+FNames.NODE_FROM+";"
              + " INSERT INTO "+CACHE_TABLE_NAME_DEFTERM+"("+FNames.KEY+", "+FNames.NODE_FROM+")"
                  + "VALUES (new."+FNames.KEY+", new."+FNames.NODE_FROM+");"
           +"END;",

    TRIGGER_CP_UPDATE_DEF="CREATE TRIGGER IF NOT EXISTS cpDefUpdate AFTER UPDATE ON "+CACHE_TABLE_NAME_CP_DEFTERM+" FOR EACH ROW BEGIN "+
              "DELETE FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE "
              + FNames.LPROJ_FROM+"=old."+FNames.LPROJ_FROM+" AND "
              + FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM+" AND "
              + FNames.KEY+"=old."+FNames.KEY+" AND "
              + FNames.NODE_FROM+"=old."+FNames.NODE_FROM+";"
               + " INSERT INTO "+CACHE_TABLE_NAME_CP_DEFTERM+"("+FNames.KEY+", "+FNames.NODE_FROM+", "+FNames.LPROJ_FROM+", "+FNames.PROJ_FROM+")"
                   + "VALUES (new."+FNames.KEY+", new."+FNames.NODE_FROM+", new."+FNames.LPROJ_FROM+", new."+FNames.PROJ_FROM+");"
            +"END;",

    TRIGGER_ADD_USE_EXISTS="CREATE TRIGGER IF NOT EXISTS useAddEx AFTER INSERT ON "+CACHE_TABLE_NAME_USEDEF+" FOR EACH ROW "
        + "WHEN EXISTS (SELECT "+FNames.NODE_FROM+" FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+"= new."+FNames.KEY+")"
        +" BEGIN "+
         " INSERT INTO "+CACHE_TABLE_NAME_RELATIONS+
           "("+FNames.NODE_FROM+", "+FNames.NODE_TO+", "+FNames.NODE_TO_VERSION+", "+FNames.KEY+") "
           +"SELECT "+CACHE_TABLE_NAME_DEFTERM+"."+FNames.NODE_FROM+", new."+FNames.NODE_TO+", new."+FNames.NODE_TO_VERSION+",new."+FNames.KEY+""
               + " FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+"= new."+FNames.KEY+";"
       +"END;",
   TRIGGER_ADD_USE_NOT_EXISTS="CREATE TRIGGER IF NOT EXISTS useAddNex AFTER INSERT ON "+CACHE_TABLE_NAME_USEDEF+" FOR EACH ROW "
       + "WHEN NOT EXISTS (SELECT "+FNames.NODE_FROM+" FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+"= new."+FNames.KEY+")"
       +" BEGIN "+
       " INSERT INTO "+CACHE_TABLE_NAME_RELATIONS+
         "("+FNames.NODE_TO+", "+FNames.NODE_TO_VERSION+", "+FNames.KEY+") VALUES "
         +"( new."+FNames.NODE_TO+", new."+FNames.NODE_TO_VERSION+",new."+FNames.KEY+");"
     +"END;",

     TRIGGER_CP_ADD_USE_EXISTS="CREATE TRIGGER IF NOT EXISTS cpUseAddEx AFTER INSERT ON "+CACHE_TABLE_NAME_CP_USEDEF+" FOR EACH ROW "
         + "WHEN EXISTS (SELECT "+FNames.NODE_FROM+", "+FNames.PROJ_FROM+" AS PROJFROM "
        + "FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE ("+FNames.KEY+"= new."+FNames.KEY+" "
            + " AND ("+FNames.LPROJ_FROM+"=new."+FNames.PROJ_FROM+" OR  "+FNames.PROJ_FROM+"=new."+FNames.PROJ_FROM+")))"
      +" BEGIN "+
         "INSERT INTO "+CACHE_TABLE_NAME_CP_RELATIONS+
            "("+FNames.LPROJ_FROM+","+FNames.PROJ_FROM+","+FNames.NODE_FROM+","
              +FNames.LPROJ_TO+","+FNames.PROJ_TO+", "+FNames.NODE_TO+", "+FNames.NODE_TO_VERSION+", "+FNames.KEY+") "
             + "SELECT "
            +""+FNames.LPROJ_FROM+", "+FNames.PROJ_FROM+","+FNames.NODE_FROM+",new."+FNames.LPROJ_TO+", new."+FNames.PROJ_TO+", new."+FNames.NODE_TO+", new."+FNames.NODE_TO_VERSION+",new."+FNames.KEY+""
            + " FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE ("+FNames.KEY+"= new."+FNames.KEY+" "
            + " AND ("+FNames.LPROJ_FROM+"=new."+FNames.PROJ_FROM+" OR  "+FNames.PROJ_FROM+"=new."+FNames.PROJ_FROM+"));"

            +"END",
    TRIGGER_CP_ADD_USE_NOT_EXISTS="CREATE TRIGGER IF NOT EXISTS cpUseAddNEx AFTER INSERT ON "+CACHE_TABLE_NAME_CP_USEDEF+" FOR EACH ROW "
        + "WHEN NOT EXISTS (SELECT "+FNames.NODE_FROM+", "+FNames.PROJ_FROM+" "
        + "FROM "+CACHE_TABLE_NAME_CP_DEFTERM+" WHERE ("+FNames.KEY+"= new."+FNames.KEY+" "
            + " AND ("+FNames.LPROJ_FROM+"=new."+FNames.PROJ_FROM+" OR  "+FNames.PROJ_FROM+"=new."+FNames.PROJ_FROM+")))"
      +" BEGIN "+
        " INSERT INTO "+CACHE_TABLE_NAME_CP_RELATIONS+
           "("+FNames.PROJ_FROM+","
             +FNames.LPROJ_TO+","+FNames.PROJ_TO+", "+FNames.NODE_TO+", "+FNames.NODE_TO_VERSION+", "+FNames.KEY+") VALUES "
           +"(new."+FNames.PROJ_FROM+",new."+FNames.LPROJ_TO+", new."+FNames.PROJ_TO+", new."+FNames.NODE_TO+", new."+FNames.NODE_TO_VERSION+",new."+FNames.KEY+");"
           +"END;",

    TRIGGER_REM_DEF_EXISTS="CREATE TRIGGER IF NOT EXISTS defRemEx AFTER DELETE ON "+CACHE_TABLE_NAME_DEFTERM+" FOR EACH ROW "
        + "WHEN EXISTS (SELECT "+FNames.NODE_FROM+"  FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+"= old."+FNames.KEY+")"
         +" BEGIN "+ 
          " UPDATE "+CACHE_TABLE_NAME_RELATIONS+" SET "+FNames.NODE_FROM+" = (SELECT "+FNames.NODE_FROM+" FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+"= old."+FNames.KEY+" AND "+FNames.NODE_FROM+" != old."+FNames.NODE_FROM+" ) WHERE "
          + FNames.KEY+"=old."+FNames.KEY+";"
        +"END;",
    TRIGGER_REM_DEF_NOT_EXISTS="CREATE TRIGGER IF NOT EXISTS defRemNEx AFTER DELETE ON "+CACHE_TABLE_NAME_DEFTERM+" FOR EACH ROW "
        + "WHEN NOT EXISTS (SELECT "+FNames.NODE_FROM+" FROM "+CACHE_TABLE_NAME_DEFTERM+" WHERE "+FNames.KEY+"= old."+FNames.KEY+" AND "+FNames.NODE_FROM+" != old."+FNames.NODE_FROM+" )"
         +" BEGIN "+
          " UPDATE "+CACHE_TABLE_NAME_RELATIONS+" SET "+FNames.NODE_FROM+" = NULL WHERE "
          + FNames.KEY+"=old."+FNames.KEY+" AND "+FNames.NODE_FROM+"=old."+FNames.NODE_FROM+";"
        +"END;",

    TRIGGER_CP_REM_DEF_EXISTS="CREATE TRIGGER IF NOT EXISTS cpDefRemEx AFTER DELETE ON "+CACHE_TABLE_NAME_CP_DEFTERM+" FOR EACH ROW "
        + "WHEN EXISTS (SELECT * FROM "+CACHE_TABLE_NAME_CP_RELATIONS+" WHERE ("+FNames.KEY+"= old."+FNames.KEY+" "
          + " AND ("+FNames.LPROJ_FROM+"=old."+FNames.LPROJ_FROM+")))"
        +" BEGIN "+
          " UPDATE "+CACHE_TABLE_NAME_CP_RELATIONS
          +" SET "+FNames.NODE_FROM+" = NULL, "+FNames.LPROJ_FROM+" = NULL "
          +" WHERE "
          + FNames.KEY+"=old."+FNames.KEY
          +" AND "+FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM
          +" AND "+FNames.LPROJ_FROM+"=old."+FNames.LPROJ_FROM+""
          + " AND "+
            "("+FNames.KEY+"= old."+FNames.KEY+" "
              + " AND ("+FNames.LPROJ_FROM+"=old."+FNames.PROJ_FROM+" AND "+FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM+")); "
        +"END;",
//    TRIGGER_CP_REM_DEF_NOT_EXISTS="CREATE TRIGGER IF NOT EXISTS cpDefRemNEx AFTER DELETE ON "+CACHE_TABLE_NAME_CP_DEFTERM+" FOR EACH ROW "
//        + "WHEN NOT EXISTS (SELECT * "
//      + "FROM "+CACHE_TABLE_NAME_CP_RELATIONS+" WHERE ("+FNames.KEY+"= old."+FNames.KEY+" "
//          + " AND ("+FNames.LPROJ_FROM+"!=old."+FNames.LPROJ_FROM+" AND "+FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM+")))"
//        +" BEGIN "+
//          " DELETE FROM "+CACHE_TABLE_NAME_CP_RELATIONS+" WHERE "
//          + FNames.KEY+"=old."+FNames.KEY+" AND "+FNames.NODE_FROM+"=old."+FNames.NODE_FROM+""
//          +" AND "+FNames.PROJ_FROM+"=old."+FNames.PROJ_FROM
//          +" AND "+FNames.LPROJ_FROM+"=old."+FNames.LPROJ_FROM+";"
//        +"END;",
//TODO trigger to remove error
            
    TRIGGER_ADD_DEF_EX_N=
    //TODO add error to nodes if duplication
        "CREATE TRIGGER IF NOT EXISTS defAddExN AFTER INSERT ON "+CACHE_TABLE_NAME_DEFTERM+" FOR EACH ROW "+
            "WHEN EXISTS (SELECT * FROM "+CACHE_TABLE_NAME_RELATIONS+" WHERE "
                + FNames.KEY +" = new."+FNames.KEY+" AND "+FNames.NODE_FROM+" IS NULL)"
        +" BEGIN "
            +   "UPDATE "+CACHE_TABLE_NAME_RELATIONS
            +     " SET "+FNames.NODE_FROM+" = new."+FNames.NODE_FROM
            +       " WHERE "+FNames.KEY+"=new."+FNames.KEY+";"+
             " END",
    TRIGGER_CP_ADD_DEF_EX_N=
        //TODO add error to nodes if duplication
            "CREATE TRIGGER IF NOT EXISTS cpDefAddExN AFTER INSERT ON "+CACHE_TABLE_NAME_CP_DEFTERM+" FOR EACH ROW  "+
                "WHEN EXISTS (SELECT * FROM "+CACHE_TABLE_NAME_CP_RELATIONS+" WHERE "
                + "("+FNames.KEY+"= new."+FNames.KEY+" "
          + " AND ("+FNames.PROJ_FROM+"=new."+FNames.LPROJ_FROM+" OR "+FNames.PROJ_FROM+"=new."+FNames.PROJ_FROM+")"
              + " AND "+FNames.NODE_FROM+" IS NULL))"
             +" BEGIN "
                +   "UPDATE "+CACHE_TABLE_NAME_CP_RELATIONS
                +     " SET "+FNames.NODE_FROM+" = new."+FNames.NODE_FROM
                +       ", "+FNames.LPROJ_FROM+"= new."+FNames.LPROJ_FROM+""
                    + " WHERE "+FNames.KEY+"=new."+FNames.KEY+" AND ("+FNames.PROJ_FROM+"=new."+FNames.PROJ_FROM+" OR "+FNames.PROJ_FROM+"=new."+FNames.LPROJ_FROM+");"+
                 " END";
       
  //TODO if not null or two defs then add error
  
  private static final List<String> relationsTriggers = new LinkedList<>(),
      cPRelationsTriggers = new LinkedList<>();
  static{
  relationsTriggers.add(TRIGGER_REMOVE_USE);
  cPRelationsTriggers.add(TRIGGER_CP_REMOVE_USE);
  relationsTriggers.add(TRIGGER_UPDATE_DEF);
  cPRelationsTriggers.add(TRIGGER_CP_UPDATE_DEF);
  relationsTriggers.add(TRIGGER_UPDATE_USE);
  cPRelationsTriggers.add(TRIGGER_CP_UPDATE_USE);
  relationsTriggers.add(TRIGGER_ADD_USE_EXISTS);
  relationsTriggers.add(TRIGGER_ADD_USE_NOT_EXISTS);
  cPRelationsTriggers.add(TRIGGER_CP_ADD_USE_EXISTS);
  cPRelationsTriggers.add(TRIGGER_CP_ADD_USE_NOT_EXISTS);
  relationsTriggers.add(TRIGGER_REM_DEF_EXISTS);
  relationsTriggers.add(TRIGGER_REM_DEF_NOT_EXISTS);
  cPRelationsTriggers.add(TRIGGER_CP_REM_DEF_EXISTS);
  //cPRelationsTriggers.add(TRIGGER_CP_REM_DEF_NOT_EXISTS);
  relationsTriggers.add(TRIGGER_ADD_DEF_EX_N);
  cPRelationsTriggers.add(TRIGGER_CP_ADD_DEF_EX_N);
  }
  public TermsCacheMan(ICacheStorageProvider cp, boolean isCp) {
    super(cp, isCp);
  }
  
  private static ISectionModel dts = new ISectionModel(){
    private Set<String> ids = new HashSet<>();;
    @Override
    public String getSectionName() {
      return CACHE_TABLE_NAME_DEFTERM;
    }
    private Map<String, CType> defs = new HashMap<>();
    {
      defs.put(FNames.NODE_FROM, CType.STRING);
      defs.put(FNames.KEY, CType.STRING);
      ids.add(FNames.KEY);
      ids.add(FNames.NODE_FROM);
    }
    @Override
    public String getVersion() {
      return DEF_TERM_MODEL_VERSION;
    };
    @Override
    public Map<String, CType> getSectionDefinition() {
      return defs;
    }
  
    @Override
    public Set<String> getIdFields() {
      return ids;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return Collections.emptySet();
    }
    @Override
    public List<String> getTriggers() {
      return null;
    }

    @Override
    public short getPriority() {
      return NORMAL;
    }
  },
  uts = new ISectionModel(){
    private Set<String> ids = new HashSet<>();;
    @Override
    public String getSectionName() {
      return CACHE_TABLE_NAME_USEDEF;
    }

    private Map<String, CType> defs = new HashMap<>();
    {
      defs.put(FNames.PROJ_FROM, CType.STRING);//only one name to meet
      defs.put(FNames.NODE_TO, CType.STRING);
      defs.put(FNames.NODE_TO_VERSION, CType.STRING);
      defs.put(FNames.KEY, CType.STRING);
      ids.add(FNames.KEY);
      ids.add(FNames.PROJ_FROM);
      ids.add(FNames.NODE_TO);
    }
    @Override
    public String getVersion() {
      return USE_TERM_MODEL_VERSION;
    };
    
    @Override
    public Map<String, CType> getSectionDefinition() {
      return defs;
    }

    @Override
    public Set<String> getIdFields() {
      // TODO Auto-generated method stub
      return ids;
    }

    @Override
    public Set<String> getAutoIncrementedFields() {
      // TODO Auto-generated method stub
      return null;
    }


    @Override
    public List<String> getTriggers() {
      // TODO Auto-generated method stub
      return null;
    }
    @Override
    public short getPriority() {
      return NORMAL;
    }
    
  },
  rels = new ISectionModel(){
    private Set<String> ids = new HashSet<>();;
    @Override
    public String getSectionName() {
      return CACHE_TABLE_NAME_RELATIONS;
    }
    private Map<String, CType> defs = new HashMap<>();
    {
      defs.put(FNames.NODE_FROM, CType.STRING);
      defs.put(FNames.NODE_TO, CType.STRING);
      defs.put(FNames.NODE_TO_VERSION, CType.STRING);
      defs.put(FNames.KEY, CType.STRING);
      ids.add(FNames.KEY);
      ids.add(FNames.NODE_FROM);
      ids.add(FNames.NODE_TO);
    }
    @Override
    public String getVersion() {
      return RELS_MODEL_VERSION;
    };
    
    @Override
    public Map<String, CType> getSectionDefinition() {
      return defs;
    }
  
    @Override
    public Set<String> getIdFields() {
      return ids;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return Collections.emptySet();
    }


    @Override
    public List<String> getTriggers() {
      return relationsTriggers;
    }
    @Override
    public short getPriority() {
      return NORMAL-1;
    }
  },
  cprels = new ISectionModel(){
    private Set<String> ids = new HashSet<>();;
    @Override
    public String getSectionName() {
      return CACHE_TABLE_NAME_CP_RELATIONS;
    }
    private Map<String, CType> defs = new HashMap<>();
    {
      defs.put(FNames.LPROJ_FROM, CType.STRING);
      defs.put(FNames.PROJ_FROM, CType.STRING);
      defs.put(FNames.NODE_FROM, CType.STRING);
      defs.put(FNames.PROJ_TO, CType.STRING);
      defs.put(FNames.LPROJ_TO, CType.STRING);
      defs.put(FNames.NODE_TO, CType.STRING);
      defs.put(FNames.NODE_TO_VERSION, CType.STRING);
      defs.put(FNames.KEY, CType.STRING);
      ids.add(FNames.KEY);
      ids.add(FNames.LPROJ_TO);
      ids.add(FNames.NODE_TO);
      ids.add(FNames.LPROJ_FROM);
      ids.add(FNames.NODE_FROM);
    }
    @Override
    public String getVersion() {
      return CPRELS_MODEL_VERSION;
    };
    
    @Override
    public Map<String, CType> getSectionDefinition() {
      return defs;
    }
  
    @Override
    public Set<String> getIdFields() {
      return ids;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return Collections.emptySet();
    }

    @Override
    public List<String> getTriggers() {
      return cPRelationsTriggers;
    }
    @Override
    public short getPriority() {
      return NORMAL-1;
    }
  },
  cputs = new ISectionModel(){

    private Set<String> ids = new HashSet<>();; 
    @Override
    public String getSectionName() {
      return CACHE_TABLE_NAME_CP_USEDEF;
    }

    private Map<String, CType> defs = new HashMap<>();
    {
      defs.put(FNames.PROJ_FROM, CType.STRING);//only one to be met
      defs.put(FNames.LPROJ_TO, CType.STRING);
      defs.put(FNames.PROJ_TO, CType.STRING);
      defs.put(FNames.NODE_TO, CType.STRING);
      defs.put(FNames.NODE_TO_VERSION, CType.STRING);
      defs.put(FNames.KEY, CType.STRING);
      ids.add(FNames.KEY);
      ids.add(FNames.PROJ_FROM);
      ids.add(FNames.LPROJ_TO);
      ids.add(FNames.NODE_TO);
    }
    @Override
    public String getVersion() {
      return CPUSE_TERM_MODEL_VERSION;
    };
    
    @Override
    public Map<String, CType> getSectionDefinition() {
      return defs;
    }

    @Override
    public Set<String> getIdFields() {
      return ids;
    }

    @Override
    public Set<String> getAutoIncrementedFields() {
      return null;
    }
//
//    @Override
//    public void reactProjectDeleted(TreeDB db, IResTracker tracker, String projectName, CacheStorage cs) {
//    }

    @Override
    public List<String> getTriggers() {
      return null;
    }
    @Override
    public short getPriority() {
      return NORMAL;
    }
    
  },
  cpdts = new ISectionModel(){
    private Set<String> ids = new HashSet<>();; 
    @Override
    public String getSectionName() {
      return CACHE_TABLE_NAME_CP_DEFTERM;
    }

    private Map<String, CType> defs = new HashMap<>();
    {
      defs.put(FNames.LPROJ_FROM, CType.STRING);
      defs.put(FNames.PROJ_FROM, CType.STRING);
      defs.put(FNames.NODE_FROM, CType.STRING);
      defs.put(FNames.KEY, CType.STRING);
      ids.add(FNames.KEY);
      ids.add(FNames.NODE_FROM);
      ids.add(FNames.LPROJ_FROM);
    }
    @Override
    public String getVersion() {
      return CPDEF_TERM_MODEL_VERSION;
    };
    
    @Override
    public Map<String, CType> getSectionDefinition() {
      return defs;
    }

    @Override
    public Set<String> getIdFields() {
      return ids;
    }

    @Override
    public Set<String> getAutoIncrementedFields() {
      return null;
    }

    
    @Override
    public List<String> getTriggers() {
      return null;
    }
    @Override
    public short getPriority() {
      return NORMAL;
    }
    
  };
  public static ISectionModel getDefTermsSection(){
    return dts;
  }
  public static ISectionModel getUseTermsSection(){
    return uts;
  }
  public static ISectionModel getRelationsSection(){
    return rels;
  }
  public static ISectionModel getCPRelationsSection(){
    return cprels;
  }

  public static ISectionModel getCPUseTermsSection() {
    return cputs;
  }
  public static ISectionModel getCPDefTermsSection() {
    return cpdts;
  }
  public List<RelationRecord> getCPDefToUseRelations(TreeDB db, Map<String,List<RelationRecord>> rr) {
    if(rr.size() == 0)
      return Collections.emptyList();
    LinkedList<RelationRecord> lr = null;
    Map<String, StringBuilder> sbs = new HashMap<>();
    Map<String, Map<String, RelationRecord>> ro = new HashMap<>();
    //TODO crossdb locks
    for(String p:rr.keySet()){
      for(RelationRecord r:rr.get(p)){
        if(!sbs.containsKey(p)){
          sbs.put(p, new StringBuilder("(?"));
          ro.put(p, new HashMap<>());
        }else
        {
          sbs.get(p).append(", ?");
        }
        ro.get(p).put(r.key, r);
      }
    }
    StringBuilder sb = new StringBuilder("SELECT * FROM ");
    sb.append(CACHE_TABLE_NAME_CP_RELATIONS);
    sb.append(" WHERE ");
    boolean first = true;
    int i = 0;
    for(String s:sbs.keySet()){
      i+= rr.get(s).size()+1;
      if(!first)
        sb.append(" OR ");
      sb.append(FNames.PROJ_FROM);
      sb.append(" = ");
      sb.append("?");
      sb.append(" AND ");
      sb.append(FNames.KEY);
      sb.append(" IN ");
      sb.append(sbs.get(s));
      sb.append(")");
    }
    Object[]req = new Object[i];
    i = 0;
    for(String s:sbs.keySet()){
      req[i++] = s;
      for(RelationRecord r: rr.get(s))
        req[i++] = r.key;
    }
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(CACHE_TABLE_NAME_CP_USEDEF, sb.toString(), req);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String lpf = rs.getString(FNames.LPROJ_FROM);
        String pf = rs.getString(FNames.PROJ_FROM);
        String lnf = rs.getString(FNames.LPROJ_TO);
        String nf = rs.getString(FNames.NODE_FROM);
        String key = rs.getString(FNames.KEY);
        //TODO refactor, looks heavy
        lr.add(new RelationRecord(lpf, pf,
            nf!=null?UUID.fromString(nf):null, 
            ro.get(pf).get(key).localProjectTo,
            ro.get(pf).get(key).projectTo,
            ro.get(pf).get(key).nodeTo,
            key,
            rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  } 
  public List<RelationRecord> getAllCPDefToUseRelations(TreeDB db) {
    LinkedList<RelationRecord> lr = null;
    Map<String, StringBuilder> sbs = new HashMap<>();
    Map<String, Map<String, RelationRecord>> ro = new HashMap<>();
    //TODO crossdb locks
    StringBuilder sb = new StringBuilder("SELECT * FROM ");
    sb.append(CACHE_TABLE_NAME_CP_RELATIONS);
    sb.append(";");
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(CACHE_TABLE_NAME_CP_USEDEF, sb.toString());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String lpf = rs.getString(FNames.LPROJ_FROM);
        String pf = rs.getString(FNames.PROJ_FROM);
        String lpt = rs.getString(FNames.LPROJ_TO);
        String pt = rs.getString(FNames.PROJ_TO);
        String nf = rs.getString(FNames.NODE_FROM);
        String nt = rs.getString(FNames.NODE_TO);
        String key = rs.getString(FNames.KEY);
        lr.add(new RelationRecord(lpf, pf,
            nf!=null?UUID.fromString(nf):null,
            lpt,
            pt,
            nt!=null?UUID.fromString(nt):null, 
            key,
            rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  } 
  public List<RelationRecord> getCPUseToDefRelations(TreeNode node) {
    String proj = node.getTreeDB().getLocalName();
    String uid = node.getSUUId();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(CACHE_TABLE_NAME_CP_USEDEF, SQL_GET_CP_USE_TO_DEF_TERMS, proj, uid);;
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String nf = rs.getString(FNames.NODE_FROM);
        String nt = rs.getString(FNames.NODE_TO);
        lr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM),
            nf!=null?UUID.fromString(nf):null, 
            rs.getString(FNames.LPROJ_FROM),
            rs.getString(FNames.PROJ_TO),
            nt!=null?UUID.fromString(nt):null,
            rs.getString(FNames.KEY),
            rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  }

  public List<RelationRecord> getCPUseToDefRelationsForNodes(TreeDB tree, Set<TreeNode> nodes) {
    String proj = tree.getLocalName();
    List<RelationRecord> lr = null;
    StringBuilder sb = new StringBuilder(SQL_GET_CP_USE_TO_DEF_TERMS_BY_NODES_PREFIX);
    sb.append("(");
    boolean first = true;
    for(TreeNode node:nodes){
      if(first)
        first = false;
      else
        sb.append(",");
      sb.append("'");
      sb.append(node.getSUUId());
      sb.append("'");
    }
    sb.append(");");
    //TODO crossdb locks
    Pair<PreparedStatement, ResultSet> p = getStorage(tree).readLocked(CACHE_TABLE_NAME_CP_USEDEF, sb.toString(), proj);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String nf = rs.getString(FNames.NODE_FROM);
        String nt = rs.getString(FNames.NODE_TO);
        lr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),
            rs.getString(FNames.PROJ_FROM),
            nf!=null?UUID.fromString(nf):null, 
            rs.getString(FNames.LPROJ_TO),
            rs.getString(FNames.PROJ_TO),
            nt!=null?UUID.fromString(nt):null,
            rs.getString(FNames.KEY),
            rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  }
  public List<RelationRecord> getCPDefToUseRelations(TreeNode node) {
    String proj = node.getTreeDB().getLocalName();
    String uid = node.getSUUId();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(CACHE_TABLE_NAME_CP_USEDEF, SQL_GET_CP_DEF_TO_USE_TERMS, proj, uid);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String nf = rs.getString(FNames.NODE_FROM);
        String nt = rs.getString(FNames.NODE_TO);
        lr.add(new RelationRecord(
            rs.getString(FNames.LPROJ_FROM),
            rs.getString(FNames.PROJ_FROM),
            nf!=null?UUID.fromString(nf):null, 
            rs.getString(FNames.LPROJ_TO),
            rs.getString(FNames.PROJ_TO),
            nt!=null?UUID.fromString(nt):null,
            rs.getString(FNames.KEY),
            rs.getString(FNames.NODE_TO_VERSION)));//TODO look
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  }
  public List<RelationRecord> getUseToDefRelationsForNodes(TreeDB tree,Set<TreeNode> nodes) {
    String lproj = tree.getLocalName();
    String proj = tree.getProjectName();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    StringBuilder sb = new StringBuilder(SQL_GET_USE_TO_DEF_TERMS_FOR_NODES);
    sb.append("(");
    boolean first = true;
    for(TreeNode node:nodes){
      if(first)
        first = false;
      else
        sb.append(",");
      sb.append("'");
      sb.append(node.getSUUId());
      sb.append("'");
    }
    sb.append(");");
    Pair<PreparedStatement, ResultSet> p = getStorage(tree).readLocked(CACHE_TABLE_NAME_RELATIONS, sb.toString());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String nf = rs.getString(FNames.NODE_FROM);
        String nt = rs.getString(FNames.NODE_TO);
        lr.add(new RelationRecord(lproj,proj,
            nf!=null?UUID.fromString(nf):null,
            lproj, proj,
            nt!=null?UUID.fromString(nt):null,
            rs.getString(FNames.KEY),
            rs.getString(FNames.NODE_TO_VERSION)));//TODO checl
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  }
  public List<RelationRecord> getUseToDefRelations(TreeNode node) {
    String lproj = node.getTreeDB().getLocalName(), proj = node.getTreeDB().getProjectName();
    String uid = node.getSUUId();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(CACHE_TABLE_NAME_RELATIONS, SQL_GET_USE_TO_DEF_TERMS, uid);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      while(rs.next()){
        if(lr == null)
          lr = new LinkedList<>();
        String nf = rs.getString(FNames.NODE_FROM);
        String nt = rs.getString(FNames.NODE_TO);
        lr.add(new RelationRecord(lproj,proj,
            nf!=null?UUID.fromString(nf):null, 
            lproj, proj,
            nt!=null?UUID.fromString(nt):null,
            rs.getString(FNames.KEY),
            rs.getString(FNames.NODE_TO_VERSION)));//TODO checl
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lr!=null?lr:Collections.emptyList();
  }
  public List<RelationRecord> getDefToUseRelationsForNode(TreeDB tree, Set<TreeNode> nodes) {
    String lproj = tree.getLocalName(), proj = tree.getProjectName();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    StringBuilder sb = new StringBuilder(SQL_GET_DEF_TO_USE_TERMS_FOR_NODES);
    sb.append("(");
    boolean first = true;
    for(TreeNode node:nodes){
      if(first)
        first = false;
      else
        sb.append(",");
      sb.append("'");
      sb.append(node.getSUUId());
      sb.append("'");
    }
    sb.append(");");
    Pair<PreparedStatement, ResultSet> p = getStorage(tree).readLocked(CACHE_TABLE_NAME_RELATIONS, sb.toString());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      if(rs!=null)
        while(rs.next()){
          if(lr == null)
            lr = new LinkedList<>();
          String nf = rs.getString(FNames.NODE_FROM);
          String nt = rs.getString(FNames.NODE_TO);
//          String projF = proj;
          
//          try{
//            projF = rs.getString(FNames.PROJ_FROM);
//          }
//          catch(Exception r){
//            System.out.println("gur error:" ); 
//            r.printStackTrace();
//          }
          lr.add(new RelationRecord(lproj,proj,
              nf!=null?UUID.fromString(nf):null, 
              lproj,proj,
              nt!=null?UUID.fromString(nt):null,
              rs.getString(FNames.KEY),
              rs.getString(FNames.NODE_TO_VERSION)));//TODO checl
        }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    if(lr!=null){
      return lr;
    }
    return Collections.emptyList();
  }
  public List<RelationRecord> getDefToUseRelations(TreeNode node) {
    String lproj = node.getTreeDB().getLocalName(), proj = node.getTreeDB().getProjectName();
    String uid = node.getSUUId();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(CACHE_TABLE_NAME_RELATIONS, SQL_GET_DEF_TO_USE_TERMS, uid);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      if(rs!=null)
        while(rs.next()){
          if(lr == null)
            lr = new LinkedList<>();
          String nf = rs.getString(FNames.NODE_FROM);
          String nt = rs.getString(FNames.NODE_TO);
          //String projF = proj;
//          try{
//            projF = rs.getString(FNames.PROJ_FROM);
//          }
//          catch(Exception r){
//            System.out.println("gur error:" ); 
//            r.printStackTrace();
//          }
          lr.add(new RelationRecord(lproj,proj,
              nf!=null?UUID.fromString(nf):null, 
              lproj,proj,
              nt!=null?UUID.fromString(nt):null,
              rs.getString(FNames.KEY),
              rs.getString(FNames.NODE_TO_VERSION)));//TODO checl
        }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    if(lr!=null){
      return lr;
    }
    return Collections.emptyList();
  }
  public List<RelationRecord> getDefToUseRelations(TreeNode node, String key) {
    String lproj = node.getTreeDB().getLocalName(), proj = node.getTreeDB().getProjectName();
    String uid = node.getSUUId();
    List<RelationRecord> lr = null;
    //TODO crossdb locks
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(CACHE_TABLE_NAME_RELATIONS, SQL_GET_DEF_TO_USE_TERMS_BY_KEY, uid, key);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      if(rs!=null)
        while(rs.next()){
          if(lr == null)
            lr = new LinkedList<>();
          String nf = rs.getString(FNames.NODE_FROM);
          String nt = rs.getString(FNames.NODE_TO);
          //String projF = proj;
//          try{
//            projF = rs.getString(FNames.PROJ_FROM);
//          }
//          catch(Exception r){
//            System.out.println("gur error:" ); 
//            r.printStackTrace();
//          }
          lr.add(new RelationRecord(lproj,proj,
              nf!=null?UUID.fromString(nf):null, 
              lproj,proj,
              nt!=null?UUID.fromString(nt):null,
              rs.getString(FNames.KEY),
              rs.getString(FNames.NODE_TO_VERSION)));//TODO checl
        }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    if(lr!=null){
      return lr;
    }
    return Collections.emptyList();
  }
  private static Pair<Set<String>,Set<Pair<String, String>>> NPAIR = new Pair<>(Collections.emptySet(), Collections.emptySet());
  private Pair<Set<String>,Set<Pair<String, String>>> getUseTerm(TreeDB db, String proj, String uuId){
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(CACHE_TABLE_NAME_USEDEF, SQL_GET_USES, uuId);
    if(p == null)
      return NPAIR;
    ResultSet rs = p.getSecond();
    //List<RelationRecord> ret = null;
    Set<String> localterms = null;
    Set<Pair<String, String>>remoteterms = null;//proj,term
    try{
      while(rs.next())
      {
        String key = rs.getString(FNames.KEY);
        String rpoj = rs.getString(FNames.PROJ_FROM);
//        ret.add(new RelationRecord(rpoj, UUID.fromString(rs.getString(FNames.NODE_FROM)), 
//            proj, uuId, key));
        if(rpoj!=null){
          if(remoteterms == null)  
            remoteterms = new HashSet<>();
          remoteterms.add(new Pair<>(proj, key));
        }else{
          if(localterms == null)  
            localterms = new HashSet<>();
          localterms.add(key);
        }
      }
      if(localterms!=null || remoteterms != null)
        return new Pair<>(localterms!=null?localterms:Collections.emptySet(), remoteterms!=null?remoteterms:Collections.emptySet());
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return NPAIR;
  }
  public void setUseTerm(TreeDB db, String uuId, String projName,String[] useold, Map<String, String> oldvers, String[] use2, Map<String, String> vers) {
    //Pair<Set<String>, Set<Pair<String, String>>> rr = getUseTerm(db, projName, uuId);
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction("Set useterm", CacheStorage.COMMIT_ALL);
    try{
      Set<String> wasloc = null;
      Map<String,Set<String>> wasProj = null;
      Set<String>addLoc = null;
      Map<String,Set<String>> addProj = null;
      Set<String>commLoc = null;
      Map<String,Set<String>> commLocMap = null;
      if(useold!=null)
        for(String s:useold){
          int ind = s.indexOf(TermsManipulator.SEPARATOR);
          if(ind>-1){
            if(wasProj == null)
              wasProj = new HashMap<>();
            String proj = s.substring(0,ind);
            s = s.substring(ind+TermsManipulator.SEPARATOR.length());
            Set<String> wass = wasProj.get(proj);
            if(wass == null) {
              wass = new HashSet<>();
              wasProj.put(proj, wass);
            }
            wass.add(s);
          }else{
            if(wasloc == null)
              wasloc = new HashSet<>();
            wasloc.add(s);
          }
        }
      if(use2!=null)
        for(String s:use2){
          String bs = s;
          int ind = s.indexOf(TermsManipulator.SEPARATOR);
          if(ind>-1){
            if(addProj == null)
              addProj = new HashMap<>();
            String proj = s.substring(0,ind);
            s = s.substring(ind+TermsManipulator.SEPARATOR.length());
            Set<String> wass = addProj.get(proj);
            if(wass == null) {
              wass = new HashSet<>();
              addProj.put(proj, wass);
            }
            wass.add(s);
            
            if(wasProj!=null) {
              Set<String> old = wasProj.get(proj);
              if(old!=null)
              {
                if(vers!=null&&oldvers!=null&&vers.getOrDefault(bs, "").equals(oldvers.getOrDefault(bs, ""))) {
                  if(commLocMap==null)
                    commLocMap = new HashMap<>();
                  Set<String> cloc = commLocMap.get(proj);
                  if(cloc == null) {
                    cloc = new HashSet<>();
                    commLocMap.put(proj, cloc);
                  }
                  cloc.add(s);
                }
              }
            }
          }else{
            if(addLoc == null)
              addLoc = new HashSet<>();
            addLoc.add(s);
            if(wasloc!=null && wasloc.contains(s))
            {
              if(vers!=null&&oldvers!=null&&vers.getOrDefault(s, "").equals(oldvers.getOrDefault(s, ""))) {
                if(commLoc==null)
                  commLoc = new HashSet<>();
                commLoc.add(s);
              }//TODO in fact else case is update of vers
            }
          }
        }
      if(commLocMap!=null)
        for(Entry<String, Set<String>> en: commLocMap.entrySet()) {
          if(addProj!=null) {
            Set<String> arold = addProj.get(en.getKey());
            if(arold!=null)
              arold.removeAll(en.getValue());
          }
          if(wasProj!=null) {
            Set<String> wsold = wasProj.get(en.getKey());
            if(wsold!=null)
              wsold.removeAll(en.getValue());
          }
        }
      if(commLoc!=null) {
        if(addLoc!=null)
          addLoc.removeAll(commLoc);
        if(wasloc!=null)
          wasloc.removeAll(commLoc);
      }
      if(wasProj!=null && wasProj.size()>0) {
        for(Entry<String, Set<String>> en:wasProj.entrySet()) {
          Set<String> wasloc2 = en.getValue();
          Object[]args = new Object[wasloc2.size()+2];
          args[0] = en.getKey();
          args[1] = uuId;
          StringBuilder sb = new StringBuilder();
          int i = 2;
          for(String s:wasloc2) {
            args[i++] = s;
            if(sb.length() == 0) 
              sb.append("(?");
            else
              sb.append(", ");
          }
          sb.append(")");
          cs.writeLocked(CACHE_TABLE_NAME_USEDEF, SQL_REMOVE_PROJ_USES_PREF+sb.toString()+";", args);
        }
      }
      if(wasloc!=null && wasloc.size()>0) {
        Object[]args = new Object[wasloc.size()+1];
        args[0] = uuId;
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for(String s:wasloc) {
          args[i++] = s;
          if(sb.length() == 0) 
            sb.append("(?");
          else
            sb.append(", ");
        }
        sb.append(")");
        cs.writeLocked(CACHE_TABLE_NAME_USEDEF, SQL_REMOVE_USES_PREF+sb.toString()+";", args);
      }
      if(addProj!=null && addProj.size()>0) {
        for(Entry<String, Set<String>> en:addProj.entrySet()) {
          String proj = en.getKey();
          for(String s:en.getValue()) {
            String versa = vers == null?null:vers.get(proj+TermsManipulator.SEPARATOR+s);
            cs.writeLocked(CACHE_TABLE_NAME_USEDEF, SQL_ADD_PROJ_USES, proj, uuId, s, versa);
          }
        }
      }
      if(addLoc!=null)
        for(String s:addLoc){
          String versa = vers == null?null:vers.get(s);
          cs.writeLocked(CACHE_TABLE_NAME_USEDEF, SQL_ADD_USES, uuId, s, versa);
        }
    }
    catch(Exception r){
      if(tr!=null && tr.error == null){
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      r.printStackTrace();
    }
    finally{
      if(tr!=null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void removeUseTerms(TreeDB db, String nodeUUID) {
  
    getStorage(db).writeLocked(CACHE_TABLE_NAME_USEDEF,SQL_REMOVE_USET, nodeUUID);
  }
  public void removeCPUseTerms(TreeNode node) {
  
    getStorage(node.getTreeDB()).writeLocked(CACHE_TABLE_NAME_CP_USEDEF, SQL_CP_REMOVE_USET, node.getTreeDB().getLocalName(), node.getSUUId());
  }
  public void removeDefTerms(TreeNode node) {
  
    getStorage(node.getTreeDB()).writeLocked(CACHE_TABLE_NAME_DEFTERM,SQL_REMOVE_DEFS, node.getSUUId());
  }
  public void removeCPDefTerms(TreeNode node) {
    getStorage(node.getTreeDB()).writeLocked(CACHE_TABLE_NAME_CP_DEFTERM,SQL_CP_REMOVE_DEFS, node.getTreeDB().getLocalName(), node.getSUUId());
  }
  public void setDefTerm(TreeNode node, String[] deftermold, String[] defterm) {
    String uuId = node.getSUUId();
    CacheStorage cs = getStorage(node.getTreeDB());
    CacheTransactionDescription tr = cs.startCacheTransaction("defTerms "+uuId+" "+Arrays.toString(defterm), CacheStorage.COMMIT_ALL);
    try{
      //getStorage(node.getTreeDB()).writeLocked(CACHE_TABLE_NAME_DEFTERM, SQL_REMOVE_DEFS, uuId);
      HashSet<String> was = defterm == null?null: new HashSet<>(Arrays.asList(defterm));
      if(deftermold!=null && deftermold.length > 0) {
        HashSet<String> wasnt = null;
        StringBuilder sb = new StringBuilder();
        for(String term:deftermold) 
          if(was!=null && !was.remove(term)) {
            if(sb.length()==0)
              sb.append("(?");
            else
              sb.append(", ?");
            if(wasnt == null)
              wasnt = new HashSet<String>();
            wasnt.add(term);
          }
        sb.append(")");
        Object[] args = new Object[wasnt == null?0:wasnt.size()+1];
        args[0] = uuId;
        int i = 1;
        if(wasnt!=null)
          for(String s:wasnt)
            args[i++] = s;
        cs.writeLocked(CACHE_TABLE_NAME_DEFTERM, SQL_REMOVE_DEF_PREF+sb.toString()+";", args);
      }
      if(was!=null)
        for(String s:was)
          cs.writeLocked(CACHE_TABLE_NAME_DEFTERM, SQL_ADD_DEF, uuId, s);
    }
    catch(Exception r){
      if(tr!=null && tr.error == null)
        cs.rollback(tr);
      r.printStackTrace();
    }
    finally{
      if(tr!=null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void setCPDefTerm(TreeNode node, String[] deftermold, String[] defterm) {
    String uuId = node.getSUUId();
    CacheStorage cs = getStorage(node.getTreeDB());
    CacheTransactionDescription tr = cs.startCacheTransaction("defTerms "+uuId+" "+Arrays.toString(defterm), CacheStorage.COMMIT_ALL);
    try{
      HashSet<String> was = defterm == null?null:new HashSet<>(Arrays.asList(defterm));
      if(deftermold!=null && deftermold.length > 0) {
        HashSet<String> wasnt = null;
        StringBuilder sb = new StringBuilder();
        for(String term:deftermold) 
          if(was!=null && !was.remove(term)) {
            if(sb.length()==0)
              sb.append("(?");
            else
              sb.append(", ?");
            if(wasnt == null)
              wasnt = new HashSet<String>();
            wasnt.add(term);
          }
        sb.append(")");
        Object[] args = new Object[wasnt == null ? 0 : wasnt.size()+2];
        args[0] = node.getTreeDB().getLocalName();
        args[1] = uuId;
        int i = 2;
        if(wasnt!=null)
          for(String s:wasnt)
            args[i++] = s;
        if(sb.length()>1)
          cs.writeLocked(CACHE_TABLE_NAME_CP_DEFTERM, SQL_CP_REMOVE_DEF_PREF+sb.toString()+";", args);
        else
          RLogger.logDebug("nothing to delete");
      }
      if(was!=null)
        for(String s:was)
          cs.writeLocked(CACHE_TABLE_NAME_CP_DEFTERM, SQL_CP_ADD_DEF,node.getTreeDB().getLocalName(), node.getTreeDB().getProjectName(), uuId, s);
    }
    catch(Exception r){
      r.printStackTrace();
    }finally {
      if(tr!=null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void setCPUseTerm(TreeNode node, Map<String, List<String>> filterusetermold, Map<String, String> versold, Map<String, List<String>> filteruseterm, Map<String, String> vers) {
    if(filteruseterm.isEmpty()) {
      removeCPUseTerms(node);
      return;
    }
    String uuId = node.getSUUId();
    CacheStorage cs = getStorage(node.getTreeDB());
    CacheTransactionDescription tr = cs.startCacheTransaction("useTerms "+uuId+" "+(filteruseterm.keySet()), CacheStorage.COMMIT_ALL);
    try{
      for(Entry<String, List<String>> oldt:filterusetermold.entrySet()) {
        String key = oldt.getKey();
        List<String> newterms = filteruseterm.get(key);
        List<String> torem = null;
        if(newterms!=null && newterms.size()>0)
        {
          for(String s:oldt.getValue())
            if(newterms.contains(s)) {
              String vkey = oldt.getKey()+TermsManipulator.SEPARATOR+s;
              if(vers.getOrDefault(vkey,"").equals(versold.getOrDefault(vkey, "")))
                newterms.remove(s);
              else {
                if(torem == null)
                  torem = new ArrayList<String>();
                torem.add(s);//TODO actually update
              }
            }else {
              if(torem == null)
                torem = new ArrayList<String>();
              torem.add(s);
            }
        }else
          torem = oldt.getValue();
        if(torem!=null)
        {
          StringBuilder sb = new StringBuilder();
          Object[] args = new Object[torem.size()+2];
          args[0] = node.getTreeDB().getLocalName();
          args[1] = uuId;
          int i = 2;
          for(String s:torem){
            args[i++] = s;
            if(sb.length() == 0)
              sb.append("(?");
            else
              sb.append(", ");
          }
          sb.append(")");
          cs.writeLocked(CACHE_TABLE_NAME_CP_USEDEF , SQL_CP_REMOVE_USET_PREF+sb.toString()+";", args);
        }
      }
      //getStorage().writeLocked(CACHE_TABLE_NAME_USEDEF, SQL_REMOVE_USES, uuId);
      for(Entry<String, List<String>> ent: filteruseterm.entrySet()) {
        String p = ent.getKey();

        for(String s:ent.getValue()){
//          тут подумать, возможно убрать парную к p вещь(local project name )
          cs.writeLocked(CACHE_TABLE_NAME_CP_USEDEF, SQL_CP_ADD_USES,p,node.getTreeDB().getLocalName(),node.getTreeDB().getProjectName(),uuId, s, vers==null?null:vers.get(p+TermsManipulator.SEPARATOR+s));
        }
      }
    }
    catch(Exception r){
      if(tr!=null&&tr.error == null){
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      r.printStackTrace();
    }finally{
      if(tr!=null && tr.error == null)
        cs.commit(tr);
    }
  }
  public Map<String, UUID> getDefMap(TreeDB db, Set<String> tf) {
    if(tf.isEmpty()) {
      return null;
    }
    Map<String, UUID > ret = new HashMap<>();
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction("get Defs for set "+Arrays.toString(tf.toArray()), CacheStorage.COMMIT_ALL);
    try{
      StringBuilder sb = new StringBuilder(SQL_GET_DEFS_PREFIX);
      boolean f = true;
      for(String s:tf){
        if(f)
          f = false;
        else
          sb.append(", ");
        sb.append("'");
        sb.append(s);
        sb.append("'");
      }
      sb.append(");");
      Pair<PreparedStatement, ResultSet> reta = cs.readLocked(CACHE_TABLE_NAME_DEFTERM , sb.toString());
      ResultSet rs = reta.getSecond();
      while(rs.next())
        ret.put(rs.getString(FNames.KEY), UUID.fromString(rs.getString(FNames.NODE_FROM)));
      if(tr!=null && tr.error == null) {
        tr.error("already commited");
        cs.commit(tr);
      }
    }
    catch(Exception r){
      if(tr!=null && tr.error == null)
        cs.rollback(tr);
      r.printStackTrace();
    }
    return ret;
  }

  public Map<String, UUID[]> getDefMapDuplicants(TreeDB db) {
    CacheStorage cs = getStorage(db);
    Map<String, List<UUID>> ret = new HashMap<>();
    Set<String> retList = new HashSet<>();
    CacheTransactionDescription tr = cs.startCacheTransaction("get all Defs for project", CacheStorage.COMMIT_ALL);
    try{

      Pair<PreparedStatement, ResultSet> reta = cs.readLocked(CACHE_TABLE_NAME_DEFTERM , SQL_GET_ALL_DEFS);
      ResultSet rs = reta.getSecond();

      while(rs.next()) {
        String key = rs.getString(FNames.KEY);
        List<UUID> list = ret.get(key);
        if(list == null) {
          list = new LinkedList<>();
          ret.put(key, list);
        }
        list.add(UUID.fromString(rs.getString(FNames.NODE_FROM)));
        if(list.size()>1)
          retList.add(key);
      }
      if(tr!=null && tr.error == null) {
        tr.error("already commited");
        cs.commit(tr);
      }
    }
    catch(Exception r){
      if(tr!=null && tr.error == null)
        cs.rollback(tr);
      r.printStackTrace();
    }
    Map<String, UUID[]> reta = new HashMap<>();
    for(String s:retList) 
      reta.put(s, ret.get(s).toArray(new UUID[0]));
    return reta;
  }
  public Map<String, UUID> getCPDefMap(TreeDB db, Map<String, List<String>> tf) {
    if(tf.isEmpty())
      return null;
    CacheStorage cs = getStorage(db);
    Map<String, UUID> ret = new HashMap<>();
    CacheTransactionDescription tr = cs.startCacheTransaction("get Defs for set "+Arrays.toString(tf.keySet().toArray()), CacheStorage.COMMIT_ALL);
    //TODO map?
    try{
      StringBuilder sb = new StringBuilder(SQL_GET_CP_DEFS_PREFIX);
      boolean first = true;
      for(String proj:tf.keySet()){
        if(first)
          first = false;
        else
        {
          sb.append(" OR ");
        }
        sb.append("("+FNames.PROJ_FROM+" = '"+proj+"' OR "+FNames.LPROJ_FROM+" = '"+db.getLocalName()+"') AND "+FNames.KEY+" IN (");
        boolean f = true;
        for(String s:tf.get(proj)){
          if(f)
            f = false;
          else
            sb.append(", ");
          sb.append("'");
          sb.append(s);
          sb.append("'");
        }
        sb.append(")");
      }
      sb.append(";");
      Pair<PreparedStatement, ResultSet> reta = cs.readLocked(CACHE_TABLE_NAME_CP_DEFTERM , sb.toString());
      ResultSet rs = reta.getSecond();
      while(rs.next())
        ret.put(rs.getString(FNames.PROJ_FROM)+TermsManipulator.SEPARATOR+rs.getString(FNames.KEY), UUID.fromString(rs.getString(FNames.NODE_FROM)));
      if(tr!=null && tr.error == null) {
        tr.error("already commited");
        cs.commit(tr);
      }
    }
    catch(Exception r){
      if(tr!=null && tr.error == null)
        cs.rollback(tr);
      r.printStackTrace();
    }
    return ret;
  }
  public void removeAll(TreeDB db) {
    if(isCp){
      getStorage(db).writeLocked(CACHE_TABLE_NAME_CP_DEFTERM, SQL_CP_REMOVE_ALL_DEFS, db.getLocalName());
      getStorage(db).writeLocked(CACHE_TABLE_NAME_CP_USEDEF, SQL_CP_REMOVE_ALL_USET, db.getLocalName());
      getStorage(db).writeLocked(CACHE_TABLE_NAME_CP_RELATIONS, SQL_CP_UPDATE_FROM_RELATIONS, db.getLocalName());
      getStorage(db).writeLocked(CACHE_TABLE_NAME_CP_RELATIONS, SQL_CP_REMOVE_TO_RELATIONS, db.getLocalName());
    }else{
      getStorage(db).writeLocked(CACHE_TABLE_NAME_DEFTERM, SQL_REMOVE_ALL_DEFS);
      getStorage(db).writeLocked(CACHE_TABLE_NAME_USEDEF, SQL_REMOVE_ALL_USET);
      getStorage(db).writeLocked(CACHE_TABLE_NAME_RELATIONS, SQL_REMOVE_ALL_RELATIONS);
    }
  }
  public boolean isCp() {
    return isCp;
  }

  public Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProject(CacheStorage cs, String lname, String pname) {
    Pair<TreeRelationsCounter, TreeRelationsCounter> ret = null;
    String target = lname;
    Pair<PreparedStatement, ResultSet> p = cs.readLocked(CACHE_TABLE_NAME_CP_DEFTERM, SQL_GET_RELATED_PROJS, lname, pname,lname,pname);
    if(p != null){
      ResultSet rs = p.getSecond();
      try{
        while(rs.next()){
          String v = rs.getString(FNames.LPROJ_TO);
          if(v!=null && !v.equals(target)){
            TreeRelationsCounter toDown = null;
            if(ret == null) {
              toDown = new TreeRelationsCounter(Direction.Forward, new HashMap<>(), new HashMap<>());
              ret = new Pair<>(toDown, TreeRelationsCounter.EMPTY);
            }else
            {
              toDown = ret.getFirst();
              if(toDown.isEmpty()) {
                toDown = new TreeRelationsCounter(Direction.Forward, new HashMap<>(), new HashMap<>());
                ret = new Pair<>(toDown, ret.getSecond());
              }
            }
            toDown.addRecord(v, rs.getString(FNames.KEY), true);
          }
          v = rs.getString(FNames.LPROJ_FROM);
          if(v!=null && !v.equals(target)){
            TreeRelationsCounter toUp = null;
            if(ret == null) {
              toUp = new TreeRelationsCounter(Direction.Backward, new HashMap<>(), new HashMap<>());
              ret = new Pair<>(TreeRelationsCounter.EMPTY, toUp);
            }else
            {
              toUp = ret.getSecond();
              if(toUp.isEmpty()) {
                toUp = new TreeRelationsCounter(Direction.Backward, new HashMap<>(), new HashMap<>());
                ret = new Pair<>(ret.getFirst(), toUp);
              }
            }
            toUp.addRecord(v, rs.getString(FNames.KEY), true);
          }
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
      finally{
        try {
          rs.close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        try {
          p.getFirst().close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    
    if(ret == null)
      return TreeRelationsCounter.EMPTY_PAIR;
    return ret;
  }
}
