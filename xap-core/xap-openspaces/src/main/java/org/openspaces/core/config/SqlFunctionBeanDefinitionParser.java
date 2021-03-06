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

package org.openspaces.core.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 * Created by Tamir on 3/6/16.
 *
 * @since 11.0
 */
public class SqlFunctionBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    String NAME = "name";
    String SQL_FUNCTION = "sql-function";

    @Override
    protected Class<?> getBeanClass(Element element) {
        return SpaceSqlFunctionBean.class;
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        super.doParse(element, parserContext, builder);
        NamedNodeMap attributes = element.getAttributes();

        if (attributes.getLength() != 0) {
            Attr attribute = (Attr) attributes.item(0);
            String functionName = attribute.getLocalName();
            if (functionName.equals(NAME)) {
                builder.addPropertyValue("functionName", attribute.getValue());
            }
        }

        Element sqlFunctionEle = DomUtils.getChildElementByTagName(element, "sql-function");
        if (sqlFunctionEle != null) {
            Object sqlFunction = parserContext.getDelegate().parsePropertyValue(sqlFunctionEle, builder.getRawBeanDefinition(), SQL_FUNCTION);
            builder.addPropertyValue("sqlFunction", sqlFunction);
        }
    }
}
