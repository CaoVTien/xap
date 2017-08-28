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

package com.j_spaces.core.cache.offHeap.optimizations;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;

/**
 * @author Yael Nahon
 * @since 12.2
 */
public class OffHeapIndexesValuesHandler {

    private static Unsafe _unsafe;
    private static int numOfBytes = 40;

    private static Unsafe getUnsafe() {
        if (_unsafe == null) {
            Constructor<Unsafe> unsafeConstructor = null;
            try {
                unsafeConstructor = Unsafe.class.getDeclaredConstructor();
                unsafeConstructor.setAccessible(true);
                _unsafe = unsafeConstructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("could not get unsafe instance");
            }
        }
        return _unsafe;
    }

    public static long allocate(){
        long address = getUnsafe().allocateMemory(numOfBytes);
        if(address == 0)
        getUnsafe().setMemory(address, numOfBytes, (byte) 0);
        return address;
    }

    public static byte[] get(long address){
        byte[] res = new byte[numOfBytes];
        for (int i = 0; i < numOfBytes; i++) {
            res[i] = getUnsafe().getByte(address);
            address++;
        }
        return res;
    }

    public static void delete(long address){
        getUnsafe().freeMemory(address);
    }

    public static void update(long address){
        for (int i = 0; i < numOfBytes; i++) {
            getUnsafe().putByte(address, (byte) 1);
            address++;
        }
    }


}