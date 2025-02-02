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
package de.fernflower.modules.decompiler;

import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.util.ListStack;

public class ExprentStack extends ListStack<Exprent> {

  public ExprentStack() {
  }

  public ExprentStack(ListStack<Exprent> list) {
    super(list);
    pointer = list.getPointer();
  }

  public Exprent push(Exprent item) {
    super.push(item);

    return item;
  }

  public Exprent pop() {

    return this.remove(--pointer);
  }

  public ExprentStack clone() {
    return new ExprentStack(this);
  }
}
