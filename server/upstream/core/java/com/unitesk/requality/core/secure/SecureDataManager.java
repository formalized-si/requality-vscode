package com.unitesk.requality.core.secure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.unitesk.requality.core.secure.SecureStorage.PassProvider;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.tool.CoreUtils;

public class SecureDataManager {
	boolean inited = false;
	private Cipher c;
	private SecretKeyFactory f;
	private Charset cs = StandardCharsets.UTF_8;
	private byte[] someSalt = null;
    private static final int ITERATIONS = 65536;
    private static final int ITERATIONS2 = 128;
    private static final int KEY_LENGTH = 512;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
	private static final SecureRandom RAND = new SecureRandom();
    public static Optional<byte[]> generateSalt (final int length) {
      if (length < 1) {
        System.err.println("error in generateSalt: length must be > 0");
        return Optional.empty();
      }
      byte[] salt = new byte[length];
      RAND.nextBytes(salt);
      return Optional.of(salt);
    }
    public SecureDataManager(IResTracker tracker) {
      Path loc = CoreUtils.getCacheDir(tracker.getGlobalCacheLocation());
      File salt = null;
      if(loc!=null)  
        salt = loc.resolve("salt").toFile();
      if(salt == null || !salt.exists()) {
          Optional<byte[]>salta = generateSalt(KEY_LENGTH);
          if(salta.isEmpty())
              throw new RuntimeException("Cannot get salt! Try to get pepper or put salt by hand in global cache location");
          someSalt = salta.get();
          try {
            if(salt!=null)
              Files.write(salt.toPath(),someSalt);
          } catch (IOException e) {
              e.printStackTrace();
          }
          
      }else{
          try {
              someSalt = Files.readAllBytes(salt.toPath());
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
    }


    public static Optional<String> hashPassword (char[] chars, byte[] bytes) {

      PBEKeySpec spec = new PBEKeySpec(chars, bytes, ITERATIONS, KEY_LENGTH);

      //Arrays.fill(chars, Character.MIN_VALUE);

      try {
        SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] securePassword = fac.generateSecret(spec).getEncoded();
        return Optional.of(Base64.getEncoder().encodeToString(securePassword));

      } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
        System.err.println("Exception encountered in hashPassword()");
        return Optional.empty();

      } finally {
        spec.clearPassword();
      }
    }
    public static boolean verifyPassword (char[] password, String key, byte[] salt) {
      Optional<String> optEncrypted = hashPassword(password, salt);
      if (!optEncrypted.isPresent()) return false;
      return optEncrypted.get().equals(key);
    }


	public void init(PassProvider pp){
//		if(!inited) {
//			if(verifyPassword(pass, "storeKey", someSalt))
//				inited = true;
//		}
	}
	public static void main(String[]args) {
	}


	public String decode(char[] somepass, byte[] substring) {
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
	        cipher.init(Cipher.DECRYPT_MODE, createSecretKey(hashPassword(somepass, someSalt).get().getBytes()));
	        return new String(cipher.doFinal(substring),cs);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
		return null;
	}
	public byte[] encodeSync(char[] somepass, byte[] substring) {
    	try {
		  Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		  cipher.init(Cipher.ENCRYPT_MODE, createSecretKey(hashPassword(somepass, someSalt).get().getBytes()));
          return cipher.doFinal(substring);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
		return null;
	}
	public String encodeNSync(char[] somepass,String substring) {
	  char[]full = new char[somepass.length+substring.length()];
	  int i = 0;
	  for(char c:somepass)
	    full[i++]=c;
	  for(int j=0;j<substring.length();j++)
	    full[i++]=(char)substring.charAt(j);
      PBEKeySpec spec = new PBEKeySpec(full, someSalt, ITERATIONS2, KEY_LENGTH);
      try {
        SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] securePassword = fac.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(securePassword);

      } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
        System.err.println("Exception encountered in hashPassword()");
        return null;

      } finally {
        spec.clearPassword();
      }
	}


	private SecretKeySpec createSecretKey(byte[]sec)
	{
	    SecretKeySpec sks    = null;
	    try {
	        MessageDigest md;
	        byte[]        key;
	        md = MessageDigest.getInstance("SHA-1");
	        key = md.digest(sec);
	        key = Arrays.copyOf(key, 16);
	        sks = new SecretKeySpec(key, "AES");
	    } catch (NoSuchAlgorithmException e) { }    	
	    return sks;
	}
	

}
