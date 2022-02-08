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

import de.fernflower.code.CodeConstants;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarVersionPair;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;
import de.fernflower.util.InterpreterUtil;

public class EnumProcessor {

  public static void clearEnum(ClassWrapper wrapper) {
    StructClass cl = wrapper.getClassStruct();

    // hide values/valueOf methods and super() invocations
    for (MethodWrapper method : wrapper.getMethods()) {
      StructMethod mt = method.methodStruct;
      String name = mt.getName();
      String descriptor = mt.getDescriptor();

      if ("values".equals(name)) {
        if (descriptor.equals("()[L" + cl.qualifiedName + ";")) {
          wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(name, descriptor));
        }
      }
      else if ("valueOf".equals(name)) {
        if (descriptor.equals("(Ljava/lang/String;)L" + cl.qualifiedName + ";")) {
          wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(name, descriptor));
        }
      }
      else if (CodeConstants.INIT_NAME.equals(name)) {
        Statement firstData = findFirstData(method.root);
        if (firstData != null && !firstData.getExprents().isEmpty()) {
          Exprent exprent = firstData.getExprents().get(0);
          if (exprent.type == Exprent.EXPRENT_INVOCATION) {
            InvocationExprent invexpr = (InvocationExprent)exprent;
            if (isInvocationSuperConstructor(invexpr, method, wrapper)) {
              firstData.getExprents().remove(0);
            }
          }
        }
      }
    }

    // hide synthetic fields of enum and it's constants
    for (StructField fd : cl.getFields()) {
      String descriptor = fd.getDescriptor();
      if (fd.isSynthetic() && descriptor.equals("[L" + cl.qualifiedName + ";")) {
        wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(fd.getName(), descriptor));
      }
    }
  }

  // FIXME: move to a util class (see also InitializerProcessor)
  private static Statement findFirstData(Statement stat) {

    if (stat.getExprents() != null) {
      return stat;
    }
    else {
      if (stat.isLabeled()) {
        return null;
      }

      switch (stat.type) {
        case Statement.TYPE_SEQUENCE:
        case Statement.TYPE_IF:
        case Statement.TYPE_ROOT:
        case Statement.TYPE_SWITCH:
        case Statement.TYPE_SYNCRONIZED:
          return findFirstData(stat.getFirst());
        default:
          return null;
      }
    }
  }

  // FIXME: move to util class (see also InitializerProcessor)
  private static boolean isInvocationSuperConstructor(InvocationExprent inv, MethodWrapper meth, ClassWrapper wrapper) {

    if (inv.getFunctype() == InvocationExprent.TYP_INIT) {
      if (inv.getInstance().type == Exprent.EXPRENT_VAR) {
        VarExprent instvar = (VarExprent)inv.getInstance();
        VarVersionPair varpaar = new VarVersionPair(instvar);

        String classname = meth.varproc.getThisVars().get(varpaar);

        if (classname != null) { // any this instance. TODO: Restrict to current class?
          return !wrapper.getClassStruct().qualifiedName.equals(inv.getClassname());
        }
      }
    }

    return false;
  }
}
