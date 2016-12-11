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


package org.openspaces.pu.container.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A simple command line parser transforming a list of string arguments into {@link
 * org.openspaces.pu.container.support.CommandLineParser.Parameter} arguments. The arguments are in
 * the form of -param1 arg1 arg2 -parm2 arg1.
 *
 * @author kimchy
 */
public abstract class CommandLineParser {

    public static class Parameter {

        private String name;

        private String[] arguments;

        public Parameter(String name, String[] arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public String[] getArguments() {
            return arguments;
        }
    }

    public static Parameter[] parse(String[] args) throws IllegalArgumentException {
        return parse(args, new HashSet<String>(0));
    }

    public static Parameter[] parse(String[] args, Set<String> parametersWithoutValues) throws IllegalArgumentException {
        return parse(args, args.length, parametersWithoutValues);
    }

    public static Parameter[] parse(String[] args, int length ) throws IllegalArgumentException{
        return parse( args, length, new HashSet<String>() );
    }

    private static Parameter[] parse(String[] args, int length, Set<String> parametersWithoutValues) throws IllegalArgumentException {
        if (length == 0) {
            return new Parameter[0];
        }
        if (length == 1 && !parametersWithoutValues.contains( args[0] )) {
            throw new IllegalArgumentException("Command line structure is incorrect, only one parameter");
        }
        List<Parameter> params = new ArrayList<Parameter>();
        int index = 0;
        while (index < length) {
            if (!args[index].startsWith("-")) {
                throw new IllegalArgumentException("Command line argument [" + args[index]
                        + "] is supposed to start with -");
            }

            boolean shouldHaveValue = !parametersWithoutValues.contains( args[index] );

            if ((index + 1) == length && shouldHaveValue) {
                throw new IllegalArgumentException("Command line argument [" + args[index] + "] has no argument");
            }
            String name;
            List<String> arguments = new ArrayList<String>();

            name = args[index].substring(1, args[index].length());
            index += 1;
            if( shouldHaveValue ) {
                for (; index < length; index++) {
                    if (args[index].startsWith("-")) {
                        break;
                    }
                    arguments.add(args[index]);
                }
            }
            else{
                arguments = Collections.EMPTY_LIST;
            }

            Parameter parameter = new Parameter(name, arguments.toArray(new String[arguments.size()]));
            params.add(parameter);
        }
        return params.toArray(new Parameter[params.size()]);
    }
}
