package com.unitesk.requality.core.storage.changelog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.unitesk.jsonclone.JSONArray;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.jsonclone.LoadedJSONObject;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.MapValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.CoreUtils;

public class ChangeLogManager {
  public static final String ATTR_REPORT_ENABLED = "ChangeLogEnabled";
  public static String FIELD_VERSIONS_FROM_REPO = "versions_from_repo", FIELD_LOG_SOURCE = "source",
      FIELD_PREDEFINED = "predefined_values", FIELD_IGNORE_COMMITS = "ignore_hashes",
      FIELD_NORM_AUTHOR = "author_normalization", BY_NAME = "by_name", BY_EMAIL = "by_email", NAME = "name",
      EMAIL = "email";
  public static String SETTINGS_NAME = ".changeLogConfig";

  private List<ChangeLogRecord> predefinedValues = null;

  private ChangeLogTransformation baseTrans = null;

  private TreeDB db;
  private File settings = null;

  public ChangeLogManager(TreeDB db) {
    this.db = db;
    settings = CoreUtils.getMainDir(db).resolve(SETTINGS_NAME).toFile();
    loadMainSettings();

  }

  private void loadMainSettings() {
    Pair<List<ChangeLogRecord>, ChangeLogTransformation> setts = loadSettingsReturnPredefined(settings, true);
    if (setts != null) {
      baseTrans = setts.getSecond();
      predefinedValues = setts.getFirst();
    } else
      predefinedValues = Collections.emptyList();

  }

  private Pair<List<ChangeLogRecord>, ChangeLogTransformation> loadSettingsReturnPredefined(File file,
      boolean initDefaults) {
    if (file.exists()) {
      List<ChangeLogRecord> predefined = null;
      ChangeLogTransformation t = new ChangeLogTransformation();
      try (FileInputStream fr = new FileInputStream(file)) {
        try (LoadedJSONObject obj = new LoadedJSONObject(fr)) {
          ChangeLogSource source = null;
          String src = obj.optString(FIELD_LOG_SOURCE);
          if (src.equals("git"))
            source = ChangeLogSource.REPO;
          if (src.equals("predefined"))
            source = ChangeLogSource.PREDEFINED;
          if (initDefaults && source == null)
            source = ChangeLogSource.REPO;
          JSONArray arr = obj.optJSONArray(FIELD_PREDEFINED);
          if (arr != null) {
            predefined = new LinkedList<>();
            for (int i = 0; i < arr.length(); i++) {
              JSONObject val = arr.getJSONObject(i);
              predefined.add(new ChangeLogRecord(val.optString("version", Integer.toString(i + 1)),
                  val.optString("hash", ""), val.optString("author-name", ""),
                  val.optString("author-email", ""), val.optString("message", ""),
                  val.optString("date", "")));
            }
          }
          t.source = source;
          if (obj.has(FIELD_VERSIONS_FROM_REPO))
            t.useVerionsFromGit = obj.optBoolean(FIELD_VERSIONS_FROM_REPO);
          else
            t.useVerionsFromGit = initDefaults ? false : null;
          arr = obj.optJSONArray(FIELD_IGNORE_COMMITS);
          if (arr != null)
            for (int i = 0; i < arr.length(); i++) {
              String comm = arr.getString(i);
              if (t.ignoreHash == null)
                t.ignoreHash = new HashSet<>();
              t.ignoreHash.add(comm);
            }
          JSONObject nauthor = obj.optJSONObject(FIELD_NORM_AUTHOR);
          if (nauthor != null) {
            JSONObject bname = nauthor.optJSONObject(BY_NAME);
            if (bname != null) {
              Iterator<Object> it = bname.keys();
              while (it.hasNext()) {
                if (t.authorNameNormalization == null)
                  t.authorNameNormalization = new HashMap<>();
                String k = (String) it.next();
                JSONObject repl = bname.optJSONObject(k);
                if (repl != null) {
                  String rname = repl.optString(NAME, "");
                  String rmail = repl.optString(EMAIL, "");
                  t.authorNameNormalization.put(k, new Pair<>(rname, rmail));
                }
              }
            }
            JSONObject bmail = nauthor.optJSONObject(BY_EMAIL);
            if (bmail != null) {
              Iterator<Object> it = bmail.keys();
              while (it.hasNext()) {
                if (t.authorEmailNormalization == null)
                  t.authorEmailNormalization = new HashMap<>();
                String k = (String) it.next();
                JSONObject repl = bmail.optJSONObject(k);
                if (repl != null) {
                  String rname = repl.optString(NAME, "");
                  String rmail = repl.optString(EMAIL, "");
                  t.authorEmailNormalization.put(k, new Pair<>(rname, rmail));
                }
              }
            }
          }
          return new Pair<>(predefined, t);
        }
      } catch (FileNotFoundException e) {
        RLogger.logError(e);
      } catch (JSONException e) {
        RLogger.logError(e);
      } catch (IOException e1) {
        RLogger.logError(e1);
      } finally {

      }
    }
    return null;
  }

  private List<ChangeLogRecord> loadChangeLog(TreeNode node) {
    File nodeSettings = getNodeSettingsFile(node);
    UUID uid = node.getUUId();
    List<ChangeLogRecord> recs = predefinedValues;
    ChangeLogTransformation t = null;
    Pair<List<ChangeLogRecord>, ChangeLogTransformation> ld = loadSettingsReturnPredefined(nodeSettings, false);
    if (ld != null) {
      t = ld.getSecond();
      List<ChangeLogRecord> nval = ld.getFirst();
      recs = nval != null ? nval : recs;
    }
    t = ChangeLogTransformation.merge(baseTrans, t);
    ChangeLogSource source = ChangeLogSource.REPO;
    if (t != null)
      source = t.source != null ? t.source : ChangeLogSource.REPO;
    if (source == ChangeLogSource.PREDEFINED) {
      if (t == null)
        return Collections.emptyList();
      List<ChangeLogRecord> ret = recs;
      if (ret == null)
        ret = predefinedValues;
      ret = t.transform(ret);
      if (ret == null)
        return Collections.emptyList();
      return ret;
    } else if (source == ChangeLogSource.REPO) {
      TypedValue tv = API.getValue("RevisionsDataProvider", node.getTreeDB(),
          node.getTreeDB().getMonitor(), node);
      LinkedList<ChangeLogRecord> lret = new LinkedList<>();
      if (tv == null) {
        RLogger.logError(
            "Revisions variable cannot be loaded. Possible reason of this problem may be an outdated requality git plugin.");
        return lret;
      }
      ListValue lv = tv.getGValue();
      Iterator<MapValue> it = lv.giterator();
      MapValue inRepoInfo = it.hasNext() ? it.next() : null;
      int i = 1;
      String inRepo = inRepoInfo == null ? "false" : (String) inRepoInfo.getValues().get("IsInRepo");
      boolean versionsFromGit = t != null && t.useVerionsFromGit != null ? t.useVerionsFromGit : false;
      // TODO revert?
      boolean wasOriginalVers = false;
      if (inRepo.equals("true")) {
        while (it.hasNext()) {
          MapValue mv = it.next();
          Map<String, Object> vals = mv.getValues();
          String vers = (String) vals.get("Version");
          if (!versionsFromGit || vers.equals("") || vers.equals(AttributesVersioningRule.VALUE_NONE)
              || vers.equals(AttributesVersioningRule.VALUE_ERROR))
            vers = "";
          if (versionsFromGit && !vers.equals(""))
            wasOriginalVers = true;
          lret.addFirst(new ChangeLogRecord(vers, (String) vals.get("Revision"),
              (String) vals.get("AuthorName"), (String) vals.get("AuthorEmail"),
              (String) vals.get("Comment"), (String) vals.get("Date")));
        }
      }
      if (!wasOriginalVers) {
        for (ChangeLogRecord clr : lret)
          if (clr.version.equals(""))
            clr.version = Integer.toString(i++);// TODO
      } else {
        int minval = 0;
        int minind = -1, maxind = 0;
        int ind = 0;
        for (ChangeLogRecord clr : lret) {
          if (clr.getVersion().isEmpty()) {
            if (minind == -1)
              minind = ind;
            maxind = ind;
          } else {
            if (minind != -1)
              for (int k = minind; k <= maxind; k++)
                lret.get(k).version = Integer.toString(minval) + "." + Integer.toString(k - minind + 1);
            minval = Integer.parseInt(clr.getVersion());
            minind = -1;
          }
          ind++;
        }
        if (minind != -1)
          for (int k = minind; k <= maxind; k++) {
            minval++;
            lret.get(k).version = Integer.toString(minval);
          }
        // we need to manage this like as situation with indecies
      }
      List<ChangeLogRecord> ret = lret;
      if (t != null)
        ret = t.transform(ret);
      return ret;
    }
    return Collections.emptyList();
  }

  private File getNodeSettingsFile(TreeNode node) {
    File folder = settings.getParentFile();
    TreeNode par = node.getWorkParent();
    LinkedList<String> ids = new LinkedList<>();
    if (par != null) {
      String id = par.getId();
      if (!id.equals("/"))
        ids.addFirst(id);
      par = par.getWorkParent();
    }
    Iterator<String> idsIt = ids.iterator();
    Path p = folder.toPath().resolve("root");
    while (idsIt.hasNext())
      p = p.resolve(idsIt.next());
    return p.resolve(SETTINGS_NAME + "-" + node.getId()).toFile();
  }

  public List<ChangeLogRecord> getChangeLog(TreeNode node) {
    return loadChangeLog(node);
  }
}