/*
 *
 * xpath-for-json
 *
 * Copyright (c) 2018 VMware, Inc.  All rights reserved
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * The BSD-2 license (the "License") set forth below applies to all parts of the
 * xpath-for-json project.  You may not use this file except in compliance with the License.
 *
 * BSD-2 License
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.vmware.xpath.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xpath.TraversalStopException;
import com.vmware.xpath.XpathVisitorException;

/**
 * @author banerjees
 * @Copyright (c) 2018 VMware, Inc.  All rights reserved
 *
 */
public final class RemoveFieldVisitor implements JsonXpathVisitor
{
    final String xpath;
    final List<String> removedConstructs = new ArrayList<>();
    private Logger logger = LoggerFactory.getLogger(RemoveFieldVisitor.class);

    /**
     *
     * @param xpath
     * @param replacementMap <origValue, replacementValue>
     */
    public RemoveFieldVisitor(String xpath, Logger srcLogger)
    {
        this.xpath = xpath;
        if(srcLogger!=null) {
            this.logger = srcLogger;
        }
    }

    @Override
    public boolean visit(JsonNode parent, JsonNode currentNodeToSelect)
            throws XpathVisitorException, TraversalStopException
    {
        logger.debug(" ==> Found parent of the node "+ parent);
        String origValue = currentNodeToSelect.asText();
        logger.debug(" ==> TRYING removal of the node " + origValue);

        if(parent instanceof ArrayNode) {
            ArrayNode parentArr = (ArrayNode)parent;
            for(Iterator<JsonNode> itr = parentArr.iterator(); itr.hasNext(); ) {
                JsonNode node = itr.next();

                if(node.asText().equals(origValue)){
                    logger.debug(" ==> // Removing construct {} for Xpath {}",
                            currentNodeToSelect, this.xpath);
                    this.removedConstructs.add(node.toString());
                    itr.remove();

                }
            }

        } else if (parent instanceof ObjectNode) {
            ObjectNode parentObj = (ObjectNode)parent;
            String xpathNode = xpath.substring(xpath.lastIndexOf('/')+1);
            String foundFieldName = "";
            for(Iterator<Map.Entry<String, JsonNode>> itr = parentObj.getFields(); itr.hasNext(); ) {
                Map.Entry<String, JsonNode> field = itr.next();

                if(xpathNode.equals(field.getKey()) && field.getValue().asText().equals(origValue)){
                    foundFieldName = field.getKey();
                    if(!foundFieldName.isEmpty()) {
                        logger.debug(" ==> Removing construct {}->{} for Xpath {}",
                                foundFieldName, currentNodeToSelect, this.xpath);
                        String msg = String.format("%s->%s", foundFieldName, currentNodeToSelect);
                        this.removedConstructs.add(msg);
                        itr.remove();
                    }
                }
            }
        }

        return true;
    }

    public List<String> getRemovedConstructs() {
        return this.removedConstructs;
    }

}