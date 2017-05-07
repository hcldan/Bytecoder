/*
 * Copyright 2017 Mirko Sertic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mirkosertic.bytecoder.unittest;

import de.mirkosertic.bytecoder.backend.js.JSBackend;
import de.mirkosertic.bytecoder.core.*;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class BytecoderUnitTestRunner extends ParentRunner<FrameworkMethod> {

    private final List<FrameworkMethod> testMethods;
    private final TestClass testClass;

    public BytecoderUnitTestRunner(java.lang.Class aClass) throws InitializationError {
        super(aClass);
        testClass = new TestClass(aClass);
        testMethods = new ArrayList<>();

        Method[] classMethods = aClass.getDeclaredMethods();
        for (int i = 0; i < classMethods.length; i++) {
            Method classMethod = classMethods[i];
            Class retClass = classMethod.getReturnType();
            int length = classMethod.getParameterTypes().length;
            int modifiers = classMethod.getModifiers();
            if (retClass == null || length != 0 || Modifier.isStatic(modifiers)
                    || !Modifier.isPublic(modifiers) || Modifier.isInterface(modifiers)
                    || Modifier.isAbstract(modifiers)) {
                continue;
            }
            String methodName = classMethod.getName();
            if (methodName.toUpperCase().startsWith("TEST")
                    || classMethod.getAnnotation(Test.class) != null) {
                testMethods.add(new FrameworkMethod(classMethod));
            }
            if (classMethod.getAnnotation(Ignore.class) != null) {
                testMethods.remove(classMethod);
            }
        }
    }

    @Override
    public Description getDescription() {
        Description spec = Description.createSuiteDescription(this.testClass.getName(),
                this.testClass.getJavaClass().getAnnotations());
        return spec;
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
        return testMethods;
    }

    @Override
    protected Description describeChild(FrameworkMethod frameworkMethod) {
        return Description.createTestDescription(testClass.getJavaClass(), frameworkMethod.getName());
    }

    private void testJSJVMBackendFrameworkMethod(FrameworkMethod aFrameworkMethod, RunNotifier aRunNotifier) {
        Description theDescription = Description.createTestDescription(testClass.getJavaClass(), aFrameworkMethod.getName() + " JVM Target");
        aRunNotifier.fireTestStarted(theDescription);
        try {
            // Simply invoke using reflection
            Object theInstance = testClass.getJavaClass().newInstance();
            Method theMethod = aFrameworkMethod.getMethod();
            theMethod.invoke(theInstance);

            aRunNotifier.fireTestFinished(theDescription);
        } catch (Exception e) {
            aRunNotifier.fireTestFailure(new Failure(theDescription, e));
        }
    }

    private void testJSBackendFrameworkMethod(FrameworkMethod aFrameworkMethod, RunNotifier aRunNotifier) {
        Description theDescription = Description.createTestDescription(testClass.getJavaClass(), aFrameworkMethod.getName() + " JS Target");
        aRunNotifier.fireTestStarted(theDescription);

        try {
            BytecodePackageReplacer theReplacer = new BytecodePackageReplacer();
            BytecodeLoader theLoader = new BytecodeLoader(theReplacer);
            BytecodeLinkerContext theLinkerContext = new BytecodeLinkerContext(theLoader);

            BytecodeSignatureParser theParser = new BytecodeSignatureParser(theReplacer);
            BytecodeMethodSignature theSignature = theParser.toMethodSignature(aFrameworkMethod.getMethod());

            BytecodeObjectTypeRef theTypeRef = new BytecodeObjectTypeRef(testClass.getName());

            theLinkerContext.linkClassMethod(theTypeRef, aFrameworkMethod.getName(), theSignature);

            JSBackend theBackend = new JSBackend();
            String theCode = theBackend.generateCodeFor(theLinkerContext);
            theCode += theBackend.toClassName(theTypeRef) + "." + theBackend.toMethodName(aFrameworkMethod.getName(), theSignature) + "({})";

            System.out.println(theCode);

            ScriptEngine theEngine = new ScriptEngineManager().getEngineByName("nashorn");
            StringWriter theError = new StringWriter();
            theEngine.getContext().setErrorWriter(theError);
            Object theResult = theEngine.eval(theCode);

            aRunNotifier.fireTestFinished(theDescription);

        } catch (Exception e) {
            aRunNotifier.fireTestFailure(new Failure(theDescription, e));
        }
    }

    @Override
    protected void runChild(FrameworkMethod aFrameworkMethod, RunNotifier aRunNotifier) {
        testJSBackendFrameworkMethod(aFrameworkMethod, aRunNotifier);
        testJSJVMBackendFrameworkMethod(aFrameworkMethod, aRunNotifier);
    }
}