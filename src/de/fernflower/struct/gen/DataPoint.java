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
package de.fernflower.struct.gen;

import de.fernflower.code.CodeConstants;
import de.fernflower.struct.StructMethod;
import de.fernflower.util.ListStack;

import java.util.ArrayList;
import java.util.List;

public class DataPoint {

  private List<VarType> localVariables = new ArrayList<VarType>();

  private ListStack<VarType> stack = new ListStack<VarType>();


  public void setVariable(int index, VarType value) {
    if (index >= localVariables.size()) {
      for (int i = localVariables.size(); i <= index; i++) {
        localVariables.add(new VarType(CodeConstants.TYPE_NOTINITIALIZED));
      }
    }

    localVariables.set(index, value);
  }

  public VarType getVariable(int index) {
    if (index < localVariables.size()) {
      return localVariables.get(index);
    }
    else if (index < 0) {
      throw new IndexOutOfBoundsException();
    }
    else {
      return new VarType(CodeConstants.TYPE_NOTINITIALIZED);
    }
  }

  public DataPoint copy() {
    DataPoint point = new DataPoint();
    point.setLocalVariables(new ArrayList<VarType>(localVariables));
    point.setStack(stack.clone());
    return point;
  }

  public static DataPoint getInitialDataPoint(StructMethod mt) {

    DataPoint point = new DataPoint();

    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    int k = 0;
    if (!mt.hasModifier(CodeConstants.ACC_STATIC)) {
      point.setVariable(k++, new VarType(CodeConstants.TYPE_OBJECT, 0, null));
    }

    for (int i = 0; i < md.params.length; i++) {
      VarType var = md.params[i];

      point.setVariable(k++, var);
      if (var.stackSize == 2) {
        point.setVariable(k++, new VarType(CodeConstants.TYPE_GROUP2EMPTY));
      }
    }

    return point;
  }


  public List<VarType> getLocalVariables() {
    return localVariables;
  }

  public void setLocalVariables(List<VarType> localVariables) {
    this.localVariables = localVariables;
  }

  public ListStack<VarType> getStack() {
    return stack;
  }

  public void setStack(ListStack<VarType> stack) {
    this.stack = stack;
  }
}
