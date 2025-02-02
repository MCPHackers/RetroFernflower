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

import de.fernflower.code.CodeConstants;
import de.fernflower.code.InstructionSequence;
import de.fernflower.code.cfg.ControlFlowGraph;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.code.DeadCodeHelper;
import de.fernflower.modules.decompiler.*;
import de.fernflower.modules.decompiler.deobfuscator.ExceptionDeobfuscator;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructMethod;

import java.io.IOException;

public class MethodProcessorRunnable implements Runnable {

  public final Object lock = new Object();

  private final StructMethod method;
  private final VarProcessor varProc;
  private final DecompilerContext parentContext;

  private volatile RootStatement root;
  private volatile Throwable error;
  private volatile boolean finished = false;

  public MethodProcessorRunnable(StructMethod method, VarProcessor varProc, DecompilerContext parentContext) {
    this.method = method;
    this.varProc = varProc;
    this.parentContext = parentContext;
  }

  @Override
  public void run() {
    DecompilerContext.setCurrentContext(parentContext);

    error = null;
    root = null;

    try {
      root = codeToJava(method, varProc);
    }
    catch (ThreadDeath ex) {
      throw ex;
    }
    catch (Throwable ex) {
      error = ex;
    }
    finally {
      DecompilerContext.setCurrentContext(null);
    }

    finished = true;
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  public static RootStatement codeToJava(StructMethod mt, VarProcessor varProc) throws IOException {
    StructClass cl = mt.getClassStruct();

    boolean isInitializer = CodeConstants.CLINIT_NAME.equals(mt.getName()); // for now static initializer only

    mt.expandData();
    InstructionSequence seq = mt.getInstructionSequence();
    ControlFlowGraph graph = new ControlFlowGraph(seq);

    DeadCodeHelper.removeDeadBlocks(graph);
    graph.inlineJsr(mt);

    // TODO: move to the start, before jsr inlining
    DeadCodeHelper.connectDummyExitBlock(graph);

    DeadCodeHelper.removeGotos(graph);

    ExceptionDeobfuscator.removeCircularRanges(graph);

    ExceptionDeobfuscator.restorePopRanges(graph);

    if (DecompilerContext.getOption(IFernflowerPreferences.REMOVE_EMPTY_RANGES)) {
      ExceptionDeobfuscator.removeEmptyRanges(graph);
    }

    if (DecompilerContext.getOption(IFernflowerPreferences.NO_EXCEPTIONS_RETURN)) {
      // special case: single return instruction outside of a protected range
      DeadCodeHelper.incorporateValueReturns(graph);
    }

    //		ExceptionDeobfuscator.restorePopRanges(graph);
    ExceptionDeobfuscator.insertEmptyExceptionHandlerBlocks(graph);

    DeadCodeHelper.mergeBasicBlocks(graph);

    DecompilerContext.getCounterContainer().setCounter(CounterContainer.VAR_COUNTER, mt.getLocalVariables());

    if (ExceptionDeobfuscator.hasObfuscatedExceptions(graph)) {
      DecompilerContext.getLogger().writeMessage("Heavily obfuscated exception ranges found!", IFernflowerLogger.Severity.WARN);
    }

    RootStatement root = DomHelper.parseGraph(graph);

    FinallyProcessor fProc = new FinallyProcessor(varProc);
    while (fProc.iterateGraph(mt, root, graph)) {
      root = DomHelper.parseGraph(graph);
    }

    // remove synchronized exception handler
    // not until now because of comparison between synchronized statements in the finally cycle
    DomHelper.removeSynchronizedHandler(root);

    //		LabelHelper.lowContinueLabels(root, new HashSet<StatEdge>());

    SequenceHelper.condenseSequences(root);

    ClearStructHelper.clearStatements(root);

    ExprProcessor proc = new ExprProcessor();
    proc.processStatement(root, cl);

    SequenceHelper.condenseSequences(root);
    
    while (true) {
      StackVarsProcessor stackProc = new StackVarsProcessor();
      stackProc.simplifyStackVars(root, mt, cl);

      varProc.setVarVersions(root);

      if (!new PPandMMHelper().findPPandMM(root)) {
        break;
      }
    }

    while (true) {
      LabelHelper.cleanUpEdges(root);

      while (true) {
        MergeHelper.enhanceLoops(root);

        if (LoopExtractHelper.extractLoops(root)) {
          continue;
        }

        if (!IfHelper.mergeAllIfs(root)) {
          break;
        }
      }

      if (DecompilerContext.getOption(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION)) {
        if (IdeaNotNullHelper.removeHardcodedChecks(root, mt)) {
          SequenceHelper.condenseSequences(root);

          StackVarsProcessor stackProc = new StackVarsProcessor();
          stackProc.simplifyStackVars(root, mt, cl);

          varProc.setVarVersions(root);
        }
      }

      LabelHelper.identifyLabels(root);

      if (InlineSingleBlockHelper.inlineSingleBlocks(root)) {
        continue;
      }

      // initializer may have at most one return point, so no transformation of method exits permitted
      if (!(isInitializer || !ExitHelper.condenseExits(root))) {
    	  continue;
      }

      // FIXME: !!
      			if(!EliminateLoopsHelper.eliminateLoops(root)) {
      				break;
      			}
    }

    ExitHelper.removeRedundantReturns(root);

    SecondaryFunctionsHelper.identifySecondaryFunctions(root);

    varProc.setVarDefinitions(root);

    // must be the last invocation, because it makes the statement structure inconsistent
    // FIXME: new edge type needed
    LabelHelper.replaceContinueWithBreak(root);

    mt.releaseResources();

    return root;
  }

  public RootStatement getResult() throws Throwable {
    Throwable t = error;
    if (t != null) throw t;
    return root;
  }

  public boolean isFinished() {
    return finished;
  }
}
