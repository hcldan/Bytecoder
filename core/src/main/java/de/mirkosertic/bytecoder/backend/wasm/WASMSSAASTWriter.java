/*
 * Copyright 2018 Mirko Sertic
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
package de.mirkosertic.bytecoder.backend.wasm;

import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.call;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.currentMemory;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.f32;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.getGlobal;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.getLocal;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.i32;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.select;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.weakFunctionReference;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.weakFunctionTableReference;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.mirkosertic.bytecoder.backend.CompileOptions;
import de.mirkosertic.bytecoder.backend.wasm.ast.Block;
import de.mirkosertic.bytecoder.backend.wasm.ast.Callable;
import de.mirkosertic.bytecoder.backend.wasm.ast.Container;
import de.mirkosertic.bytecoder.backend.wasm.ast.ExportableFunction;
import de.mirkosertic.bytecoder.backend.wasm.ast.Expressions;
import de.mirkosertic.bytecoder.backend.wasm.ast.Function;
import de.mirkosertic.bytecoder.backend.wasm.ast.Iff;
import de.mirkosertic.bytecoder.backend.wasm.ast.Try;
import de.mirkosertic.bytecoder.backend.wasm.ast.WASMType;
import de.mirkosertic.bytecoder.backend.wasm.ast.Global;
import de.mirkosertic.bytecoder.backend.wasm.ast.I32Const;
import de.mirkosertic.bytecoder.backend.wasm.ast.LabeledContainer;
import de.mirkosertic.bytecoder.backend.wasm.ast.Local;
import de.mirkosertic.bytecoder.backend.wasm.ast.Loop;
import de.mirkosertic.bytecoder.backend.wasm.ast.Module;
import de.mirkosertic.bytecoder.backend.wasm.ast.PrimitiveType;
import de.mirkosertic.bytecoder.backend.wasm.ast.WASMExpression;
import de.mirkosertic.bytecoder.backend.wasm.ast.WASMValue;
import de.mirkosertic.bytecoder.classlib.Address;
import de.mirkosertic.bytecoder.classlib.MemoryManager;
import de.mirkosertic.bytecoder.core.BytecodeClass;
import de.mirkosertic.bytecoder.core.BytecodeLinkedClass;
import de.mirkosertic.bytecoder.core.BytecodeLinkerContext;
import de.mirkosertic.bytecoder.core.BytecodeMethodSignature;
import de.mirkosertic.bytecoder.core.BytecodeObjectTypeRef;
import de.mirkosertic.bytecoder.core.BytecodePrimitiveTypeRef;
import de.mirkosertic.bytecoder.core.BytecodeResolvedFields;
import de.mirkosertic.bytecoder.core.BytecodeTypeRef;
import de.mirkosertic.bytecoder.core.BytecodeVirtualMethodIdentifier;
import de.mirkosertic.bytecoder.relooper.Relooper;
import de.mirkosertic.bytecoder.ssa.ArrayEntryExpression;
import de.mirkosertic.bytecoder.ssa.ArrayLengthExpression;
import de.mirkosertic.bytecoder.ssa.ArrayStoreExpression;
import de.mirkosertic.bytecoder.ssa.BinaryExpression;
import de.mirkosertic.bytecoder.ssa.BreakExpression;
import de.mirkosertic.bytecoder.ssa.ByteValue;
import de.mirkosertic.bytecoder.ssa.CheckCastExpression;
import de.mirkosertic.bytecoder.ssa.ClassReferenceValue;
import de.mirkosertic.bytecoder.ssa.CompareExpression;
import de.mirkosertic.bytecoder.ssa.ComputedMemoryLocationReadExpression;
import de.mirkosertic.bytecoder.ssa.ComputedMemoryLocationWriteExpression;
import de.mirkosertic.bytecoder.ssa.ContinueExpression;
import de.mirkosertic.bytecoder.ssa.CurrentExceptionExpression;
import de.mirkosertic.bytecoder.ssa.DirectInvokeMethodExpression;
import de.mirkosertic.bytecoder.ssa.DoubleValue;
import de.mirkosertic.bytecoder.ssa.Expression;
import de.mirkosertic.bytecoder.ssa.ExpressionList;
import de.mirkosertic.bytecoder.ssa.FixedBinaryExpression;
import de.mirkosertic.bytecoder.ssa.FloatValue;
import de.mirkosertic.bytecoder.ssa.FloatingPointCeilExpression;
import de.mirkosertic.bytecoder.ssa.FloatingPointFloorExpression;
import de.mirkosertic.bytecoder.ssa.FloorExpression;
import de.mirkosertic.bytecoder.ssa.GetFieldExpression;
import de.mirkosertic.bytecoder.ssa.GetStaticExpression;
import de.mirkosertic.bytecoder.ssa.GotoExpression;
import de.mirkosertic.bytecoder.ssa.IFExpression;
import de.mirkosertic.bytecoder.ssa.InstanceOfExpression;
import de.mirkosertic.bytecoder.ssa.IntegerValue;
import de.mirkosertic.bytecoder.ssa.InvokeStaticMethodExpression;
import de.mirkosertic.bytecoder.ssa.InvokeVirtualMethodExpression;
import de.mirkosertic.bytecoder.ssa.LongValue;
import de.mirkosertic.bytecoder.ssa.LookupSwitchExpression;
import de.mirkosertic.bytecoder.ssa.MaxExpression;
import de.mirkosertic.bytecoder.ssa.MemorySizeExpression;
import de.mirkosertic.bytecoder.ssa.MethodHandlesGeneratedLookupExpression;
import de.mirkosertic.bytecoder.ssa.MethodRefExpression;
import de.mirkosertic.bytecoder.ssa.MethodTypeExpression;
import de.mirkosertic.bytecoder.ssa.MinExpression;
import de.mirkosertic.bytecoder.ssa.NegatedExpression;
import de.mirkosertic.bytecoder.ssa.NewArrayExpression;
import de.mirkosertic.bytecoder.ssa.NewMultiArrayExpression;
import de.mirkosertic.bytecoder.ssa.NewObjectExpression;
import de.mirkosertic.bytecoder.ssa.NullValue;
import de.mirkosertic.bytecoder.ssa.PHIExpression;
import de.mirkosertic.bytecoder.ssa.Program;
import de.mirkosertic.bytecoder.ssa.PutFieldExpression;
import de.mirkosertic.bytecoder.ssa.PutStaticExpression;
import de.mirkosertic.bytecoder.ssa.RegionNode;
import de.mirkosertic.bytecoder.ssa.ResolveCallsiteObjectExpression;
import de.mirkosertic.bytecoder.ssa.ReturnExpression;
import de.mirkosertic.bytecoder.ssa.ReturnValueExpression;
import de.mirkosertic.bytecoder.ssa.RuntimeGeneratedTypeExpression;
import de.mirkosertic.bytecoder.ssa.SetMemoryLocationExpression;
import de.mirkosertic.bytecoder.ssa.ShortValue;
import de.mirkosertic.bytecoder.ssa.SqrtExpression;
import de.mirkosertic.bytecoder.ssa.StackTopExpression;
import de.mirkosertic.bytecoder.ssa.StringValue;
import de.mirkosertic.bytecoder.ssa.TableSwitchExpression;
import de.mirkosertic.bytecoder.ssa.ThrowExpression;
import de.mirkosertic.bytecoder.ssa.TypeConversionExpression;
import de.mirkosertic.bytecoder.ssa.TypeOfExpression;
import de.mirkosertic.bytecoder.ssa.TypeRef;
import de.mirkosertic.bytecoder.ssa.UnreachableExpression;
import de.mirkosertic.bytecoder.ssa.Value;
import de.mirkosertic.bytecoder.ssa.Variable;
import de.mirkosertic.bytecoder.ssa.VariableAssignmentExpression;

public class WASMSSAASTWriter {

    private static final String LABEL_LOCAL = "__label__";
    private static final String SP = "SP";
    private static final String OLDSP = "OLDSP";

    public static final String STACKTOP = "STACKTOP";
    public static final int GENERATED_INSTANCEOF_METHOD_ID = -1;
    public static final String VTABLEFUNCTIONSUFFIX = "__resolvevtableindex";
    public static final String RUNTIMECLASSSUFFIX = "__runtimeclass";
    public static final String INSTANCEOFSUFFIX = "__instanceof";
    public static final String EXCEPTION_NAME = "EX";

    public interface Resolver {
        Global globalForStringFromPool(StringValue aValue);
        Function resolveCallsiteBootstrapFor(BytecodeClass owningClass, String callsiteId, Program program, RegionNode bootstrapMethod);
    }

    public static PrimitiveType toType(final TypeRef aType) {
        switch (aType.resolve()) {
        case DOUBLE:
            return PrimitiveType.f32;
        case FLOAT:
            return PrimitiveType.f32;
        default:
            return PrimitiveType.i32;
        }
    }


    private final Resolver resolver;
    private final BytecodeLinkerContext linkerContext;
    private final ExportableFunction function;
    private final Container container;
    private final Expressions flow;
    private final Module module;
    private final CompileOptions compileOptions;
    private final List<Variable> stackVariables;
    private final WASMMemoryLayouter memoryLayouter;

    public WASMSSAASTWriter(
            final Resolver aResolver, final BytecodeLinkerContext aLinkerContext, final Module aModule, final CompileOptions aOptions, final Program aProgram, final WASMMemoryLayouter aMemoryLayouter, final ExportableFunction aFunction) {
        resolver = aResolver;
        linkerContext = aLinkerContext;
        function = aFunction;
        module = aModule;
        compileOptions = aOptions;
        stackVariables = new ArrayList<>();
        memoryLayouter = aMemoryLayouter;
        flow = function.flow;
        container = function;
        for (final Variable theVariable : aProgram.getVariables()) {
            if (theVariable.resolveType().resolve() == TypeRef.Native.REFERENCE) {
                stackVariables.add(theVariable);
            }
        }
    }

    private WASMSSAASTWriter(
            final Resolver aResolver, final BytecodeLinkerContext aLinkerContext, final Module aModule, final CompileOptions aOptions, final WASMMemoryLayouter aMemoryLayouter, final ExportableFunction aFunction, final LabeledContainer aContainer,
            final List<Variable> aStackVariables) {
        resolver = aResolver;
        linkerContext = aLinkerContext;
        function = aFunction;
        module = aModule;
        compileOptions = aOptions;
        stackVariables = aStackVariables;
        memoryLayouter = aMemoryLayouter;
        container = aContainer;
        flow = container.flow;
    }

    private WASMSSAASTWriter block(final String label) {
        final Block block = flow.block(label);
        return new WASMSSAASTWriter(resolver, linkerContext, module, compileOptions, memoryLayouter, function, block, stackVariables);
    }

    private WASMSSAASTWriter iff(final String label, final WASMValue condition) {
        final Iff block = flow.iff(label, condition);
        return new WASMSSAASTWriter(resolver, linkerContext, module, compileOptions, memoryLayouter, function, block, stackVariables);
    }

    private WASMSSAASTWriter Try(final String label) {
        final Try block = flow.Try(label);
        return new WASMSSAASTWriter(resolver, linkerContext, module, compileOptions, memoryLayouter, function, block, stackVariables);
    }

    private WASMSSAASTWriter loop(final String label) {
        final Loop loop = flow.loop(label);
        return new WASMSSAASTWriter(resolver, linkerContext, module, compileOptions, memoryLayouter, function, loop, stackVariables);
    }

    private int stackSize() {
        return stackVariables.size() * 4;
    }

    public boolean isStackVariable(final Variable aVariable) {
        for (final Variable theVariable : stackVariables) {
            if (Objects.equals(theVariable.getName(), aVariable.getName())) {
                return true;
            }
        }
        return false;
    }

    private int stackOffsetFor(final Variable aVariable) {
        int theStart = 0;
        for (final Variable theVariable : stackVariables) {
            if (Objects.equals(theVariable.getName(), aVariable.getName())) {
                return theStart;
            }
            theStart += 4;
        }
        throw new IllegalStateException("Unknown variable : " + aVariable);
    }

    public void writeExpressionList(final ExpressionList aList) {
        for (final Expression theExpression : aList.toList()) {
            generateExpressions(theExpression);
        }
    }

    private void generateExpressions(final Expression aExpression) {
        if (aExpression instanceof CheckCastExpression) {
            return;
        }
        if (aExpression instanceof ReturnExpression) {
            generateReturnExpression((ReturnExpression) aExpression);
            return;
        }
        if (aExpression instanceof VariableAssignmentExpression) {
            generateInitVariableExpression((VariableAssignmentExpression) aExpression);
            return;
        }
        if (aExpression instanceof DirectInvokeMethodExpression) {
            generateDirectMethodInvokeExpression((DirectInvokeMethodExpression) aExpression);
            return;
        }
        if (aExpression instanceof IFExpression) {
            generateIFExpression((IFExpression) aExpression);
            return;
        }
        if (aExpression instanceof GotoExpression) {
            generateGotoExpression((GotoExpression) aExpression);
            return;
        }
        if (aExpression instanceof ReturnValueExpression) {
            generateReturnExpression((ReturnValueExpression) aExpression);
            return;
        }
        if (aExpression instanceof PutFieldExpression) {
            generatePutFieldExpression((PutFieldExpression) aExpression);
            return;
        }
        if (aExpression instanceof SetMemoryLocationExpression) {
            generateSetMemoryLocationExpression((SetMemoryLocationExpression) aExpression);
            return;
        }
        if (aExpression instanceof PutStaticExpression) {
            generatePutStaticExpression((PutStaticExpression) aExpression);
            return;
        }
        if (aExpression instanceof InvokeStaticMethodExpression) {
            generateInvokeStaticExpression((InvokeStaticMethodExpression) aExpression);
            return;
        }
        if (aExpression instanceof ThrowExpression) {
            generateThrowExpression((ThrowExpression) aExpression);
            return;
        }
        if (aExpression instanceof ArrayStoreExpression) {
            generateArrayStoreExpression((ArrayStoreExpression) aExpression);
            return;
        }
        if (aExpression instanceof InvokeVirtualMethodExpression) {
            generateInvokeVirtualExpression((InvokeVirtualMethodExpression) aExpression);
            return;
        }
        if (aExpression instanceof TableSwitchExpression) {
            generateTableSwitchExpression((TableSwitchExpression) aExpression);
            return;
        }
        if (aExpression instanceof LookupSwitchExpression) {
            generateLookupSwitchExpression((LookupSwitchExpression) aExpression);
            return;
        }
        if (aExpression instanceof UnreachableExpression) {
            generateUnreachable((UnreachableExpression) aExpression);
            return;
        }
        if (aExpression instanceof BreakExpression) {
            final BreakExpression theBreak = (BreakExpression) aExpression;
            if (theBreak.isSetLabelRequired()) {
                final Local label = function.localByLabel(LABEL_LOCAL);
                flow.setLocal(label, i32.c(theBreak.jumpTarget().getAddress()));
            }
            if (!theBreak.isSilent()) {
                final LabeledContainer target = container.findByLabelInHierarchy(theBreak.blockToBreak().name());
                flow.branch(target);
            }
            return;
        }
        if (aExpression instanceof ContinueExpression) {
            final ContinueExpression theContinue = (ContinueExpression) aExpression;

            final Local label = function.localByLabel(LABEL_LOCAL);
            flow.setLocal(label, i32.c(theContinue.jumpTarget().getAddress()));

            final LabeledContainer target = container.findByLabelInHierarchy(theContinue.labelToReturnTo().name() + "_inner");
            flow.branch(target);
            return;
        }

        throw new IllegalStateException("Not supported : " + aExpression);
    }

    private void generateUnreachable(final UnreachableExpression aExpression) {
        flow.unreachable();
    }

    private void generateLookupSwitchExpression(final LookupSwitchExpression aExpression) {
        final WASMSSAASTWriter outer = block("outer");

        final Value theValue = aExpression.incomingDataFlows().get(0);

        // For each statement
        for (final Map.Entry<Long, ExpressionList> theEntry : aExpression.getPairs().entrySet()) {
            final WASMSSAASTWriter inner = outer.iff("switch_" + theEntry.getKey(), i32.eq(i32.c(((Number) theEntry.getKey()).intValue()), toValue(theValue)));
            inner.writeExpressionList(theEntry.getValue());
            inner.flow.branch((LabeledContainer) outer.container);
        }

        outer.writeExpressionList(aExpression.getDefaultExpressions());
    }

    private void generateTableSwitchExpression(final TableSwitchExpression aExpression) {

        final Value theValue = aExpression.incomingDataFlows().get(0);
        final WASMSSAASTWriter theTableSwitch = block("tableswitch");
        final WASMSSAASTWriter theMinCheck = theTableSwitch.block("label0");
        theMinCheck.flow.branchIff((LabeledContainer) theMinCheck.container, i32.ge_s(toValue(theValue), i32.c(((Number) aExpression.getLowValue()).intValue())));
        theMinCheck.writeExpressionList(aExpression.getDefaultExpressions());
        theMinCheck.flow.branch((LabeledContainer) theTableSwitch.container);

        final WASMSSAASTWriter theMaxCheck = theTableSwitch.block("label0");
        theMaxCheck.flow.branchIff((LabeledContainer) theMaxCheck.container, i32.le_s(toValue(theValue), i32.c(((Number) aExpression.getHighValue()).intValue())));
        theMaxCheck.writeExpressionList(aExpression.getDefaultExpressions());
        theMaxCheck.flow.branch((LabeledContainer) theTableSwitch.container);

        // For each statement
        for (final Map.Entry<Long, ExpressionList> theEntry : aExpression.getOffsets().entrySet()) {

            final WASMSSAASTWriter theSwitch = theTableSwitch.iff("switch_" + theEntry.getKey(), i32.eq(i32.c(((Number) theEntry.getKey()).intValue()), i32.sub(toValue(theValue), i32.c(((Number) aExpression.getLowValue()).intValue()))));
            theSwitch.writeExpressionList(theEntry.getValue());
            theSwitch.flow.branch((LabeledContainer) theTableSwitch.container);
        }
        theTableSwitch.flow.unreachable();
    }

    private void generateInvokeVirtualExpression(final InvokeVirtualMethodExpression aExpression) {
        if (aExpression.getSignature().getReturnType().isVoid()) {
            container.addChild(invokeVirtualValue(aExpression));
        } else {
            flow.drop(invokeVirtualValue(aExpression));
        }
    }

    private void generateArrayStoreExpression(final ArrayStoreExpression aExpression) {

        final List<Value> theIncomingData = aExpression.incomingDataFlows();
        final Value theArray = theIncomingData.get(0);
        final Value theIndex = theIncomingData.get(1);
        final Value theValue = theIncomingData.get(2);

        // If the index is a constant, we can precompute the offset.
        if (theIndex instanceof IntegerValue) {
            final int offset = 20 + ((IntegerValue)theIndex).getIntValue() * 4;

            switch (aExpression.getArrayType().resolve()) {
                case DOUBLE:
                case FLOAT: {
                    flow.f32.store(offset, toValue(theArray), toValue(theValue));
                    break;
                }
                default: {
                    flow.i32.store(offset, toValue(theArray), toValue(theValue));
                    break;
                }
            }
            return;
        }

        final WASMValue thePtr = i32.add(toValue(theArray), i32.mul(toValue(theIndex), i32.c(4)));

        switch (aExpression.getArrayType().resolve()) {
            case DOUBLE:
            case FLOAT: {
                flow.f32.store(20, thePtr, toValue(theValue));
                break;
            }
            default: {
                flow.i32.store(20, thePtr, toValue(theValue));
                break;
            }
        }
    }

    private void generateThrowExpression(final ThrowExpression aExpression) {
        if (compileOptions.isEnableExceptions()) {
            final Value theException = aExpression.incomingDataFlows().get(0);
            final WASMValue theValue = toValue(theException);
            stackExit();
            flow.throwException(module.getExceptions().exceptionIndex().byLabel(EXCEPTION_NAME), Collections.singletonList(theValue));
        } else {
            stackExit();
            flow.unreachable();
        }
    }

    private void generateInvokeStaticExpression(final InvokeStaticMethodExpression aExpression) {
        if (aExpression.getSignature().getReturnType().isVoid()) {
            container.addChild(invokeStaticValue(aExpression));
        } else {
            flow.drop(invokeStaticValue(aExpression));
        }
    }

    private void generatePutStaticExpression(final PutStaticExpression aExpression) {

        final WASMMemoryLayouter.MemoryLayout theLayout = memoryLayouter.layoutFor(BytecodeObjectTypeRef.fromUtf8Constant(aExpression.getField().getClassIndex().getClassConstant().getConstant()));
        final int theMemoryOffset = theLayout.offsetForClassMember(aExpression.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue());

        final List<Value> theIncomingData = aExpression.incomingDataFlows();

        final String theClassName = WASMWriterUtils.toClassName(aExpression.getField().getClassIndex().getClassConstant());
        final Global theGlobal = module.globalsIndex().globalByLabel(theClassName + RUNTIMECLASSSUFFIX);
        switch (theIncomingData.get(0).resolveType().resolve()) {
            case DOUBLE:
            case FLOAT: {
                flow.f32.store(theMemoryOffset, getGlobal(theGlobal), toValue(theIncomingData.get(0)));
                break;
            }
            default: {
                flow.i32.store(theMemoryOffset, getGlobal(theGlobal), toValue(theIncomingData.get(0)));
                break;
            }

        }
    }

    private void generateSetMemoryLocationExpression(final SetMemoryLocationExpression aExpression) {
        final List<Value> theIncomingData = aExpression.incomingDataFlows();
        flow.i32.store(0, toValue(theIncomingData.get(0)), toValue(theIncomingData.get(1)));
    }

    private void generatePutFieldExpression(final PutFieldExpression aExpression) {

        final WASMMemoryLayouter.MemoryLayout theLayout = memoryLayouter.layoutFor(BytecodeObjectTypeRef.fromUtf8Constant(aExpression.getField().getClassIndex().getClassConstant().getConstant()));
        final int theMemoryOffset = theLayout.offsetForInstanceMember(aExpression.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue());

        final BytecodeLinkedClass theLinkedClass = linkerContext.resolveClass(BytecodeObjectTypeRef.fromUtf8Constant(aExpression.getField().getClassIndex().getClassConstant().getConstant()));
        final BytecodeResolvedFields theInstanceFields = theLinkedClass.resolvedFields();
        final BytecodeResolvedFields.FieldEntry theField = theInstanceFields.fieldByName(aExpression.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue());

        final List<Value> theIncomingData = aExpression.incomingDataFlows();

        switch (TypeRef.toType(theField.getValue().getTypeRef()).resolve()) {
            case DOUBLE:
            case FLOAT:
                flow.f32.store(theMemoryOffset, toValue(theIncomingData.get(0)), toValue(theIncomingData.get(1)));
                break;
            default:
                flow.i32.store(theMemoryOffset, toValue(theIncomingData.get(0)), toValue(theIncomingData.get(1)));
                break;
        }
    }

    private void generateGotoExpression(final GotoExpression aExpression) {
        // TODO: Is this still relevant?
        //print("(set_local $currentLabel (i32.const ");
        //print(aExpression.getJumpTarget().getAddress());
        //println("))");
        //println("(br $controlflowloop)");
    }

    private void generateIFExpression(final IFExpression aExpression) {
        final WASMSSAASTWriter iff = iff("if_" + aExpression.getAddress().getAddress(), i32.ne(toValue(aExpression.incomingDataFlows().get(0)), i32.c(0)));
        iff.writeExpressionList(aExpression.getExpressions());
    }

    private void generateDirectMethodInvokeExpression(final DirectInvokeMethodExpression aExpression) {
        if (aExpression.getSignature().getReturnType().isVoid()) {
            container.addChild(directMethodInvokeValue(aExpression));
        } else {
            flow.drop(directMethodInvokeValue(aExpression));
        }
    }

    private void generateInitVariableExpression(final VariableAssignmentExpression aExpression) {

        final Variable theVariable = aExpression.getVariable();
        final Value theNewValue = aExpression.getValue();

        if (theNewValue instanceof PHIExpression) {
            return;
        }

        final Local theLocal = function.localByLabel(theVariable.getName());
        flow.setLocal(theLocal, toValue(theNewValue));

        if (isStackVariable(theVariable)) {
            final Local sp = function.localByLabel(SP);
            final int theOffset = stackOffsetFor(theVariable);
            switch (theVariable.resolveType().resolve()) {
                case DOUBLE:
                case FLOAT: {
                    flow.f32.store(theOffset, getLocal(sp), getLocal(theLocal));
                    break;
                }
                case UNKNOWN:
                    throw new IllegalStateException();
                default: {
                    flow.i32.store(theOffset, getLocal(sp), getLocal(theLocal));
                    break;
                }
            }
        }
    }

    private WASMValue toValue(final Value aValue) {
        if (aValue instanceof Variable) {
            return variableName((Variable) aValue);
        }
        if (aValue instanceof BinaryExpression) {
            return binaryValue((BinaryExpression) aValue);
        }
        if (aValue instanceof ByteValue) {
            return byteValue((ByteValue) aValue);
        }
        if (aValue instanceof IntegerValue) {
            return tntegerValue((IntegerValue) aValue);
        }
        if (aValue instanceof DirectInvokeMethodExpression) {
            return directMethodInvokeValue((DirectInvokeMethodExpression) aValue);
        }
        if (aValue instanceof InvokeStaticMethodExpression) {
            return invokeStaticValue((InvokeStaticMethodExpression) aValue);
        }
        if (aValue instanceof GetFieldExpression) {
            return getFieldValue((GetFieldExpression) aValue);
        }
        if (aValue instanceof NewObjectExpression) {
            return newObjectValue((NewObjectExpression) aValue);
        }
        if (aValue instanceof GetStaticExpression) {
            return getStaticValue((GetStaticExpression) aValue);
        }
        if (aValue instanceof LongValue) {
            return longValue((LongValue) aValue);
        }
        if (aValue instanceof FixedBinaryExpression) {
            return fixedBinaryValue((FixedBinaryExpression) aValue);
        }
        if (aValue instanceof ComputedMemoryLocationReadExpression) {
            return computedMemoryLocationValue((ComputedMemoryLocationReadExpression) aValue);
        }
        if (aValue instanceof ComputedMemoryLocationWriteExpression) {
            return computedMemoryLocationValue((ComputedMemoryLocationWriteExpression) aValue);
        }
        if (aValue instanceof TypeConversionExpression) {
            return typeConversion((TypeConversionExpression) aValue);
        }
        if (aValue instanceof NullValue) {
            return nullValue((NullValue) aValue);
        }
        if (aValue instanceof StackTopExpression) {
            return stackTopValue((StackTopExpression) aValue);
        }
        if (aValue instanceof MemorySizeExpression) {
            return memorySizeValue((MemorySizeExpression) aValue);
        }
        if (aValue instanceof ShortValue) {
            return shortValue((ShortValue) aValue);
        }
        if (aValue instanceof FloatValue) {
            return floatValue((FloatValue) aValue);
        }
        if (aValue instanceof InvokeVirtualMethodExpression) {
            return invokeVirtualValue((InvokeVirtualMethodExpression) aValue);
        }
        if (aValue instanceof FloorExpression) {
            return floorValue((FloorExpression) aValue);
        }
        if (aValue instanceof NewArrayExpression) {
            return newArrayValue((NewArrayExpression) aValue);
        }
        if (aValue instanceof ArrayLengthExpression) {
            return arrayLengthValue((ArrayLengthExpression) aValue);
        }
        if (aValue instanceof StringValue) {
            return stringValue((StringValue) aValue);
        }
        if (aValue instanceof ArrayEntryExpression) {
            return arrayEntryValue((ArrayEntryExpression) aValue);
        }
        if (aValue instanceof CompareExpression) {
            return compareValue((CompareExpression) aValue);
        }
        if (aValue instanceof NegatedExpression) {
            return negateValue((NegatedExpression) aValue);
        }
        if (aValue instanceof InstanceOfExpression) {
            return instanceOfValue((InstanceOfExpression) aValue);
        }
        if (aValue instanceof DoubleValue) {
            return doubleValue((DoubleValue) aValue);
        }
        if (aValue instanceof ResolveCallsiteObjectExpression) {
            return resolveCallSiteObjectValue((ResolveCallsiteObjectExpression) aValue);
        }
        if (aValue instanceof MethodHandlesGeneratedLookupExpression) {
            return methodHandlesGeneratedLookupValue((MethodHandlesGeneratedLookupExpression) aValue);
        }
        if (aValue instanceof MethodTypeExpression) {
            return methodTypeValue((MethodTypeExpression) aValue);
        }
        if (aValue instanceof CurrentExceptionExpression) {
            return currentException((CurrentExceptionExpression) aValue);
        }
        if (aValue instanceof ClassReferenceValue) {
            return classReferenceValue((ClassReferenceValue) aValue);
        }
        if (aValue instanceof TypeOfExpression) {
            return typeOfValue((TypeOfExpression) aValue);
        }
        if (aValue instanceof RuntimeGeneratedTypeExpression) {
            return runtimeGeneratedTypeValue((RuntimeGeneratedTypeExpression) aValue);
        }
        if (aValue instanceof MethodRefExpression) {
            return methodRefValue((MethodRefExpression) aValue);
        }
        if (aValue instanceof NewMultiArrayExpression) {
            return newMultiArrayValue((NewMultiArrayExpression) aValue);
        }
        if (aValue instanceof SqrtExpression) {
            return sqrtValue((SqrtExpression) aValue);
        }
        if (aValue instanceof MaxExpression) {
            return maxValue((MaxExpression) aValue);
        }
        if (aValue instanceof MinExpression) {
            return minValue((MinExpression) aValue);
        }
        if (aValue instanceof FloatingPointFloorExpression) {
            return floatingPointFloor((FloatingPointFloorExpression) aValue);
        }
        if (aValue instanceof FloatingPointCeilExpression) {
            return floatingPointCeil((FloatingPointCeilExpression) aValue);
        }
        throw new IllegalStateException("Not supported : " + aValue);
    }

    private WASMValue floatingPointCeil(final FloatingPointCeilExpression aValue) {
        final List<Value> theArguments = aValue.incomingDataFlows();
        return f32.ceil(toValue(theArguments.get(0)));
    }

    private WASMValue floatingPointFloor(final FloatingPointFloorExpression aValue) {
        final List<Value> theArguments = aValue.incomingDataFlows();
        return f32.floor(toValue(theArguments.get(0)));
    }

    private WASMValue minValue(final MinExpression aValue) {
        final List<Value> theArguments = aValue.incomingDataFlows();
        switch (aValue.resolveType().resolve()) {
        case DOUBLE:
        case FLOAT: {
            return f32.min(toValue(theArguments.get(0)), toValue(theArguments.get(1)));
        }
        default: {
            final WASMValue left = toValue(theArguments.get(0));
            final WASMValue right = toValue(theArguments.get(1));
            return select(left, right, i32.lt_s(left, right));
        }
        }
    }

    private WASMValue maxValue(final MaxExpression aValue) {
        final List<Value> theArguments = aValue.incomingDataFlows();
        switch (aValue.resolveType().resolve()) {
        case DOUBLE:
        case FLOAT: {
            return f32.max(toValue(theArguments.get(0)), toValue(theArguments.get(1)));
        }
        default: {
            final WASMValue left = toValue(theArguments.get(0));
            final WASMValue right = toValue(theArguments.get(1));
            return select(left, right, i32.gt_s(left, right));
        }
        }
    }

    private WASMValue sqrtValue(final SqrtExpression aValue) {
        return f32.sqrt(toValue(aValue.incomingDataFlows().get(0)));
    }

    private WASMValue newMultiArrayValue(final NewMultiArrayExpression aValue) {

        final List<Value> theDimensions = aValue.incomingDataFlows();

        final String theMethodName;
        switch (theDimensions.size()) {
            case 1:
                theMethodName = WASMWriterUtils.toMethodName(
                        BytecodeObjectTypeRef.fromRuntimeClass(MemoryManager.class),
                        "newArray",
                        new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                                Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));
                break;
            case 2:
                theMethodName = WASMWriterUtils.toMethodName(
                        BytecodeObjectTypeRef.fromRuntimeClass(MemoryManager.class),
                        "newArray",
                        new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                                Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));
                break;
            default:
                throw new IllegalStateException("Unsupported number of dimensions : " + theDimensions.size());
        }

        final String theClassName = WASMWriterUtils.toClassName(BytecodeObjectTypeRef.fromRuntimeClass(Array.class));
        final Global theRuntimeClass = module.getGlobals().globalsIndex().globalByLabel(theClassName + RUNTIMECLASSSUFFIX);
        final Function theFunction = module.functionIndex().firstByLabel(theMethodName);

        final List<WASMValue> theArguments = new ArrayList<>();
        theArguments.add(i32.c(0));
        for (final Value theDimension : theDimensions) {
            theArguments.add(toValue(theDimension));
        }
        theArguments.add(getGlobal(theRuntimeClass));
        theArguments.add(weakFunctionTableReference(theClassName + VTABLEFUNCTIONSUFFIX));

        return call(theFunction, theArguments);
    }

    private WASMValue methodRefValue(final MethodRefExpression aValue) {
        final String theMethodName = WASMWriterUtils.toMethodName(
                BytecodeObjectTypeRef.fromUtf8Constant(aValue.getMethodRef().getClassIndex().getClassConstant().getConstant()),
                aValue.getMethodRef().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue(),
                aValue.getMethodRef().getNameAndTypeIndex().getNameAndType().getDescriptorIndex().methodSignature());
        return weakFunctionTableReference(theMethodName);
    }

    private WASMExpression runtimeGeneratedTypeValue(final RuntimeGeneratedTypeExpression aValue) {
        final Function theNew = module.functionIndex().firstByLabel("newLambda");
        return call(theNew, Arrays.asList(toValue(aValue.getType()), toValue(aValue.getMethodRef())));
    }

    private WASMValue typeOfValue(final TypeOfExpression aValue) {
        return i32.load(0, toValue(aValue.incomingDataFlows().get(0)));
    }

    private WASMValue classReferenceValue(final ClassReferenceValue aValue) {
        final BytecodeLinkedClass theLinkedClass = linkerContext.resolveClass(aValue.getType());
        final String globalName = WASMWriterUtils.toClassName(theLinkedClass.getClassName()) + RUNTIMECLASSSUFFIX;
        return getGlobal(module.globalsIndex().globalByLabel(globalName));
    }

    private WASMValue currentException(final CurrentExceptionExpression aValue) {
        return i32.c(0);
    }

    private WASMValue methodTypeValue(final MethodTypeExpression aValue) {
//        print("(i32.const ");
//        print(idResolver.resolveTypeIDForSignature(aValue.getSignature()));
//        print(")");
        return i32.c(0);
    }

    private WASMValue methodHandlesGeneratedLookupValue(final MethodHandlesGeneratedLookupExpression aValue) {
        return i32.c(0);
    }

    private WASMExpression resolveCallSiteObjectValue(final ResolveCallsiteObjectExpression aValue) {
        final Function theFunction = resolver.resolveCallsiteBootstrapFor(aValue.getOwningClass(),
                aValue.getCallsiteId(),
                aValue.getProgram(),
                aValue.getBootstrapMethod()
        );
        return call(theFunction, Collections.emptyList());
    }

    private WASMValue doubleValue(final DoubleValue aValue) {
        return f32.c(((Number) aValue.getDoubleValue()).floatValue());
    }

    private WASMValue instanceOfValue(final InstanceOfExpression aValue) {
        final BytecodeLinkedClass theClass = linkerContext.resolveClass(BytecodeObjectTypeRef.fromUtf8Constant(aValue.getType().getConstant()));
        final Function theFunction = module.functionIndex().firstByLabel("INSTANCEOF_CHECK");
        return call(theFunction, Arrays.asList(toValue(aValue.incomingDataFlows().get(0)), i32.c(theClass.getUniqueId())));
    }

    private WASMValue negateValue(final NegatedExpression aValue) {
        final Value theValue = aValue.incomingDataFlows().get(0);
        switch (theValue.resolveType().resolve()) {
            case DOUBLE:
            case FLOAT: {
                return f32.neg(toValue(theValue));
            }
            default: {
                return i32.mul(i32.c(-1), toValue(theValue));
            }
        }
    }

    private WASMExpression compareValue(final CompareExpression aValue) {

        final List<Value> theIncomingFlows = aValue.incomingDataFlows();
        final Value theValue1 = theIncomingFlows.get(0);
        final Value theValue2 = theIncomingFlows.get(1);

        final WASMValue left = toValue(theValue1);
        final WASMValue right = toValue(theValue2);

        final TypeRef.Native theValue1Type = theValue1.resolveType().resolve();
        final TypeRef.Native theValue2Type = theValue2.resolveType().resolve();
        if (theValue1Type != theValue2Type) {
            throw new IllegalStateException("Does not support mixed types : " + theValue1Type + " -> " + theValue2Type);
        }

        switch (theValue1Type) {
            case DOUBLE:
            case FLOAT:
                return select(i32.c(1), select(i32.c(-1), i32.c(0), f32.lt(left, right)), f32.gt(left, right));
            default:
                return select(i32.c(1), select(i32.c(-1), i32.c(0), i32.lt_s(left, right)), i32.gt_s(left, right));
        }
    }

    private WASMValue arrayEntryValue(final ArrayEntryExpression aValue) {

        final List<Value> theIncomingFlows = aValue.incomingDataFlows();
        final WASMValue thePtr = i32.add(toValue(theIncomingFlows.get(0)), i32.mul(toValue(theIncomingFlows.get(1)), i32.c(4)));

        switch (aValue.resolveType().resolve()) {
            case DOUBLE:
            case FLOAT: {
                return f32.load(20, thePtr);
            }
            default: {
                return i32.load(20, thePtr);
            }
        }
    }

    private WASMValue stringValue(final StringValue aValue) {
        return getGlobal(resolver.globalForStringFromPool(aValue));
    }

    private WASMExpression newArray(final Value aValue) {
        final String theMethodName = WASMWriterUtils.toMethodName(
                BytecodeObjectTypeRef.fromRuntimeClass(MemoryManager.class),
                "newArray",
                new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                        Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));

        final String theClassName = WASMWriterUtils.toClassName(BytecodeObjectTypeRef.fromRuntimeClass(Array.class));
        final Global theRuntimeClass = module.getGlobals().globalsIndex().globalByLabel(theClassName + RUNTIMECLASSSUFFIX);
        final Function theFunction = module.functionIndex().firstByLabel(theMethodName);

        return call(theFunction, Arrays.asList(i32.c(0), toValue(aValue), getGlobal(theRuntimeClass), weakFunctionTableReference(theClassName + VTABLEFUNCTIONSUFFIX)));
    }

    private WASMValue newArrayValue(final NewArrayExpression aValue) {
        return newArray(aValue.incomingDataFlows().get(0));
    }

    private WASMValue arrayLengthValue(final ArrayLengthExpression aValue) {
        return i32.load(16, toValue(aValue.incomingDataFlows().get(0)));
    }

    private WASMValue floorValue(final FloorExpression aValue) {
        return i32.trunc_sF32(f32.floor(toValue(aValue.incomingDataFlows().get(0))));
    }

    private WASMExpression invokeVirtualValue(final InvokeVirtualMethodExpression aValue) {

        final List<Value> theFlows = aValue.incomingDataFlows();

        final Value theTarget = theFlows.get(0);
        final List<Value> theVariables = theFlows.subList(1, theFlows.size());

        final List<PrimitiveType> theSignatureParams = new ArrayList<>();
        theSignatureParams.add(PrimitiveType.i32);
        for (int i = 0; i < aValue.getSignature().getArguments().length; i++) {
            final BytecodeTypeRef theParamType = aValue.getSignature().getArguments()[i];
            theSignatureParams.add(toType(TypeRef.toType(theParamType)));
        }

        final WASMType theCalledFunction;
        if (!aValue.getSignature().getReturnType().isVoid()) {
            theCalledFunction = module.getTypes().typeFor(theSignatureParams, toType(TypeRef.toType(aValue.getSignature().getReturnType())));
        } else {
            theCalledFunction = module.getTypes().typeFor(theSignatureParams);
        }

        final List<WASMValue> theArguments = new ArrayList<>();
        theArguments.add(toValue(theTarget));
        for (final Value theValue : theVariables) {
            theArguments.add(toValue(theValue));
        }

        final BytecodeVirtualMethodIdentifier theMethodIdentifier = linkerContext.getMethodCollection().identifierFor(aValue.getMethodName(), aValue.getSignature());

        final WASMType theResolveType = module.getTypes().typeFor(Arrays.asList(PrimitiveType.i32, PrimitiveType.i32), PrimitiveType.i32);
        final List<WASMValue> theResolveArgument = new ArrayList<>();
        theResolveArgument.add(toValue(theTarget));
        theResolveArgument.add(i32.c(theMethodIdentifier.getIdentifier()));
        final WASMValue theIndex = call(theResolveType, theResolveArgument, i32.load(4, toValue(theTarget)));

        return call(theCalledFunction, theArguments, theIndex);
    }

    private WASMValue floatValue(final FloatValue aValue) {
        return f32.c(aValue.getFloatValue());
    }

    private WASMValue shortValue(final ShortValue aValue) {
        return i32.c(aValue.getShortValue());
    }

    private WASMValue stackTopValue(final StackTopExpression aValue) {
        final Global stackTop = module.globalsIndex().globalByLabel(STACKTOP);
        return getGlobal(stackTop);
    }

    private WASMValue memorySizeValue(final MemorySizeExpression aValue) {
        return i32.mul(currentMemory(), i32.c(65536));
    }

    private WASMValue nullValue(final NullValue aValue) {
        return i32.c(0);
    }

    private WASMValue typeConversion(final TypeConversionExpression aValue) {
        final TypeRef theTargetType = aValue.resolveType();
        final Value theSource = aValue.incomingDataFlows().get(0);
        if (Objects.equals(theTargetType.resolve(), theSource.resolveType().resolve())) {
            // No conversion needed!
            return toValue(theSource);
        }
        switch (theSource.resolveType().resolve()) {
            case DOUBLE:
            case FLOAT: {
                // Convert floating point to something else
                switch (aValue.resolveType().resolve()) {
                    case DOUBLE:
                    case FLOAT: {
                        // No conversion needed
                        return toValue(theSource);
                    }
                    case INT:
                    case SHORT:
                    case BYTE:
                    case LONG:
                    case CHAR: {
                        // Convert f32 to i32
                        return i32.trunc_sF32(toValue(theSource));
                    }
                    default:
                        throw new IllegalStateException("target type " + aValue.resolveType() + " not supported!");
                }
            }
            case INT:
            case LONG:
            case BYTE:
            case SHORT:
            case CHAR: {
                // Convert integer type to something else
                // Convert floating point to something else
                switch (aValue.resolveType().resolve()) {
                case DOUBLE:
                case FLOAT: {
                    // Convert i32 to f32
                    return f32.convert_sI32(toValue(theSource));
                }
                case INT:
                case SHORT:
                case BYTE:
                case LONG:
                case CHAR: {
                    // No conversion needed
                    return toValue(theSource);
                }
                default:
                    throw new IllegalStateException("target type " + aValue.resolveType() + " not supported!");
                }
            }
            default:
                throw new IllegalStateException("Conversion of " + theSource.resolveType() + " not supported!");
        }
    }

    private WASMValue computedMemoryLocationValue(final ComputedMemoryLocationWriteExpression aValue) {
        final List<Value> theIncomingData = aValue.incomingDataFlows();
        return i32.add(toValue(theIncomingData.get(0)), toValue(theIncomingData.get(1)));
    }

    private WASMValue computedMemoryLocationValue(final ComputedMemoryLocationReadExpression aValue) {
        final List<Value> theIncomingData = aValue.incomingDataFlows();
        return i32.load(0, i32.add(toValue(theIncomingData.get(0)), toValue(theIncomingData.get(1))));
    }

    private WASMValue fixedBinaryValue(final FixedBinaryExpression aValue) {

        switch (aValue.getOperator()) {
            case ISNULL: {
                return i32.eq(toValue(aValue.incomingDataFlows().get(0)), i32.c(0));
            }
            case ISNONNULL: {
                return i32.ne(toValue(aValue.incomingDataFlows().get(0)), i32.c(0));
            }
            case ISZERO: {
                return i32.eq(toValue(aValue.incomingDataFlows().get(0)), i32.c(0));
            }
            default: {
                throw new IllegalStateException("Not supported");
            }
        }
    }

    private WASMValue longValue(final LongValue aValue) {
        return i32.c(((Number) aValue.getLongValue()).intValue());
    }

    private WASMValue getStaticValue(final GetStaticExpression aValue) {
        final BytecodeLinkedClass theLinkedClass = linkerContext.resolveClass(BytecodeObjectTypeRef.fromUtf8Constant(aValue.getField().getClassIndex().getClassConstant().getConstant()));

        final WASMMemoryLayouter.MemoryLayout theLayout = memoryLayouter.layoutFor(BytecodeObjectTypeRef.fromUtf8Constant(aValue.getField().getClassIndex().getClassConstant().getConstant()));
        final int theMemoryOffset = theLayout.offsetForClassMember(aValue.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue());
        final String theFieldName = aValue.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue();
        final BytecodeResolvedFields theStaticFields = theLinkedClass.resolvedFields();
        final BytecodeResolvedFields.FieldEntry theField = theStaticFields.fieldByName(theFieldName);
        if (!theField.getValue().getAccessFlags().isStatic()) {
            throw new IllegalStateException("Field " + theFieldName + " is not static!");
        }

        final String theClassName = WASMWriterUtils.toClassName(aValue.getField().getClassIndex().getClassConstant());
        final Global theRuntimeClass = module.getGlobals().globalsIndex().globalByLabel(theClassName + RUNTIMECLASSSUFFIX);
        switch (TypeRef.toType(theField.getValue().getTypeRef()).resolve()) {
            case DOUBLE:
            case FLOAT: {
                return f32.load(theMemoryOffset, getGlobal(theRuntimeClass));
            }
            default: {
                return i32.load(theMemoryOffset, getGlobal(theRuntimeClass));
            }
        }
    }

    private WASMExpression newObjectValue(final NewObjectExpression aValue) {

        final BytecodeObjectTypeRef theType = BytecodeObjectTypeRef.fromUtf8Constant(aValue.getType().getConstant());

        final WASMMemoryLayouter.MemoryLayout theLayout = memoryLayouter.layoutFor(theType);

        final String theMethodName = WASMWriterUtils.toMethodName(
                BytecodeObjectTypeRef.fromRuntimeClass(MemoryManager.class),
                "newObject",
                new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                        Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));

        final BytecodeLinkedClass theLinkedClass = linkerContext.resolveClass(theType);
        final String theClassName = WASMWriterUtils.toClassName(theLinkedClass.getClassName());
        final Global theRuntimeClass = module.getGlobals().globalsIndex().globalByLabel(theClassName + RUNTIMECLASSSUFFIX);
        final Function theFunction = module.functionIndex().firstByLabel(theMethodName);

        return call(theFunction, Arrays.asList(i32.c(0), i32.c(theLayout.instanceSize()), getGlobal(theRuntimeClass), weakFunctionTableReference(theClassName + VTABLEFUNCTIONSUFFIX)));
    }

    private WASMValue getFieldValue(final GetFieldExpression aValue) {
        final BytecodeLinkedClass theLinkedClass = linkerContext.resolveClass(BytecodeObjectTypeRef.fromUtf8Constant(aValue.getField().getClassIndex().getClassConstant().getConstant()));

        final WASMMemoryLayouter.MemoryLayout theLayout = memoryLayouter.layoutFor(BytecodeObjectTypeRef.fromUtf8Constant(aValue.getField().getClassIndex().getClassConstant().getConstant()));
        final int theMemoryOffset = theLayout.offsetForInstanceMember(aValue.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue());
        final String theFieldName = aValue.getField().getNameAndTypeIndex().getNameAndType().getNameIndex().getName().stringValue();

        final BytecodeResolvedFields theInstanceFields = theLinkedClass.resolvedFields();
        final BytecodeResolvedFields.FieldEntry theField = theInstanceFields.fieldByName(theFieldName);
        if (theField.getValue().getAccessFlags().isStatic()) {
            throw new IllegalStateException("Field " + theFieldName + " is static!");
        }

        switch (TypeRef.toType(theField.getValue().getTypeRef()).resolve()) {
            case DOUBLE:
            case FLOAT:
                return f32.load(theMemoryOffset, toValue(aValue.incomingDataFlows().get(0)));
            default:
                return i32.load(theMemoryOffset, toValue(aValue.incomingDataFlows().get(0)));
        }
    }

    private WASMExpression directMethodInvokeValue(final DirectInvokeMethodExpression aValue) {

        final Function function = module.functionIndex().firstByLabel(WASMWriterUtils.toMethodName(aValue.getClazz(), aValue.getMethodName(), aValue.getSignature()));

        final List<Value> theIncomingData = aValue.incomingDataFlows();

        final List<WASMValue> arguments = new ArrayList<>();
        arguments.add(toValue(theIncomingData.get(0)));
        for (final Value theValue : theIncomingData.subList(1, theIncomingData.size())) {
            arguments.add(toValue(theValue));
        }

        return call(function, arguments);
    }

    private WASMExpression invokeStaticValue(final InvokeStaticMethodExpression aValue) {

        final Callable function = weakFunctionReference(WASMWriterUtils.toMethodName(aValue.getClassName(), aValue.getMethodName(), aValue.getSignature()));
        final List<WASMValue> arguments = new ArrayList<>();
        arguments.add(i32.c(0));

        for (final Value theValue : aValue.incomingDataFlows()) {
            arguments.add(toValue(theValue));
        }

        return call(function, arguments);
    }

    private I32Const byteValue(final ByteValue aValue) {
        return i32.c(aValue.getByteValue());
    }

    private I32Const tntegerValue(final IntegerValue aValue) {
        return i32.c(aValue.getIntValue());
    }

    private WASMValue binaryValueI32(final BinaryExpression.Operator aOperator, final Value aValue1, final Value aValue2) {
        switch (aOperator) {
        case NOTEQUALS: {
            return i32.ne(toValue(aValue1), toValue(aValue2));
        }
        case EQUALS: {
            return i32.eq(toValue(aValue1), toValue(aValue2));
        }
        case LESSTHAN: {
            return i32.lt_s(toValue(aValue1), toValue(aValue2));
        }
        case LESSTHANOREQUALS: {
            return i32.le_s(toValue(aValue1), toValue(aValue2));
        }
        case GREATEROREQUALS: {
            return i32.ge_s(toValue(aValue1), toValue(aValue2));
        }
        case GREATERTHAN: {
            return i32.gt_s(toValue(aValue1), toValue(aValue2));
        }
        case ADD: {
            return i32.add(toValue(aValue1), toValue(aValue2));
        }
        case MUL: {
            return i32.mul(toValue(aValue1), toValue(aValue2));
        }
        case DIV: {
            return f32.div(toFloatValue(aValue1), toFloatValue(aValue2));
        }
        case REMAINDER: {
            final WASMValue a = toValue(aValue1);
            final WASMValue b = toValue(aValue2);
            return i32.rem_s(a, b);
        }
        case SUB: {
            return i32.sub(toValue(aValue1), toValue(aValue2));
        }
        case BINARYXOR: {
            return i32.xor(toValue(aValue1), toValue(aValue2));
        }
        case BINARYOR: {
            return i32.or(toValue(aValue1), toValue(aValue2));
        }
        case BINARYAND: {
            return i32.and(toValue(aValue1), toValue(aValue2));
        }
        case BINARYSHIFTLEFT: {
            return i32.shl(toValue(aValue1), toValue(aValue2));
        }
        case BINARYSHIFTRIGHT: {
            return i32.shr_s(toValue(aValue1), toValue(aValue2));
        }
        case BINARYUNSIGNEDSHIFTRIGHT: {
            return i32.shr_u(toValue(aValue1), toValue(aValue2));
        }
        default:
            throw new IllegalStateException("Operator not supported : " + aOperator);
        }
    }

    private WASMValue binaryValueF32(final BinaryExpression.Operator aOperator, final Value aValue1, final Value aValue2) {
        switch (aOperator) {
        case NOTEQUALS: {
            return f32.ne(toValue(aValue1), toValue(aValue2));
        }
        case EQUALS: {
            return f32.eq(toValue(aValue1), toValue(aValue2));
        }
        case LESSTHAN: {
            return f32.lt(toValue(aValue1), toValue(aValue2));
        }
        case LESSTHANOREQUALS: {
            return f32.le(toValue(aValue1), toValue(aValue2));
        }
        case GREATEROREQUALS: {
            return f32.ge(toValue(aValue1), toValue(aValue2));
        }
        case GREATERTHAN: {
            return f32.gt(toValue(aValue1), toValue(aValue2));
        }
        case ADD: {
            return f32.add(toValue(aValue1), toValue(aValue2));
        }
        case MUL: {
            return f32.mul(toValue(aValue1), toValue(aValue2));
        }
        case DIV: {
            return f32.div(toValue(aValue1), toValue(aValue2));
        }
        case REMAINDER: {
            final WASMValue a = toValue(aValue1);
            final WASMValue b = toValue(aValue2);
            return f32.sub(a, f32.mul(b, f32.trunc(f32.div(a, b))));
        }
        case SUB: {
            return f32.sub(toValue(aValue1), toValue(aValue2));
        }
        default:
            throw new IllegalStateException("Operator not supported : " + aOperator);
        }
    }


    private WASMValue binaryValue(final BinaryExpression aValue) {

        final List<Value> theIncomingData = aValue.incomingDataFlows();

        final Value theValue1 = theIncomingData.get(0);
        final Value theValue2 = theIncomingData.get(1);

        final String theType1 = WASMWriterUtils.toType(theValue1.resolveType());
        final String theType2 = WASMWriterUtils.toType(theValue2.resolveType());
        switch (theType1) {
            case "i32": {
                return binaryValueI32(aValue.getOperator(), theValue1, theValue2);
            }
            case "f32": {
                return binaryValueF32(aValue.getOperator(), theValue1, theValue2);
            }
            default: {
                throw new IllegalArgumentException("Not supported type : " + theType1);
            }
        }
    }

    private void generateReturnExpression(final ReturnExpression aExpression) {
        stackExit();
        flow.ret();
    }

    private void generateReturnExpression(final ReturnValueExpression aExpression) {
        stackExit();
        flow.ret(toValue(aExpression.incomingDataFlows().get(0)));
    }

    private WASMValue toFloatValue(final Value aValue) {
        switch (aValue.resolveType().resolve()) {
            case DOUBLE:
            case FLOAT:
                return toValue(aValue);
            default:
                return f32.convert_sI32(toValue(aValue));
        }
    }

    private WASMValue variableName(final Variable aVariable) {
        final Local local = function.localByLabel(aVariable.getName());
        return getLocal(local);
    }

    public void stackEnter() {

        final int theStackSize = stackSize();
        if (theStackSize > 0) {
            final Global stackTop = module.getGlobals().globalsIndex().globalByLabel(STACKTOP);
            final Local sp = function.newLocal(SP, PrimitiveType.i32);
            final Local oldsp = function.newLocal(OLDSP, PrimitiveType.i32);
            flow.setLocal(oldsp, getGlobal(stackTop));
            flow.setGlobal(stackTop, i32.sub(getGlobal(stackTop), i32.c(theStackSize)));
            flow.setLocal(sp, getGlobal(stackTop));
        }
    }

    private void stackExit() {

        final int theStackSize = stackSize();
        if (theStackSize > 0) {
            flow.setGlobal(module.getGlobals().globalsIndex().globalByLabel(STACKTOP), getLocal(function.localByLabel(OLDSP)));
        }
    }

    public void writeRelooped(final Relooper.Block aBlock) {
        // We need the local label for structured control flow
        function.newLocal(LABEL_LOCAL, PrimitiveType.i32);
        stackEnter();
        writeReloopedInternal(aBlock);
        flow.unreachable();
    }

    private void writeReloopedInternal(final Relooper.Block aBlock) {
        if (aBlock == null) {
            return;
        }
        if (aBlock instanceof Relooper.SimpleBlock) {
            writeSimpleBlock((Relooper.SimpleBlock) aBlock);
            return;
        }
        if (aBlock instanceof Relooper.LoopBlock) {
            writeLoopBlock((Relooper.LoopBlock) aBlock);
            return;
        }
        if (aBlock instanceof Relooper.MultipleBlock) {
            writeMultipleBlock((Relooper.MultipleBlock) aBlock);
            return;
        }
        throw new IllegalStateException("Don't know how to handle : " + aBlock);
    }

    private void writeSimpleBlock(final Relooper.SimpleBlock aSimpleBlock) {
        WASMSSAASTWriter theWriter = this;

        final boolean canThrowExeption = aSimpleBlock.internalLabel().canThrowException() && compileOptions.isEnableExceptions();
        if (canThrowExeption) {
            theWriter = Try(aSimpleBlock.label().name());
            theWriter.writeExpressionList(aSimpleBlock.internalLabel().getExpressions());

            final Try theTry = (Try) theWriter.container;

            theTry.catchBlock.flow.rethrowException();
        } else {
            if (aSimpleBlock.isLabelRequired()) {
                theWriter = block(aSimpleBlock.label().name());
            }

            theWriter.writeExpressionList(aSimpleBlock.internalLabel().getExpressions());
        }

        writeReloopedInternal(aSimpleBlock.next());
    }

    private void writeLoopBlock(final Relooper.LoopBlock aLoopBlock) {
        WASMSSAASTWriter theWriter = this;
        if (aLoopBlock.isLabelRequired()) {
            theWriter = block(aLoopBlock.label().name());
        }

        final WASMSSAASTWriter loop = theWriter.loop(aLoopBlock.label().name() + "_inner");
        loop.writeReloopedInternal(aLoopBlock.inner());

        writeReloopedInternal(aLoopBlock.next());
    }

    private void writeMultipleBlock(final Relooper.MultipleBlock aMultipleBlock) {
        WASMSSAASTWriter theWriter = this;
        if (aMultipleBlock.isLabelRequired()) {
            theWriter = block(aMultipleBlock.label().name());
        }

        final WASMSSAASTWriter loop = theWriter.loop(aMultipleBlock.label().name() + "_inner");

        final Local label = function.localByLabel(LABEL_LOCAL);

        for (final Relooper.Block theHandler : aMultipleBlock.handlers()) {
            for (final RegionNode theEntry : theHandler.entries()) {
                final int theEntryAddress = theEntry.getStartAddress().getAddress();

                final WASMSSAASTWriter block = loop.iff("case_" + theEntryAddress, i32.eq(getLocal(label), i32.c(theEntryAddress)));
                block.writeReloopedInternal(theHandler);
            }
        }

        writeReloopedInternal(aMultipleBlock.next());
    }
}