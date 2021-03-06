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

package org.tugraz.sysds.runtime.controlprogram.paramserv.dp;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.tugraz.sysds.runtime.controlprogram.caching.MatrixObject;
import org.tugraz.sysds.runtime.controlprogram.paramserv.ParamservUtils;
import org.tugraz.sysds.runtime.matrix.data.MatrixBlock;
import org.tugraz.sysds.runtime.util.DataConverter;

/**
 * Disjoint_Round_Robin data partitioner:
 * for each worker, use a permutation multiply
 * or simpler a removeEmpty such as removeEmpty
 * (target=X, margin=rows, select=(seq(1,nrow(X))%%k)==id)
 */
public class DRRLocalScheme extends DataPartitionLocalScheme {

	public static MatrixBlock removeEmpty(MatrixBlock mb, int k, int workerId) {
		double[] data = LongStream.range(0, mb.getNumRows()).mapToDouble(l -> l % k == workerId ? 1 : 0).toArray();
		MatrixBlock select = DataConverter.convertToMatrixBlock(data, true);
		return mb.removeEmptyOperations(new MatrixBlock(), true, true, select);
	}

	private MatrixObject internalRemoveEmpty(MatrixBlock mb, int k, int workerId) {
		MatrixObject result = ParamservUtils.newMatrixObject(removeEmpty(mb, k, workerId));
		result.enableCleanup(false);
		return result;
	}

	@Override
	public Result doPartitioning(int workersNum, MatrixBlock features, MatrixBlock labels) {
		List<MatrixObject> pfs = IntStream.range(0, workersNum).mapToObj(i -> internalRemoveEmpty(features, workersNum, i)).collect(Collectors.toList());
		List<MatrixObject> pls = IntStream.range(0, workersNum).mapToObj(i -> internalRemoveEmpty(labels, workersNum, i)).collect(Collectors.toList());
		return new Result(pfs, pls);
	}
}
