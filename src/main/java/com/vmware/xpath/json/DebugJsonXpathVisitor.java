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

import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xpath.TraversalStopException;
import com.vmware.xpath.XpathVisitorException;
import com.vmware.xpath.context.Context;


/**
 * @Copyright (c) 2018 VMware, Inc.  All rights reserved
 * @author banerjees
 *
 */
public final class DebugJsonXpathVisitor implements JsonXpathVisitor
{
    final String xpath;
    private boolean isVerbose;

    private static final Logger logger = LoggerFactory.getLogger(DebugJsonXpathVisitor.class);

    /**
     *
     * @param ctx not really used.
     * @param xpath
     */
    public DebugJsonXpathVisitor(String xpath, boolean isVerbose)
    {
        this.xpath = xpath;
        this.isVerbose = isVerbose;
    }

    public DebugJsonXpathVisitor(String xpath)
    {
        this(xpath, false);
    }

    @Override
    public boolean visit(Context ctx, JsonNode parent, JsonNode currentNodeToSelect)
            throws XpathVisitorException, TraversalStopException
    {
        if(isVerbose) {
            logger.debug( " ==> Found node for Xpath " + this.xpath + " : " + currentNodeToSelect );
            logger.debug( " ==> Found parent of the node " + parent );
        } else {
            logger.debug(" ==> Found node for Xpath "+ this.xpath+ " : "+currentNodeToSelect);
            logger.debug(" ==> Found parent of the node "+ parent);
        }
        return true;
    }

}