/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.verona;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Identifies usage of potentially problematic system properties.
 * See <a href="http://openjdk.java.net/projects/verona/">JEP223</a> for more information.
 * Relies on Java 8 and below class file format.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class Main {

    private static final String[] JAR_EXTS = { ".jar" };
    private static final String CLASS_EXT = ".class";
    private static final String[] PROPERTIES = {
            "java.version",
            "java.runtime.version",
            "java.vm.version",
            "java.specification.version",
            "java.vm.specification.version",
    };

    private static final FileFilter JAR_FILTER = new FileFilter() {
        @Override
        public boolean accept(final File f) {
            return f.isDirectory() || isJarFile(f);
        }
    };

    private Main() {
        // forbidden instantiation
    }

    public static void main(final String... args) throws IOException {
        if (args == null || args.length == 0) {
            System.out.println("This tool identifies potentially problematic code from JDK9 migration point of view.");
            System.out.println("Usage: " + Main.class.getName() + " [ classFile | jarArchive | jarsDir ]+");
            return;
        }
        for (final String dir : args) {
            final File f = new File(dir);
            if (f.isDirectory()) {
                processDir(f);
            } else {
                processJar(f);
            }
        }
    }

    private static void processDir(final File dir) throws IOException {
        if (!dir.exists()) throw new IllegalArgumentException("Directory '" + dir.getAbsolutePath() + "' does not exist");
        File[] candidates = dir.listFiles(JAR_FILTER);
        FileInputStream fis = null;
        for (final File candidate : candidates) {
            if (candidate.isDirectory()) {
                processDir(candidate);
            } else {
                if (isJarFile(candidate)) {
                    processJar(candidate);
                } else {
                    if (candidate.getName().endsWith(CLASS_EXT)) {
                        try {
                            fis = new FileInputStream(candidate);
                            findStrings(candidate.getParentFile().getAbsolutePath(), fis, PROPERTIES, true);
                        } finally {
                            safeClose(fis);
                        }
                    }
                }
            }
        }
    }

    private static void processJar(final File file) throws IOException {
        final JarInputStream jis = new JarInputStream(new FileInputStream(file));
        try {
            JarEntry jarEntry;
            String jarEntryName;
            while ((jarEntry = jis.getNextJarEntry()) != null) {
                if (jarEntry.isDirectory()) continue;
                jarEntryName = jarEntry.getName();
                if (!jarEntryName.endsWith(CLASS_EXT)) continue;
                findStrings(file.getAbsolutePath(), jis, PROPERTIES, false);
            }
        } finally {
            safeClose(jis);
        }
    }

    private static void findStrings(final String archivePath, final InputStream is, final String[] strings, final boolean exploded) throws IOException {
        final DataInputStream dis = new DataInputStream(is);
        if (dis.readInt() != 0xCAFEBABE) throw new IllegalArgumentException("Invalid magic");
        dis.readUnsignedShort(); // ignore minor version
        if (dis.readUnsignedShort() > 52) throw new IllegalArgumentException("Unsupported class file version");
        final Object[] pool = new Object[dis.readUnsignedShort()];
        int tag;
        for (int i = 1; i < pool.length; i++) {
            tag = dis.readUnsignedByte();
            //
            // processed constant pool items
            //
            if (tag == 1) {
                // CONSTANT_UTF8
                byte[] bytes = new byte[dis.readUnsignedShort()];
                int count = 0;
                int current;
                while (count != bytes.length) {
                    current = dis.read(bytes, count, bytes.length - count);
                    if (current > 0) {
                        count += current;
                    }
                }
                pool[i] = new String(bytes);
            }
            else if (tag == 7) {
                // CONSTANT_Class
                pool[i] = dis.readUnsignedShort();
            }
            //
            // ignored constant pool items
            //
            else if (tag == 3) {
                // CONSTANT_Integer
                dis.readInt();
            }
            else if (tag == 4) {
                // CONSTANT_Float
                dis.readFloat();
            }
            else if (tag == 5) {
                // CONSTANT_Long
                dis.readLong();
                i++;
            }
            else if (tag == 6) {
                // CONSTANT_Double
                dis.readDouble();
                i++;
            }
            else if (tag == 8) {
                // CONSTANT_String
                dis.readUnsignedShort();
            }
            else if (tag == 9) {
                // CONSTANT_Fieldref
                dis.readUnsignedShort();
                dis.readUnsignedShort();
            }
            else if (tag == 10) {
                // CONSTANT_Methodref
                dis.readUnsignedShort();
                dis.readUnsignedShort();
            }
            else if (tag == 11) {
                // CONSTANT_InterfaceMethodref
                dis.readUnsignedShort();
                dis.readUnsignedShort();
            }
            else if (tag == 12) {
                // CONSTANT_NameAndType
                dis.readUnsignedShort();
                dis.readUnsignedShort();
            }
            else if (tag == 15) {
                // CONSTANT_MethodHandle
                dis.readUnsignedByte();
                dis.readUnsignedShort();
            }
            else if (tag == 16) {
                // CONSTANT_MethodType
                dis.readUnsignedShort();
            }
            else if (tag == 18) {
                // CONSTANT_InvokeDynamic
                dis.readUnsignedShort();
                dis.readUnsignedShort();
            }
            else throw new IllegalStateException();
        }

        dis.readUnsignedShort(); // ignore class flags
        // process class name
        final int classNameIndex = (int) pool[dis.readUnsignedShort()];
        final String className = (String) pool[classNameIndex];

        // inspect constant pool
        Collection<String> found = new HashSet<>();
        String candidate;
        for (int i = 1; i < pool.length; i++) {
            if (pool[i] != null && pool[i] instanceof String) {
                candidate = (String) pool[i];
                for (final String s : strings) {
                    if (candidate.equals(s)) {
                        found.add(candidate);
                    }
                }
            }
        }

        if (!found.isEmpty()) {
            final String suffix = (found.size() > 1 ? "contains strings " : "contains string ") + found;
            System.out.println((exploded ? "Directory '" : "Archive '") + archivePath + "', class '" + className + "', " + suffix);
        }
    }

    private static boolean isJarFile(final File f) {
        final String fileName = f.getName();
        for (final String jarExt : JAR_EXTS) {
            if (fileName.endsWith(jarExt)) return true;
        }
        return false;
    }

    private static void safeClose(final Closeable c) {
        if (c != null) try { c.close(); } catch (final IOException ignored) {}
    }

}
