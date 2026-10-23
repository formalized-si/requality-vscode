package com.unitesk.requality.core.attribute.valueprovider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class HtmlValueProvider extends TemplValueProvider {
  public static String TYPE="HTMLValueProvider";
  //private File dir = null;
  private IRelatedFilesProvider added;
  public HtmlValueProvider(){
    
  }
  private Object createAppendedValue(AttributeType tp, Object obj){
    if(tp == AttributeType.STRING || tp == AttributeType.HTML)
      return val!=null?val.toString()+obj.toString():obj.toString();
    ListValue lv = (ListValue) val;
    if(lv!=null){
      lv.getValues().add(obj);//TODO check
      return lv;
    }
    return obj;//TODO fix list add value
  }
  public HtmlValueProvider createAppended(AttributeType html,Object ds){
    return new HtmlValueProvider(html,createAppendedValue(html,ds),null,added);
  }
  public HtmlValueProvider(AttributeType html, Object string, IRelatedFilesProvider resFolder) {
    this(html,string,null,resFolder);
  }
  public HtmlValueProvider(
      AttributeType html, Object ds, Set<String> deps, IRelatedFilesProvider added2) {
    this.added = added2;
    if(added == null)
      added = TempFolderFilesProvider.getNewNotInited(html);
    init(html,ds,deps);
  }

  @Override
  public boolean equals(Object obj) {
    if( obj == null || !(obj instanceof HtmlValueProvider))
      return false;
    HtmlValueProvider cvp = (HtmlValueProvider)obj;
    Set<String> localFiles = added.getRelatedNames();
    Set<String> oldFiles = cvp.added.getRelatedNames();
    for(String s:localFiles){
      InputStream iso = cvp.added.getFileContent(s);
      InputStream lso = added.getFileContent(s);
      try {
        if(iso == null || !CoreUtils.streamEquals(iso, lso))
          return false;
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      finally{
        try {
          if(lso != null)
            lso.close();
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
        try {
          if(iso != null)
            iso.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      oldFiles.remove(s);
    }
    if(!oldFiles.isEmpty()){
      return false;
    }
    return super.equals(obj);
  }
  private boolean checkEquals(File f, List<File> added2) {
    for(File f2:added2)
      if(f2.getName().equals(f.getName()))
      {
        try {
          try(FileInputStream fis1 = new FileInputStream(f)){
            try(FileInputStream fis2 = new FileInputStream(f2)){
              if(CoreUtils.streamEquals(fis1, fis2))
                return true;
            }
          }
        } catch (FileNotFoundException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    return false;
  }
  @Override
  public String getType() {
    return TYPE;
  }
  @Override
  protected void init(AttributeType at2, Object val2, Set<String> dps) {
    if(added == null)
      added = TempFolderFilesProvider.getNewNotInited(at2);
//    if(dir==null){
//      dir = CoreUtils.getTempDir().resolve("htmlvp"+UUID.randomUUID().toString()).toFile();
//      if(!dir.exists())
//        dir.mkdirs();
//      //dir = Files.createTempDirectory("htmlvp"+UUID.randomUUID().toString()).toFile();
//      dir.deleteOnExit();
//    }
    Object v3 = recHndl(at2,val2);
    if(v3!=null)
      val2 = v3;
    super.init(at2,val2,dps);
    
  }
  public Object recHndl(AttributeType at, Object val){
    if(at == AttributeType.LIST){
      ListValue lv = (ListValue)val;
      List<Object>nval = new ArrayList<>();
      boolean changed = false;
      if(lv.getType()==AttributeType.LIST)
        for(Object o:lv.getValues()){
          Object r = (recHndl(lv.getType(), o));
          if(r != null){
            nval.add(r);
            changed = true;
          }
          else 
            nval.add(o);
        }
      else
        if(lv.getType()==AttributeType.STRING||lv.getType()==AttributeType.HTML){
          for(Object o : lv.getValues()){
            String s = o.toString();
            Object r = recHndl(lv.getType(),s);
            if(r != null){
              nval.add(r);
              changed = true;
            }else
              nval.add(o);
          }
        }
      if(changed)
        return new ListValue(at, nval);
      else
        return lv;
    }else
      if(at == AttributeType.STRING||at == AttributeType.HTML){
        return hndlString(val!=null?val.toString():null);
      }
    return null;
  }
  private Object hndlString(String s) {
    Object odesc = s, raw = s;
    // if(raw!=null){
    // if desc is null then we also needs to delete all images
    String desc = raw == null ? null : raw.toString();
    if(added instanceof TempFolderFilesProvider){
      TempFolderFilesProvider fp = (TempFolderFilesProvider)added;
      if(desc!=null){
        String nd = desc.toLowerCase();
        boolean hasimg = nd.contains("<img") || nd.contains("<object");
        if(hasimg){
          File f = fp.getDir();
          if(f==null)
            return desc;
          URI uri = f.toURI();
          desc = RequalityCLI.updateImages(uri, desc, fp,true).replace((char) 160, ' ');
          if (raw != null) {
            try {
              // TODO can be removed?
              desc = desc.replace(uri.toString(),ResManager.ATTR_RES_VAR);
            } catch (Exception e) { // unsop
              RLogger.logError(e);
            }
            //return new AttributeDefinition(nlist.getOrigin(),
              //  nlist.getKey(), new TemplValueProvider(AttributeType.HTML, desc));
          }
        }
        if(!desc.equals(odesc))
          return desc;
      }
    }
    return null;
  }
  private static Map<String,String> getContext(String key, AttributeStorage as){
    Map<String,String> ret = new HashMap<>(1);
    if(as!=null){
      URI path = as.getNode().getNodeResourcesPath(key);
      URI apath = as.getNode().getNodeResourcesPath();
      if(path!=null)
        ret.put(ResManager.ATTR_RES_VAR, path.toString());
      if(apath!=null)
        ret.put(ResManager.ATTR_RES_VAR, apath.toString());
    }
    return ret;
  }
  
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    if(as!=null){
      if(added == null || added instanceof TempFolderFilesProvider){
        if(added!=null) {
          TempFolderFilesProvider ttfp = (TempFolderFilesProvider) added;
          File resFolder = as.getNode().getNodeResourcesFile();//TODO replace by path
          if(resFolder == null){
            RLogger.logError("Attempt to write image to incorrect node "+as.getNode().getQualifiedId()+" on key "+key);
            
          }else
            if(ttfp.dir!=null && !ttfp.dir.getAbsolutePath().equals(resFolder.getAbsolutePath()))
            {
              Set<String> res = null;
              for(String en:added.getRelatedNames()){
                //java.nio.file.Path p = ttfp.getDir().toPath().relativize(ttfp.getFileContent(en));
                String name = en;
                if(!name.startsWith(key+"/")){
                  String fullKey = key+"/"+name;
                  if(res == null)
                    res = as.getNode().listResources();
                  if(res.contains(fullKey))
                  {
                    try(InputStream isOld = as.getNode().getResourceContent(fullKey)){
                      try(InputStream isNew = added.getFileContent(name)){
                        if(CoreUtils.streamEquals(isOld, isNew))
                          continue;
                      }
                    } catch (IOException e) {
                      RLogger.logError(e);
                    }
                  }
                  try(InputStream newCont = added.getFileContent(name)){
                    if(newCont!=null)
                      as.getNode().setResourceContent(fullKey, newCont, OFlag.NONE);//TODO change?
                  } catch (IOException e) {
                    RLogger.logError(e);
                  }
                }
                else if (name.startsWith(key+"/")) {
                  if(res == null)
                    res = as.getNode().listResources();
                  if(res.contains(name)) {
                    try(InputStream isOld = as.getNode().getResourceContent(name)){
                      try(InputStream isNew = added.getFileContent(name)){
                        if(CoreUtils.streamEquals(isOld, isNew))
                          continue;
                      }
                    }catch(IOException e) {
                      RLogger.logError(e);
                    }
                  }
                  try (InputStream newCont = added.getFileContent(name)){
                    if(newCont!=null)
                      as.getNode().setResourceContent(name, newCont, OFlag.NONE);//TODO change?
                  } catch (IOException e) {
                    RLogger.logError(e);
                  }
                }
              }
            }
        }
        added = new ResFilesProvider(as, key);
      }
    }
    TypedValue tv = new TypedValue(at, calculateValue(as, at, val ,getContext(key, as)));
    //TypedValue tv = super.getTypedValue(key, as);
    return tv;
  }
  public IRelatedFilesProvider getAdded() {
    return added;
  }
  public Set<String> gatherResNames() {
    Set<String> ret = new HashSet<>();
    Set<String> resnow = getAdded().getRelatedNames();
    gatherResNames(ret, at, val, resnow);
    return ret;
  }
  private void gatherResNames(Set<String> ret, AttributeType at, Object val, Set<String> resnow) {
    if(at == AttributeType.LIST){
      ListValue lv = (ListValue)val;
      List<Object>nval = new ArrayList<>();
      boolean changed = false;
      if(lv.getType()==AttributeType.LIST)
        for(Object o:lv.getValues()){
          gatherResNames(ret, lv.getType(), o, resnow);
        }
      else
        if(lv.getType()==AttributeType.STRING||lv.getType()==AttributeType.HTML){
          for(Object o : lv.getValues()){
            String s = o.toString();
            gatherResNames(ret, lv.getType(), s, resnow);
          }
        }
    }else
      if(at == AttributeType.STRING||at == AttributeType.HTML){
        if(val!=null){
          String s = val.toString();
          for(String r:resnow)
            if(s.contains(ResManager.RES_VAR+"/"+r) || s.contains(ResManager.ATTR_RES_VAR+"/"+r))
              ret.add(r);
        }
      }
  };
}
