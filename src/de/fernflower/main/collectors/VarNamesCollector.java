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
package de.fernflower.main.collectors;

import java.util.HashSet;
import java.util.Set;

import de.fernflower.struct.gen.VarType;

public class VarNamesCollector {

  private final Set<String> usedNames = new HashSet<String>();

  public VarNamesCollector() { }

  public VarNamesCollector(Set<String> setNames) {
    usedNames.addAll(setNames);
  }

  public void addName(String value) {
    usedNames.add(value);
  }

  public String getFreeName(int index, VarType type) {
    return getFreeName(type.getTypeName() + index);
  }

  public String getFreeName(int index) {
    return getFreeName("var" + index);
  }

  public String getFreeName(String proposition) {
    while (usedNames.contains(proposition)) {
      proposition += "x";
    }
    usedNames.add(proposition);
    return proposition;
  }
}
