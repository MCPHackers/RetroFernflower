/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package de.fernflower.main;

import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.extern.IBytecodeProvider;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.extern.IResultSaver;
import de.fernflower.main.providers.IJavadocProvider;
import de.fernflower.modules.renamer.IdentifierConverter;
import de.fernflower.struct.IDecompiledData;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructContext;
import de.fernflower.struct.lazy.LazyLoader;

import java.util.Map;

public class Fernflower implements IDecompiledData {

  private final StructContext structContext;
  private ClassesProcessor classesProcessor;

  public Fernflower(IBytecodeProvider provider, IResultSaver saver, Map<String, Object> options, IFernflowerLogger logger, IJavadocProvider javadocProvider) {
    structContext = new StructContext(saver, this, new LazyLoader(provider));
    DecompilerContext.initContext(options);
    DecompilerContext.setCounterContainer(new CounterContainer());
    DecompilerContext.setLogger(logger);
    DecompilerContext.setJavadocProvider(javadocProvider);
  }

  public void decompileContext() {
    if (DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
      new IdentifierConverter().rename(structContext);
    }

    classesProcessor = new ClassesProcessor(structContext);

    DecompilerContext.setClassProcessor(classesProcessor);
    DecompilerContext.setStructContext(structContext);

    structContext.saveContext();
  }

  public void clearContext() {
    DecompilerContext.setCurrentContext(null);
  }

  public StructContext getStructContext() {
    return structContext;
  }

  @Override
  public String getClassEntryName(StructClass cl, String entryName) {
    ClassNode node = classesProcessor.getMapRootClasses().get(cl.qualifiedName);
    if (node.type != ClassNode.CLASS_ROOT) {
      return null;
    }
    else {
      if (DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
        String simple_classname = cl.qualifiedName.substring(cl.qualifiedName.lastIndexOf('/') + 1);
        return entryName.substring(0, entryName.lastIndexOf('/') + 1) + simple_classname + ".java";
      }
      else {
        return entryName.substring(0, entryName.lastIndexOf(".class")) + ".java";
      }
    }
  }

  @Override
  public String getClassContent(StructClass cl) {
    try {
      TextBuffer buffer = new TextBuffer(ClassesProcessor.AVERAGE_CLASS_SIZE);
      buffer.append(DecompilerContext.getProperty(IFernflowerPreferences.BANNER).toString());
      classesProcessor.writeClass(cl, buffer);
      return buffer.toString();
    }
    catch (Throwable ex) {
      DecompilerContext.getLogger().writeMessage("Class " + cl.qualifiedName + " couldn't be fully decompiled.", ex);
      return null;
    }
  }
}
