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

//
package com.j_spaces.core.cache.offHeap;

import com.gigaspaces.internal.cluster.node.impl.directPersistency.embeddedSyncList.EntryHolderEmbeddedSyncOpInfo;
import com.gigaspaces.internal.server.storage.IEntryHolder;
import com.j_spaces.core.cache.CacheManager;
import com.j_spaces.core.cache.context.Context;
import com.j_spaces.core.cache.offHeap.storage.bulks.BlobStoreBulkInfo;

/**
 * extention of entry-holder for off-heap
 *
 * @author yechiel
 * @since 9.8
 */
public interface IOffHeapEntryHolder {
    void setOffHeapResidentPart(OffHeapRefEntryCacheInfo offHeapResidentPart);

    OffHeapRefEntryCacheInfo getOffHeapResidentPart();

    IEntryHolder getLatestEntryVersion(CacheManager cacheManager, boolean attatchToMemory, Context attachingContext);

    short getOffHeapVersion();

    void setOffHeapVersion(short version);

    void setDirty(CacheManager cacheManager);

    String getTypeName();

    byte getEntryTypeCode();

    void insertOrTouchInternalCache(CacheManager cacheManager);

    BlobStoreBulkInfo getBulkInfo();

    void setBulkInfo(BlobStoreBulkInfo bulkInfo);

    boolean isAlwaysPinned();

    void setAlwaysPinned();

    //----------------- embedded sync list related --------------------------------
    EntryHolderEmbeddedSyncOpInfo getEmbeddedSyncOpInfo();

    void setEmbeddedSyncOpInfo(long generationId, long sequenceId, boolean phantom, boolean partOfMultipleUidsInfo);

    boolean isPhantom();
}
