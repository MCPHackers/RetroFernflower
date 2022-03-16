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
package de.fernflower.main.decompiler;

import de.fernflower.main.Fernflower;
import de.fernflower.main.extern.IBytecodeProvider;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IResultSaver;
import de.fernflower.main.providers.IJavadocProvider;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class BaseDecompiler {

  public Fernflower fernflower;

  public BaseDecompiler(IBytecodeProvider provider, IResultSaver saver, Map<String, Object> options, IFernflowerLogger logger) {
    this(provider, saver, options, logger, null);
  }

  public BaseDecompiler(IBytecodeProvider provider, IResultSaver saver, Map<String, Object> options, IFernflowerLogger logger, IJavadocProvider javadocProvider) {
    fernflower = new Fernflower(provider, saver, options, logger, javadocProvider);
  }

  public void addSpace(File file, boolean isOwn) throws IOException {
    fernflower.getStructContext().addSpace(file, isOwn);
  }

  public void decompileContext() {
    try {
      fernflower.decompileContext();
    }
    finally {
      fernflower.clearContext();
    }
  }
}
