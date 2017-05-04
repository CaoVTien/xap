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
package com.gigaspaces.internal.server.space.recovery.direct_persistency;

import com.gigaspaces.attribute_store.AttributeStore;
import com.gigaspaces.attribute_store.PropertiesFileAttributeStore;
import com.gigaspaces.attribute_store.TransientAttributeStore;
import com.gigaspaces.cluster.activeelection.ISpaceModeListener;
import com.gigaspaces.cluster.activeelection.SpaceMode;
import com.gigaspaces.internal.server.space.SpaceEngine;
import com.gigaspaces.internal.server.space.SpaceImpl;
import com.gigaspaces.start.SystemInfo;
import com.j_spaces.core.Constants;
import com.j_spaces.kernel.ClassLoaderHelper;
import com.j_spaces.kernel.SystemProperties;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.j_spaces.core.Constants.DirectPersistency.ZOOKEEPER.ATTRIBUET_STORE_HANDLER_CLASS_NAME;

/**
 * helper functions in order to maintain direct-persistency recovery consistency
 *
 * @author yechiel
 * @since 10.1
 */
@com.gigaspaces.api.InternalApi
public class DirectPersistencyRecoveryHelper implements IStorageConsistency, ISpaceModeListener {

    private final static long WAIT_FOR_ANOTHER_PRIMARY_MAX_TIME = 5 * 60 * 1000;


    private final SpaceImpl _spaceImpl;
    private final IStorageConsistency _storageConsistencyHelper;
    private volatile boolean _pendingBackupRecovery;
    private final Logger _logger;
    private AttributeStore _attributeStore;
    private final String _attributeStoreKey;
    private String attributeStoreValue;
    private final String _fullSpaceName;
    private final int _recoverRetries = Integer.getInteger(SystemProperties.DIRECT_PERSISTENCY_RECOVER_RETRIES,
            SystemProperties.DIRECT_PERSISTENCY_RECOVER_RETRIES_DEFAULT);
    private final String LAST_PRIMARY_PATH_PROPERTY = "com.gs.blobstore.zookeeper.lastprimarypath";
    public static final String LAST_PRIMARY_ZOOKEEPER_PATH_DEFAULT = "/last_primary";

    private final boolean isMemoryXtendSpace;
    public DirectPersistencyRecoveryHelper(SpaceImpl spaceImpl, Logger logger) {
        _spaceImpl = spaceImpl;
        _logger = logger;

        Boolean isLastPrimaryStateKeeperEnabled = Boolean.parseBoolean((String) _spaceImpl.getCustomProperties().get(Constants.CacheManager.FULL_CACHE_MANAGER_BLOBSTORE_PERSISTENT_PROP));
        boolean useZooKeeper = !SystemInfo.singleton().getManagerClusterInfo().isEmpty();
        final SpaceEngine spaceEngine = spaceImpl.getEngine();
        _storageConsistencyHelper = spaceEngine.getCacheManager().isOffHeapCachePolicy() && isLastPrimaryStateKeeperEnabled
                ? spaceEngine.getCacheManager().getBlobStoreRecoveryHelper()
                : new DefaultStorageConsistency();
        _fullSpaceName = spaceEngine.getFullSpaceName();
        _attributeStoreKey = spaceEngine.getSpaceName() + "." + spaceEngine.getPartitionIdOneBased() + ".primary";
        final String lastPrimaryZookeepertPath = System.getProperty(LAST_PRIMARY_PATH_PROPERTY, LAST_PRIMARY_ZOOKEEPER_PATH_DEFAULT);
        isMemoryXtendSpace = spaceEngine.getCacheManager().isOffHeapCachePolicy() && _storageConsistencyHelper.isPerInstancePersistency();
        if (isMemoryXtendSpace) {
            AttributeStore attributeStoreImpl = (AttributeStore) _spaceImpl.getCustomProperties().get(Constants.DirectPersistency.DIRECT_PERSISTENCY_ATTRIBURE_STORE_PROP);
            if (attributeStoreImpl == null) {
                if (useZooKeeper) {
                    _attributeStore = createZooKeeperAttributeStore(lastPrimaryZookeepertPath);
                } else {
                    String attributeStorePath = System.getProperty(Constants.StorageAdapter.DIRECT_PERSISTENCY_LAST_PRIMARY_STATE_PATH_PROP);
                    if (attributeStorePath == null)
                        attributeStorePath = SystemInfo.singleton().locations().work() + File.separator + this.getClass().getSimpleName();
                    _attributeStore = new PropertiesFileAttributeStore(attributeStorePath);
                }
            } else {
                _attributeStore = attributeStoreImpl;
            }
        }
        else if(useZooKeeper){
            _attributeStore = createZooKeeperAttributeStore(lastPrimaryZookeepertPath);
        }
        else {
            _attributeStore = new TransientAttributeStore();
        }

        attributeStoreValue = _fullSpaceName;
        if(!isMemoryXtendSpace){
            attributeStoreValue += "#_#" + _spaceImpl.getSpaceUuid().toString();
        }

        // add DirectPersistencyRecoveryHelper as a listener to spaceMode changed events to set last primary when afterSpaceModeChange occurs
        _spaceImpl.addSpaceModeListener(this);
    }

    @Override
    public StorageConsistencyModes getStorageState() {
        return _storageConsistencyHelper.getStorageState();
    }

    @Override
    public void setStorageState(StorageConsistencyModes s) {
        _storageConsistencyHelper.setStorageState(s);
    }

    @Override
    public boolean isPerInstancePersistency() {
        return _storageConsistencyHelper.isPerInstancePersistency();
    }


    public void beforePrimaryElectionProcess() {
        if (!_storageConsistencyHelper.isPerInstancePersistency())
            return;
        StorageConsistencyModes res = _storageConsistencyHelper.getStorageState();
        boolean validStorageState = res != StorageConsistencyModes.Inconsistent;
        if (_logger.isLoggable(Level.INFO))
            _logger.log(Level.INFO, "space tested for storageconsistency - result=" + res);

        String latestPrimary = getLastPrimaryName();
        if (_logger.isLoggable(Level.INFO))
            _logger.log(Level.INFO, "space tested for latest-primary - result=" + latestPrimary);

        boolean iWasPrimary = _spaceImpl.getEngine().getFullSpaceName().equals(latestPrimary);
        boolean iMayBePrimary = ((iWasPrimary || latestPrimary == null) && validStorageState);
        if (iMayBePrimary)
            return; //passed ok)

        if (!validStorageState && iWasPrimary) {
            if (_logger.isLoggable(Level.SEVERE))
                _logger.log(Level.SEVERE, "Inconsistent storage state but space was primary]");
            throw new DirectPersistencyRecoveryException("Failed to start [" + (_spaceImpl.getEngine().getFullSpaceName())
                    + "] inconsistent storage state but space was primary]");

        }
        if (_logger.isLoggable(Level.INFO)) {
            _logger.log(Level.INFO, "Waiting for any other space to become primary");
        }
        GSDirectPersistencyLusWaiter waiter;
        //initiate a thread that scans the lus waiting for primary to be - temp solution until election is changed
        try {
            waiter = new GSDirectPersistencyLusWaiter(_spaceImpl,
                    _spaceImpl.getClusterPolicy(),
                    _logger, _spaceImpl.getJoinManager(),
                    new Object());
        } catch (Exception ex) {
            _logger.severe("Exception while initiating waiter-for-primary thread " + ex);
            if (ex instanceof DirectPersistencyRecoveryException)
                throw (DirectPersistencyRecoveryException) ex;
            throw new DirectPersistencyRecoveryException("space " + _spaceImpl.getEngine().getFullSpaceName() + " got exception while initiating waiter-for-primary thread", ex);
        }
        waiter.start();
        waiter.waitForAnotherPrimary(WAIT_FOR_ANOTHER_PRIMARY_MAX_TIME);

    }

    public boolean isInconsistentStorage() {
        return (_storageConsistencyHelper.getStorageState() == StorageConsistencyModes.Inconsistent);
    }


    private String getLastPrimaryName() {
        try {
            return _attributeStore.get(_attributeStoreKey);
        } catch (IOException e) {
            throw new DirectPersistencyAttributeStoreException("Failed to get last primary", e);
        }
    }

    public void setMeAsLastPrimary() {
        try {
            String previousLastPrimary = _attributeStore.set(_attributeStoreKey, attributeStoreValue);
            if (_logger.isLoggable(Level.INFO))
                _logger.log(Level.INFO, "Set as last primary ["+ attributeStoreValue +"], previous last primary is ["+previousLastPrimary+"]");
        } catch (IOException e) {
            throw new DirectPersistencyAttributeStoreException("Failed to set last primary", e);
        }
    }

    public boolean isMeLastPrimary() {
        return attributeStoreValue.equals(getLastPrimaryName());
    }

    @Override
    public void beforeSpaceModeChange(SpaceMode newMode) throws RemoteException {
        if (isMemoryXtendSpace && newMode == SpaceMode.PRIMARY && isPendingBackupRecovery()) {
            throw DirectPersistencyRecoveryException.createBackupNotFinishedRecoveryException(_fullSpaceName);
        }
        // mark backup as started recovery but not yet finished
        else if (isMemoryXtendSpace && newMode == SpaceMode.BACKUP) {
            setPendingBackupRecovery(true);
        }
        if (newMode == SpaceMode.PRIMARY) {
            setMeAsLastPrimary();
        }
    }

    @Override
    public void afterSpaceModeChange(SpaceMode newMode) throws RemoteException {
    }

    public void setPendingBackupRecovery(boolean pendingRecovery) {
        this._pendingBackupRecovery = pendingRecovery;
    }

    public boolean isPendingBackupRecovery() {
        return _pendingBackupRecovery;
    }

    public void handleDirectPersistencyRecoverFailure(int retryCount) {
        if (_logger.isLoggable(Level.WARNING)) {
            _logger.warning("failed during recover, retrying for the " + retryCount + " time");
        }
        if (isPendingBackupRecovery() && _recoverRetries == retryCount) {
            throw DirectPersistencyRecoveryException.createBackupNotFinishedRecoveryException(_fullSpaceName);
        }
    }

    private AttributeStore createZooKeeperAttributeStore(String lastPrimaryPath) {
        int connectionTimeout = _spaceImpl.getConfig().getZookeeperConnectionTimeout();
        int sessionTimeout = _spaceImpl.getConfig().getZookeeperSessionTimeout();
        int retryTimeout = _spaceImpl.getConfig().getZookeeperRetryTimeout();
        int retryInterval = _spaceImpl.getConfig().getZookeeperRetryInterval();

        final Constructor constructor;
        try {
            constructor = ClassLoaderHelper.loadLocalClass(ATTRIBUET_STORE_HANDLER_CLASS_NAME)
                    .getConstructor(String.class, int.class, int.class, int.class, int.class);
            return (AttributeStore) constructor.newInstance(lastPrimaryPath, sessionTimeout, connectionTimeout, retryTimeout, retryInterval);
        } catch (Exception e) {
            if (_logger.isLoggable(Level.SEVERE))
                _logger.log(Level.SEVERE, "Failed to create attribute store ");
            throw new DirectPersistencyRecoveryException("Failed to start [" + (_spaceImpl.getEngine().getFullSpaceName())
                    + "] Failed to create attribute store.");
        }
    }

    public String getFullSpaceName() {
        return _fullSpaceName;
    }
}
