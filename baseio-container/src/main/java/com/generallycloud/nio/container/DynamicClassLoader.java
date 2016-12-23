/*
 * Copyright 2015 GenerallyCloud.com
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 
package com.generallycloud.nio.container;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.generallycloud.nio.common.FileUtil;
import com.generallycloud.nio.common.Logger;
import com.generallycloud.nio.common.LoggerFactory;
import com.generallycloud.nio.common.LoggerUtil;

public class DynamicClassLoader extends ClassLoader {

	private Logger					logger		= LoggerFactory.getLogger(DynamicClassLoader.class);
	private Map<String, ClassEntry>	clazzEntries	= new HashMap<String, ClassEntry>();
	private ClassLoader				parent;
	private ClassLoader				systemClassLoader;

	public DynamicClassLoader() {

		ClassLoader parent = getParent();

		if (parent == null) {
			parent = getSystemClassLoader();
		}

		this.parent = parent;

		this.systemClassLoader = this.getClass().getClassLoader();
	}

	private Class<?> findLoadedClass0(String name) throws ClassNotFoundException {

		ClassEntry entry = clazzEntries.get(name);

		if (entry == null) {
			return null;
		}

		if (entry.loadedClass == null) {
			return defineClass(entry);
		}

		return entry.loadedClass;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {

		Class<?> clazz = findLoadedClass0(name);

		if (clazz == null) {

			clazz = defineClass(name);

			if (clazz == null) {

				try {
					return parent.loadClass(name);
				} catch (Exception e) {
					return systemClassLoader.loadClass(name);
				}
			}
		}

		return clazz;
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {

		Class<?> clazz = findLoadedClass0(name);

		if (clazz == null) {
			return this.parent.loadClass(name);
		}

		return clazz;

	}

	public void scan(String file) throws IOException {
		this.scan(new File(file));
	}

	public void scan(File file) throws IOException {
		this.scan0(file);
		LoggerUtil.prettyNIOServerLog(logger, "预加载 class 字节码到缓存[ {} ]个 ", clazzEntries.size());
	}

	private void scan0(File file) throws IOException {
		if (file.exists()) {
			if (file.isDirectory()) {
				File[] files = file.listFiles();
				for (File _file : files) {
					scan0(_file);
				}
			} else {
				String fileName = file.getName();
				if (fileName.endsWith(".class")) {
					LoggerUtil.prettyNIOServerLog(logger, "{} 已经被忽略", fileName);
				} else if (fileName.endsWith(".jar")) {
					scanZip(new JarFile(file));
				}
			}
		} else {
			LoggerUtil.prettyNIOServerLog(logger, "文件/文件夹 [ {} ] 不存在", file.getAbsoluteFile());
		}

	}

	private void scanZip(JarFile file) throws IOException {

		try {
			
			LoggerUtil.prettyNIOServerLog(logger, "加载文件 [ {} ]", file.getName());

			Enumeration<JarEntry> entries = file.entries();
			
			for (; entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				if (!entry.isDirectory()) {
					String name = entry.getName();
					if (name.endsWith(".class") && !matchSystem(name)) {
						storeClass(file, name, entry);
					}
				}
			}
		} finally {

			file.close();
		}
	}

	public boolean matchSystem(String name) {

		return name.startsWith("java") || name.startsWith("sun") || name.startsWith("com/sun") || matchExtend(name);

	}

	public boolean matchExtend(String name) {

		return name.startsWith("com/gifisan");

	}

	private void storeClass(JarFile file, String name, JarEntry entry) throws IOException {
		
		String className = name.replace('/', '.').replace(".class", "");
		
		if (clazzEntries.containsKey(className)) {
			throw new DuplicateClassException(className);
		}
		
		try {
			
			DynamicClassLoader.class.getClassLoader().loadClass(className);
			
			throw new DuplicateClassException(className);
		} catch (ClassNotFoundException e) {
		}

		InputStream inputStream = file.getInputStream(entry);
		
		byte[] binaryContent = FileUtil.toByteArray(inputStream, entry.getSize());

		ClassEntry classEntry = new ClassEntry();

		classEntry.binaryContent = binaryContent;

		classEntry.className = className;

		clazzEntries.put(className, classEntry);

	}

	private Class<?> defineClass(String name) throws ClassNotFoundException {
		ClassEntry entry = clazzEntries.get(name);

		if (entry == null) {
			return null;
		}

		return defineClass(entry);
	}

	private Class<?> defineClass(ClassEntry entry) throws ClassNotFoundException {

		String name = entry.className;

		try {
			Class<?> clazz = defineClass(name, entry.binaryContent, 0, entry.binaryContent.length);

			entry.loadedClass = clazz;

			LoggerUtil.prettyNIOServerLog(logger, "define class [ {} ]", name);

			return clazz;
		} catch (Throwable e) {
			throw new ClassNotFoundException(e.getMessage(), e);
		}
	}

	public Class<?> forName(String name) throws ClassNotFoundException {
		return this.findClass(name);
	}

	class ClassEntry {

		private String		className;

		private byte[]		binaryContent;

		private Class<?>	loadedClass;

	}

	public void unload() {
		this.clazzEntries.clear();

		Set<Entry<String, ClassEntry>> entries = this.clazzEntries.entrySet();

		for (Entry<String, ClassEntry> entry : entries) {
			ClassEntry classEntry = entry.getValue();
			if (classEntry.loadedClass != null) {
				unloadClass(classEntry.loadedClass);
			}
		}

		this.logger = null;
		System.gc();
	}

	private void unloadClass(Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers())) {
				try {
					if (!field.isAccessible()) {
						field.setAccessible(true);
					}
					field.set(null, null);
				} catch (Throwable e) {
					logger.debug(e);
				}
			}
		}
	}

}
