/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.tugraz.sysds.runtime.instructions.cp;

import org.tugraz.sysds.api.DMLScript;
import org.tugraz.sysds.common.Types.ExecMode;
import org.tugraz.sysds.common.Types.DataType;
import org.tugraz.sysds.runtime.DMLRuntimeException;
import org.tugraz.sysds.runtime.controlprogram.caching.CacheableData;
import org.tugraz.sysds.runtime.controlprogram.context.ExecutionContext;
import org.tugraz.sysds.runtime.functionobjects.Builtin;
import org.tugraz.sysds.runtime.instructions.InstructionUtils;
import org.tugraz.sysds.runtime.matrix.data.MatrixBlock;
import org.tugraz.sysds.runtime.matrix.data.MatrixIndexes;
import org.tugraz.sysds.runtime.matrix.operators.AggregateUnaryOperator;
import org.tugraz.sysds.runtime.matrix.operators.Operator;
import org.tugraz.sysds.runtime.matrix.operators.SimpleOperator;
import org.tugraz.sysds.runtime.meta.MatrixCharacteristics;

public class AggregateUnaryCPInstruction extends UnaryCPInstruction
{
	public enum AUType {
		NROW, NCOL, LENGTH, EXISTS,
		DEFAULT;
		public boolean isMeta() {
			return this != DEFAULT;
		}
	}
	
	private final AUType _type;
	
	private AggregateUnaryCPInstruction(Operator op, CPOperand in, CPOperand out, AUType type, String opcode, String istr) {
		this(op, in, null, null, out, type, opcode, istr);
	}

	protected AggregateUnaryCPInstruction(Operator op, CPOperand in1, CPOperand in2, CPOperand in3, CPOperand out,
			AUType type, String opcode, String istr) {
		super(CPType.AggregateUnary, op, in1, in2, in3, out, opcode, istr);
		_type = type;
	}
	
	public static AggregateUnaryCPInstruction parseInstruction(String str) {
		String[] parts = InstructionUtils.getInstructionPartsWithValueType(str);
		String opcode = parts[0];
		CPOperand in1 = new CPOperand(parts[1]);
		CPOperand out = new CPOperand(parts[2]);
		
		if(opcode.equalsIgnoreCase("nrow") || opcode.equalsIgnoreCase("ncol") 
			|| opcode.equalsIgnoreCase("length") || opcode.equalsIgnoreCase("exists")){
			return new AggregateUnaryCPInstruction(new SimpleOperator(Builtin.getBuiltinFnObject(opcode)),
				in1, out, AUType.valueOf(opcode.toUpperCase()), opcode, str);
		}
		else { //DEFAULT BEHAVIOR
			AggregateUnaryOperator aggun = InstructionUtils
				.parseBasicAggregateUnaryOperator(opcode, Integer.parseInt(parts[3]));
			return new AggregateUnaryCPInstruction(aggun, in1, out, AUType.DEFAULT, opcode, str);
		}
	}
	
	@Override
	public void processInstruction( ExecutionContext ec ) {
		String output_name = output.getName();
		String opcode = getOpcode();
		
		if( _type.isMeta() && _type!=AUType.EXISTS ) //nrow/ncol/length
		{
			//check existence of input variable
			if( !ec.getVariables().keySet().contains(input1.getName()) )
				throw new DMLRuntimeException("Variable '"+input1.getName()+"' does not exist.");
			
			//get meta data information
			long rval = -1;
			if (input1.getDataType() == DataType.LIST && _type == AUType.LENGTH ) {
				rval = ((ListObject)ec.getVariable(input1.getName())).getLength();
			}
			else if( input1.getDataType().isMatrix() || input1.getDataType().isFrame() ) {
				MatrixCharacteristics mc = ec.getMatrixCharacteristics(input1.getName());
				rval = getSizeMetaData(_type, mc);
	
				//check for valid output, and acquire read if necessary
				//(Use case: In case of forced exec type singlenode, there are no reblocks. For csv
				//we however, support unspecified input sizes, which requires a read to obtain the
				//required meta data)
				//Note: check on matrix characteristics to cover incorrect length (-1*-1 -> 1)
				if( !mc.dimsKnown() ) //invalid nrow/ncol/length
				{
					if( DMLScript.getGlobalExecMode() == ExecMode.SINGLE_NODE 
						|| input1.getDataType() == DataType.FRAME )
					{
						//read the input matrix/frame and explicitly refresh meta data
						CacheableData<?> obj = ec.getCacheableData(input1.getName());
						obj.acquireRead();
						obj.refreshMetaData();
						obj.release();
						
						//update meta data information
						mc = ec.getMatrixCharacteristics(input1.getName());
						rval = getSizeMetaData(_type, mc);
					}
					else {
						throw new DMLRuntimeException("Invalid meta data returned by '"+opcode+"': "+rval + ":" + instString);
					}
				}
			}
			
			//create and set output scalar
			ec.setScalarOutput(output_name, new IntObject(rval));
		}
		else if( _type == AUType.EXISTS ) {
			//probe existence of variable in symbol table w/o error
			String varName = !input1.isScalar() ? input1.getName() :
				ec.getScalarInput(input1).getStringValue();
			boolean rval = ec.getVariables().keySet().contains(varName);
			//create and set output scalar
			ec.setScalarOutput(output_name, new BooleanObject(rval));
		}
		else { //DEFAULT
			MatrixBlock matBlock = ec.getMatrixInput(input1.getName());
			AggregateUnaryOperator au_op = (AggregateUnaryOperator) _optr;
			
			MatrixBlock resultBlock = (MatrixBlock) matBlock.aggregateUnaryOperations(au_op, new MatrixBlock(),
				matBlock.getNumRows(), matBlock.getNumColumns(), new MatrixIndexes(1, 1), true);
			
			ec.releaseMatrixInput(input1.getName());
			if(output.getDataType() == DataType.SCALAR){
				DoubleObject ret = new DoubleObject(resultBlock.getValue(0, 0));
				ec.setScalarOutput(output_name, ret);
			} else{
				// since the computed value is a scalar, allocate a "temp" output matrix
				ec.setMatrixOutput(output_name, resultBlock);
			}
		}
	}
	
	private static long getSizeMetaData(AUType type, MatrixCharacteristics mc) {
		switch( type ) {
			case NROW: return mc.getRows();
			case NCOL: return mc.getCols();
			case LENGTH: return mc.getRows() * mc.getCols();
			default:
				throw new RuntimeException("Opcode not applicable: "+type.name());
		}
	}
}
