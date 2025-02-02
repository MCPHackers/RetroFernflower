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
package de.fernflower.main.rels;

import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.sforms.FlattenStatementsHelper;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPair;
import de.fernflower.struct.StructMethod;

import java.util.HashSet;
import java.util.List;


public class MethodWrapper {

  public final RootStatement root;

  public final VarProcessor varproc;

  public final StructMethod methodStruct;

  public final CounterContainer counter;

  public DirectGraph graph;

  public List<VarVersionPair> signatureFields;

  public boolean decompiledWithErrors;

  public final HashSet<String> setOuterVarNames = new HashSet<String>();

  public MethodWrapper(RootStatement root, VarProcessor varproc, StructMethod methodStruct, CounterContainer counter) {
    this.root = root;
    this.varproc = varproc;
    this.methodStruct = methodStruct;
    this.counter = counter;
  }

  public DirectGraph getOrBuildGraph() {
    if (graph == null && root != null) {
      FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
      graph = flatthelper.buildDirectGraph(root);
    }
    return graph;
  }
}
