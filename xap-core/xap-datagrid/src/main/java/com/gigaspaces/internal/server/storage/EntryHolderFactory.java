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

package com.gigaspaces.internal.server.storage;

import com.gigaspaces.internal.metadata.EntryType;
import com.gigaspaces.internal.metadata.EntryTypeDesc;
import com.gigaspaces.internal.server.metadata.IServerTypeDesc;
import com.gigaspaces.internal.transport.IEntryPacket;
import com.gigaspaces.time.SystemTime;
import com.j_spaces.core.LeaseManager;
import com.j_spaces.core.XtnEntry;
import com.j_spaces.core.cache.CacheManager;
import com.j_spaces.core.cache.EntryCacheInfoFactory;
import com.j_spaces.core.cache.IEntryCacheInfo;
import com.j_spaces.core.cache.layeredStorage.EntryStorageLayer;
import com.j_spaces.core.cache.offHeap.OffHeapEntryHolder;
import com.j_spaces.core.client.ClientUIDHandler;
import com.j_spaces.kernel.IObjectInfo;
import com.j_spaces.kernel.IStoredList;

import java.util.ArrayList;

/**
 * Factory to create EntryHolder instances.
 *
 * @author Niv Ingberg
 * @since 7.0
 */
@com.gigaspaces.api.InternalApi
public class EntryHolderFactory {

    private final CacheManager _cacheManager;

    public EntryHolderFactory(CacheManager cm)
    {
        _cacheManager = cm;
    }

    public  IEntryHolder createEntryHolder(IServerTypeDesc typeDesc, IEntryPacket entryPacket,
                                                 EntryDataType entryDataType, String uid, long expirationTime, XtnEntry xidOriginated, long scn, boolean offHeapEntryHolder) {
        return createEntryHolder(typeDesc, entryPacket,
                entryDataType, uid, expirationTime, xidOriginated, scn,
                -1 /*versionID*/, false /*keepExpiration*/, offHeapEntryHolder);
    }


    public IEntryHolder createEntryHolder(IServerTypeDesc typeDesc, IEntryPacket entryPacket,
                                                 EntryDataType entryDataType, String uid, long expirationTime,
                                                 XtnEntry xidOriginated, long scn, int versionID, boolean keepExpiration, boolean offHeapEntryHolder) {
        if (offHeapEntryHolder)
            return
                    createOffHeapEntryHolder(typeDesc, entryPacket,
                            entryDataType, uid, expirationTime,
                            xidOriginated, scn, versionID, keepExpiration);

        ITransactionalEntryData entryData =
                createEntryData(entryPacket, entryDataType, versionID,
                        expirationTime, xidOriginated != null, keepExpiration);

        if (xidOriginated != null)
            entryData.setXidOriginated(xidOriginated);

        return new EntryHolder(typeDesc, uid, scn, entryPacket.isTransient(), entryData);
    }

    /**
     * Used by DataAdaptor, the EntryHolder Ctor make sure the version is always bigger than 0.
     */
    public IEntryHolder createEntryHolder(IServerTypeDesc typeDesc,
                                                 IEntryPacket entryPacket, EntryDataType entryDataType) {
        String uid;
        if (entryPacket.getUID() != null)
            uid = entryPacket.getUID();
        else //generate UID from Primary key
            uid = ClientUIDHandler.createUIDFromName(
                    entryPacket.getPropertyValue(typeDesc.getTypeDesc().getDefaultPropertyName()).toString(),
                    entryPacket.getTypeName());

        int version = entryPacket.getVersion();
        ITransactionalEntryData entryData = createEntryData(entryPacket, entryDataType,
                version > 0 ? version : 1, -1 /*lease*/, false /*createEntryXtnInfo*/, false /*keepExpiration*/);

        return new EntryHolder(typeDesc, uid, SystemTime.timeMillis(), entryPacket.isTransient(), entryData);
    }

    public static IEntryHolder createEntryHolder(IServerTypeDesc typeDesc,
                                                 ITransactionalEntryData entryData, String uid, boolean isTransient) {
        return new EntryHolder(typeDesc, uid, SystemTime.timeMillis(), isTransient, entryData);

    }

    public static ShadowEntryHolder createShadowEntryHolder(IEntryHolder master,
                                                            ArrayList<IObjectInfo<IEntryCacheInfo>> backRefs, int[] backrefIndexPos,
                                                            IStoredList<Object> leaseManagerListRef, IObjectInfo<Object> leaseManagerPosRef) {
        return new ShadowEntryHolder(master, backRefs, backrefIndexPos, leaseManagerListRef, leaseManagerPosRef);
    }


    private static IEntryHolder createOffHeapEntryHolder(IServerTypeDesc typeDesc, IEntryPacket entryPacket,
                                                         EntryDataType entryDataType, String uid, long expirationTime,
                                                         XtnEntry xidOriginated, long scn, int versionID, boolean keepExpiration) {
        ITransactionalEntryData entryData =
                createEntryData(entryPacket, entryDataType, versionID,
                        expirationTime, xidOriginated != null, keepExpiration);

        if (xidOriginated != null)
            entryData.setXidOriginated(xidOriginated);

        IEntryHolder entryHolder = new OffHeapEntryHolder(typeDesc, uid, scn, entryPacket.isTransient(), entryData);
        EntryCacheInfoFactory.createOffHeapEntryCacheInfo(entryHolder);//create the cache info as a resident part for locking etc
        return entryHolder;
    }

    private static ITransactionalEntryData createEntryData(IEntryPacket entryPacket,
                                                           EntryDataType entryDataType, int versionID, long expiration, boolean createXtnEntryInfo, boolean keepExpiration) {
        final EntryType entryType = entryPacket.getEntryType();
        if (entryType == null)
            throw new IllegalStateException("entryPacket.getEntryType() is null (packet class: " + entryPacket.getClass().getName() + ").");

        final EntryTypeDesc entryTypeDesc = entryPacket.getTypeDescriptor().getEntryTypeDesc(entryType);
        final int version = versionID > 0 ? versionID : entryPacket.getVersion();
        final long lease = (expiration > 0 || keepExpiration) ? expiration : LeaseManager.toAbsoluteTime(entryPacket.getTTL());

        if (entryDataType == EntryDataType.FLAT)
            return new FlatEntryData(entryPacket.getFieldValues(), entryPacket.getDynamicProperties(),
                    entryTypeDesc, version, lease, createXtnEntryInfo);

        return new UserTypeEntryData(entryPacket.toObject(entryType), entryTypeDesc, version, lease, createXtnEntryInfo);
    }

    private EntryStorageLayer selectStorageLayer(IServerTypeDesc typeDesc,ITransactionalEntryData entryData,EntryCreationReason reason )
    {
      if (reason == EntryCreationReason.OTHER_OP)
          return EntryStorageLayer.TRANSIENT;
        else if (reason == EntryCreationReason.WRITE || reason == EntryCreationReason.DB_INITIALLOAD)
          return _cacheManager.getStorageLayerSelector().selectLayer(entryData,typeDesc.getTypeName(),reason);
        else if (reason == EntryCreationReason.DB_ITER)
          return EntryStorageLayer.DB_BASED;
        else if (reason == EntryCreationReason.BLOBSTORE_INITIALLOAD)
          return EntryStorageLayer.BLOBSTORE_BASED;
        else if (reason == EntryCreationReason.DB_INITIALLOAD)
          return EntryStorageLayer.BLOBSTORE_BASED;

        throw new IllegalArgumentException();

    }
}
