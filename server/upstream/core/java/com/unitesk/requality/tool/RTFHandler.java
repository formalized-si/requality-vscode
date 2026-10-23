package com.unitesk.requality.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.apache.commons.codec.binary.Hex;
import com.unitesk.requality.core.log.RLogger;

public class RTFHandler {
  private static String docstart = "d0cf11e0";

  private static byte getHex(char c) throws Exception {
    if (c >= '0' && c <= '9') return (byte) (c - '0');

    if (c >= 'a' && c <= 'f') return (byte) (10 + (c - 'a'));

    if (c >= 'A' && c <= 'F') return (byte) (10 + (c - 'A'));

    throw new Exception("invalid char");
  }

  public static void extractOLE(File file, String path, String path2) throws Exception {
    StringBuffer sb = new StringBuffer();
    FileReader fr = new FileReader(file);
    BufferedReader br = new BufferedReader(fr);
    while (br.ready()) {
      sb.append(br.readLine());
    }
    br.close();
    fr.close();
    extractDoc(sb.toString(), path);
    extractWMF(sb.toString(), path2);
  }

  public static void extractWMF(File rtf, String path) throws FileNotFoundException {
    FileReader fr = new FileReader(rtf);
    BufferedReader br = new BufferedReader(fr);
    File f = new File(path);

    FileOutputStream fos = new FileOutputStream(f);
    FileChannel fc = fos.getChannel();
    try {
      byte started = 0;
      String th = null;
      boolean high = true;
      while (br.ready()) {
        String s = br.readLine();
        String st = s;
        int start = 0;
        if (started == 0) {
          start = s.indexOf("wmetafile8");
          if (start > -1) {
            started = 1;
            start += 10;
            if (s.indexOf(" ", start) > -1 && started == 1) {
              started = 2;
              start = s.indexOf(" ", start);
            }
            if (start < s.length()) th = s.substring(start, s.length());
          }
        } else if (started >= 1) started = 3;

        if (started > 2) {
          if (s.indexOf("}") > -1) {
            th = s.substring(0, s.indexOf("}"));
          } else {
            th = s;
          }
        }
        if (th == null || started == 0) continue;
        th = th.replace("\n", "");
        th = th.replace("\r", "");
        th = th.replace(" ", "");
        int pos = start;
        byte[] temp = new byte[100];
        short bpos = -1;
        byte b = 0;
        boolean ignore = false;
        for (int i = 0; i < th.length(); i++) {
          char c = th.charAt(i);
          if (c == '\\') ignore = true;
          if (Character.isWhitespace(c)) {
            ignore = false;
            continue;
          }
          if (ignore) continue;
          if (high) b = (byte) (16 * getHex(c));
          else {
            b = (byte) (b | getHex(c));
            bpos++;
            temp[bpos] = b;
            if (bpos == 99) {
              bpos = -1;
              fc.write(ByteBuffer.wrap(temp));
            }
          }
          high = !high;
        }
        if (bpos > -1) fc.write(ByteBuffer.wrap(Arrays.copyOf(temp, bpos + 1)));
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        br.close();
      } catch (IOException e) {e.printStackTrace();}
      try {
        fc.close();
      } catch (IOException e) {e.printStackTrace();}
      try {
        fos.close();
      } catch (IOException e) {e.printStackTrace();}
      try {
        fr.close();
      } catch (IOException e) {e.printStackTrace();}
    }
  }

  public static void extractWMF(String rtf, String path) {
    File f = new File(URI.create(path));
    if(!f.exists()){
      if(!f.getParentFile().exists())
        f.getParentFile().mkdirs();
      try {
        f.createNewFile();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    try {
      int start = rtf.indexOf("\\wmetafile8");
      if (start > -1) start = rtf.indexOf(' ', start);
      if (start > -1) {
        int end = rtf.indexOf("}", start);
        if (end == -1) end = rtf.length();
        FileOutputStream fos = new FileOutputStream(f);
        FileChannel fc = fos.getChannel();
        int pos = start;
        boolean high = true;
        byte[] temp = new byte[100];
        short bpos = -1;
        byte b = 0;
        while (pos < end) {
          char c = rtf.charAt(pos);
          pos++;
          if (!Character.isAlphabetic(c)&&!Character.isDigit(c))
            continue;
          if (high) b = (byte) (16 * getHex(c));
          else {
            b = (byte) (b | getHex(c));
            bpos++;
            temp[bpos] = b;
            if (bpos == 99) {
              bpos = -1;
              fc.write(ByteBuffer.wrap(temp));
            }
          }
          high = !high;
        }
        if(high){
          b = (byte) (b | getHex('0'));
          bpos++;
          temp[bpos] = b;
          if (bpos == 99) {
            bpos = -1;
            fc.write(ByteBuffer.wrap(temp));
          }
        }
        if (bpos > -1){
          fc.write(ByteBuffer.wrap(Arrays.copyOf(temp, bpos+1)));
        }
        fc.close();
        fos.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void extractDoc(String rtf, String path) throws Exception {
    File f = new File(path);
    try {
      int start = rtf.indexOf(docstart);
      if (start < 0) throw new Exception("rtf does not contain an word doc");
      int end = rtf.indexOf("}", start);
      if (end == -1) end = rtf.length();
      FileOutputStream fos = new FileOutputStream(f);
      FileChannel fc = fos.getChannel();
      int pos = start;
      boolean high = true;
      byte[] temp = new byte[100];
      short bpos = -1;
      byte b = 0;
      while (pos < end) {
        char c = rtf.charAt(pos);
        pos++;
        if (Character.isWhitespace(c)) continue;
        if (high) b = (byte) (16 * getHex(c));
        else {
          b = (byte) (b | getHex(c));
          bpos++;
          temp[bpos] = b;
          if (bpos == 99) {
            bpos = -1;
            fc.write(ByteBuffer.wrap(temp));
          }
        }
        high = !high;
      }
      if (bpos > -1) fc.write(ByteBuffer.wrap(Arrays.copyOf(temp, bpos)));
      fc.close();
      fos.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static void DOCToHex(File file, BufferedWriter bw) {
    // StringBuffer sb = new StringBuffer();
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(file);
      FileChannel ch = fis.getChannel();
      ByteBuffer buffer = ByteBuffer.allocate(100);
      int noOfBytesRead = ch.read(buffer);
      int cnt = 0;
      while (noOfBytesRead != -1) {
        RLogger.logDebug("Number of bytes read: " + noOfBytesRead);
        buffer.flip();
        while (buffer.hasRemaining()) {
          bw.write(Hex.encodeHexString(new byte[] {buffer.get()}));
          cnt += 2;
          if (cnt == 78) {
            bw.write("\r\r\n");
            cnt = 0;
          }
        }
        buffer.clear();
        noOfBytesRead = ch.read(buffer);
      }
      // ch.close();
      // ByteBuffer bb = ByteBuffer.allocate(100);
      //
      // while(ch.isOpen()){
      // int cnt = ch.read(bb,0);
      // byte[]res=bb.array();
      // sb.append(Hex.encodeHexString(res));
      // }
      ch.close();
      fis.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(fis!=null)
          fis.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
}
