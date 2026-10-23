package com.unitesk.requality.core.storage.formatversion;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.IStoredVersionProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.repo.ICVSClient;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;

public class StoredFormatVersioning {
  private static StoredFormatVersioning one = new StoredFormatVersioning();
  public static StoredFormatVersioning getOne(){
    return one;
  }
  public String getFullVersionByGroupId(String gid) {
    getActiveVersions();
    if (!versionsMap.containsKey(gid)) return null;
    return gid + "|" + versionsMap.get(gid);
  }

  public String getVersionByGroupId(String gid) {
    getActiveVersions();
    if (!versionsMap.containsKey(gid)) return "undefined";
    return versionsMap.get(gid);
  }

  public List<String> getFormatsVersions(File proj) {
    
    if (proj == null) return null;
    File f = proj.toPath().resolve(".reqversions").toFile();
    if (!f.exists()) return null;
//    try {
//      //proj.touch(null);
//      f.refreshLocal(0, null);
//    } catch (CoreException e) {
//      // TODO Auto-generated catch block
//      //e.printStackTrace();
//    }
    try (FileReader fr = new FileReader(f)){
      return getFormatsVersions(fr);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new LinkedList<>();//TODO mb coll empty but it can be used for add, needs to be tested
  }

  private List<String> getFormatsVersions(Reader fr) {
    List<String> vers = new ArrayList<>();
    BufferedReader br = new BufferedReader(fr);
    Map<String, String> avers = new HashMap<>();
    try {
      while (br.ready()) {
        String s = br.readLine();
        if (s != null) {
          int ind = s.indexOf("|");
          if (ind > -1) {
            String name = s.substring(0, ind), v = s.substring(ind + 1);
            boolean replace = !avers.containsKey(name);
            if (!replace) {
              if (betterVers(v, avers.get(name))) {
                replace = true;
                vers.remove(name + "|" + avers.get(name));
              }
            }
            if (replace) {
              avers.put(name, v);
              vers.add(s);
            }
          }
        }
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }finally{
      try {
        br.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return vers;
  }
  private boolean betterVers(String v, String string) {
    v = v.trim();
    string = string.trim();
    int s1 = 0, s2 = 0;
    ;
    int i = 0, j = 0;
    while (i < v.length() || j < string.length()) {
      int n1 = 0, n2 = 0;
      while (i < v.length() && Character.isDigit(v.charAt(i))) i++;
      while (j < string.length() && Character.isDigit(string.charAt(j))) j++;
      String i1 = v.substring(s1, i), i2 = string.substring(s2, j);
      if (i <= v.length() && !i1.isEmpty()) n1 = Integer.parseInt(i1);
      if (j <= string.length() && !i2.isEmpty()) n2 = Integer.parseInt(i2);
      if (n1 > n2) return true;
      while (i < v.length() && !Character.isDigit(v.charAt(i))) i++;
      while (j < string.length() && !Character.isDigit(string.charAt(j))) j++;
      s1 = i;
      s2 = j;
    }
    return false;
  }

  private static final String separator = System.getProperty("line.separator");

  public void setFormatsVersions(File proj, List<String> vers) {
    File f = proj.toPath().resolve(".reqversions").toFile();
    String cont = "";
    for (String s : vers) cont += s + separator;
    try {
      if (!f.getParentFile().exists())
        f.getParentFile().mkdirs();
      if (f.exists()) f.delete();
      Files.copy(new ByteArrayInputStream(cont.getBytes("UTF-8")), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private List<String> versions = null;
  private SortedMap<String, String> versionsMap = new TreeMap<>();
  //vers
  //	-old\new
  //		-conv
//  public static List<VersionUpdateInfo> collectUpdates(File proj) {
//    List<VersionUpdateInfo> vui = new ArrayList<>();
//    List<String> vers = getFormatsVersions(proj);
//    //special case of old projects
//    List<String> avers = getActiveVersions();
//    List<String> miss = getMissed(vers);
//    if(miss.isEmpty())
//    if (vers == null) {
//      for (String s : avers) {
//        IFormatVersionConverter conv = getConverter(getGroupIdPart(s), "*", getVersionPart(s));
//        if (conv != null) {
//          vui.add(
//              new VersionUpdateInfo(
//                  getGroupIdPart(s), conv.versionFromMin(),conv.versionFromMax(), getVersionPart(s), "update", conv));
//        }
//      }
//    } else {
//      for (String m : miss) {
//        boolean fnd = false;
//        for (String s : avers)
//          if (hasSameGroup(m, s)) {
//            if (!hasSameVersion(m, s)) {
//              IFormatVersionConverter conv =
//                  getConverter(getGroupIdPart(s), getVersionPart(m), getVersionPart(s));
//              if (conv != null && conv.isAppliable(proj)) {
//                fnd = true;
//                vui.add(
//                    new VersionUpdateInfo(
//                        getGroupIdPart(s), getVersionPart(m), getVersionPart(s), "update", conv));
//              }
//            } else fnd = true;
//          }
//        if (!fnd) {
//          String gid = getGroupIdPart(m);
//          String v = getVersionByGroupId(gid);
//          vui.add(
//              new VersionUpdateInfo(
//                  gid,
//                  v,
//                  getVersionPart(m),
//                  v.equals("undefined") ? "plugin_not_found" : "version_not_found",
//                  null));
//        }
//      }
//    }
//    return vui;
//  }

  public List<VersionUpdateInfo> listProjectUpdates(File proj) {
    List<String> vers = getFormatsVersions(proj);
    //special case of old projects
    List<String> avers = getActiveVersions();
    List<VersionUpdateInfo> vis = new ArrayList<>();
    List<String> miss = getMissed(vers);
    int convi = 0;
    Map<String, Queue<IFormatVersionConverter>> convs = new HashMap<>();
    for (String m : miss) {
      boolean fnd = false;
      VersionUpdateInfo vui = null;
      for (String s : avers)
        if (hasSameGroup(m, s)) {
          if (!hasSameVersion(m, s)) {
            Queue<IFormatVersionConverter> convPath = 
                gatherPath(getGroupIdPart(s), getVersionPart(m), getVersionPart(s));
            if(convPath == null || convPath.isEmpty()){
              vis.add(
                  new VersionUpdateInfo(
                      getGroupIdPart(m),
                      "There are no converters from "+getVersionPart(m)+" to "+getVersionPart(s)+" for plugin "+getGroupIdPart(s),
                      null, null, null, getVersionPart(s)));
            }
            else{
              convs.put(getGroupIdPart(m), convPath);
              fnd = true;
              vis.add(
                  new VersionUpdateInfo(
                      getGroupIdPart(s),
                      null,
                      convPath,
                      convPath.iterator().next().versionFromMin(),
                      (convPath.toArray(new IFormatVersionConverter[0])[convPath.size()-1]).versionFromMax(),
                      getVersionPart(s)));
            }
          } else {
            fnd = true;
            convi++;
          }
        }
      if (!fnd) {
        String gid = getGroupIdPart(m);
        String v = getVersionByGroupId(gid);
        vis.add(
            new VersionUpdateInfo(
                v,
                v.equals("undefined") ? "Plugin "+gid+" was not found" : "Required version ("+v+") of plugin '"+gid+"' was not found",
                null, null, null, null));
      }
    }
   // }
    return vis;
  }

  private static Comparator<String> versComp = new Comparator<String>(){
    @Override
    public int compare(String o1, String o2) {
      if(o1.equals(o2))
        return 0;
      return recCompare(o1, o2);
    }
    public int recCompare(String o1, String o2){
      if(o1.equals("*"))
        return o2.equals(o1)?0:-1;
      if(o2.equals("*"))
        return o1.equals(o1)?0:1;
      int p1=o1.indexOf("."), p2=o2.indexOf(".");
      if(p1==-1 && o1.length() != 0)
        p1 = o1.length();
      if(p2==-1 && o2.length() != 0)
        p2 = o2.length();
      if(p1 == -1 && p2 == -1)
        return 0;
      int n1=0,n2=0;
      if(p1>0)
        n1 = Integer.parseInt(o1.substring(0, p1));
      if(p2>0)
        n2 = Integer.parseInt(o2.substring(0, p2));
      if(p1<0)
        p1 = 0;
      if(p2<0)
        p2 = 0;
      if(n1>n2)
        return 1;
      if(n1<n2)
        return -1;
      if(p1+1<o1.length())
        o1 = o1.substring(p1+1);
      else o1 = "";
      if(p2+1<o2.length())
        o2 = o2.substring(p2+1);
      else o2 = "";
      return recCompare(o1, o2);
    }
  };
  
  private static Queue<IFormatVersionConverter> gatherPath(
      String groupIdPart, String v1, String v2) {
    IStoredVersionProvider prov = API.getStorageVersionProvider(groupIdPart);
    if(prov == null)
      return null;
    SortedSet<IFormatVersionConverter> ss = new TreeSet<>(new Comparator<IFormatVersionConverter>(){
      @Override
      public int compare(IFormatVersionConverter o1, IFormatVersionConverter o2) {
        return versComp.compare(o1.versionFromMax(), o2.versionFromMax());
      }
    });
    ss.addAll(prov.getStoredversionConverters());
    IFormatVersionConverter start = null, last = null;
    Queue<IFormatVersionConverter> convs = new ArrayDeque<>();
    for(IFormatVersionConverter vp:ss){
      if(start == null && versComp.compare(vp.versionFromMin(),v1)<=0 &&
          versComp.compare(vp.versionFromMax(),v1)>0)
        start = vp;
      if(start!=null)
        convs.add(vp);
      last = vp;
    }
    if(last!=null && last.versionTo().equals(v2))//TODO checl
      return convs;
    return null;
  }

  private SortedMap<String, SortedMap<String, SortedMap<String, IFormatVersionConverter>>>
      rgraph = null;

  private Set<String> emptySet = new TreeSet<>();
  private static Comparator<IFormatVersionConverter> convertorsComparator = new 
      Comparator<IFormatVersionConverter>(){
        @Override
        public int compare(IFormatVersionConverter o1, IFormatVersionConverter o2) {
          IStoredVersionProvider v1 = API.getStorageVersionProvider(o1.getGroup());
          IStoredVersionProvider v2 = API.getStorageVersionProvider(o2.getGroup());
          if(v1 == null)
            return -1;
          if(v2 == null)
            return 1;
          return Integer.valueOf(v1.getPriority()).compareTo(v2.getPriority());
        }
      };

  private Set<String> reachConvAdd(String gr, String versold) {
    if (!rgraph.get(gr).containsKey(versold)) return emptySet;
    Set<String> convs = rgraph.get(gr).get(versold).keySet();
    for (String versnew : convs) {
      Set<String> rec = reachConvAdd(gr, versnew);
      for (String s : rec)
        if (!rgraph.get(gr).get(versold).containsKey(s))
          rgraph.get(gr).get(versold).put(s, rgraph.get(gr).get(versold).get(versnew));
    }
    return rgraph.get(gr).get(versold).keySet();
  }

  public static String getGroupIdPart(String vers) {
    return vers.substring(0, vers.lastIndexOf("|"));
  }

  public static String getVersionPart(String vers) {
    return vers.substring(vers.lastIndexOf("|") + 1);
  }

  private static boolean hasSameGroup(String v1, String v2) {
    return v1.substring(0, v1.lastIndexOf("|")).equals(v2.substring(0, v2.lastIndexOf("|")));
  }

  private static boolean hasSameVersion(String v1, String v2) {
    return v1.substring(v1.lastIndexOf("|")).equals(v2.substring(v2.lastIndexOf("|")));
  }

  public List<String> getMissed(List<String> stored) {
    List<String> nowv = getActiveVersions();
    List<String> ret = new ArrayList<>();
    if (stored == null || stored.size() == 0){
      for(String s:nowv)
        ret.add(getGroupIdPart(s)+"|0");
      return ret;//TODO think
    }
    for (String s : stored) {
      boolean fnd = false;
      for (String s2 : nowv)
        if (hasSameGroup(s, s2)) {
          fnd = true;
          if (!hasSameVersion(s, s2)) ret.add(s);
        }
      if (!fnd) ret.add(getGroupIdPart(s)+"|0");//not inited
    }
    return ret;
  }

  public List<String> getActiveVersions() {
    //if (versions == null) {
    List<String> versions = new ArrayList<>();
      List<IStoredVersionProvider> provs = API.getStorageVersionProviders();
//      IExtensionRegistry reg = Platform.getExtensionRegistry();
//      IConfigurationElement[] configurationElements =
//          reg.getConfigurationElementsFor("com.unitesk.requality.nodegroup");
      for (IStoredVersionProvider sp : provs) {
        try {
          try {
            String gid = sp.getGroupName(), vers = sp.getCurrentVersion();
            versions.add(gid + "|" + vers);
            versionsMap.put(gid, vers);
          } catch (ClassCastException cce) {
            //so, this is bad handlers(
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    //}
    return versions;
  }
  
  public static class ConversionResult{
    public String error, result;
    public ConversionResult(String error, String result){
      this.error = error;
      this.result = result;
    }
  }
  private static ConcurrentSkipListSet<String> converting = new ConcurrentSkipListSet<>();
  public ConversionResult update(IResTracker tracker,File file, RevisionInfo ri, List<VersionUpdateInfo> vilist, IProgressMonitor mon) {
    StringBuffer err = new StringBuffer();
    SortedMap<String, SortedSet<IFormatVersionConverter>> updates = 
        new TreeMap<>(versComp);
    if (vilist.size() > 0) {
      for (VersionUpdateInfo vui : vilist) {
        if (vui.getConverters() == null) {
          if(vui.getStatus()!=null)
            err.append(vui.getStatus());
        }else{
          for(IFormatVersionConverter c:vui.getConverters()){
            String v1 = getVersionPart(c.versionFromMin());
            String v2 = getVersionPart(c.versionFromMax());
            if(!updates.containsKey(v2))
              updates.put(v2, new TreeSet<>(convertorsComparator));
            updates.get(v2).add(c);
          }
        }
      }
      List<String> lv = getFormatsVersions(file);
      if(lv == null)
        lv = new LinkedList<>();
      String convpath = null;
      if(err.length() == 0){
        File f = getConvertFile(file);
        if(f.exists()){
          f.deleteOnExit();
          if(converting.contains(file.getAbsolutePath()))
            return null;
          else {
            f.delete();//file after tool restart, we need try to clear the state before next attempt
            try {
              ICVSClient cli = RepoManager.getCVSClient(tracker, file.toURI(), tracker.getLocalName(file.toURI()));//reset state of project
              if(cli!=null)
                cli.resetToHead(mon);
            } catch(Exception e) {
              RLogger.logError(e);
            }
            try {
              tracker.refresh(file, -1);
			} catch (Exception e) {
              RLogger.logError(e);
			}//refresh eclipse-related abstractions
          }
        }
        try {
          convpath = file.getAbsolutePath();
          converting.add(convpath);
          f.createNewFile();
          f.deleteOnExit();
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
        try{
          StringBuilder sb = new StringBuilder();
          for(String s1:updates.keySet()){
            try{
              for(IFormatVersionConverter conv:updates.get (s1)){
                RLogger.logDebug(" converter start "+conv.getGroup()+" "+conv.versionFromMin()+"-"+conv.versionFromMax()+" -> "+conv.versionTo()+" for "+file.getAbsolutePath(), 5);
                String result = conv.convert(tracker, ri, file, mon);
                if(result != null){
                  sb.append("\n");
                  sb.append(result);
                }
                java.util.Iterator<String> vers = lv.iterator();
                while(vers.hasNext()){
                  String s = vers.next();
                  if(getGroupIdPart(s).equals(conv.getGroup())){
                    vers.remove();
                    break;
                  }
                }
                lv.add(conv.getGroup()+"|"+conv.versionTo());
              }
              setFormatsVersions(file, lv);
            }catch(Exception e){
              RLogger.logError(e);
              err.append(e.getMessage()+":"+CoreUtils.getTrace(e)+"\n");
            }
            //setFormatsVersions(file, lv);
          }
          return new ConversionResult(err.toString(), sb.toString());
        }finally{
          //tracker.reOpen(file, ri, true, true);
          CoreUtils.delete(f,true,true);
          converting.remove(convpath);
        }
      }
    }
    return new ConversionResult(err.toString(), "");
  }
  public static File getConvertFile(File file) {
    return file.toPath().resolve("convert").toFile();
  }
  public static String getVersionByGroupId(List<String> vf, String string) {
    for(String s:vf)
      if(getGroupIdPart(s).equals(string))
        return getVersionPart(s);
    return null;
  }
  public String getCoreVersion(InputStreamReader isr) {
    if(isr == null)
      return null;
    List<String> vers = getFormatsVersions(isr);
    return getCoreVersion(vers);
  }  
  public String getCoreVersion(List<String> vers) {
    if(vers!=null){
      for(String s:vers)
        if(getGroupIdPart(s).equals("com.unitesk.nodegroup.requality"))
          return getVersionPart(s);
    }
    return null;
  }
  public boolean storedVersionCorrect(URI uri) {
    //TODO test
    if(uri == null)
      return false;//TODO mb return true on incorrect states
    try {
	  File fc = new File(uri);
      if(!fc.exists())
        return false;
      final List<VersionUpdateInfo> vilist = StoredFormatVersioning.getOne().listProjectUpdates(fc);
      if(!vilist.isEmpty())
        return false;
      return true;
    }catch(IllegalArgumentException iae) {
      if(uri.getScheme().equals("memory"))//test one
        return true;
      RLogger.logError("Incorrect scheme for stored format gathering "+uri);
    }
	return false;
  }
}
