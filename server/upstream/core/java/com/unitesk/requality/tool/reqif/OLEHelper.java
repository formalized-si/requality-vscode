package com.unitesk.requality.tool.reqif;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.Document;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.formatversion.IInputStreamProvider;
import com.unitesk.requality.document.CustomXWPFDocument;
import com.unitesk.requality.document.OmnivorousImporter;
import com.unitesk.requality.tool.IMMagic;
import com.unitesk.requality.tool.RTFHandler;
import com.unitesk.requality.tool.cli.LibreCLI;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class OLEHelper {
  private static final Byte OPEN = 0, CLOSE = 1, SELFCLOSE=2, NEW_IMAGE=3;
  private static long imgId = 0;
  public static String revertConvertTablesAndPics(String desc, String frompath, TreeNode node) {
  if (desc == null) return null;
    int pos = desc.indexOf("<");
    String endstr = "</object>";
    if(pos>-1){
      LinkedList<TagRecord> tags = new LinkedList<>();
//      Map<Integer,Byte> ends = new HashMap<>();
      while (pos > -1) {
        int endpos = desc.indexOf(">",pos);
        String textin = desc.substring(pos+1,endpos);
        int objtextpos = textin.indexOf("object");
        if(objtextpos>-1){
          //poss.add(pos);
          byte kind = -1;
          int objdivpos = textin.lastIndexOf("/");
          if(objdivpos>-1 && textin.indexOf("'",objdivpos)==-1&& textin.indexOf("\"",objdivpos)==-1){
            //TODO / in " or ' case
            if(objdivpos>objtextpos)
              kind = SELFCLOSE;
            else
              kind = CLOSE;
          }else
          {
            objdivpos = textin.indexOf("/");
            int q1 = textin.indexOf("'"), q2=textin.indexOf("\"");
            if((q1==-1||q1>objdivpos)&&(q2 == -1||q2>objdivpos))
            {
              if(objdivpos<=objtextpos)
                kind = CLOSE;
              else
                kind = SELFCLOSE;//TODO test
            }
            else
              kind = OPEN;//start tag
          }
          tags.add(new TagRecord(pos, endpos+1, kind, textin));          
        }
        pos = desc.indexOf("<", pos+1);
      }
      LinkedList<TagRecord[]> toHandle = new LinkedList<>();
      findOrder(tags.iterator(), toHandle);
      ConcurrentHashMap<TagRecord, TagRecord> results = new ConcurrentHashMap<>();
      for(TagRecord[] tr:toHandle){
        pos = tr[0].posStart;
        int posend = tr[tr.length-1].posEnd;
        String text = tr[0].text;
        int datapos = text.indexOf("data");
        int quote = text.indexOf("\"", datapos), quote2 = text.indexOf("'", datapos);
        char qu = '"';
        if (quote2 > -1 && quote2 < quote) {
          qu = '\'';
          quote = quote2;
        }
        //TODO images and tables by type?
        String name = text.substring(quote + 1, text.indexOf(qu, quote + 1));
        File f = new File(URI.create(frompath + "/" + name));
        URI nodep = node.getNodeResourcesPath();
        imgId++;
        try {
          String newname = Long.toString(imgId)+".png";
          int dot = name.lastIndexOf(".");
          boolean isOle = true;
          if (dot > -1){
            String exc = name.substring(dot+1).toLowerCase();
            isOle = exc.equals("ole") || exc.equals("rtf");
          }
          if(!isOle){
            FileInputStream fis = new FileInputStream(f);
            FileOutputStream fos = new FileOutputStream(new File(URI.create(frompath+"/"
                  + newname)));
            Files.copy(f.toPath(), fos);
            try{
              //node.setResourceContent(newname, fis);//TODO folders for attr
              results.put(tr[0],new TagRecord(tr[0].posStart,-1, NEW_IMAGE, "<img src='"
                  +frompath+"/"
                  + newname
                  + "'/>"));
            }finally{
              fos.close();
              fis.close();
            }
            continue;
          }
          //TODO table
          StringBuffer sb = new StringBuffer();
          FileReader fr = new FileReader(f);
          BufferedReader vr = new BufferedReader(fr);
          while (vr.ready()) {
            sb.append(vr.readLine());
          }
          vr.close();
          fr.close();
          String wmfString = frompath + "/" + newname + ".wmf";
          RTFHandler.extractWMF(sb.toString(), wmfString);
          boolean su = false;
          try {
            RTFHandler.extractDoc(sb.toString(), new File(URI.create(frompath + "/" + newname + ".doc")).getAbsolutePath());
          } catch (Exception e) { //no doc
          }
          try {
            File nf = new File(URI.create(frompath + "/" + newname + ".doc"));
            String ret = null;
            if (nf.exists()) {
              byte[] bt = Files.readAllBytes(nf.toPath());
              if (bt != null) {
                OmnivorousImporter oi = new OmnivorousImporter();
                IInputStreamProvider isp = new IInputStreamProvider() {
          
                  @Override
                  public InputStream getStream() {
                    try {
                      return new FileInputStream(nf);
                    } catch (FileNotFoundException e) {
                      RLogger.logError(e);
                    }
					return null;
                  }
                };
                ret = getBody(oi.importDOC( isp, node));
              }
            }
            if (ret != null) {
              su = true;
              results.put(tr[0], new TagRecord(tr[0].posStart,-1, NEW_IMAGE, ret));
            }

          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          if (!su) {
            File wmf = new File(URI.create(wmfString));
            if(wmf.exists()){
              try{
                IMMagic.getDefault().convertToPNG(wmfString, new File(URI.create(frompath + "/" + newname)).getAbsolutePath());
                if(results.contains(tr[0]))
                  results.get(tr[0]).text+="<img src='"+frompath+"/"
                      + newname
                      + "'/>";
                else
                  results.put(tr[0], new TagRecord(tr[0].posStart,-1, NEW_IMAGE, "<img src='"+frompath+"/"
                      + newname
                      + "'/>"));
              }catch(Exception e){
                RLogger.logError("Unsucessfull conversion of pic ");
                RLogger.logError(e);
                //unsucceed conv
              }
            }
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      int lastend = 0;
      if(tags.size()>0){
        StringBuffer sb = new StringBuffer();
        ListIterator<TagRecord> it = tags.listIterator(tags.size());
        StringBuffer descbuf = new StringBuffer(desc).reverse();
        int desclen = desc.length();
        while(it.hasPrevious() || lastend>-1){
          TagRecord tr = null;
          int end = 0;
          if(it.hasPrevious()){
            tr = it.previous();
            end = tr.posEnd;
          }
          if(end>desclen)
            RLogger.logDebug("End > desclen for handling desc on node "+node.getSUUId(), 5); 
          sb.append(descbuf.subSequence(lastend, desclen-end));
          lastend = -1;
          if(tr!=null){
            if(tr.kind == OPEN || tr.kind == SELFCLOSE){
              TagRecord repl = results.get(tr);
              if(repl!=null)
                sb.append(new StringBuffer(repl.text).reverse());
            }
            lastend = desclen - tr.posStart;
          }
        }
        return sb.reverse().toString();
      }
    }
    return desc;
  }
  private static class TagRecord{
    public String text;
    public int posStart, posEnd, kind;
    public TagRecord(int posStart, int posEnd, int kind, String text){
      this.posEnd = posEnd;
      this.posStart = posStart;
      this.kind = kind;
      this.text = text;
    }
  }

  private static void findOrder(
    Iterator<TagRecord> tagsIt, LinkedList<TagRecord[]> toHandle) {
    LinkedList<TagRecord> starts = new LinkedList<>();
    while(tagsIt.hasNext()){
      TagRecord t = tagsIt.next();
      switch(t.kind){
        case 0: 
          starts.addLast(t);
        break;
        case 1:{
          if(starts.isEmpty())
            throw new RuntimeException("Close of not openned tag!");
          TagRecord s = starts.removeLast();
          toHandle.addFirst(new TagRecord[]{s,t});
        }break;
        case 2:{
          toHandle.addFirst(new TagRecord[]{t});
        }
        break;
      }
    }
  }

  private static String getBody(String importDOC) {
    if (importDOC == null) return null;
    int st = importDOC.indexOf("<body"), et = importDOC.indexOf("</body");
    if(et == -1)
      return "";
    return importDOC.substring(importDOC.indexOf(">", st + 1)+1, et - 1);
  }

  private static List<File> todel = new LinkedList<>();;

  public static void removeAll() {
    for (File f : new LinkedList<>(todel)) {
      if (f != null) {
        f.delete();
        todel.remove(f);
      }
    }
  }

  private static long tcounter = 0;

  public static long getAdditionalThreadsCount() {
    return tcounter;
  }

  public static String convertTablesAndPics(
      TreeNode node, String content, String npath, Map<UUID, String> doorsId) {
    try{
      new File(URI.create(npath)).getAbsolutePath();//TODO support uri in reqif
    }catch(Exception e){
      npath = new File(npath).toURI().toString();
    }
    final String path = npath;
    String tag = "table";
    while (tag != null) {
      int tstart = content.indexOf("<" + tag);
      int len = tag.length() + 3;
      while (tstart > -1) {
        int tst = tstart;
        int tend = content.indexOf("</" + tag + ">", tstart);
        int atend = tag.equals("table") ? -1 : content.indexOf("/>", tstart);
        if (tend == -1) {
          tend = atend;
          len = 2;
        } else if (atend != -1) {
          tend = Math.min(tend, atend);
          if (tend == atend) len = 2;
        }
        tstart = content.indexOf("<" + tag, tst + 1);
        boolean b = false;
        while (tstart != -1 && tstart < tend) { //in
          if (!b) {
            tend = content.indexOf("</" + tag + ">", tstart);
            atend = tag.equals("table") ? -1 : content.indexOf("/>", tstart);
            if (tend == -1) {
              tend = atend;
              len = 2;
            } else if (atend != -1) {
              tend = Math.min(tend, atend);
              if (tend == atend) len = 2;
            }

          } else tstart = content.indexOf("<" + tag, tend + 1);
          b = !b;
        }
        String tabl = content.substring(tst, tend + len);
        final String name = "OLE_AB_" + UUID.randomUUID();
        String ext = null;
        String ipath = null;
        StringBuffer tables = new StringBuffer();
        if (!tag.equals("img")) {
          if (!tabl.isEmpty()) {
            try {
              //								RTFHandler.extractOLE(new File("C:\\Users\\kildishev\\Documents\\forTable\\OLE_AB_56a1f082194a10e7_23_2100009640_2800000003_ff0000001c_00000001_0.rtf"),
              //								"C:\\Users\\kildishev\\Documents\\forTable\\OLE_AB_56a1f082194a10e7_23_2100009640_2800000003_ff0000001c_00000001_0.doc",
              //							"C:\\Users\\kildishev\\Documents\\forTable\\OLE_AB_56a1f082194a10e7_23_2100009640_2800000003_ff0000001c_00000001_0.wmf");
            } catch (Exception e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            }
            //tabl = CoreUtils.reworkLibreDesc(node, tabl, true, null, doorsId);
            try {
              File t = new File(URI.create(path + "/" + name + ".htm"));
              Writer osw =
                  new BufferedWriter(
                      new OutputStreamWriter(
                          new FileOutputStream(t), Charset.forName("UTF-8").newEncoder()));
              todel.add(t);
              String html =
                  "<html><head><style>\n"
                      + "html *{   font-size: 0.95em !important;   color: #000 !important;   font-family: Arial !important;}\n"
                      + "table, th, td { border: 1px solid black; }\n"
                      + "table { border-collapse: collapse; margin-top: 5mm }\n"
                      + "</style><meta charset=\"UTF-8\"/></head><body style=\"background-color:#ffffff\">"
                      + tabl
                      + "</body></html>";
              osw.write(html);
              osw.close();
            } catch (Exception e) {

            }
            //java.nio.file.Path wiki_path = Paths.get(path, name+".htm");
            //html = String.join("\n",Files.readAllLines(wiki_path,Charset.forName("UTF-8")));
            //						t.delete();
            //IMMagic.getDefault().convertToSVG(path+File.separator+name+".png",path+File.separator+name+".svg");
            //LibreCLI.getDefault().convertToWMF(path+File.separator+name+".svg",path);
          }
          ipath = (path + "/" + name + ".htm");
          File f = new File(URI.create(ipath));
        }
        {
          if (tag.equals("img")) {
            int pos = tabl.indexOf("src") + 3;
            int cnt = 0;
            char bc = '"';
            StringBuilder sb = new StringBuilder();
            while (cnt < 3 && pos < tabl.length()) {
              char c = tabl.charAt(pos);
              //    ^^
              if (cnt == 0 && c == '=')
                // UU UU
                cnt++;
              else if (cnt == 1 && (c == '"' || c == '\'')) {
                cnt++;
                bc = c;
              } else if (cnt == 2)
                if (c == bc) cnt++;
                else sb.append(c);
              pos++;
            }
            try {
              ipath = URI.create(sb.toString().trim()).getPath();
            } catch (Exception e) {
              ipath = sb.toString().trim();
            }
            if (ipath.startsWith("file:")) ipath = ipath.substring(5);

            int wpos = 0;
            for (wpos = 0; wpos < Math.min(ipath.length(), 10); wpos++)
              if (RequalityCLI.isWindowsLocalPath(ipath, wpos)) {
                ipath = ipath.substring(wpos);
                break;
              }
          }
          try {

            File f = new File(ipath);
            final String ftag = tag;
            final String fipath = ipath;
            while (tcounter > 5) Thread.currentThread().join(100);
            String fname = name;
            String newName = "OLE_" + UUID.randomUUID().toString();
            final File ff = f;
            tcounter++;
            new Thread(
                    new Runnable() {

                      @Override
                      public void run() {
                        CustomXWPFDocument doc = null;
                        try {
                          File f = ff;
                          todel.add(f);
                          String name = fname;
                          String ipath = fipath;
                          // TODO Auto-generated method stub
                          if (ftag.equals("img")) {
                            todel.remove(f);
                            try {
                              File t = new File(URI.create(path + "/" + name + ".htm"));
                              Writer osw =
                                  new BufferedWriter(
                                      new OutputStreamWriter(
                                          new FileOutputStream(t), Charset.forName("UTF-8").newEncoder()));
                              todel.add(t);
                              String html =
                                  "<html><head><meta charset=\"UTF-8\"/></head><body style=\"background-color:#ffffff\">"
                                      + "<img src = \""+new File(ipath).toURI().toURL().toString()+"\">"
                                      + "</body></html>";
                              osw.write(html);
                              osw.close();
                            } catch (Exception e) {

                            }
                          }
                          LibreCLI.getDefault()
                              .convertHTMLToPNG(
                                  path + "/" + name + ".htm", path + "/" + newName + ".png");
                          ipath = path + "/" + newName + ".png";
                          f = new File(URI.create(path + "/" + newName + ".png"));
                          todel.add(f);
                        //									LibreCLI.getDefault().convertHTMLToPNG(tabl,ipath.substring(0, ipath.lastIndexOf("/")+1)+name+".png");
                          name = newName;
                          IMMagic.getDefault()
                              .convertToPNG(f.getAbsolutePath(), new File(URI.create(path + "/" + name + ".png")).getAbsolutePath());
                          File wfa2 = new File(URI.create(path + "/" + name + ".png"));
                          todel.add(wfa2);
                          String sizes1 = IMMagic.getDefault().getInfo(new File(URI.create(path + "/" + name + ".png")).getAbsolutePath());
                          ;
                          List<File> toh = new LinkedList<>();
                          toh.add(wfa2);
                          //TODO use sizes
                          int[] size1 = detSizes(sizes1);
                          int hlimit = 1200;
                          if (size1[1] > hlimit) {
                            toh.remove(wfa2);
                            toh.clear();
                            IMMagic.getDefault()
                                .crop(
                                    new File(URI.create(path + "/" + name + ".png")).getAbsolutePath(),
                                    new File(URI.create(path + "/" + name + "_0" + ".png")).getAbsolutePath(),
                                    size1[0],
                                    hlimit,
                                    0,
                                    0);
                            File n = new File(URI.create(path + "/" + name + "_0" + ".png"));
                            toh.add(n);
                            todel.add(n);
                            int j = 1;
                            while (j * hlimit < size1[1]) {
                              n = new File(URI.create(path + "/" + name + "_" + j + ".png"));
                              toh.add(n);
                              todel.add(n);
                              IMMagic.getDefault()
                                  .crop(
                                      new File(URI.create(path + "/" + name + ".png")).getAbsolutePath(),
                                      new File(URI.create(path + "/" + name + "_" + j + ".png")).getAbsolutePath(),
                                      size1[0],
                                      hlimit,
                                      0,
                                      (j) * hlimit);
                              j++;
                            }
                          }

                          //ImageInputStream im = javax.imageio.ImageIO.createImageInputStream(fr);
                          //ImageIO.write(im, "wmf",new FileWriter(path+File.separator+"test.wmf"));
                          doc = new CustomXWPFDocument();
                          for (File wf : toh) {

                            //name = wf.getName().substring(0,wf.getName().lastIndexOf("."));
                            //f = wf;
                            //IMMagic.getDefault()
                              //  .convertToSVG(wf.getAbsolutePath(), new File(URI.create(path + "/" + name + ".bmp")).getAbsolutePath());
                            try {
                              File t = new File(URI.create(path + "/" + wf.getName() + ".htm"));
                              Writer osw =
                                  new BufferedWriter(
                                      new OutputStreamWriter(
                                          new FileOutputStream(t), Charset.forName("UTF-8").newEncoder()));
                              todel.add(t);
                              String html =
                                  "<html><head><meta charset=\"UTF-8\"/></head><body style=\"background-color:#ffffff\">"
                                      + "<img src = \""+wf.getName()+"\">"
                                      + "</body></html>";
                              osw.write(html);
                              osw.close();
                            } catch (Exception e) {

                            }
                            LibreCLI.getDefault()
                            .convertHTMLToPNG(
                                path + "/" + wf.getName() + ".htm", path + "/" + wf.getName() + ".png");
                        
                          
//                            IMMagic.getDefault()
//                                .convertToSVG(
//                                    new File(URI.create(path + "/" + name + ".bmp")).getAbsolutePath(), new File(URI.create(path + "/" + name + ".svg")).getAbsolutePath());
                            todel.add(new File(URI.create(path + "/" + wf.getName() + ".png")));
                            todel.add(new File(URI.create(path + "/" + wf.getName() + ".png.svg")));
                            //f.delete();
                            LibreCLI.getDefault().convertToWMF(new File(URI.create(path + "/" + wf.getName() + ".png.svg")).getAbsolutePath(), new File(URI.create(path)).getAbsolutePath());
                            File wfa = new File(URI.create(path + "/" + wf.getName() + ".png.wmf"));
                            todel.add(wfa);

                            String sizes = IMMagic.getDefault().getInfo(wf.getAbsolutePath());
                            ;
                            int[] size = detSizes(sizes);
                            if (size == null) continue;
                            int w = size[0], h = size[1];
                            RLogger.logInfo(wf.getName() + " " + w + " " + h);
                            FileInputStream fr = new FileInputStream(wfa);
                            //doc.set
                            //XWPFRun run = title.createRun();
                            int tp = Document.PICTURE_TYPE_WMF;
                            switch (ipath.substring(ipath.lastIndexOf(".") + 1)) {
                              case "jpg":
                              case "jpeg":
                                tp = Document.PICTURE_TYPE_JPEG;
                                break;
                              case "bmp":
                                tp = Document.PICTURE_TYPE_WMF;
                                break;
                            }
                            try {
                              String blipId = doc.addPictureData(fr, tp);
                            } catch (InvalidFormatException e) {
                              // TODO Auto-generated catch block
                              e.printStackTrace();
                            }
                            fr.close();
                            //TODO possible issue here
                            fr = new FileInputStream(wfa);
                            doc.addPicture(fr, tp, w, h);
                            fr.close();
                          }
                          name = f.getName().substring(0, f.getName().lastIndexOf("."));
                          File t = new File(URI.create(path + "/" + name + ".docx"));
                          todel.add(t);
                          FileOutputStream fos = new FileOutputStream(t);
                          try{
                            doc.write(fos);
                          }finally{
                            fos.close();
                          }
                          LibreCLI.getDefault().convertToRTF(t.getAbsolutePath(), path);
                        } catch (Exception e) {
                          e.printStackTrace();
                        } finally {
                          tcounter--;
                          if(doc!=null)
                            try {
                              doc.close();
                            } catch (IOException e) {
                              // TODO Auto-generated catch block
                              e.printStackTrace();
                            }
                        }
                      }
                    })
                .start();
            tabl = "";
            //name = f.getName().substring(0,f.getName().lastIndexOf("."));
            ext = "rtf";
            tables.append(
                "<object name=\""
                    + newName
                    + "\" data=\""
                    + newName
                    + "."
                    + ext
                    + "\" type=\"text/"
                    + ext
                    + "\"></object>");
            //tabl = "<img src=\"data:image/"+ipath.substring(ipath.lastIndexOf(".")+1)+";base64,"+new String(Base64.getEncoder().encode(acnt))+"\"/>";
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        content = content.substring(0, tst) + tables.toString() + content.substring(tend + len);
        tstart = content.indexOf("<" + tag, tst + 1);
      }
      if (tag.equals("table")) tag = "img";
      else tag = null;
    }
    return content;
  }

  private static int[] detSizes(String sizes) {
    int pos = sizes.indexOf("x");
    if (pos > -1) {
      int st = pos - 1, en = pos + 1;
      while (Character.isDigit(sizes.charAt(st))) st--;
      int w = Integer.parseInt(sizes.substring(st + 1, pos));
      while (Character.isDigit(sizes.charAt(en))) en++;
      int h = Integer.parseInt(sizes.substring(pos + 1, en));
      return new int[] {w, h};
    }
    return null;
  }
}
