package com.unitesk.requality.core.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.tool.CoreUtils;

public class StaticFilePaths {
	public static Map<String, File> paths = new HashMap<>();
	private static boolean inited = false;
	public static void initIfNot() {
		if(inited)
			return;
		String path = ResManager.getManager().getJSResourcesPath();
		File f = new File(path);
		if(f.exists()) 
			for(File fc: f.listFiles()) 
				recAdd(fc, "");
		
		inited = true;
	}
	private static void recAdd(File f, String path) {
		if(f.isDirectory()) {
			for(File fc:f.listFiles())
				recAdd(fc, path+"/"+CoreUtils.URLEncode(f.getName()));
		}else 
			paths.put(path+"/"+CoreUtils.URLEncode(f.getName()), f);
	}
}
