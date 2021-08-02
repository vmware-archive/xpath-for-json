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

package com.vmware.xpath.json.tests;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.MissingNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xpath.TraversalStopException;
import com.vmware.xpath.XpathVisitorException;
import com.vmware.xpath.context.Context;
import com.vmware.xpath.json.DebugJsonXpathVisitor;
import com.vmware.xpath.json.DistinctTextValueJsonXpathVisitor;
import com.vmware.xpath.json.JsonXpath;
import com.vmware.xpath.json.JsonXpathVisitor;
import com.vmware.xpath.json.NullJsonFilter;
import com.vmware.xpath.json.ReplaceTextValueVisitor;

import junit.framework.TestCase;

/**
 * @Copyright (c) 2021 VMware, Inc.  All rights reserved
 * @author banerjees
 *
 */
public class TestJsonXpathWithContext extends TestCase{

    private static final Logger LOG = LoggerFactory
            .getLogger(TestJsonXpathWithContext.class);

    /**
     * Sample Visitor Class to depict how a vistitor can be used to conveniently
     * transform a JSON into another
     * 
     * @author banerjees@vmware.com
     *
     */
    private static final class CustomContextAwareVisitor implements JsonXpathVisitor {

        /**
         * 
         */
        public CustomContextAwareVisitor() {
        }

        @Override
        public boolean visit(Context ctx, JsonNode parent, JsonNode currentNodeToSelect)
                throws XpathVisitorException, TraversalStopException {
            
        	LOG.info("CTX: {}", ctx);
            return true;
        }
    }

    
    private static final class Result
    {
        public List<String> xpathResultSet;
        public String ctxTree;

        @Override
        public String toString()
        {
            return this.xpathResultSet+(this.ctxTree==null ? "  " : " : "+this.ctxTree);
        }
    }

    /*
    private static final class RmqHosts
    {
        private List<HostPort> hostports = new ArrayList<HostPort>();
        ObjectMapper mapper = new ObjectMapper().setVisibility(JsonMethod.FIELD, Visibility.ANY);

        public void addHostPort(HostPort hp)
        {
            this.hostports.add(hp);
        }

        public void readFrom(List<JsonNode> jn) throws JsonProcessingException, IOException
        {
            for(int i=0 ; i< jn.size() ; i++)
            {
                HostPort hp = new HostPort();
                mapper.readerForUpdating(hp).readValue(jn.get(i));
                addHostPort(hp);
            }
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for(HostPort hp : this.hostports)
            {
                if(!first)
                    sb.append("; ");
                sb.append(hp.toString());
                first = false;
            }
            return sb.toString();
        };

    } */

    /**
     * Test Basic Xpath context
     */
    @Test
    public void testXpathBasicFetchOnInputJsonObject() throws Exception {
        /*
         * ---------------------------------
         *     Sample code for reference
         * ---------------------------------
         */
        String orgVdcList = IOUtils.toString(TestJsonXpathWithContext.class.getResourceAsStream("orgVdcList.json"), "UTF-8");
        JsonNode jn = getJsonNode(orgVdcList);
        String xpathSample = "//orgVdcs//name";
        List<String> textValues = new ArrayList<>();
        Context ctx = execWithContext(jn, xpathSample, textValues);
        

        // Note: the order of nodes in the result-set is important.
//        assertEquals("Incorrect results for Xpath:"+ xpathSample,
//                (Arrays.<String>asList(
//                        "1000")),
//                textValues);
        
//        assertEquals("Incorrect results for Xpath:"+ xpathSample,
//                " >> /id", ctx.toString());
//        
        
        
        /*
         * ---------------------------------
         */
        
//        List<String> xpathList = Arrays.asList(
//        		"/name", "//name", "//orgVdcs//name" 
//        		);
//        
//        for(String xpath: xpathList) {
//        	Context ctxi = execWithContext(jn, xpath, textValues);
//        	LOG.info("--------------\n\n");
//        }
    }



	private Context execWithContext(JsonNode jn, String xpath, List<String> textValues) {
		textValues.removeAll(textValues);
		Context ctx = Context.create(xpath);
        List<JsonNode> results = JsonXpath.findAndUpdateMultiple(ctx, jn, xpath, new CustomContextAwareVisitor());
        results.forEach(new Consumer<JsonNode>() {
            @Override
            public void accept(JsonNode t) {
                textValues.add(t.asText());

                /*
                 * Note: We cannot directly update the source JSON directly from here, as it will lead to ConcurrentModificaitonException.
                 * For updates on to the source JSON, refer to 'testXpathUpdat...'  and the visitor testcases.
                 */
            }
        });
		return ctx;
	}

    

    private void quickAssertXpathResult(String xpath, String expectedResultString, JsonNode jn) {
        List<JsonNode> res = JsonXpath.findAndUpdateMultiple(
                jn, xpath, new DebugJsonXpathVisitor(xpath));
        assertEquals("Incorrect results for Xpath:"+ xpath, expectedResultString, res.toString());
    }

    private JsonNode getJsonNode(String inputJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonParser jp = mapper.getJsonFactory().createJsonParser(inputJson);
            return jp.readValueAsTree();
        } catch (Throwable th) {
            LOG.error("Incorrect input JSON supplied", th);
            fail("Incorrect input JSON supplied");
            throw new RuntimeException(th);
        }
    }

    private int calculatePrefixLengthFromNetMask(String nMask) {
        SubnetUtils subnetUtils = new SubnetUtils(nMask, nMask);
        return new Integer(subnetUtils.getInfo().getCidrSignature().split("/")[1]);
    }

    private String calculateNetMaskFromPrefixLength(int pLength) {
        SubnetUtils subnetUtils = new SubnetUtils("0.0.0.0/" + pLength);
        return subnetUtils.getInfo().getNetmask();
    }
}

