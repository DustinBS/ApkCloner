package com.example.appcloner.dextools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TestSmaliBridge {

    // Simple test stub used by unit tests to emulate disassemble/assemble behavior.
    public static boolean disassemble(String dexPath, String outDir) {
        try {
            File out = new File(outDir);
            out.mkdirs();
            File sm = new File(out, "Test.smali");
            String content = "Lcom/example/orig/Test;\n";
            Files.write(sm.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean assemble(String smaliDir, String outDex) {
        try {
            File dir = new File(smaliDir);
            StringBuilder sb = new StringBuilder();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        sb.append(new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
            File out = new File(outDex);
            out.getParentFile().mkdirs();
            Files.write(out.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}
