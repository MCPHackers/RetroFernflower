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
package de.fernflower.modules.renamer;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.extern.IIdentifierRenamer;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructContext;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.FieldDescriptor;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.struct.gen.NewClassNameBuilder;
import de.fernflower.util.VBStyleCollection;

import java.io.IOException;
import java.util.*;

public class IdentifierConverter implements NewClassNameBuilder {

  private StructContext context;
  private IIdentifierRenamer helper;
  private PoolInterceptor interceptor;
  private List<ClassWrapperNode> rootClasses = new ArrayList<ClassWrapperNode>();
  private List<ClassWrapperNode> rootInterfaces = new ArrayList<ClassWrapperNode>();
  private Map<String, Map<String, String>> interfaceNameMaps = new HashMap<String, Map<String, String>>();

  public void rename(StructContext context) {
    try {
      this.context = context;

      String user_class = (String)DecompilerContext.getProperty(IFernflowerPreferences.USER_RENAMER_CLASS);
      if (user_class != null) {
        try {
          helper = (IIdentifierRenamer)IdentifierConverter.class.getClassLoader().loadClass(user_class).newInstance();
        }
        catch (Exception ignored) { }
      }

      if (helper == null) {
        helper = new ConverterHelper();
      }

      interceptor = new PoolInterceptor(helper);

      buildInheritanceTree();

      renameAllClasses();

      renameInterfaces();

      renameClasses();

      DecompilerContext.setPoolInterceptor(interceptor);
      context.reloadContext();
    }
    catch (IOException ex) {
      throw new RuntimeException("Renaming failed!");
    }
  }

  private void renameClasses() {
    List<ClassWrapperNode> lstClasses = getReversePostOrderListIterative(rootClasses);
    Map<String, Map<String, String>> classNameMaps = new HashMap<String, Map<String, String>>();

    for (ClassWrapperNode node : lstClasses) {
      StructClass cl = node.getClassStruct();
      Map<String, String> names = new HashMap<String, String>();

      // merge information on super class
      if (cl.superClass != null) {
        Map<String, String> mapClass = classNameMaps.get(cl.superClass.getString());
        if (mapClass != null) {
          names.putAll(mapClass);
        }
      }

      // merge information on interfaces
      for (String ifName : cl.getInterfaceNames()) {
        Map<String, String> mapInt = interfaceNameMaps.get(ifName);
        if (mapInt != null) {
          names.putAll(mapInt);
        }
        else {
          StructClass clintr = context.getClass(ifName);
          if (clintr != null) {
            names.putAll(processExternalInterface(clintr));
          }
        }
      }

      renameClassIdentifiers(cl, names);

      if (!node.getSubclasses().isEmpty()) {
        classNameMaps.put(cl.qualifiedName, names);
      }
    }
  }

  private Map<String, String> processExternalInterface(StructClass cl) {
    Map<String, String> names = new HashMap<String, String>();

    for (String ifName : cl.getInterfaceNames()) {
      Map<String, String> mapInt = interfaceNameMaps.get(ifName);
      if (mapInt != null) {
        names.putAll(mapInt);
      }
      else {
        StructClass clintr = context.getClass(ifName);
        if (clintr != null) {
          names.putAll(processExternalInterface(clintr));
        }
      }
    }

    renameClassIdentifiers(cl, names);

    return names;
  }

  private void renameInterfaces() {
    List<ClassWrapperNode> lstInterfaces = getReversePostOrderListIterative(rootInterfaces);
    Map<String, Map<String, String>> interfaceNameMaps = new HashMap<String, Map<String, String>>();

    // rename methods and fields
    for (ClassWrapperNode node : lstInterfaces) {

      StructClass cl = node.getClassStruct();
      Map<String, String> names = new HashMap<String, String>();

      // merge information on super interfaces
      for (String ifName : cl.getInterfaceNames()) {
        Map<String, String> mapInt = interfaceNameMaps.get(ifName);
        if (mapInt != null) {
          names.putAll(mapInt);
        }
      }

      renameClassIdentifiers(cl, names);

      interfaceNameMaps.put(cl.qualifiedName, names);
    }

    this.interfaceNameMaps = interfaceNameMaps;
  }

  private void renameAllClasses() {
    // order not important
    List<ClassWrapperNode> lstAllClasses = new ArrayList<ClassWrapperNode>(getReversePostOrderListIterative(rootInterfaces));
    lstAllClasses.addAll(getReversePostOrderListIterative(rootClasses));

    // rename all interfaces and classes
    for (ClassWrapperNode node : lstAllClasses) {
      renameClass(node.getClassStruct());
    }
  }

  private void renameClass(StructClass cl) {

    if (!cl.isOwn()) {
      return;
    }

    String classOldFullName = cl.qualifiedName;

    // TODO: rename packages
    String clSimpleName = ConverterHelper.getSimpleClassName(classOldFullName);
    if (helper.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_CLASS, clSimpleName, null, null)) {
      String classNewFullName;

      do {
        String classname = helper.getNextClassName(classOldFullName, ConverterHelper.getSimpleClassName(classOldFullName));
        classNewFullName = ConverterHelper.replaceSimpleClassName(classOldFullName, classname);
      }
      while (context.getClasses().containsKey(classNewFullName));

      interceptor.addName(classOldFullName, classNewFullName);
    }
  }

  private void renameClassIdentifiers(StructClass cl, Map<String, String> names) {
    // all classes are already renamed
    String classOldFullName = cl.qualifiedName;
    String classNewFullName = interceptor.getName(classOldFullName);

    if (classNewFullName == null) {
      classNewFullName = classOldFullName;
    }

    // methods
    HashSet<String> setMethodNames = new HashSet<String>();
    for (StructMethod md : cl.getMethods()) {
      setMethodNames.add(md.getName());
    }

    VBStyleCollection<StructMethod, String> methods = cl.getMethods();
    for (int i = 0; i < methods.size(); i++) {

      StructMethod mt = methods.get(i);
      String key = methods.getKey(i);

      boolean isPrivate = mt.hasModifier(CodeConstants.ACC_PRIVATE);

      String name = mt.getName();
      if (!cl.isOwn() || mt.hasModifier(CodeConstants.ACC_NATIVE)) {
        // external and native methods must not be renamed
        if (!isPrivate) {
          names.put(key, name);
        }
      }
      else if (helper.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_METHOD, classOldFullName, name, mt.getDescriptor())) {
        if (isPrivate || !names.containsKey(key)) {
          do {
            name = helper.getNextMethodName(classOldFullName, name, mt.getDescriptor());
          }
          while (setMethodNames.contains(name));

          if (!isPrivate) {
            names.put(key, name);
          }
        }
        else {
          name = names.get(key);
        }

        interceptor.addName(classOldFullName + " " + mt.getName() + " " + mt.getDescriptor(),
                            classNewFullName + " " + name + " " + buildNewDescriptor(false, mt.getDescriptor()));
      }
    }

    // external fields are not being renamed
    if (!cl.isOwn()) {
      return;
    }

    // fields
    // FIXME: should overloaded fields become the same name?
    HashSet<String> setFieldNames = new HashSet<String>();
    for (StructField fd : cl.getFields()) {
      setFieldNames.add(fd.getName());
    }

    for (StructField fd : cl.getFields()) {
      if (helper.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_FIELD, classOldFullName, fd.getName(), fd.getDescriptor())) {
        String newName;
        do {
          newName = helper.getNextFieldName(classOldFullName, fd.getName(), fd.getDescriptor());
        }
        while (setFieldNames.contains(newName));

        interceptor.addName(classOldFullName + " " + fd.getName() + " " + fd.getDescriptor(),
                            classNewFullName + " " + newName + " " + buildNewDescriptor(true, fd.getDescriptor()));
      }
    }
  }

  @Override
  public String buildNewClassname(String className) {
    return interceptor.getName(className);
  }

  private String buildNewDescriptor(boolean isField, String descriptor) {
    String newDescriptor;
    if (isField) {
      newDescriptor = FieldDescriptor.parseDescriptor(descriptor).buildNewDescriptor(this);
    }
    else {
      newDescriptor = MethodDescriptor.parseDescriptor(descriptor).buildNewDescriptor(this);
    }
    return newDescriptor != null ? newDescriptor : descriptor;
  }

  private static List<ClassWrapperNode> getReversePostOrderListIterative(List<ClassWrapperNode> roots) {
    List<ClassWrapperNode> res = new ArrayList<ClassWrapperNode>();

    LinkedList<ClassWrapperNode> stackNode = new LinkedList<ClassWrapperNode>();
    LinkedList<Integer> stackIndex = new LinkedList<Integer>();

    Set<ClassWrapperNode> setVisited = new HashSet<ClassWrapperNode>();

    for (ClassWrapperNode root : roots) {
      stackNode.add(root);
      stackIndex.add(0);
    }

    while (!stackNode.isEmpty()) {
      ClassWrapperNode node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      List<ClassWrapperNode> lstSubs = node.getSubclasses();

      for (; index < lstSubs.size(); index++) {
        ClassWrapperNode sub = lstSubs.get(index);
        if (!setVisited.contains(sub)) {
          stackIndex.add(index + 1);
          stackNode.add(sub);
          stackIndex.add(0);
          break;
        }
      }

      if (index == lstSubs.size()) {
        res.add(0, node);
        stackNode.removeLast();
      }
    }

    return res;
  }

  private void buildInheritanceTree() {
    Map<String, ClassWrapperNode> nodes = new HashMap<String, ClassWrapperNode>();
    Map<String, StructClass> classes = context.getClasses();

    List<ClassWrapperNode> rootClasses = new ArrayList<ClassWrapperNode>();
    List<ClassWrapperNode> rootInterfaces = new ArrayList<ClassWrapperNode>();

    for (StructClass cl : classes.values()) {
      if (!cl.isOwn()) {
        continue;
      }

      LinkedList<StructClass> stack = new LinkedList<StructClass>();
      LinkedList<ClassWrapperNode> stackSubNodes = new LinkedList<ClassWrapperNode>();

      stack.add(cl);
      stackSubNodes.add(null);

      while (!stack.isEmpty()) {
        StructClass clStr = stack.removeFirst();
        ClassWrapperNode child = stackSubNodes.removeFirst();

        ClassWrapperNode node = nodes.get(clStr.qualifiedName);
        boolean isNewNode = (node == null);

        if (isNewNode) {
          nodes.put(clStr.qualifiedName, node = new ClassWrapperNode(clStr));
        }

        //noinspection ConstantConditions
        if (child != null) {
          node.addSubclass(child);
        }

        if (!isNewNode) {
          break;
        }
        else {
          boolean isInterface = clStr.hasModifier(CodeConstants.ACC_INTERFACE);
          boolean found_parent = false;

          if (isInterface) {
            for (String ifName : clStr.getInterfaceNames()) {
              StructClass clParent = classes.get(ifName);
              if (clParent != null) {
                stack.add(clParent);
                stackSubNodes.add(node);
                found_parent = true;
              }
            }
          }
          else if (clStr.superClass != null) { // null iff java/lang/Object
            StructClass clParent = classes.get(clStr.superClass.getString());
            if (clParent != null) {
              stack.add(clParent);
              stackSubNodes.add(node);
              found_parent = true;
            }
          }

          if (!found_parent) { // no super class or interface
            (isInterface ? rootInterfaces : rootClasses).add(node);
          }
        }
      }
    }

    this.rootClasses = rootClasses;
    this.rootInterfaces = rootInterfaces;
  }
}
