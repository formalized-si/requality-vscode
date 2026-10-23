package com.unitesk.requality;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Templates {
    private static final String CLASSPATH_PREFIX = "META-INF/com.unitesk.requality/templates/";

    public static void extractToDirectory(File f) throws IOException {
        URL ownPath = Templates.class.getProtectionDomain().getCodeSource().getLocation();

        byte[] copyBuffer = new byte[1024];
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(ownPath.openStream())) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.getName().startsWith(CLASSPATH_PREFIX) && !entry.isDirectory()) {
                    String strippedName = entry.getName().substring(CLASSPATH_PREFIX.length());
                    File targetFile = new File(f, strippedName);

                    File parentFile = targetFile.getParentFile();
                    if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs())
                        throw new IOException("Cannot make directory " + parentFile.getCanonicalPath());

                    try (FileOutputStream out = new FileOutputStream(targetFile)) {
                        int r;
                        while ((r = zis.read(copyBuffer)) != -1) {
                            out.write(copyBuffer, 0, r);
                        }
                    }

                    count++;
                }

                entry = zis.getNextEntry();
            }
        }
    }
}
