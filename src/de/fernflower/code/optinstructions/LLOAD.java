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
package de.fernflower.code.optinstructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class LLOAD extends Instruction {

  private static final int[] opcodes = new int[]{opc_lload_0, opc_lload_1, opc_lload_2, opc_lload_3};

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    int index = getOperand(0);
    if (index > 3) {
      if (wide) {
        out.writeByte(opc_wide);
      }
      out.writeByte(opc_lload);
      if (wide) {
        out.writeShort(index);
      }
      else {
        out.writeByte(index);
      }
    }
    else {
      out.writeByte(opcodes[index]);
    }
  }

  public int length() {
    int index = getOperand(0);
    if (index > 3) {
      return wide ? 4 : 2;
    }
    else {
      return 1;
    }
  }
}
