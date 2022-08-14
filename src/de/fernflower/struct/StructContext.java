/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.fernflower.struct;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

import de.fernflower.main.DecompilerContext;
import de.fernflower.main.extern.IFernflowerLogger.Severity;
import de.fernflower.main.extern.IResultSaver;
import de.fernflower.struct.lazy.LazyLoader;
import de.fernflower.util.DataInputFullStream;

public class StructContext {

  private final IResultSaver saver;
  private final IDecompiledData decompiledData;
  private final LazyLoader loader;
  private final Map<String, ContextUnit> units = new HashMap<String, ContextUnit>();
  private final Map<String, StructClass> classes = new HashMap<String, StructClass>();

  public StructContext(IResultSaver saver, IDecompiledData decompiledData, LazyLoader loader) {
    this.saver = saver;
    this.decompiledData = decompiledData;
    this.loader = loader;

    ContextUnit defaultUnit = new ContextUnit(ContextUnit.TYPE_FOLDER, null, "", true, saver, decompiledData);
    units.put("", defaultUnit);
  }

  public StructClass getClass(String name) {
    return classes.get(name);
  }

  public void reloadContext() throws IOException {
    for (ContextUnit unit : units.values()) {
      for (StructClass cl : unit.getClasses()) {
        classes.remove(cl.qualifiedName);
      }

      unit.reload(loader);

      // adjust global class collection
      for (StructClass cl : unit.getClasses()) {
        classes.put(cl.qualifiedName, cl);
      }
    }
  }

  public void saveContext() {
    for (ContextUnit unit : units.values()) {
      if (unit.isOwn()) {
        unit.save();
      }
    }
  }

  public void addSpace(File file, boolean isOwn) {
    addSpace("", file, isOwn, 0);
  }

  private void addSpace(String path, File file, boolean isOwn, int level) {
    if (file.isDirectory()) {
      if (level == 1) path += file.getName();
      else if (level > 1) path += "/" + file.getName();

      File[] files = file.listFiles();
      if (files != null) {
        for (int i = files.length - 1; i >= 0; i--) {
          addSpace(path, files[i], isOwn, level + 1);
        }
      }
    }
    else {
      String filename = file.getName();

      boolean isArchive = false;
      try {
        if (filename.endsWith(".jar")) {
          isArchive = true;
          addArchive(path, file, ContextUnit.TYPE_JAR, isOwn);
        }
        else if (filename.endsWith(".zip")) {
          isArchive = true;
          addArchive(path, file, ContextUnit.TYPE_ZIP, isOwn);
        }
      }
      catch (IOException ex) {
        String message = "Corrupted archive file: " + file;
        DecompilerContext.getLogger().writeMessage(message, ex);
      }
      if (isArchive) {
        return;
      }

      ContextUnit unit = units.get(path);
      if (unit == null) {
        unit = new ContextUnit(ContextUnit.TYPE_FOLDER, null, path, isOwn, saver, decompiledData);
        units.put(path, unit);
      }

      if (filename.endsWith(".class")) {
        try {
          DataInputFullStream in = loader.getClassStream(file.getAbsolutePath(), null);
          try {
            StructClass cl = new StructClass(in, isOwn, loader);
            classes.put(cl.qualifiedName, cl);
            unit.addClass(cl, filename);
            loader.addClassLink(cl.qualifiedName, new LazyLoader.Link(LazyLoader.Link.CLASS, file.getAbsolutePath(), null));
          }
          finally {
            in.close();
          }
        }
        catch (IOException ex) {
          String message = "Corrupted class file: " + file;
          DecompilerContext.getLogger().writeMessage(message, ex);
        }
      }
      else {
        unit.addOtherEntry(file.getAbsolutePath(), filename);
      }
    }
  }

  private void addArchive(String externalPath, File file, int type, boolean isOwn) throws IOException {
    DecompilerContext.getLogger().writeMessage("Adding Archive: " + file.getAbsolutePath(), Severity.INFO);
    FileSystem fs;
    try {
      URI uri = new URI("jar:file", null, file.toURI().getPath(), null);
      try {
        fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
      } catch (FileSystemAlreadyExistsException e) {
        fs = FileSystems.getFileSystem(uri);
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    addFileSystem(fs, externalPath, file, type, isOwn);
    fs.close();
  }

  private void addFileSystem(FileSystem fs, String externalPath, File file, int type, boolean isOwn) throws IOException {
    ContextUnit unit = units.computeIfAbsent(externalPath + "/" + file, k -> new ContextUnit(type, externalPath, file.getName(), isOwn, saver, decompiledData));
    Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
        String name;
        if (path.getNameCount() > 2 && "modules".equals(path.getName(0).toString()) && "jrt".equals(path.getFileSystem().provider().getScheme())) {
          name = path.subpath(2, path.getNameCount()).toString();
        } else {
          name = path.toString().substring(1);
        }
        if (name.endsWith(".class")) {
          byte[] bytes = Files.readAllBytes(path);
          StructClass cl = new StructClass(bytes, isOwn, loader);
          classes.put(cl.qualifiedName, cl);
          unit.addClass(cl, name);
          loader.addClassLink(cl.qualifiedName, new LazyLoader.Link(LazyLoader.Link.ENTRY, file.getAbsolutePath(), name));
        } else {
          if ("META-INF/MANIFEST.MF".equals(name)) {
            unit.setManifest(new Manifest(Files.newInputStream(path)));
          }
          unit.addOtherEntry(file.getAbsolutePath(), name);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        String dirStr = dir.toString();
        if (dirStr.length() > 1) unit.addDirEntry(dirStr.substring(1));
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public Map<String, StructClass> getClasses() {
    return classes;
  }
}
