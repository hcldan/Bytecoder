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
package de.mirkosertic.bytecoder.core;

public class BytecodeInstructionINVOKESTATIC extends BytecodeInstructionGenericInvoke {

    public BytecodeInstructionINVOKESTATIC(final BytecodeOpcodeAddress aOpcodeIndex, final int aIndex, final BytecodeConstantPool aConstantPool) {
        super(aOpcodeIndex, aIndex, aConstantPool);
    }

    @Override
    public void performLinking(final BytecodeClass aOwningClass, final BytecodeLinkerContext aLinkerContext, final AnalysisStack analysisStack) {
        final BytecodeMethodRefConstant theMethodRefConstant = getMethodReference();
        final BytecodeClassinfoConstant theClassConstant = theMethodRefConstant.getClassIndex().getClassConstant();
        final BytecodeNameAndTypeConstant theMethodRef = theMethodRefConstant.getNameAndTypeIndex().getNameAndType();

        final BytecodeMethodSignature theSig = theMethodRef.getDescriptorIndex().methodSignature();
        final BytecodeUtf8Constant theName = theMethodRef.getNameIndex().getName();

        final BytecodeObjectTypeRef className = BytecodeObjectTypeRef.fromUtf8Constant(theClassConstant.getConstant());
        final BytecodeLinkedClass invokedType = aLinkerContext.resolveClass(className, analysisStack);
        invokedType.tagWith(BytecodeLinkedClass.Tag.INVOKESTATIC_TARGET);
        if (!invokedType.resolveStaticMethod(theName.stringValue(), theSig, analysisStack)) {
            throw new MissingLinkException("Cannot find static method " + className.name() +"." + theName.stringValue() + "(" + theSig + "). Analysis stack is \n" + analysisStack.toDebugOutput());
        }
    }
}