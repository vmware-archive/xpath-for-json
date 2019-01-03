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

import com.vmware.xpath.TraversalStopException;
import com.vmware.xpath.XpathVisitorException;

/**
 * 
 * @author banerjees
 * @Copyright (c) 2018 VMware, Inc.  All rights reserved
 *
 */
public interface JsonXpathVisitor
{
    /**
     * 
     * @param parent
     * @param currentNodeToSelect
     * @return true always! ignored for now!
     * @throws XpathVisitorException This will not cause the next traversal to stop
     * @throws TraversalStopException if the client wants to abort iterating further
     */
    boolean visit(JsonNode parent, JsonNode currentNodeToSelect) throws XpathVisitorException, TraversalStopException;
}