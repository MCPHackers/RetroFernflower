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
package de.fernflower.modules.decompiler.exps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import de.fernflower.main.DecompilerContext;
import de.fernflower.main.TextBuffer;
import de.fernflower.main.collectors.BytecodeMappingTracer;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.vars.CheckTypesResult;
import de.fernflower.modules.decompiler.vars.VarVersionPair;
import de.fernflower.struct.gen.VarType;
import de.fernflower.struct.match.IMatchable;
import de.fernflower.struct.match.MatchEngine;
import de.fernflower.struct.match.MatchNode;
import de.fernflower.struct.match.MatchNode.RuleValue;

public class Exprent implements IMatchable {

  public static final int MULTIPLE_USES = 1;
  public static final int SIDE_EFFECTS_FREE = 2;
  public static final int BOTH_FLAGS = 3;

  public static final int EXPRENT_ARRAY = 1;
  public static final int EXPRENT_ASSIGNMENT = 2;
  public static final int EXPRENT_CONST = 3;
  public static final int EXPRENT_EXIT = 4;
  public static final int EXPRENT_FIELD = 5;
  public static final int EXPRENT_FUNCTION = 6;
  public static final int EXPRENT_IF = 7;
  public static final int EXPRENT_INVOCATION = 8;
  public static final int EXPRENT_MONITOR = 9;
  public static final int EXPRENT_NEW = 10;
  public static final int EXPRENT_SWITCH = 11;
  public static final int EXPRENT_VAR = 12;
  public static final int EXPRENT_ANNOTATION = 13;
  public static final int EXPRENT_ASSERT = 14;

  public final int type;
  public final int id;
  public Set<Integer> bytecode = null;  // offsets of bytecode instructions decompiled to this exprent

  public Exprent(int type) {
    this.type = type;
    this.id = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.EXPRENT_COUNTER);
  }

  public int getPrecedence() {
    return 0; // the highest precedence
  }

  public VarType getExprType() {
    return VarType.VARTYPE_VOID;
  }

  public int getExprentUse() {
    return 0;
  }

  public CheckTypesResult checkExprTypeBounds() {
    return new CheckTypesResult();
  }

  public boolean containsExprent(Exprent exprent) {
    List<Exprent> listTemp = new ArrayList<Exprent>(getAllExprents(true));
    listTemp.add(this);

    for (Exprent lstExpr : listTemp) {
      if (lstExpr.equals(exprent)) {
        return true;
      }
    }

    return false;
  }

  public List<Exprent> getAllExprents(boolean recursive) {
    List<Exprent> lst = getAllExprents();
    if (recursive) {
      for (int i = lst.size() - 1; i >= 0; i--) {
        lst.addAll(lst.get(i).getAllExprents(true));
      }
    }
    return lst;
  }

  public Set<VarVersionPair> getAllVariables() {
    List<Exprent> lstAllExprents = getAllExprents(true);
    lstAllExprents.add(this);

    Set<VarVersionPair> set = new HashSet<VarVersionPair>();
    for (Exprent expr : lstAllExprents) {
      if (expr.type == EXPRENT_VAR) {
        set.add(new VarVersionPair((VarExprent)expr));
      }
    }
    return set;
  }

  public List<Exprent> getAllExprents() {
    throw new RuntimeException("not implemented");
  }

  public Exprent copy() {
    throw new RuntimeException("not implemented");
  }

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    throw new RuntimeException("not implemented");
  }

  public void replaceExprent(Exprent oldExpr, Exprent newExpr) { }

  public void addBytecodeOffsets(Collection<Integer> bytecodeOffsets) {
    if (bytecodeOffsets != null && !bytecodeOffsets.isEmpty()) {
      if (bytecode == null) {
        bytecode = new HashSet<Integer>(bytecodeOffsets);
      }
      else {
        bytecode.addAll(bytecodeOffsets);
      }
    }
  }
  
  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************
  
  public IMatchable findObject(MatchNode matchNode, int index) {
    
    if(matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return null;
    }

    List<Exprent> lstAllExprents = getAllExprents();
    
    if(lstAllExprents == null || lstAllExprents.isEmpty()) {
      return null;
    }
    
    String position = (String)matchNode.getRuleValue(MatchProperties.EXPRENT_POSITION);
    if(position != null) {
      if(position.matches("-?\\d+")) {
        return lstAllExprents.get((lstAllExprents.size() + Integer.parseInt(position)) % lstAllExprents.size()); // care for negative positions
      }
    } else if(index < lstAllExprents.size()) { // use 'index' parameter
      return lstAllExprents.get(index);
    }

    return null;
  }

  public boolean match(MatchNode matchNode, MatchEngine engine) {
    
    if(matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return false;
    }
    
    for(Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      switch(rule.getKey()) {
      case EXPRENT_TYPE:
        if(this.type != ((Integer)rule.getValue().value).intValue()) {
          return false;
        }
        break;
      case EXPRENT_RET:
        if(!engine.checkAndSetVariableValue((String)rule.getValue().value, this)) {
          return false;
        }
        break;
      }
      
    }
    
    return true;
  }
  
}
