/*
 * Modifications Copyright 2018 Graz University of Technology
 * 
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


package org.tugraz.sysds.runtime.data;


public abstract class DenseBlockDRB extends DenseBlock
{
	private static final long serialVersionUID = 3581157975703708947L;

	protected DenseBlockDRB(int[] dims) {
		super(dims);
	}

	@Override
	public int numBlocks() {
		return 1;
	}
	
	@Override
	public int blockSize() {
		return _rlen;
	}
	
	@Override
	public int blockSize(int bix) {
		return _rlen;
	}
	
	@Override
	public boolean isContiguous() {
		return true;
	}
	
	@Override
	public boolean isContiguous(int rl, int ru) {
		return true;
	}
	
	@Override
	public int size(int bix) {
		return (int)size();
	}
	
	@Override
	public int pos(int r) {
		return r * _odims[0];
	}

	@Override
	public int pos(int r, int c) {
		return r * _odims[0] + c;
	}
	
	@Override
	public int pos(int[] ix) {
		int pos = ix[ix.length-1];
		for(int i=0; i<ix.length-1; i++)
			pos += ix[i] * _odims[i];
		return pos;
	}
}
