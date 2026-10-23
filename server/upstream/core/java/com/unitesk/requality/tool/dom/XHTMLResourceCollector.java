/*
 * Copyright 2015 ISP RAS (http0://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.tool.dom;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.htmlcleaner.TagNode;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tool.CoreUtils;

public class XHTMLResourceCollector {
  private static String[] allTags = new String[] { "a", "abbr", "acronym", "address", "applet", "area", "b", "base",
      "basefont", "bdo", "big", "blockquote", "body", "br", "button", "caption", "center", "cite", "code", "col",
      "colgroup", "dd", "del", "dir", "div", "dfn", "dl", "dt", "em", "fieldset", "font", "form", "frame",
      "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "hr", "html", "i", "iframe", "img", "input", "ins",
      "isindex", "kbd", "label", "legend", "li", "link", "map", "menu", "meta", "noframes", "noscript", "object",
      "ol", "optgroup", "option", "p", "param", "pre", "q", "s", "samp", "script", "select", "small", "span",
      "strike", "strong", "style", "sub", "sup", "table", "tbody", "td", "textarea", "tfoot", "th", "thead",
      "title", "tr", "tt", "u", "ul", "var", "xmp" };
  private static Set<String> tagset = new HashSet<>();
  static Pattern p;
  static {
    StringBuilder sb = new StringBuilder();
    tagset.addAll(Arrays.asList(allTags));
    for (String s : allTags) {
      if (sb.length() > 0)
        sb.append("|");
      sb.append(s.toUpperCase());
      sb.append("|");
      sb.append(Character.toUpperCase(s.charAt(0)));
      if (s.length() > 1) {
        sb.append(s.substring(1).toLowerCase());
      }
    }
    p = Pattern.compile(sb.toString(), Pattern.MULTILINE | Pattern.DOTALL);
  }
  private final TagNode root;
  private final String src;
  private final String dst;
  private Map<String, String> procesedResoureces = new HashMap<>();

  public XHTMLResourceCollector(TagNode tn2, String srcHtmlFolder, String dst) {
    this.root = tn2;
    this.src = srcHtmlFolder;
    this.dst = dst;
  }

  public Map<String, String> process(TreeNode doc, File file, Map<String, Document> docFiles) {
    return walk(doc, root, file, docFiles);
  }

  private Map<String, String> walk(TreeNode doc, TagNode root2, File file, Map<String, Document> docFiles) {
    Map<String, String> ret = new HashMap<>();
    ret.putAll(proccess(doc, root2, file, docFiles));
    if (root2 != null)
      for (TagNode child : root2.getChildren()) {
        ret.putAll(walk(doc, child, file, docFiles));
      }
    return ret;
  }

  private Map<String, String> proccess(TreeNode doc, TagNode root2, File file, Map<String, Document> docFiles) {
    Map<String, String> ret = new HashMap<>();
    try {
      boolean ch = false;
      if (root2 == null)
        return Collections.emptyMap();
      Map<String, String> attrs = root2.getAttributes();
      Map<String, String> lc = new HashMap<>();
      for (String s : attrs.keySet())
        lc.put(s.toLowerCase(), s);
      if (root2.getName().toLowerCase().equals("frame")) {
        if (lc.get("src") != null) {
          String src = attrs.get(lc.get("src"));
          int ind = src.indexOf("#");
          String asrc = "";
          if (ind > -1) {
            asrc = src.substring(ind);
            src = src.substring(0, ind);
          }
          if (isExternalHref(src))
            return Collections.emptyMap();
          if (Character.isJavaLetterOrDigit(src.charAt(0))) {
            // relative path
            int li = src.lastIndexOf("/");
            if (li > -1) {
              String sind = src.substring(li + 1);
              src = src.substring(0, li + 1);
              int point = sind.lastIndexOf(".");
              String nid = sind.substring(0, point) + ".xhtml";
              attrs.put(lc.get("src"), "../" + src + nid + ResManager.RESOURCES_SUFFIX + "/" + nid);
              ch = true;
            }
          }
          if (!ch) {
            File nf = file.toPath().resolve(src).toFile();
            if (!nf.exists())
              nf = tryFind(file, src);
            if (nf != null && nf.exists()) {
              nf = nf.getCanonicalFile();
              Document doca = docFiles.get(nf.toPath().toString());
              if (doca != null) {
                StringBuilder sb = new StringBuilder("../");// out of doc/res
                if (!doca.getWorkParentUUID().equals(doc.getWorkParentUUID())) {
                  String q1 = doc.getWorkParent().getQualifiedId(),
                      q2 = doca.getWorkParent().getQualifiedId();
                  String rp = null;
                  FileSystem fs = null;
                  
                  try{
                    fs = file.toPath().getFileSystem();
                    rp = CoreUtils.getRelativePath(fs, q1, q2);
                  }finally {
                    if(fs!=null)try {fs.close();}catch(Exception e) {};
                  }
                  if (rp.startsWith("./"))
                    rp = rp.substring(2);
                  sb.append(rp);
                  sb.append("/");
                }
                sb.append(doca.getNodeResourcesFile().getName());
                sb.append("/");
                sb.append(doca.getResourceName());
                if (!asrc.isEmpty())
                  sb.append(asrc);
                attrs.put(lc.get("src"), sb.toString());
                ch = true;
              }
            }
          }
        }
      }
      if (root2.getName().toLowerCase().equals("link")) {
        if (lc.get("type") != null && attrs.get(lc.get("type")).toLowerCase().equals("text/css")) {
          String href = attrs.get(lc.get("href"));
          if (href != null) {
            File f = null;
            InputStream fis = null;
            try {
              String fname = null;
              if (isExternalHref(href)) {
                // TODO http/ftp files loading?
                if (href.startsWith("https") || href.startsWith("ftp")) {
                  URL srcUrl = new URL(href);
                  InputStream in = null;
                  try {
                    in = srcUrl.openConnection().getInputStream();
                    int questPos = href.lastIndexOf("?");
                    if (questPos > -1) {
                      // TODO may be in common case? tends to be not
                      href = href.substring(0, questPos);
                    }
                    fis = in;
                    fname = new File(href).getName();// TODO change?
                  } finally {
                  }
                } else
                  return Collections.emptyMap();
              } else {
                href = CoreUtils.URLDecode(href);
                f = file.toPath().resolve(href).toFile();
                fname = f.getName();
                if (f.exists())
                  fis = getLowerCssStream(f);
              }
              if (fis != null) {
                String rname = "css/" + fname;
                if (doc.isResourceExists(rname)) {
                  int i = 0;
                  while (doc.isResourceExists(rname))
                    rname = "css/" + "(" + (i++) + ")" + fname;
                }
                doc.setResourceContent(rname, fis, OFlag.NONE);
                attrs.put(lc.get("href"), rname);
                ch = true;
              }
            } finally {
              if (fis != null)
                fis.close();
            }
          }
        }
      }
      if (root2.getName().toLowerCase().equals("img")) {
        if (lc.get("src") != null) {
          String old = attrs.get(lc.get("src"));
          String op = procesedResoureces.get(old);
          try {
            String newPath = copyResource(op, doc, old, "imgs");
            procesedResoureces.put(old, newPath);
            ret.put(old, newPath);
            ch = true;
            attrs.put(lc.get("src"), newPath);
          } catch (Exception e) {
            System.err.println(e.getMessage());
          }
        }
        if (lc.get("alt") != null) {
          String old = attrs.get(lc.get("alt"));
          try {
            String newPath = copyResource(procesedResoureces.get(old), doc, old, "imgs");
            procesedResoureces.put(old, newPath);
            ret.put(old, newPath);
            ch = true;
            attrs.put(lc.get("alt"), newPath);
          } catch (Exception e) {
            System.err.println(e.getMessage());
          }
        }
      } else if (root2.getName().toLowerCase().equals("object")) {
        if (lc.get("data") != null) {
          String old = attrs.get(lc.get("data"));
          try {
            String newPath = copyResource(procesedResoureces.get(old), doc, old, "imgs");
            procesedResoureces.put(old, newPath);
            ret.put(old, newPath);
            ch = true;
            attrs.put(lc.get("data"), newPath);
          } catch (Exception e) {
            System.err.println(e.getMessage());
          }
        }
      } else if (root2.getName().toLowerCase().equals("a")) {
        try {
          if (lc.containsKey("href")) {
            Document where = (Document) doc;
            String href = attrs.get(lc.get("href"));
            int ind = href.indexOf("#");
            String asrc = "";
            if (ind > -1) {
              asrc = href.substring(ind);
              href = href.substring(0, ind);
            }
            boolean b = false;
            if (!isExternalHref(href)) {
              if (!href.trim().startsWith("javascript")) {
                try {
                  File f = file.toPath().resolve(href).toFile();
                  Document doca = docFiles.get(f.getCanonicalFile().toPath().toString());
                  if (f.exists()) {
                    if(doca == null)
                    {
                      //Attempt to locate
                      String lhref = href.toLowerCase();
                      if(lhref.endsWith(".htm") || lhref.endsWith(".html"))
                        href = href.substring(0, href.lastIndexOf("."));
                      href = href+".xhtml";
                      TreeNode alt = (Document) doc.getTreeDB().getNode(doc.getWorkParent().getQualifiedId()+"/"+href);
                      if(alt!=null && alt.getType().equals(Document.TYPE_NAME))
                        doca = (Document) alt;
                      
                    }
                    if (doca != null) {
                      StringBuilder sb = new StringBuilder("../");// out of doc/res
                      if (!doca.getWorkParentUUID().equals(where.getWorkParentUUID())) {
                        String q1 = where.getWorkParent().getQualifiedId(),
                            q2 = doca.getWorkParent().getQualifiedId();
                        String rp = null;
                        FileSystem fs = null;
                        try  {
                          fs = file.toPath().getFileSystem();
                          rp = CoreUtils.getRelativePath(fs, q1, q2);
                        }finally{
                          if(fs!=null)
                            try{
                              fs.close();
                            }catch(Exception e){}
                        }
                        if (rp.startsWith("./"))
                          rp = rp.substring(2);
                        if (rp.startsWith("/"))
                          rp = rp.substring(1);
                        sb.append(rp);
                        if (!rp.endsWith("/"))
                          sb.append("/");
                      }
                      sb.append(doca.getNodeResourcesFile().getName());
                      sb.append("/");
                      sb.append(doca.getResourceName());
                      if (!asrc.isEmpty())
                        sb.append(asrc);
                      attrs.put(lc.get("href"), sb.toString());
                      ch = true;
                      b = true;
                    }
                  }
                } catch (InvalidPathException ipe) {
                  RLogger.logDebug(
                      "Incorrect path during resource processing, tends to be external resource "
                          + href,
                      6);
                  RLogger.logDebug(ipe, 6);
                }
              }
              if (!b) {
                // frame-related behavior?
                int sp = href.lastIndexOf("/");
                String name = "";
                if (sp > -1) {
                  name = href.substring(sp+1);
                  href = href.substring(0, sp);
                } else {
                  name = href;
                  href = "";
                }
                int point = name.indexOf(".");
                if (point > -1)
                  name = name.substring(0, point);
                name = name + ".xhtml";
                String path = "../" + (href.isEmpty() ? "" : (href + "/")) + name
                    + ResManager.RESOURCES_SUFFIX + "/" + name;
                attrs.put(lc.get("href"), path);
                ch = true;
              }
            }
            // String nm = f.getName();
            // int ex = nm.lastIndexOf(".");
            // int hi = href.lastIndexOf("/");
            // String sp = "";
            // if(hi>-1)
            // sp = href.substring(0,hi);
            // if(ex>-1)
            // nm = nm.substring(0,ex)+".xhtml";
            // else
            // nm = nm+".xhtml";
            // if(ex>-1){
            // attrs.put(lc.get("href"), "../"+sp+"/"+nm+"_resources/"+nm);
            // ch = true;
            // }
          }
          // if ("stylesheet".equals(attrs.get("rel"))
          // && attrs.get("href") != null
          // && !procesedResoureces.contains(attrs.get("href"))) {
          // String old = attrs.get("href");
          // procesedResoureces.add(old);
          // String newPath;
          // newPath = copyResource(doc, attrs.get("href"), "css");
          // ret.put(old, newPath);
          // ch = true;attrs.put("href", newPath);
          // }
        } finally {

        }
      } else if (root2.getName().toLowerCase().equals("script")) {
        if (lc.get("src") != null) {
          String old = attrs.get(lc.get("src"));
          String newPath = copyResource(procesedResoureces.get(old), doc, old, "js");
          procesedResoureces.put(old, newPath);
          ret.put(old, newPath);
          ch = true;
          attrs.put(lc.get("src"), newPath);
        }
      }
      if (ch)
        root2.setAttributes(attrs);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return ret;
  }

  private boolean isExternalHref(String src2) {
    int pointsPos = src2.indexOf(":");
    if (pointsPos > -1) {
      String substr = src2.substring(0, pointsPos);
      if (substr.length() > 2 && !substr.toLowerCase().equals("file"))// Tends that most disks are mostly 2 char
        return true;
      return false;
    }
    return false;
  }

  private FileInputStream getLowerCssStream(File f) {
    FileInputStream fr;
    try {
      final Charset ch = StandardCharsets.UTF_8;
      byte brstart = ch.encode("{").get(), brend = ch.encode("}").get(), brstart2 = ch.encode("[").get(),
          brend2 = ch.encode("]").get(), dot = ch.encode(".").get(), sharp = ch.encode("#").get();
      fr = new FileInputStream(f) {
        @Override
        public int read(byte[] b) throws IOException {
          return read(b, 0, b.length);
        }

        StringBuilder sb = new StringBuilder();
        short brlevel = 0;
        boolean dotstart = false, sharpstart = false;
        int extraoffset = 0, extralen = -1;
        String oTag = null;
        String tagName = null;
        int ts = -1, ic = -1, cq = 0;

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          int startoff = off;
          if (oTag != null && oTag.length() > 0)
            sb.append(oTag);

          int read = super.read(b, off, len);

          int ret = read == -1 ? extralen : extralen != -1 ? (read + extralen) : read;
          if (ret == -1)
            return ret;
          extralen = -1;
          for (int i = startoff; i < read; i++) {
            if (b[i] == brstart || b[i] == brstart2)
              brlevel++;
            if (b[i] == brend || b[i] == brend2)
              brlevel--;
            if (b[i] == dot && brlevel == 0)
              dotstart = true;
            if (brlevel == 0 && b[i] == sharp)
              sharpstart = true;
            Character c = (char) b[i];
            boolean isName = Character.isLetterOrDigit(c) || c == '_';
            if (brlevel == 0) {
              if (isName)
                sb.append(c);
            }
            if (!isName) {
              if (!sharpstart && !dotstart) {
                String tag = sb.toString();
                tag = tag.toLowerCase();
                if (tagset.contains(tag)) {
                  for (int f = 0; f < tag.length(); f++)
                    b[i - tag.length() + f] = ch.encode(Character.toString(tag.charAt(f))).get();
                }
              }
              sb.setLength(0);
            }
          }
          if (sb.length() > 0)
            oTag = sb.toString();
          extralen = -1;
          extraoffset = 0;
          int diff = ret - len;
          if (diff > 0) {
            extralen = diff;
            extraoffset = 0;
            // some stream part was cut
          }

          return ret;
        }

        @Override
        public int read() throws IOException {
          throw new EOFException();
        }

        @Override
        public int available() throws IOException {
          // TODO Auto-generated method stub
          return super.available();
        }
      };
      return fr;
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  private File tryFind(File file, String src2) {
    file = file.getParentFile();
    if (file == null)
      return file;
    File f2 = file.toPath().resolve(src2).toFile();
    if (f2.exists())
      return f2;
    return tryFind(file, src2);
  }

  private String copyResource(String op, TreeNode doc, String encodedDocImgSrc, String subfolder) throws Exception {
    String docImgSrc = java.net.URLDecoder.decode(encodedDocImgSrc, "utf-8");
    String imgNewPath = (dst.isEmpty() ? "" : (dst + "/")) + subfolder + "/";
    String fileName = new File(docImgSrc).getName();
    String dstFile = getDstFileName(doc, imgNewPath, fileName);
    // new File(imgNewPath).mkdirs();
    if (op == null) {
      if (docImgSrc.startsWith("data:")) {
        String ext = "wmf";
        if (docImgSrc.substring(4).startsWith(":image/")) {
          int nextSeq = docImgSrc.indexOf(";", 11);
          if ((nextSeq - 11) < 1 || (nextSeq - 11) > 20)
            return encodedDocImgSrc;// strange extension
          ext = docImgSrc.substring(11, nextSeq);
        } else {

        }

        String[] baseParts = docImgSrc.split(",");
        byte[] content = Base64.getDecoder().decode(docImgSrc);
        String name = "dataImage", name2 = name;
        int cnt = 2;
        while (doc.hasResource("imgs/" + name2 + "." + ext)) {
          name2 = name + cnt;
          cnt++;
        }
        doc.setResourceContent("imgs/" + name2 + "." + ext, new ByteArrayInputStream(content), OFlag.NONE);
      } else if (docImgSrc.startsWith("http:/") || docImgSrc.startsWith("https:/")
          || docImgSrc.startsWith("ftp:/")) {
        URL srcUrl = new URL(docImgSrc);
        InputStream in = srcUrl.openConnection().getInputStream();
        try {
          int questPos = dstFile.lastIndexOf("?");
          if (questPos > -1) {
            // TODO may be in common case? tends to be not
            dstFile = dstFile.substring(0, questPos);
          }
          doc.setResourceContent(dstFile, in, OFlag.NONE);
          // FileOutputStream out = new FileOutputStream(URLDecoder.decode(dstFile,
          // "utf-8"));
          // int inp;
          // while ((inp = in.read()) != -1) {
          // out.write(inp);
          // }
          // out.close();
        } finally {
          in.close();
        }
      } else if (docImgSrc.startsWith("file:/")) {
        FileInputStream fis2 = new FileInputStream(new File(new URI(encodedDocImgSrc)));
        try {
          doc.setResourceContent(dstFile, fis2, OFlag.NONE);
        } finally {
          fis2.close();
        }
        // RequalityCLI.copyfile(null, new URI(encodedDocImgSrc), new
        // File(URI.create(dstFile)).toURI());
      } else {
        String sourcePath = src + "/" + docImgSrc;
        File sf = new File(sourcePath);
        if (!sf.exists())
          sf = new File(docImgSrc);
        FileInputStream fis2 = new FileInputStream(sf);
        try {
          doc.setResourceContent(dstFile, fis2, OFlag.NONE);
        } finally {
          fis2.close();
        }
        // RequalityCLI.copyfile(null, sf.toURI(), new
        // File(URI.create(dstFile)).toURI());
      }
    } else
      dstFile = op;
    return dstFile;
  }

  private String getDstFileName(TreeNode doc, String imgNewPath, String name) {
    if (!doc.hasResource(imgNewPath + name)) {
      return imgNewPath + name;
    }
    do {
      name = "_" + name;
    } while (doc.hasResource(imgNewPath + name));
    return imgNewPath + name;
  }
}
