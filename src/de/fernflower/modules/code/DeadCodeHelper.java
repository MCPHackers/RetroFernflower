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
package de.fernflower.modules.code;

import de.fernflower.code.CodeConstants;
import de.fernflower.code.Instruction;
import de.fernflower.code.InstructionSequence;
import de.fernflower.code.cfg.BasicBlock;
import de.fernflower.code.cfg.ControlFlowGraph;
import de.fernflower.code.cfg.ExceptionRangeCFG;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.extern.IFernflowerPreferences;

import java.util.*;

public class DeadCodeHelper {

  public static void removeDeadBlocks(ControlFlowGraph graph) {

    LinkedList<BasicBlock> stack = new LinkedList<BasicBlock>();
    HashSet<BasicBlock> setStacked = new HashSet<BasicBlock>();

    stack.add(graph.getFirst());
    setStacked.add(graph.getFirst());

    while (!stack.isEmpty()) {
      BasicBlock block = stack.removeFirst();

      List<BasicBlock> lstSuccs = new ArrayList<BasicBlock>(block.getSuccs());
      lstSuccs.addAll(block.getSuccExceptions());

      for (BasicBlock succ : lstSuccs) {
        if (!setStacked.contains(succ)) {
          stack.add(succ);
          setStacked.add(succ);
        }
      }
    }

    HashSet<BasicBlock> setAllBlocks = new HashSet<BasicBlock>(graph.getBlocks());
    setAllBlocks.removeAll(setStacked);

    for (BasicBlock block : setAllBlocks) {
      graph.removeBlock(block);
    }
  }

  public static void removeEmptyBlocks(ControlFlowGraph graph) {

    List<BasicBlock> blocks = graph.getBlocks();

    boolean cont;
    do {
      cont = false;

      for (int i = blocks.size() - 1; i >= 0; i--) {
        BasicBlock block = blocks.get(i);

        if (removeEmptyBlock(graph, block, false)) {
          cont = true;
          break;
        }
      }
    }
    while (cont);
  }

  private static boolean removeEmptyBlock(ControlFlowGraph graph, BasicBlock block, boolean merging) {

    boolean deletedRanges = false;

    if (block.getSeq().isEmpty()) {

      if (block.getSuccs().size() > 1) {
        if (block.getPreds().size() > 1) {
          // ambiguous block
          throw new RuntimeException("ERROR: empty block with multiple predecessors and successors found");
        }
        else if (!merging) {
          throw new RuntimeException("ERROR: empty block with multiple successors found");
        }
      }

      HashSet<BasicBlock> setExits = new HashSet<BasicBlock>(graph.getLast().getPreds());

      if (block.getPredExceptions().isEmpty() &&
          (!setExits.contains(block) || block.getPreds().size() == 1)) {

        if (setExits.contains(block)) {
          BasicBlock pred = block.getPreds().get(0);

          // FIXME: flag in the basic block
          if (pred.getSuccs().size() != 1 || (!pred.getSeq().isEmpty()
                                              && pred.getSeq().getLastInstr().group == CodeConstants.GROUP_SWITCH)) {
            return false;
          }
        }

        HashSet<BasicBlock> setPreds = new HashSet<BasicBlock>(block.getPreds());
        HashSet<BasicBlock> setSuccs = new HashSet<BasicBlock>(block.getSuccs());

        // collect common exception ranges of predecessors and successors
        HashSet<BasicBlock> setCommonExceptionHandlers = null;
        for (int i = 0; i < 2; ++i) {
          for (BasicBlock pred : i == 0 ? setPreds : setSuccs) {
            if (setCommonExceptionHandlers == null) {
              setCommonExceptionHandlers = new HashSet<BasicBlock>(pred.getSuccExceptions());
            }
            else {
              setCommonExceptionHandlers.retainAll(pred.getSuccExceptions());
            }
          }
        }

        // check the block to be in each of the common ranges
        if (setCommonExceptionHandlers != null && !setCommonExceptionHandlers.isEmpty()) {
          for (BasicBlock handler : setCommonExceptionHandlers) {
            if (!block.getSuccExceptions().contains(handler)) {
              return false;
            }
          }
        }

        // remove ranges consisting of this one block
        List<ExceptionRangeCFG> lstRanges = graph.getExceptions();
        for (int i = lstRanges.size() - 1; i >= 0; i--) {
          ExceptionRangeCFG range = lstRanges.get(i);
          List<BasicBlock> lst = range.getProtectedRange();

          if (lst.size() == 1 && lst.get(0) == block) {
            if (DecompilerContext.getOption(IFernflowerPreferences.REMOVE_EMPTY_RANGES)) {
              block.removeSuccessorException(range.getHandler());
              lstRanges.remove(i);

              deletedRanges = true;
            }
            else {
              return false;
            }
          }
        }


        // connect remaining nodes
        if (merging) {
          BasicBlock pred = block.getPreds().get(0);
          pred.removeSuccessor(block);

          List<BasicBlock> lstSuccs = new ArrayList<BasicBlock>(block.getSuccs());
          for (BasicBlock succ : lstSuccs) {
            block.removeSuccessor(succ);
            pred.addSuccessor(succ);
          }
        }
        else {
          for (BasicBlock pred : setPreds) {
            for (BasicBlock succ : setSuccs) {
              pred.replaceSuccessor(block, succ);
            }
          }
        }

        // finally exit edges
        Set<BasicBlock> setFinallyExits = graph.getFinallyExits();
        if (setFinallyExits.contains(block)) {
          setFinallyExits.remove(block);
          setFinallyExits.add(setPreds.iterator().next());
        }

        // replace first if necessary
        if (graph.getFirst() == block) {
          if (setSuccs.size() != 1) {
            throw new RuntimeException("multiple or no entry blocks!");
          }
          else {
            graph.setFirst(setSuccs.iterator().next());
          }
        }

        // remove this block
        graph.removeBlock(block);

        if (deletedRanges) {
          removeDeadBlocks(graph);
        }
      }
    }

    return deletedRanges;
  }


  public static boolean isDominator(ControlFlowGraph graph, BasicBlock block, BasicBlock dom) {

    HashSet<BasicBlock> marked = new HashSet<BasicBlock>();

    if (block == dom) {
      return true;
    }

    LinkedList<BasicBlock> lstNodes = new LinkedList<BasicBlock>();
    lstNodes.add(block);

    while (!lstNodes.isEmpty()) {

      BasicBlock node = lstNodes.remove(0);
      if (marked.contains(node)) {
        continue;
      }
      else {
        marked.add(node);
      }

      if (node == graph.getFirst()) {
        return false;
      }

      for (int i = 0; i < node.getPreds().size(); i++) {
        BasicBlock pred = node.getPreds().get(i);
        if (!marked.contains(pred) && pred != dom) {
          lstNodes.add(pred);
        }
      }

      for (int i = 0; i < node.getPredExceptions().size(); i++) {
        BasicBlock pred = node.getPredExceptions().get(i);
        if (!marked.contains(pred) && pred != dom) {
          lstNodes.add(pred);
        }
      }
    }

    return true;
  }

  public static void removeGotos(ControlFlowGraph graph) {

    for (BasicBlock block : graph.getBlocks()) {
      Instruction instr = block.getLastInstruction();

      if (instr != null && instr.opcode == CodeConstants.opc_goto) {
        block.getSeq().removeLast();
      }
    }

    removeEmptyBlocks(graph);
  }

  public static void connectDummyExitBlock(ControlFlowGraph graph) {

    BasicBlock exit = graph.getLast();
    for (BasicBlock block : new HashSet<BasicBlock>(exit.getPreds())) {
      exit.removePredecessor(block);
      block.addSuccessor(exit);
    }
  }

  public static void incorporateValueReturns(ControlFlowGraph graph) {

    for (BasicBlock block : graph.getBlocks()) {
      InstructionSequence seq = block.getSeq();

      int len = seq.length();
      if (len > 0 && len < 3) {

        boolean ok = false;

        if (seq.getLastInstr().opcode >= CodeConstants.opc_ireturn && seq.getLastInstr().opcode <= CodeConstants.opc_return) {
          if (len == 1) {
            ok = true;
          }
          else if (seq.getLastInstr().opcode != CodeConstants.opc_return) {
            switch (seq.getInstr(0).opcode) {
              case CodeConstants.opc_iload:
              case CodeConstants.opc_lload:
              case CodeConstants.opc_fload:
              case CodeConstants.opc_dload:
              case CodeConstants.opc_aload:
              case CodeConstants.opc_aconst_null:
              case CodeConstants.opc_bipush:
              case CodeConstants.opc_sipush:
              case CodeConstants.opc_lconst_0:
              case CodeConstants.opc_lconst_1:
              case CodeConstants.opc_fconst_0:
              case CodeConstants.opc_fconst_1:
              case CodeConstants.opc_fconst_2:
              case CodeConstants.opc_dconst_0:
              case CodeConstants.opc_dconst_1:
              case CodeConstants.opc_ldc:
              case CodeConstants.opc_ldc_w:
              case CodeConstants.opc_ldc2_w:
                ok = true;
            }
          }
        }

        if (ok) {

          if (!block.getPreds().isEmpty()) {

            HashSet<BasicBlock> setPredHandlersUnion = new HashSet<BasicBlock>();
            HashSet<BasicBlock> setPredHandlersIntersection = new HashSet<BasicBlock>();

            boolean firstpred = true;
            for (BasicBlock pred : block.getPreds()) {
              if (firstpred) {
                setPredHandlersIntersection.addAll(pred.getSuccExceptions());
                firstpred = false;
              }
              else {
                setPredHandlersIntersection.retainAll(pred.getSuccExceptions());
              }

              setPredHandlersUnion.addAll(pred.getSuccExceptions());
            }

            // add exception ranges from predecessors
            setPredHandlersIntersection.removeAll(block.getSuccExceptions());
            BasicBlock predecessor = block.getPreds().get(0);

            for (BasicBlock handler : setPredHandlersIntersection) {
              ExceptionRangeCFG range = graph.getExceptionRange(handler, predecessor);

              range.getProtectedRange().add(block);
              block.addSuccessorException(handler);
            }

            // remove redundant ranges
            HashSet<BasicBlock> setRangesToBeRemoved = new HashSet<BasicBlock>(block.getSuccExceptions());
            setRangesToBeRemoved.removeAll(setPredHandlersUnion);

            for (BasicBlock handler : setRangesToBeRemoved) {
              ExceptionRangeCFG range = graph.getExceptionRange(handler, block);

              if (range.getProtectedRange().size() > 1) {
                range.getProtectedRange().remove(block);
                block.removeSuccessorException(handler);
              }
            }
          }


          if (block.getPreds().size() == 1 && block.getPredExceptions().isEmpty()) {

            BasicBlock bpred = block.getPreds().get(0);
            if (bpred.getSuccs().size() == 1) {

              // add exception ranges of predecessor
              for (BasicBlock succ : bpred.getSuccExceptions()) {
                if (!block.getSuccExceptions().contains(succ)) {
                  ExceptionRangeCFG range = graph.getExceptionRange(succ, bpred);

                  range.getProtectedRange().add(block);
                  block.addSuccessorException(succ);
                }
              }

              // remove superfluous ranges from successors
              for (BasicBlock succ : new HashSet<BasicBlock>(block.getSuccExceptions())) {
                if (!bpred.getSuccExceptions().contains(succ)) {
                  ExceptionRangeCFG range = graph.getExceptionRange(succ, block);

                  if (range.getProtectedRange().size() > 1) {
                    range.getProtectedRange().remove(block);
                    block.removeSuccessorException(succ);
                  }
                }
              }
            }
          }
        }
      }
    }
  }


  public static void mergeBasicBlocks(ControlFlowGraph graph) {

    while (true) {

      boolean merged = false;

      for (BasicBlock block : graph.getBlocks()) {

        InstructionSequence seq = block.getSeq();

        if (block.getSuccs().size() == 1) {
          BasicBlock next = block.getSuccs().get(0);

          if (next != graph.getLast() && (seq.isEmpty() || seq.getLastInstr().group != CodeConstants.GROUP_SWITCH)) {

            if (next.getPreds().size() == 1 && next.getPredExceptions().isEmpty()
                && next != graph.getFirst()) {
              // TODO: implement a dummy start block
              boolean sameRanges = true;
              for (ExceptionRangeCFG range : graph.getExceptions()) {
                if (range.getProtectedRange().contains(block) ^
                    range.getProtectedRange().contains(next)) {
                  sameRanges = false;
                  break;
                }
              }

              if (sameRanges) {
                seq.addSequence(next.getSeq());
                block.getInstrOldOffsets().addAll(next.getInstrOldOffsets());
                next.getSeq().clear();

                removeEmptyBlock(graph, next, true);

                merged = true;
                break;
              }
            }
          }
        }
      }

      if (!merged) {
        break;
      }
    }
  }
}
