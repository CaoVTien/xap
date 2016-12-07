/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
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

package com.gigaspaces.internal.server.space.redolog;

import com.gigaspaces.internal.cluster.node.impl.packets.IReplicationOrderedPacket;
import com.gigaspaces.internal.server.space.redolog.storage.INonBatchRedoLogFileStorage;

/**
 * Configures a {@link FixedSizeSwapRedoLogFileConfig}
 *
 * @author eitany
 * @since 7.1
 */
@com.gigaspaces.api.InternalApi
public class FixedSizeSwapRedoLogFileConfig<T extends IReplicationOrderedPacket> {
    private final int _memoryMaxCapacity;
    private final INonBatchRedoLogFileStorage<T> _redoLogFileStorage;
    private final int _fetchBatchSize;

    public FixedSizeSwapRedoLogFileConfig(int memoryMaxCapacity,
                                          int fetchBatchSize, INonBatchRedoLogFileStorage<T> redoLogFileStorage) {
        if (fetchBatchSize > memoryMaxCapacity)
            throw new IllegalArgumentException("fetchBatchSize cannot be more than memoryMaxCapacity");
        this._memoryMaxCapacity = memoryMaxCapacity;
        this._fetchBatchSize = fetchBatchSize;
        this._redoLogFileStorage = redoLogFileStorage;
    }

    public int getMemoryMaxPackets() {
        return _memoryMaxCapacity;
    }

    public INonBatchRedoLogFileStorage<T> getRedoLogFileStorage() {
        return _redoLogFileStorage;
    }

    public int getFetchBatchSize() {
        return _fetchBatchSize;
    }

}