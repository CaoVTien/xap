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

package com.gigaspaces.internal.cluster.node.impl.groups.sync;

@com.gigaspaces.api.InternalApi
public class ConstantReplicationThrottleController
        implements IReplicationThrottleController, IReplicationThrottleControllerBuilder {

    private final int _threshold;
    private final long _throttleDelay;

    public ConstantReplicationThrottleController(int threshold,
                                                 long throttleDelay) {
        _threshold = threshold;
        _throttleDelay = throttleDelay;
    }

    public boolean throttle(long backlogSize, int contextSize, boolean channelActive) {
        if (backlogSize < _threshold)
            return false;
        try {
            Thread.sleep(_throttleDelay);
            return true;
        } catch (InterruptedException e) {
            // Break throttle
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public IReplicationThrottleController createController(String groupName,
                                                           String sourceMemberName, String targetMemberName) {
        //Stateless implementation
        return this;
    }

    public void suggestThroughPut(int throughPut) {
        //Do nothing, basic implementation
    }

}
