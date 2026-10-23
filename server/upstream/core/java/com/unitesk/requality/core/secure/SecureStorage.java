package com.unitesk.requality.core.secure;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JOptionPane;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.tool.CoreUtils;

public class SecureStorage {
	private static final String FILE_START = "settingsFile_0.1";
	private static String SEPARATOR="||";
//	private TreeDB db;
	private PassProvider somepassprovider;
	private File storage;
	private SecureDataManager sdm;
//	private static Map<TreeDB, SecureStorage> map = new HashMap<>();
	private static PassProvider DEFAULT_PASS_PROVIDER = new PassProvider() {
		@Override
		public char[] getPass() {
			return SecureStorage.getPass();
		}
		
	};
	public interface PassProvider{
		public char[] getPass();
	}
	public SecureStorage(TreeDB db, PassProvider p) {
//		this.db = db;
		somepassprovider = p;
		Path cd = CoreUtils.getCacheDir(db);
		if(cd==null)
			return;
		storage = cd.resolve("secStore.db").toFile();
		sdm = new SecureDataManager(db.getTracker());
		sdm.init(p);
	}
	public SecureStorage(TreeDB db) {
//		this.db = db;
		Path cd = CoreUtils.getCacheDir(db);
		if(cd==null)
			return;
		storage = cd.resolve("secStore.db").toFile();
		sdm = new SecureDataManager(db.getTracker());
		if(somepassprovider!=null)
		  sdm.init(somepassprovider);
	}
	private static String META_SECURE_STORAGE = "SECURE_STORAGE";
	public static SecureStorage getOne(TreeDB db, PassProvider p) {
		SecureStorage s = db.getMetaObject(META_SECURE_STORAGE);
		if(s != null)
			return s;
		s = new SecureStorage(db,p);
		db.registerMetaObject(META_SECURE_STORAGE, s);
		return s;
	}
	public static SecureStorage getOneWithDefPass(TreeDB db) {
		return getOne(db, DEFAULT_PASS_PROVIDER);
	}
	public static SecureStorage getOne(TreeDB db) {
		SecureStorage s = db.getMetaObject(META_SECURE_STORAGE);
		if(s != null)
			return s;
		s = new SecureStorage(db);
		db.registerMetaObject(META_SECURE_STORAGE, s);
		return s;
	}
	public void saveValue(String key, String value) {
		String seed = sdm.encodeNSync(somepassprovider.getPass(), FILE_START);
		Map<String,byte[]> saved = new HashMap<>();
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(Files.newInputStream(storage.toPath(), StandardOpenOption.READ));
//				saved = Files.readAllLines(storage.toPath());
			String st = dis.readUTF();
			if(st.equals(seed)) {
				int len = dis.readInt();
				for(int i = 0;i<len;i++) {
				  String rkey = dis.readUTF();
  				  int l = dis.readInt();
				  byte[] b = dis.readNBytes(l);
				  saved.put(rkey, b);
				}
			}
		} catch (IOException e) {
		}
		finally {
		  if(dis!=null)
			try {
			  dis.close();
			} catch (IOException e) {
			  e.printStackTrace();
			}
		}
		String keya = sdm.encodeNSync(somepassprovider.getPass(), key);
		byte[] newv = sdm.encodeSync(somepassprovider.getPass(), value.getBytes(StandardCharsets.UTF_8));
		saved.put(keya,newv);
		
		try {
			Files.deleteIfExists(storage.toPath());
			FileOutputStream fw = null;
			DataOutputStream dos = null;
			try {
			  fw = new FileOutputStream(storage.toPath().toFile());
			  dos = new DataOutputStream(fw);
			  dos.writeUTF(seed);
			  dos.writeInt(saved.keySet().size());
			  for(Entry<String, byte[]> en:saved.entrySet()) {
				dos.writeUTF(en.getKey());
				byte[]val = en.getValue();
				dos.writeInt(val.length);
				dos.write(val);
			  }
			}
			finally {
			  if(dos!=null)
			    dos.close();
			  if(fw!=null)
			    fw.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
	public String getValue(String key, String defvalue) {
		String seed = sdm.encodeNSync(somepassprovider.getPass(), FILE_START);
		InputStream fis = null;
		DataInputStream dis = null;
		try {
			fis = Files.newInputStream(storage.toPath(), StandardOpenOption.READ);
			dis = new DataInputStream(fis);
//			saved = Files.readAllLines(storage.toPath());
			String s = dis.readUTF();
			if(s.equals(seed)) {
				int len = dis.readInt();
				String keya = sdm.encodeNSync(somepassprovider.getPass(), key);
				for(int i = 0;i<len;i++) {
					String rkey = dis.readUTF();
					int l = dis.readInt();
					byte[] b = dis.readNBytes(l);
					if(rkey.equals(keya)) {
						return sdm.decode(somepassprovider.getPass(), b);
					}
				}
			}
		} catch (IOException e) {
		}
		finally {
		  if(dis!=null)
			try {
			  dis.close();
			} catch (IOException e) {
			  e.printStackTrace();
			}
		  if(fis!=null)
			try {
				fis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return defvalue;
		
	}
	private static Object sync = "secureStorageLock";
	private static char[] pass = null;
	private static char[] getPass() {
//		TODO proper pass init
		synchronized (sync) {
			if(pass == null) {
				String s = (String)JOptionPane.showInputDialog("Please enter a pass");
				pass = s.toCharArray();
			}
			return pass;
		}
	}
}

