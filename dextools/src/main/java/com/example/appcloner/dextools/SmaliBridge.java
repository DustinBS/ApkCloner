package com.example.appcloner.dextools;

public class SmaliBridge {

    public static boolean disassemble(String dexPath, String outDir) {
        try {
            try {
                Class<?> bk = Class.forName("org.jf.baksmali.Main");
                java.lang.reflect.Method m = bk.getMethod("main", String[].class);
                m.invoke(null, (Object) new String[]{"-o", outDir, dexPath});
                return true;
            } catch (ClassNotFoundException cnf) {
                try {
                    Class<?> bk2 = Class.forName("baksmali.Main");
                    java.lang.reflect.Method m2 = bk2.getMethod("main", String[].class);
                    m2.invoke(null, (Object) new String[]{"-o", outDir, dexPath});
                    return true;
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean assemble(String smaliDir, String outDex) {
        try {
            try {
                Class<?> sm = Class.forName("org.jf.smali.Main");
                java.lang.reflect.Method m = sm.getMethod("main", String[].class);
                m.invoke(null, (Object) new String[]{"assemble", "-o", outDex, smaliDir});
                return true;
            } catch (ClassNotFoundException cnf) {
                try {
                    Class<?> sm2 = Class.forName("smali.Main");
                    java.lang.reflect.Method m2 = sm2.getMethod("main", String[].class);
                    m2.invoke(null, (Object) new String[]{"assemble", "-o", outDex, smaliDir});
                    return true;
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}
