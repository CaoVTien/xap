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


package com.j_spaces.core.exception;
/*******************************************************************************
 * Copyright (c) 2010 GigaSpaces Technologies Ltd. All rights reserved
 *
 * The software source code is proprietary and confidential information of GigaSpaces. You may use
 * the software source code solely under the terms and limitations of The license agreement granted
 * to you by GigaSpaces.
 *******************************************************************************/


/**
 * This exception is thrown in case where there is an attempt to stop already stopped space.
 *
 * @author Michael Konnikov
 * @version 4.0
 */
public class SpaceAlreadyStoppedException extends SpaceUnavailableException {
    private static final long serialVersionUID = 3487826802841980532L;

    /**
     * Constructs a <code>SpaceAlreadyStoppedException</code> with the specified detail message.
     *
     * @param s - the detail message
     */
    public SpaceAlreadyStoppedException(String spaceName, String s) {
        super(spaceName, s);
    }
}
