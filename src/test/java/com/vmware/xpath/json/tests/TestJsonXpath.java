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
import org.codehaus.jettison.json.JSONException;
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
 * @Copyright (c) 2018 VMware, Inc.  All rights reserved
 * @author banerjees
 *
 */
public class TestJsonXpath extends TestCase{

    private static final Logger LOG = LoggerFactory
            .getLogger(TestJsonXpath.class);

    /**
     * Sample Visitor Class to depict how a vistitor can be used to conveniently
     * transform a JSON into another
     * 
     * @author banerjees
     *
     */
    private static final class CustomSpecUpdateVisitor implements JsonXpathVisitor {

        private Object fieldValue;
        private String fieldName;

        /**
         * * * Sample code * * *
         * 
         * @param fieldName
         *            Must be a unique field, throughout the JSON (any depth)
         * @param fieldValue
         *            Replacement for the field value
         */
        public CustomSpecUpdateVisitor(String fieldName, Object fieldValue) {
            fieldName = fieldName.contains("/") ? fieldName.substring(fieldName.lastIndexOf("/") + 1) : fieldName;
            this.fieldName = fieldName;
            this.fieldValue = fieldValue;
        }

        @Override
        public boolean visit(Context ctx, JsonNode parent, JsonNode currentNodeToSelect)
                throws XpathVisitorException, TraversalStopException {
            if (parent instanceof ObjectNode) {
                ObjectNode pNode = (ObjectNode) parent;
                if (fieldValue instanceof Integer) {
                    pNode.put(fieldName, ((Integer) fieldValue).intValue());
                } else if (fieldValue instanceof Boolean) {
                    pNode.put(fieldName, ((Boolean) fieldValue).booleanValue());
                } else {
                    pNode.put(fieldName, fieldValue.toString());
                }
            } else if (parent instanceof ArrayNode) {
                ArrayNode pNode = (ArrayNode) parent;
                pNode.removeAll();
                if (fieldValue instanceof Iterable) {
                    for (Object it : ((Iterable) fieldValue)) {
                        pNode.add(it.toString());
                    }
                } else if (fieldValue instanceof Object[]) {
                    for (Object it : ((Object[]) fieldValue)) {
                        pNode.add(it.toString());
                    }
                } else if (fieldValue instanceof Integer) {
                    pNode.add(((Integer) fieldValue).intValue());
                } else if (fieldValue instanceof Boolean) {
                    pNode.add(((Boolean) fieldValue).booleanValue());
                } else {
                    pNode.add(fieldValue.toString());
                }
            }

            return true;
        }
    }

    private static final class HostPort
    {
        public String host;
        public String port;

        @Override
        public String toString()
        {
            return this.host+(this.port==null ? "" : ":"+this.port);
        }
    }

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

    }

    /**
     * Test Basic Xpath fetch (or select)
     * Sample Code for reference
     */
    @Test
    public void testXpathBasicFetchOnInputJsonObject() throws Exception {
        /*
         * ---------------------------------
         *     Sample code for reference
         * ---------------------------------
         */
        String orgVdcList = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("orgVdcList.json"), "UTF-8");
        JsonNode jn = getJsonNode(orgVdcList);
        String xpathSample = "//orgName";
        List<String> textValues = new ArrayList<>();
        List<JsonNode> results = JsonXpath.findAndUpdateMultiple(jn, xpathSample, NullJsonFilter.instance());
        results.forEach(new Consumer<JsonNode>() {
            @Override
            public void accept(JsonNode t) {
                textValues.add(t.asText());

                // We can do some more interesting things out here...
                LOG.info("isArray={}, isText={}, isValue={}, isContainer={} for Node: {}",
                        t.isArray(), t.isTextual(), t.isValueNode(), t.isContainerNode(), t);
                /*
                 * Note: We cannot directly update the source JSON directly from here, as it will lead to ConcurrentModificaitonException.
                 * For updates on to the source JSON, refer to 'testXpathUpdat...'  and the visitor testcases.
                 */
            }
        });

        // Note: the order of nodes in the result-set is important.
        assertEquals("Incorrect results for Xpath:"+ xpathSample,
                (Arrays.<String>asList(
                        "OrgName-1000-A", "OrgName-1000-B", "OrgName-1000-C", "OrgName-1000-B",
                        "OrgName-1000-X", "OrgName-1000-Y", "OrgName-1000-X", "OrgName-1000-X")),
                textValues);
        /*
         * ---------------------------------
         */


        /*
         * ---------------------------------
         *     Some quick dirty tests
         * ---------------------------------
         */
        String[] xpaths = new String[] {
                "/name",
                "/gwRecord/name",
                "//name",
                "/gwRecord//name",
                "//networkService/value",
                "//networkService/name",
                "//networkService/value//name",
                "/gatewayName",
                "/declaredType",
                "/description",
                "/networkService",
                "//natRule//originalIp",
                "//originalIp",
                "//gatewayName",
                "//haEnabled",

                "//natRule",                           // returns a JSON array of jsonObjects, each representing a NAT rule.
        };

        String[] expectedResults = new String[] {
                "[]",
                "[\"gateway_1\"]",
                "[null, null, null, null, null, null, null, null, null, null, \"gateway_1\", \"\", \"external_network\", \"external_network\", \"gateway_1_routed_network\", \"gateway_1_routed_network\", \"routed_network_gateway1\", \"routed_network_gateway1\", \"{http://www.vmware.com/vcloud/v1.5}GatewayDhcpService\", \"gateway_1_routed_network\", \"routed_network_gateway1\", \"{http://www.vmware.com/vcloud/v1.5}FirewallService\", \"{http://www.vmware.com/vcloud/v1.5}NatService\", \"external_network\", \"external_network\", \"{http://www.vmware.com/vcloud/v1.5}GatewayIpsecVpnService\", \"VPN new\", \"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\", \"gateway_1_routed_network\", \"192.168.169.10/24\", \"asdfa\", \"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\", \"routed_network_gateway1\", \"192.168.100.109/32\", \"{http://www.vmware.com/vcloud/v1.5}StaticRoutingService\", \"{http://www.vmware.com/vcloud/v1.5}LoadBalancerService\", \"Test LB Modified\", \"LB Test\", \"external_network\"]",
                "[null, null, null, null, null, null, null, null, null, null, \"gateway_1\", \"\", \"external_network\", \"external_network\", \"gateway_1_routed_network\", \"gateway_1_routed_network\", \"routed_network_gateway1\", \"routed_network_gateway1\", \"{http://www.vmware.com/vcloud/v1.5}GatewayDhcpService\", \"gateway_1_routed_network\", \"routed_network_gateway1\", \"{http://www.vmware.com/vcloud/v1.5}FirewallService\", \"{http://www.vmware.com/vcloud/v1.5}NatService\", \"external_network\", \"external_network\", \"{http://www.vmware.com/vcloud/v1.5}GatewayIpsecVpnService\", \"VPN new\", \"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\", \"gateway_1_routed_network\", \"192.168.169.10/24\", \"asdfa\", \"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\", \"routed_network_gateway1\", \"192.168.100.109/32\", \"{http://www.vmware.com/vcloud/v1.5}StaticRoutingService\", \"{http://www.vmware.com/vcloud/v1.5}LoadBalancerService\", \"Test LB Modified\", \"LB Test\", \"external_network\"]",
                "[{\"otherAttributes\":{},\"isEnabled\":false,\"pool\":[{\"otherAttributes\":{},\"isEnabled\":true,\"network\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/e3e22818-e04a-4176-a852-1944474915c9\",\"id\":null,\"name\":\"gateway_1_routed_network\",\"type\":\"application/vnd.vmware.vcloud.orgVdcNetwork+xml\",\"vcloudExtension\":[]},\"defaultLeaseTime\":3600,\"maxLeaseTime\":7200,\"lowIpAddress\":\"192.168.150.115\",\"highIpAddress\":\"192.168.150.115\",\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":true,\"network\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/f53c1a3e-1b52-463e-9418-62133fd2a9d0\",\"id\":null,\"name\":\"routed_network_gateway1\",\"type\":\"application/vnd.vmware.vcloud.orgVdcNetwork+xml\",\"vcloudExtension\":[]},\"defaultLeaseTime\":3600,\"maxLeaseTime\":7200,\"lowIpAddress\":\"192.168.200.105\",\"highIpAddress\":\"192.168.200.105\",\"vcloudExtension\":[]}],\"vcloudExtension\":[]}, {\"otherAttributes\":{},\"isEnabled\":true,\"defaultAction\":\"drop\",\"logDefaultAction\":false,\"firewallRule\":[{\"otherAttributes\":{},\"id\":\"1\",\"isEnabled\":false,\"matchOnTranslate\":false,\"description\":\"Firewall Description\",\"policy\":\"allow\",\"protocols\":{\"tcp\":null,\"udp\":true,\"icmp\":null,\"any\":null,\"other\":null},\"icmpSubType\":null,\"port\":-1,\"destinationPortRange\":\"Any\",\"destinationIp\":\"1.1.1.1\",\"destinationVm\":null,\"sourcePort\":-1,\"sourcePortRange\":\"Any\",\"sourceIp\":\"1.1.1.1\",\"sourceVm\":null,\"direction\":null,\"enableLogging\":true,\"vcloudExtension\":[]},{\"otherAttributes\":{},\"id\":\"2\",\"isEnabled\":false,\"matchOnTranslate\":false,\"description\":\"Firewall Description1\",\"policy\":\"allow\",\"protocols\":{\"tcp\":null,\"udp\":true,\"icmp\":null,\"any\":null,\"other\":null},\"icmpSubType\":null,\"port\":-1,\"destinationPortRange\":\"Any\",\"destinationIp\":\"2.2.2.2\",\"destinationVm\":null,\"sourcePort\":-1,\"sourcePortRange\":\"Any\",\"sourceIp\":\"2.2.2.2\",\"sourceVm\":null,\"direction\":null,\"enableLogging\":true,\"vcloudExtension\":[]}],\"vcloudExtension\":[]}, {\"otherAttributes\":{},\"isEnabled\":true,\"natType\":null,\"policy\":null,\"natRule\":[{\"otherAttributes\":{},\"description\":\"DNAT Description 1\",\"ruleType\":\"DNAT\",\"isEnabled\":false,\"id\":65538,\"gatewayNatRule\":{\"otherAttributes\":{},\"originalIp\":\"192.168.100.100\",\"originalPort\":\"any\",\"translatedIp\":\"1.1.1.1\",\"translatedPort\":\"any\",\"protocol\":\"tcp\",\"icmpSubType\":null,\"interface\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/b9ad3587-289f-4395-984e-6c5d879ba69e\",\"id\":null,\"name\":\"external_network\",\"type\":\"application/vnd.vmware.admin.network+xml\",\"vcloudExtension\":[]},\"vcloudExtension\":[]},\"oneToOneBasicRule\":null,\"oneToOneVmRule\":null,\"portForwardingRule\":null,\"vmRule\":null,\"vcloudExtension\":[]},{\"otherAttributes\":{},\"description\":\"SNAT Description 2\",\"ruleType\":\"SNAT\",\"isEnabled\":false,\"id\":65538,\"gatewayNatRule\":{\"otherAttributes\":{},\"originalIp\":\"192.168.100.120\",\"originalPort\":\"any\",\"translatedIp\":\"1.1.1.2\",\"translatedPort\":\"any\",\"protocol\":\"tcp\",\"icmpSubType\":null,\"interface\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/b9ad3587-289f-4395-984e-6c5d879ba69e\",\"id\":null,\"name\":\"external_network\",\"type\":\"application/vnd.vmware.admin.network+xml\",\"vcloudExtension\":[]},\"vcloudExtension\":[]},\"oneToOneBasicRule\":null,\"oneToOneVmRule\":null,\"portForwardingRule\":null,\"vmRule\":null,\"vcloudExtension\":[]}],\"externalIp\":null,\"vcloudExtension\":[]}, {\"otherAttributes\":{},\"isEnabled\":true,\"endpoint\":[],\"tunnel\":[{\"otherAttributes\":{},\"name\":\"VPN new\",\"description\":\"Modified\",\"ipsecVpnPeer\":{\"name\":\"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\",\"declaredType\":\"com.vmware.vcloud.api.rest.schema_v1_5.IpsecVpnThirdPartyPeerType\",\"scope\":\"javax.xml.bind.JAXBElement$GlobalScope\",\"value\":{\"otherAttributes\":{},\"peerId\":\"192.168.105.100\",\"vcloudExtension\":[]},\"nil\":false,\"globalScope\":true,\"typeSubstituted\":false},\"peerIpAddress\":\"192.168.105.100\",\"peerId\":\"192.168.105.100\",\"localIpAddress\":\"192.168.100.100\",\"localId\":\"192.168.100.100\",\"localSubnet\":[{\"otherAttributes\":{},\"name\":\"gateway_1_routed_network\",\"gateway\":\"192.168.150.1\",\"netmask\":\"255.255.255.0\",\"vcloudExtension\":[]}],\"peerSubnet\":[{\"otherAttributes\":{},\"name\":\"192.168.169.10/24\",\"gateway\":\"192.168.169.10\",\"netmask\":\"255.255.255.0\",\"vcloudExtension\":[]}],\"sharedSecret\":\"7bLbtYmk8qLqU3fwHmkyhx4P38359S3nqbkdXkdtK5Dth3kxF6f4oP54J56QmTVi\",\"sharedSecretEncrypted\":false,\"encryptionProtocol\":\"AES256\",\"mtu\":1500,\"isEnabled\":true,\"isOperational\":false,\"errorDetails\":null,\"vcloudExtension\":[]},{\"otherAttributes\":{},\"name\":\"asdfa\",\"description\":\"asdf\",\"ipsecVpnPeer\":{\"name\":\"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\",\"declaredType\":\"com.vmware.vcloud.api.rest.schema_v1_5.IpsecVpnThirdPartyPeerType\",\"scope\":\"javax.xml.bind.JAXBElement$GlobalScope\",\"value\":{\"otherAttributes\":{},\"peerId\":\"168\",\"vcloudExtension\":[]},\"nil\":false,\"globalScope\":true,\"typeSubstituted\":false},\"peerIpAddress\":\"192.168.100.102\",\"peerId\":\"168\",\"localIpAddress\":\"192.168.100.100\",\"localId\":\"192\",\"localSubnet\":[{\"otherAttributes\":{},\"name\":\"routed_network_gateway1\",\"gateway\":\"192.168.200.1\",\"netmask\":\"255.255.255.0\",\"vcloudExtension\":[]}],\"peerSubnet\":[{\"otherAttributes\":{},\"name\":\"192.168.100.109/32\",\"gateway\":\"192.168.100.109\",\"netmask\":\"255.255.255.255\",\"vcloudExtension\":[]}],\"sharedSecret\":\"D6tkY44B7j2B54U9WkNuHzQ8bKujqe46xe8tSy54Ly2oPe88ideCwSg6fbviJG3n\",\"sharedSecretEncrypted\":false,\"encryptionProtocol\":\"AES256\",\"mtu\":1500,\"isEnabled\":true,\"isOperational\":false,\"errorDetails\":null,\"vcloudExtension\":[]}],\"vcloudExtension\":[]}, {\"otherAttributes\":{},\"isEnabled\":false,\"staticRoute\":[],\"vcloudExtension\":[]}, {\"otherAttributes\":{},\"isEnabled\":false,\"pool\":[{\"otherAttributes\":{},\"id\":null,\"name\":\"Test LB Modified\",\"description\":\"Modifiedasdf\",\"servicePort\":[{\"otherAttributes\":{},\"isEnabled\":true,\"protocol\":\"HTTP\",\"algorithm\":\"ROUND_ROBIN\",\"port\":\"80\",\"healthCheckPort\":\"7777\",\"healthCheck\":[{\"otherAttributes\":{},\"mode\":\"HTTP\",\"uri\":\"/xyz\",\"healthThreshold\":\"2\",\"unhealthThreshold\":\"3\",\"interval\":\"5\",\"timeout\":\"15\",\"vcloudExtension\":[]}],\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":true,\"protocol\":\"HTTPS\",\"algorithm\":\"ROUND_ROBIN\",\"port\":\"443\",\"healthCheckPort\":\"5555\",\"healthCheck\":[{\"otherAttributes\":{},\"mode\":\"SSL\",\"uri\":null,\"healthThreshold\":\"2\",\"unhealthThreshold\":\"3\",\"interval\":\"5\",\"timeout\":\"15\",\"vcloudExtension\":[]}],\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":false,\"protocol\":\"TCP\",\"algorithm\":\"ROUND_ROBIN\",\"port\":\"\",\"healthCheckPort\":\"\",\"healthCheck\":[{\"otherAttributes\":{},\"mode\":\"TCP\",\"uri\":null,\"healthThreshold\":\"2\",\"unhealthThreshold\":\"3\",\"interval\":\"5\",\"timeout\":\"15\",\"vcloudExtension\":[]}],\"vcloudExtension\":[]}],\"member\":[{\"otherAttributes\":{},\"ipAddress\":\"192.168.100.100\",\"weight\":\"1\",\"servicePort\":[{\"otherAttributes\":{},\"isEnabled\":null,\"protocol\":\"HTTP\",\"algorithm\":null,\"port\":\"80\",\"healthCheckPort\":\"5555\",\"healthCheck\":[],\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":null,\"protocol\":\"HTTPS\",\"algorithm\":null,\"port\":\"\",\"healthCheckPort\":\"\",\"healthCheck\":[],\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":null,\"protocol\":\"TCP\",\"algorithm\":null,\"port\":\"\",\"healthCheckPort\":\"\",\"healthCheck\":[],\"vcloudExtension\":[]}],\"vcloudExtension\":[]}],\"operational\":false,\"errorDetails\":null,\"vcloudExtension\":[]}],\"virtualServer\":[{\"otherAttributes\":{},\"isEnabled\":true,\"name\":\"LB Test\",\"description\":\"LB Test\",\"ipAddress\":\"192.168.100.100\",\"serviceProfile\":[{\"otherAttributes\":{},\"isEnabled\":true,\"protocol\":\"HTTP\",\"port\":\"80\",\"persistence\":{\"otherAttributes\":{},\"method\":\"\",\"cookieName\":null,\"cookieMode\":null,\"vcloudExtension\":[]},\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":false,\"protocol\":\"HTTPS\",\"port\":\"443\",\"persistence\":{\"otherAttributes\":{},\"method\":\"SSL_SESSION_ID\",\"cookieName\":null,\"cookieMode\":null,\"vcloudExtension\":[]},\"vcloudExtension\":[]},{\"otherAttributes\":{},\"isEnabled\":false,\"protocol\":\"TCP\",\"port\":\"\",\"persistence\":{\"otherAttributes\":{},\"method\":\"\",\"cookieName\":null,\"cookieMode\":null,\"vcloudExtension\":[]},\"vcloudExtension\":[]}],\"logging\":false,\"pool\":\"Test LB Modified\",\"loadBalancerTemplates\":[],\"interface\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/b9ad3587-289f-4395-984e-6c5d879ba69e\",\"id\":null,\"name\":\"external_network\",\"type\":\"application/vnd.vmware.vcloud.orgVdcNetwork+xml\",\"vcloudExtension\":[]},\"vcloudExtension\":[]}],\"vcloudExtension\":[]}]",
                "[\"{http://www.vmware.com/vcloud/v1.5}GatewayDhcpService\", \"{http://www.vmware.com/vcloud/v1.5}FirewallService\", \"{http://www.vmware.com/vcloud/v1.5}NatService\", \"{http://www.vmware.com/vcloud/v1.5}GatewayIpsecVpnService\", \"{http://www.vmware.com/vcloud/v1.5}StaticRoutingService\", \"{http://www.vmware.com/vcloud/v1.5}LoadBalancerService\"]",
                "[\"gateway_1_routed_network\", \"routed_network_gateway1\", \"external_network\", \"external_network\", \"VPN new\", \"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\", \"gateway_1_routed_network\", \"192.168.169.10/24\", \"asdfa\", \"{http://www.vmware.com/vcloud/v1.5}IpsecVpnThirdPartyPeer\", \"routed_network_gateway1\", \"192.168.100.109/32\", \"Test LB Modified\", \"LB Test\", \"external_network\"]",
                "[\"gateway_1\"]",
                "[]",
                "[]",
                "[]",
                "[\"192.168.100.100\", \"192.168.100.120\"]",
                "[\"192.168.100.100\", \"192.168.100.120\"]",
                "[\"gateway_1\"]",
                "[false, false]",

                "[{\"otherAttributes\":{},\"description\":\"DNAT Description 1\",\"ruleType\":\"DNAT\",\"isEnabled\":false,\"id\":65538,\"gatewayNatRule\":{\"otherAttributes\":{},\"originalIp\":\"192.168.100.100\",\"originalPort\":\"any\",\"translatedIp\":\"1.1.1.1\",\"translatedPort\":\"any\",\"protocol\":\"tcp\",\"icmpSubType\":null,\"interface\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/b9ad3587-289f-4395-984e-6c5d879ba69e\",\"id\":null,\"name\":\"external_network\",\"type\":\"application/vnd.vmware.admin.network+xml\",\"vcloudExtension\":[]},\"vcloudExtension\":[]},\"oneToOneBasicRule\":null,\"oneToOneVmRule\":null,\"portForwardingRule\":null,\"vmRule\":null,\"vcloudExtension\":[]}, {\"otherAttributes\":{},\"description\":\"SNAT Description 2\",\"ruleType\":\"SNAT\",\"isEnabled\":false,\"id\":65538,\"gatewayNatRule\":{\"otherAttributes\":{},\"originalIp\":\"192.168.100.120\",\"originalPort\":\"any\",\"translatedIp\":\"1.1.1.2\",\"translatedPort\":\"any\",\"protocol\":\"tcp\",\"icmpSubType\":null,\"interface\":{\"otherAttributes\":{},\"href\":\"https://build-virtual-machine/api/admin/network/b9ad3587-289f-4395-984e-6c5d879ba69e\",\"id\":null,\"name\":\"external_network\",\"type\":\"application/vnd.vmware.admin.network+xml\",\"vcloudExtension\":[]},\"vcloudExtension\":[]},\"oneToOneBasicRule\":null,\"oneToOneVmRule\":null,\"portForwardingRule\":null,\"vmRule\":null,\"vcloudExtension\":[]}]"
        };

        String vcdEdge = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("vcdEdge.json"), "UTF-8");
        jn = getJsonNode(vcdEdge);
        int i=0;
        for(String xpath: xpaths) {
            quickAssertXpathResult(xpath, expectedResults[i], jn);
            i++;
        }
    }

    /**
     * Test Basic Xpath fetch (or select) from a JSONArray
     */
    @Test
    public void testXpathBasicFetchOnInputJsonArray() throws Exception {
        /*
         * ---------------------------------
         *     Some quick tests
         * ---------------------------------
         */
        String[] xpaths = new String[] {
                "/originalAddress",      // Find all 'originalAddress' at first level depth in the JSON
                "//originalAddress",     // Find all 'originalAddress' anywhere in the JSON
                "//description",
                "//desc",                // Negative test - Correct Xpath syntax, but does not point to anything in the JSON
        };

        String[] expectedResults = new String[] {
                "[\"192.0.33.33-192.0.33.133\", \"192.168.0.133-192.168.0.135\", \"192.168.3.120/31\", \"192.168.23.1\", \"10.13.13.13\"]",
                "[\"192.0.33.33-192.0.33.133\", \"192.168.0.133-192.168.0.135\", \"192.168.3.120/31\", \"192.168.23.1\", \"10.13.13.13\"]",
                "[\"\", \"EZZZt\", \"\", \"Nated to .13\", \"5656 tcp to 5677, no interface, logging not enabled\"]",
                "[]",
        };
        String natRulesArray = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("natRulesArray.json"),
                "UTF-8");
        JsonNode jn = getJsonNode(natRulesArray);
        int i=0;
        for(String xpath: xpaths) {
            quickAssertXpathResult(xpath, expectedResults[i], jn);
            i++;
        }

        /*
         * ------------------------------------------------
         *     Some more tests on a deeply nested JSON
         * ------------------------------------------------
         */
        xpaths = new String[] {
                "/source/groupingObjectId",   // With absolute path - i.e. at the specified location in the JSON
                "//groupingObjectId",         // With descendant path - anywhere in the doc in the JSON
                "/groupingObjectId",          // Negative test - Correct Xpath syntax, but does not point to anything in the JSON
        };

        expectedResults = new String[] {
                "[\"ipset-2\", \"resgroup-65\", \"vm-43\", \"securitygroup-10\", \"dvportgroup-40\", \"ipset-3\"]",
                "[\"ipset-2\", \"resgroup-65\", \"vm-43\", \"securitygroup-10\", \"dvportgroup-40\", \"ipset-3\", \"vm-58\", \"ipset-4\", \"ipset-3\"]",
                "[]",
        };

        String edgeFirewall = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("edgeFirewall.json"), "UTF-8");
        jn = getJsonNode(edgeFirewall);
        // ---- Find the list of fw-rules Array within, the quick easy way. ----//
        String newJsonArrayInput = JsonXpath.findAndUpdateMultiple(
                // There are two 'firewallRules' nested within one-another
                jn, "//firewallRules/firewallRules", NullJsonFilter.instance()).toString();
        LOG.info("Changing input to: {}", newJsonArrayInput);
        jn = getJsonNode(newJsonArrayInput);

        i=0;
        for(String xpath: xpaths) {
            quickAssertXpathResult(xpath, expectedResults[i], jn);
            i++;
        }
    }


    /**
     * Test and sample-code, for fetching 'distinct values' for a given Xpath,
     * from an input JSONObject
     */
    @Test
    public void testXpathDistinctTextValueJsonXpathVisitorForInputJsonObject()
    {
        try
        {
            // Preparing input JSON
            String orgVdcList = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("orgVdcList.json"), "UTF-8");
            JsonNode jn = getJsonNode(orgVdcList);

            //1
            String xpath = "//orgName";
            DistinctTextValueJsonXpathVisitor distinctVis = new DistinctTextValueJsonXpathVisitor(xpath);
            JsonXpath.findAndUpdateMultiple(jn, xpath, distinctVis);
            assertEquals("Incorrect results for Xpath:"+ xpath,
                    new HashSet<String>(Arrays.asList(
                    "OrgName-1000-A", "OrgName-1000-B", "OrgName-1000-C", "OrgName-1000-X", "OrgName-1000-Y")),
                    distinctVis.getDistinctSet());

            //2
            xpath = "//vcloud/name";
            distinctVis = new DistinctTextValueJsonXpathVisitor(xpath);
            JsonXpath.findAndUpdateMultiple(jn, xpath, distinctVis);
            assertEquals("Incorrect results for Xpath:"+ xpath,
                    new HashSet<String>(Arrays.asList(
                    "vcd-1000-2", "vcd-1000-1")),
                    distinctVis.getDistinctSet());

            //3
            xpath = "//orgVdcs/id";
            distinctVis = new DistinctTextValueJsonXpathVisitor(xpath);
            JsonXpath.findAndUpdateMultiple(jn, xpath, distinctVis);
            assertEquals("Incorrect results for Xpath:"+ xpath,
                    new HashSet<String>(Arrays.asList(
                    "orgVdc-1000-2-01", "orgVdc-1000-2-02", "orgVdc-1000-1-01", "orgVdc-1000-1-03",
                    "orgVdc-1000-1-02", "orgVdc-1000-2-03", "orgVdc-1000-1-04", "orgVdc-1000-2-04")),
                    distinctVis.getDistinctSet());

            //4
            xpath = "//id";
            distinctVis = new DistinctTextValueJsonXpathVisitor(xpath);
            JsonXpath.findAndUpdateMultiple(jn, xpath, distinctVis);
            assertEquals("Incorrect results for Xpath:"+ xpath,
                    new HashSet<String>(Arrays.asList(
                    "orgVdc-1000-2-01", "orgVdc-1000-2-02", "orgVdc-1000-1-01", "1000", "vcd-1000-2",
                    "vcd-1000-1", "orgVdc-1000-1-03", "orgVdc-1000-1-02", "orgVdc-1000-2-03", "orgVdc-1000-1-04",
                    "orgVdc-1000-2-04", "cld-1000-2", "cld-1000-1")),
                    distinctVis.getDistinctSet());

        }
        catch(Exception e)
        {
            LOG.error("Could not execute test", e);
        }
    }



    /**
     * Select using Xpath with predicate (single as well as multi-level)
     * "//.../[]/..."
     * "//.../[]/.../[]/..." (multi-level predicates)
     */
    @Test
    public void testXpathSelectWithPredicatesForInputJsonObject() throws Exception {
        String orgVdcList = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("orgVdcList.json"), "UTF-8");
        JsonNode jn = getJsonNode(orgVdcList);

        String[] xpaths = new String[] {
            "/clouds[ 'cld-1000-2' == unDoubleQuote(value.get('id'))]//orgName",   // Find all the 'orgName's that lie within clouds with id=='cld-1000-2'
            "//clouds['cld-1000-1' == unDoubleQuote(value.get('id')) ]//orgName",  // Similar query, with descendant paths '//'
            "//id[unDoubleQuote(value).matches('cld-('+ d+ '+)-'+ d+ '')]",        // Find all 'id's that match the specified Regex "cld-(\d+)-\d+"
            "//id[unDoubleQuote(value).matches( 'orgVdc-[0-1]+-[2]-([0-9]+)' )]",  // With modified Regex "orgVdc-[0-1]+-[2]-([0-9]+)"
            "//orgVdcs[ '\"Active\"' !=  value.get( 'status' ) ]/name",            // All orgVdc names whose 'status' is non-'Active'
            "//orgVdcs[ 'Active' !=  unDoubleQuote( value.get('status') ) ]/name", // Same query, using 'unDoubleQuote' function to trim leading and trailing
                                                                                   // double-quotes in the string representation.

                                                                                   // Same query, with predicates at multiple levels
            "//orgVdcs[ 'Active' !=  unDoubleQuote( value.get('status') ) ]/name[!unDoubleQuote(value).endsWith('-03')]",


            "//id[true == false]",                    // Negative - the predicate can never be true, so no values would be selected.
        };

        String[] expectedResults = new String[] {
            "[\"OrgName-1000-X\", \"OrgName-1000-Y\", \"OrgName-1000-X\", \"OrgName-1000-X\"]",
            "[\"OrgName-1000-A\", \"OrgName-1000-B\", \"OrgName-1000-C\", \"OrgName-1000-B\"]",
            "[\"cld-1000-1\", \"cld-1000-2\"]",
            "[\"orgVdc-1000-2-01\", \"orgVdc-1000-2-02\", \"orgVdc-1000-2-03\", \"orgVdc-1000-2-04\"]",
            "[\"orgVdc-1000-1-03\", \"orgVdc-1000-2-04\"]",
            "[\"orgVdc-1000-1-03\", \"orgVdc-1000-2-04\"]",


            "[\"orgVdc-1000-2-04\"]",   // Expected value for multi-level predicate

            "[]",
        };

        int i = 0;
        for (String xpath : xpaths) {
            quickAssertXpathResult(xpath, expectedResults[i], jn);
            i++;
        }

    }

    /**
     * Test code to update source JSON, using ReplaceTextValueVisitor visitor.
     * @see ReplaceTextValueVisitor
     */
    @Test
    public void testXpathReplaceTextValueVisitorOnInputJson() throws Exception {
        String edgeFirewall = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("edgeFirewall.json"), "UTF-8");
        JsonNode jn = getJsonNode(edgeFirewall);
        LOG.info("Original JSON: {}", jn.toString());
        /*
         *  We haven't modified anything yet.
         *  Let's first see if query results on the original input is as expected
         */
        assertEquals("Incorret Xpath select //ruleId values",
                // expected
                "[131074, 133123, 131073]",
                // Actual ruleId values (array)
                JsonXpath.findAndUpdateMultiple(jn, "//ruleId", NullJsonFilter.instance()).toString()
        );
        assertEquals("Incorrect values for xpath: //groupingObjectId",
                // expected
                "[\"ipset-2\", \"resgroup-65\", \"vm-43\", \"securitygroup-10\", \"dvportgroup-40\", \"ipset-3\", \"vm-58\", \"ipset-4\", \"ipset-3\"]",
                // Actual ruleId values (array)
                JsonXpath.findAndUpdateMultiple(jn, "//groupingObjectId", NullJsonFilter.instance()).toString()
        );

        // Modification lookup
        Map<String, String> replacementValues = new HashMap<>();
        replacementValues.put("ipset-3", "$ipset_3");
        replacementValues.put("ipset-2", "$ipset_2");
        replacementValues.put("131074", "$ruleId");

        /*
         * Now we start modifying the source JSON
         */

        // #1 Replacement: ruleId; the original JSON is modified.
        String xpath = "//ruleId";
        String expectedJsonStr = "{\"featureType\":\"firewall_4.0\",\"version\":6,\"enabled\":true,\"globalConfig\":{\"tcpPickOngoingConnections\":false,\"tcpAllowOutOfWindowPackets\":false,"
                + "\"tcpSendResetForClosedVsePorts\":true,\"dropInvalidTraffic\":true,\"logInvalidTraffic\":false,\"tcpTimeoutOpen\":30,\"tcpTimeoutEstablished\":21600,\"tcpTimeoutClose\":30,"
                + "\"udpTimeout\":60,\"icmpTimeout\":10,\"icmp6Timeout\":10,\"ipGenericTimeout\":120,\"enableSynFloodProtection\":false,\"logIcmpErrors\":false,\"dropIcmpReplays\":false},"
                + "\"defaultPolicy\":{\"action\":\"accept\",\"loggingEnabled\":true},\"firewallRules\":{\"firewallRules\":[{\"ruleTag\":131074,\"name\":\"firewall\",\"ruleType\":\"internal_high\","
                + "\"enabled\":true,\"loggingEnabled\":false,\"description\":\"firewall\",\"action\":\"accept\",\"source\":{\"exclude\":false,\"ipAddress\":[],\"groupingObjectId\":[],"
                + "\"vnicGroupId\":[\"vse\"]},\"ruleId\":\"$ruleId\"},{\"ruleId\":133123,\"ruleTag\":133123,\"name\":\"test1\",\"ruleType\":\"user\",\"enabled\":true,\"loggingEnabled\":false,"
                + "\"description\":\"\",\"matchTranslated\":false,\"action\":\"accept\",\"source\":{\"exclude\":false,\"ipAddress\":[\"80.80.80.0/23\"],\"groupingObjectId\":[\"ipset-2\","
                + "\"resgroup-65\",\"vm-43\",\"securitygroup-10\",\"dvportgroup-40\",\"ipset-3\"],\"vnicGroupId\":[\"vse\"]},\"destination\":{\"exclude\":false,\"ipAddress\":[],"
                + "\"groupingObjectId\":[\"vm-58\",\"ipset-4\",\"ipset-3\"],\"vnicGroupId\":[\"vnic-index-0\"]},\"application\":{}},{\"ruleId\":131073,\"ruleTag\":131073,"
                + "\"name\":\"default rule for ingress traffic\",\"ruleType\":\"default_policy\",\"enabled\":true,\"loggingEnabled\":true,\"description\":\"default rule for ingress traffic\","
                + "\"action\":\"accept\"}]}}";
        JsonXpath.findAndUpdateMultiple(jn, xpath,
              new ReplaceTextValueVisitor(xpath, replacementValues));
        LOG.info("#1 modified JSON: {}", jn.toString());
        assertTrue("Incorrect replacement for xpath: "+ xpath, jn.toString().contains("$ruleId") );
        // Verifying no side-effects on other nodes.
        assertFalse("Incorrect replacement for xpath: "+ xpath, jn.toString().contains("$ipset_2") );
        assertFalse("Incorrect replacement for xpath: "+ xpath, jn.toString().contains("$ipset_3") );
        assertEquals("Incorrect replacement for xpath: "+ xpath,
                getJsonNode(expectedJsonStr), jn);
        /*
         *  The //ruleId values should have been modified based on the above 'Modification lookup'
         */
        assertEquals("Incorrect replacement for xpath: "+ xpath,
                // expected
                "[\"$ruleId\", 133123, 131073]",
                // Actual ruleId values (array)
                JsonXpath.findAndUpdateMultiple(jn, xpath, NullJsonFilter.instance()).toString()
        );


        // #2 Replacement: groupingObjectId
        xpath = "//groupingObjectId";
        JsonXpath.findAndUpdateMultiple(jn, xpath,
                new ReplaceTextValueVisitor(xpath, replacementValues));
        LOG.info("#2 modified JSON: {}", jn.toString());
        /*
         *  The //groupingObjectId values should have been modified based on the above 'Modification lookup'
         */
        assertEquals("Incorrect replacement for xpath: "+ xpath,
                // expected
                "[\"resgroup-65\", \"vm-43\", \"securitygroup-10\", \"dvportgroup-40\", \"$ipset_2\", \"$ipset_3\", \"vm-58\", \"ipset-4\", \"$ipset_3\"]",
                // Actual groupingObjectIds values (array)
                JsonXpath.findAndUpdateMultiple(jn, xpath, NullJsonFilter.instance()).toString()
        );

        // #3 Replacement ruleId
        replacementValues.put("131073", "$ruleId_2");
        xpath = "//firewallRules/firewallRules/ruleId";
        JsonXpath.findAndUpdateMultiple(jn, xpath,
                new ReplaceTextValueVisitor(xpath, replacementValues));
        LOG.info("#3 modified JSON: {}", jn.toString());
        /*
         *  The //ruleId values should have been modified based on the above 'Modification lookup'
         */
        assertEquals("Incorrect replacement for xpath: "+ xpath,
                // expected
                "[\"$ruleId\", 133123, \"$ruleId_2\"]",
                // Actual groupingObjectIds values (array)
                JsonXpath.findAndUpdateMultiple(jn, xpath, NullJsonFilter.instance()).toString()
        );


        // #4 Replacement groupingObjectId
        xpath = "//source/groupingObjectId";
        replacementValues.put("vm-43", "$go_1");
        JsonXpath.findAndUpdateMultiple(jn, xpath,
                new ReplaceTextValueVisitor(xpath, replacementValues));
        LOG.info("#3 modified JSON: {}", jn.toString());
        /*
         *  The //groupingObjectId values should have been modified based on the above 'Modification lookup'
         */
        assertEquals("Incorrect replacement for xpath: "+ xpath,
                // expected
                "[\"resgroup-65\", \"securitygroup-10\", \"dvportgroup-40\", \"$ipset_2\", \"$ipset_3\", \"$go_1\"]",
                // Actual groupingObjectIds values (array)
                JsonXpath.findAndUpdateMultiple(jn, xpath, NullJsonFilter.instance()).toString()
        );


        // #4 Replacement ruleId;
        xpath = "/firewallRules/ruleId";
        replacementValues.put("133123", "someValue");
        JsonXpath.findAndUpdateMultiple(jn, xpath,
                new ReplaceTextValueVisitor(xpath, replacementValues));
        /*
         *  Negative testcase: The //ruleId values not changed as xpath does not
         *  point to anything.
         */
        assertEquals("Incorrect replacement for xpath: "+ xpath,
                // expected
                "[\"$ruleId\", 133123, \"$ruleId_2\"]",
                // Actual groupingObjectIds values (array)
                JsonXpath.findAndUpdateMultiple(jn, "//ruleId", NullJsonFilter.instance()).toString()
        );
    }


    /**
     * Fetch a subDocument within a JSON
     */
    @Test
    public void testXpathSelectJsonSubdocument() throws Exception {
        String edgeFirewall = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("edgeFirewall.json"), "UTF-8");
        JsonNode jn = getJsonNode(edgeFirewall);
        // ---- Find the list of fw-rules Array within, the quick easy way. ----//
        List<JsonNode> resultSet = JsonXpath.findAndUpdateMultiple(
                jn, "//firewallRules", NullJsonFilter.instance());

        assertEquals("We expected only one 'firewallRules' subdocument", 1, resultSet.size());

        JsonNode resultSubdoc = resultSet.get(0);
        String expectedJson = "{\"firewallRules\":[{\"ruleId\":131074,\"ruleTag\":131074,\"name\":\"firewall\",\"ruleType\":\"internal_high\","
                + "\"enabled\":true,\"loggingEnabled\":false,\"description\":\"firewall\",\"action\":\"accept\",\"source\":"
                + "{\"exclude\":false,\"ipAddress\":[],\"groupingObjectId\":[],\"vnicGroupId\":[\"vse\"]}},"
                + "{\"ruleId\":133123,\"ruleTag\":133123,\"name\":\"test1\",\"ruleType\":\"user\",\"enabled\":true,\"loggingEnabled\":false,\"description\":\"\","
                + "\"matchTranslated\":false,\"action\":\"accept\",\"source\":{\"exclude\":false,\"ipAddress\":[\"80.80.80.0/23\"],"
                + "\"groupingObjectId\":[\"ipset-2\",\"resgroup-65\",\"vm-43\",\"securitygroup-10\",\"dvportgroup-40\",\"ipset-3\"],\"vnicGroupId\":[\"vse\"]},"
                + "\"destination\":{\"exclude\":false,\"ipAddress\":[],\"groupingObjectId\":[\"vm-58\",\"ipset-4\",\"ipset-3\"],\"vnicGroupId\":[\"vnic-index-0\"]},\"application\":{}},{\"ruleId\":131073,\"ruleTag\":131073,\"name\":\"default rule for ingress traffic\",\"ruleType\":\"default_policy\",\"enabled\":true,\"loggingEnabled\":true,\"description\":\"default rule for ingress traffic\",\"action\":\"accept\"}]}";

        assertEquals("Incorrect subdocument selection.", getJsonNode(expectedJson), resultSubdoc);
    }


    /**
     * Basic Xpath tests to query an array-item for a given index
     */
    @Test
    public void testXpathBasicFetchIndexedValueOnInputJsonArray() throws Exception {
        String[] xpaths = new String[] {
                "vcd/userName",
                "rmq/hosts[0]/host",
                "rmq/hosts[1]/host",
                "rmq/hostss[1]/host",
                "rmq/hosts[3]/host",
                "rmq/hosts[2]/host",
                "rmq/hosts[1]/port",
                "rmq/hosts",
                "nsx",
        };

        /*
         * For testing NodeTypes of the returned resultsets
         */
        Class<?>[] expectedNodeTypes = new Class[] {
                TextNode.class,
                TextNode.class,
                TextNode.class,
                MissingNode.class,
                MissingNode.class,
                TextNode.class,
                MissingNode.class,
                ArrayNode.class,
                ArrayNode.class,
        };

        String[] expedtedResults = new String[] {
                "\"VCD USER NAME\"",
                "\"10.144.99.33\"",
                "\"110.244.199.133\"",
                "",
                "",
                "\"20.200.20.200\"",
                "",
                "[{\"host\":\"10.144.99.33\",\"port\":\"15678\"},{\"host\":\"110.244.199.133\"},{\"host\":\"20.200.20.200\",\"port\":\"25678\"}]",
                "[{\"url\":\"NSX HOST\",\"userName\":\"NSX USER NAME\",\"password\":\"NSX PASSWORD\"},{\"url\":\"NSX HOST2\",\"userName\":\"NSX USER NAME2\",\"password\":\"NSX PASSWORD\"}]",
        };

        String vcdAdminConfig = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("vcdAdminConfig.json"),
                "UTF-8");
        JsonNode jn = getJsonNode(vcdAdminConfig);
        int i=0;
        for(String xpath: xpaths) {
            JsonNode resNode = JsonXpath.find(jn, xpath);
            LOG.info("{} - {} - {}", xpath, resNode.getClass().getName(), resNode);
            assertEquals("Incorrect String-value for xpath: "+ xpath+
                    "; Index="+i, expedtedResults[i], resNode.toString());
            assertEquals("Incorrect NodeType for xpath: "+ xpath+
                    "; Index="+i, expectedNodeTypes[i], resNode.getClass());
            i++;
        }
    }

    /**
     * Code reference to use Visitor (or nested visitors) to do more complex queries
     */
    @Test
    public void testXpathComplexQueriesUsingVisitors() throws Exception {
        String orgVdcList = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("orgVdcList.json"), "UTF-8");
        JsonNode jn = getJsonNode(orgVdcList);

        //1 Buid {vcd --> orgNameList[]} map from the input JSON
        final Map<String,  List<String>> map = new HashMap<>();
        JsonXpath.findAndUpdateMultiple(jn, "//vcloud", new JsonXpathVisitor()
        {
            @Override
            public boolean visit(Context ctx, JsonNode parent, JsonNode currentNodeToSelect)
                    throws XpathVisitorException, TraversalStopException
            {
                JsonNode vcd = currentNodeToSelect;
                String vcdName = JsonXpath.find(vcd, "name").asText();
                DistinctTextValueJsonXpathVisitor distinctVis = new DistinctTextValueJsonXpathVisitor("//orgName");
                JsonXpath.findAndUpdateMultiple(parent, "//orgName", distinctVis);
                for(String orgName: distinctVis.getDistinctSet())
                {
                    List<String> orgList = map.get(vcdName);
                    if(orgList==null) {
                        orgList = new ArrayList<>();
                    }
                    orgList.add(orgName);
                    map.put(vcdName, orgList);
                }
                return true;
            }
        });
        assertEquals("{vcd-1000-2=[OrgName-1000-X, OrgName-1000-Y], "
                    + "vcd-1000-1=[OrgName-1000-A, OrgName-1000-B, OrgName-1000-C]}",
                    map.toString());
    }

    @Test
    public void testXpathUpdateSourceUsingCustomVisitor() throws Exception {
        String edgeFirewall = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("edgeFirewall.json"), "UTF-8");
        JsonNode jn = getJsonNode(edgeFirewall);

        /*
         * 1. Modification: Adding a custom field 'testNumberOfGroupingObjects' to each firwall-rule containing the GOs.
         */
        JsonXpath.findAndUpdateMultiple(jn, "//firewallRules/firewallRules", new JsonXpathVisitor()
        {
            @Override
            public boolean visit(Context ctx, JsonNode parent, JsonNode currentNodeToSelect)
                    throws XpathVisitorException, TraversalStopException
            {
                // Parent would be the containing JSONArray;
                // currentNodeToSelect would be a JSONObject (for each item in the JSONArray)
                LOG.info("parent: {}", parent);
                LOG.info("current: {}", currentNodeToSelect);

                if(currentNodeToSelect instanceof ObjectNode) {
                    // ---- Using a nested visitor ---- //
                    int countGO = JsonXpath.findAndUpdateMultiple(
                            currentNodeToSelect, "//groupingObjectId", NullJsonFilter.instance()).size();

                    ((ObjectNode)currentNodeToSelect).put("testNumberOfGroupingObjects", countGO);
                }
                return true;
            }
        });

        // The source JSON should hasve been modified.
        assertEquals("Incorrect replacement for xpath: //testNumberOfGroupingObjects",
                "[0, 9, 0]",
                JsonXpath.findAndUpdateMultiple(jn, "//testNumberOfGroupingObjects",
                        NullJsonFilter.instance()).toString());
    }

    @Test
    public void testXpathComplexUpdatesOnJsonObject() throws Exception {

        // Templates
        String nsxEdgeTemplate = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("nsxEdge.json"), "UTF-8");
        String exportedEdgeServices = IOUtils
                .toString(TestJsonXpath.class.getResourceAsStream("exportedEdgeServices.json"), "UTF-8");
        assertNotNull("Could not read JSONObject 'nsxEdge.json'", nsxEdgeTemplate);
        JsonNode edge = getJsonNode(nsxEdgeTemplate);

        LOG.info("Tenant:{}", edge.get("tenant").getTextValue());

        String nsxEdgeVnicTemplate = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("nsxEdgeVnic.json"),
                "UTF-8");
        assertNotNull("Could not read JSONObject 'nsxEdgeVnic.json'", nsxEdgeVnicTemplate);

        LOG.info("Edge template = {}", new JSONObject(nsxEdgeTemplate.toString()).toString(2));
        LOG.info("Edge vNic template = {}", new JSONObject(nsxEdgeVnicTemplate.toString()).toString(2));

        // Expected
        final String expectedEdgeSpec = IOUtils
                .toString(TestJsonXpath.class.getResourceAsStream("expectedResultEdgeSpec.json"), "UTF-8");

        // Ensure basic IP functionalities.
        assertEquals("Could not calculate Subnet-PrefixLength correctly", 24,
                calculatePrefixLengthFromNetMask("255.255.255.0"));
        assertEquals("Could not calculate Subnet-PrefixLength correctly", 23,
                calculatePrefixLengthFromNetMask("255.255.254.0"));
        assertEquals("Could not calculate Subnet-PrefixLength correctly", 32,
                calculatePrefixLengthFromNetMask("255.255.255.255"));

        assertEquals("Could not calculate Subnet-PrefixLength correctly", "255.255.255.255",
                calculateNetMaskFromPrefixLength(32));
        assertEquals("Could not calculate Subnet-PrefixLength correctly", "255.255.255.0",
                calculateNetMaskFromPrefixLength(24));
        assertEquals("Could not calculate Subnet-PrefixLength correctly", "255.255.254.0",
                calculateNetMaskFromPrefixLength(23));


        List<String> placeholderNames = new ArrayList<>();
        // Pattern p = Pattern.compile("\"\\$\\S*\"");
        Pattern p = Pattern.compile("\"(\\$[^\",:]+)\"");
        Matcher m = p.matcher(exportedEdgeServices);
        int matchCount = 0;
        while (m.find()) {
            placeholderNames.add(m.group());
            matchCount++;
        }
        LOG.info("Placeholder count={}, Matches: {}", matchCount, placeholderNames);

        // Create Edge Spec using JsonUtils

        /*
         *  Updating moids to the Edge Spec
         */
        Map<String, String> moidMap = new HashMap<>();
        moidMap.put("datacenterMoid", "datacenter-1");
        moidMap.put("name", "TestEdge-0001");
        moidMap.put("resourcePoolId", "resourcePool-39");
        moidMap.put("datastoreId", "datastore-21");
        
        for (Entry<String, String> entry : moidMap.entrySet()) {
            JsonXpath.findAndUpdateMultiple(edge, "//" + entry.getKey(),
                    new CustomSpecUpdateVisitor(entry.getKey(), entry.getValue()));
        }

        /*
         * Addition of 3 interfaces to the Edge
         */
        JSONArray interfaceSpec = new JSONArray();
        interfaceSpec.put("internal:dvpg-23:192.168.10.1,192.168.10.2,192.168.10.3,192.168.10.4/23");
        interfaceSpec.put("uplink:dvpg-19:10.192.168.1,10.192.168.2,10.192.168.3,10.192.168.4/24");
        interfaceSpec.put("internal:virtualwire-12:192.168.111.253,192.168.111.250,192.168.111.251/22");
        List<JsonNode> intxNodes = new ArrayList<>();
        for (int i=0; i<interfaceSpec.length(); i++) {
            String intx = interfaceSpec.getString(i);
            
            int prefixLength = Integer.valueOf(intx.split("/")[1]);
            String interfaceType = intx.split("/")[0].split(":")[0];
            String nwPortgroup = intx.split("/")[0].split(":")[1];
            String ips[] = intx.split("/")[0].split(":")[2].split(",");
            String nMask = calculateNetMaskFromPrefixLength(prefixLength);
            
            Map<String, Object> intxMap = new HashMap<>();
            intxMap.put("//index", Integer.valueOf(i));
            intxMap.put("//name", nwPortgroup);
            intxMap.put("//label", nwPortgroup);
            intxMap.put("/type", interfaceType);
            intxMap.put("//portgroupId", nwPortgroup);
            intxMap.put("//primaryAddress", ips[0]);
            intxMap.put("//ipAddress", Arrays.copyOfRange(ips, 1, ips.length));
            intxMap.put("//subnetMask", nMask);


            JsonNode vNic = getJsonNode(nsxEdgeVnicTemplate);
            for (Entry<String, Object> entry : intxMap.entrySet()) {

                // LOG.info("ENV: {} -> {}", entry.getKey(), entry.getValue());

                JsonXpath.findAndUpdateMultiple(vNic, entry.getKey(),
                        new CustomSpecUpdateVisitor(entry.getKey(), entry.getValue()));
            }
            
            intxNodes.add(vNic);
        }
        
        for (JsonNode intxNode : intxNodes) {
            ((ArrayNode) (edge.get("vnics").get("vnics"))).add(intxNode);
        }

        JSONObject edgeSpec = new JSONObject(edge.toString());
        LOG.info("Edge Spec = {}", edgeSpec.toString(2));

        assertEquals("Incorrect JSON transformation", getJsonNode(expectedEdgeSpec),
                getJsonNode(edgeSpec.toString()));

        
        String nsxEdgeCreationSuccessResponse = IOUtils
                .toString(TestJsonXpath.class.getResourceAsStream("nsxEdgeCreationSuccessResponse.json"), "UTF-8");

        String edgeGetUrl = JsonXpath.findAndUpdateMultiple(getJsonNode(nsxEdgeCreationSuccessResponse), "//Location",
                NullJsonFilter.instance()).get(0).toString();
        edgeGetUrl = edgeGetUrl.replaceAll("\"", "");
        LOG.info("EdgeId from response: {}", edgeGetUrl);
        String edgeId = edgeGetUrl.contains("/") ? edgeGetUrl.substring(edgeGetUrl.lastIndexOf("/") + 1) : edgeGetUrl;
        assertEquals("Incorrect EdgeId extracted", "edge-13", edgeId);

        // Updating the sub-sub-elements
        final JsonNode exportedEdgeServicesTree = getJsonNode(exportedEdgeServices);
        final Map<String, String> userMappings = new HashMap<>();
        userMappings.put("vNic_0", "vNic_0");
        userMappings.put("vNic_1", "vNic_1");
        userMappings.put("99.99.99.99", "55.55.55.55");
        userMappings.put("190.0.0.1", "155.0.0.5");
        JsonXpath.findAndUpdateMultiple(exportedEdgeServicesTree,
                "//metadata//userInputNeeded[value.toString() == 'true']",
                new JsonXpathVisitor() {

                    @Override
                    public boolean visit(Context ctx, JsonNode parent, JsonNode currentNodeToSelect)
                            throws XpathVisitorException, TraversalStopException {

                        ObjectNode md = (ObjectNode) parent;
                        String oldValue = md.get("oldValue").getTextValue();
                        String newValue = userMappings.get(oldValue);
                        md.put("newValue", newValue);

                        return true;
                    }
                });

        LOG.info("User input updated: {}", new JSONObject(exportedEdgeServicesTree.toString()).toString(2));
        List<JsonNode> newValues = JsonXpath.findAndUpdateMultiple(exportedEdgeServicesTree, "//newValue",
                NullJsonFilter.instance());

        String expectedUserInputsSubstituted[] = new String[] { "vNic_0", "vNic_1", "55.55.55.55", "155.0.0.5" };
        int index = 0;
        for (JsonNode valNode : newValues) {
            assertEquals("Incorrect JSON transformation, for value:" + valNode, expectedUserInputsSubstituted[index++],
                    valNode.asText());
        }
    }

    /**
     * Test if resulting JSON structure after XPath application
     * is deserializable.
     */
    @Test
    public void testXpathIntegrity() throws Exception {
        String vcdAdminConfig = IOUtils.toString(TestJsonXpath.class.getResourceAsStream("vcdAdminConfig.json"),
                "UTF-8");
        JsonNode jn = getJsonNode(vcdAdminConfig);
        RmqHosts hostports = new RmqHosts();
        List<JsonNode> hostportJsonNode = JsonXpath.findAndUpdateMultiple(jn, "//rmq/hosts", NullJsonFilter.instance());
        hostports.readFrom(hostportJsonNode);
        assertEquals("Incorrect deserialization", "10.144.99.33:15678; 110.244.199.133; 20.200.20.200:25678",  hostports.toString());
    }
    
    @Test
    public void testUsingJsonPath() throws Exception{
//        JsonNode jn = getJsonNode(EDGE_FW);
//        String xpath = "//groupingObjectId[unDoubleQuote(value).startsWith('resgroup-')]";
//        List<JsonNode> resultSet1 = JsonXpath.findAndUpdateMultiple(jn, xpath, NullJsonFilter.instance());
//        LOG.info("|||||||  Grouping objects of type resourceGroup: {}", resultSet1);
//        assertEquals("Incorrect Xpath Resultset", "[\"resgroup-65\"]", resultSet1.toString());
//        
//        
//        final DocumentContext documentContext = JsonPath.using(TestBase.JSON_PATH_CONFIGURATION).parse(EDGE_FW);
//        /*
//         * Functional Matrix for JsonPath:
//         * 
//         * $..groupingObjectId[?(@.length > 0)]  - Does NOT work; no element selected.
//         * $..groupingObjectId                   - Does NOT work; but wiped off the entire JSONArray.
//         * $..groupingObjectId[0]                - Works precisely, replaced the first element in the array as intended where > 1 elements where found; Handled cases where there were 0 elements.
//         * $..groupingObjectId[4]                - Does NOT work, Replaced one instance where there were > 5 elements. Threw Exceptions elsewhere.
//         * $..groupingObjectId[?(@ == 'vm-43')]  - Works precisely, the correct element replaced.
//         * $..groupingObjectId[?(@.startsWith('vm-'))]                                   - Does NOT work
//         * $..groupingObjectId[?(@ =~ /^ipset-[\\d]+$/)]                                 - Works precisely.
//         * $..groupingObjectId[ ?(@ =~ /^ipset-[\\d]+$/) ][ ?(@ =~ /^ipset-[\\d]+$/) ]   - Works
//         * $..groupingObjectId[ ?(@ =~ /^ipset-[\\d]+$/) ][ 0 ]                          - Does NOT work.
//         * $..groupingObjectId[?(true)]                                                  - Does NOT work.
//         * $..groupingObjectId[true]                                                     - Does NOT work, that means dynamic evaluation not possible. No, custom functions supported.
//         * $..ruleId                              - Works, replaces all occurrences
//         * $..ruleId[0]                           - Does NOT work; no elements replaced.
//         * $..action[ ?(@ =~ /^accept.*$/) ]      - Does NOT work; no elements replaced.
//         * $..exclude                             - Works, replaces all occurrences
//         * $..exclude                             - Works, replaces all occurrences
//         * $..exclude[?(@ == true)]               - Does NOT work; no elements replaced.
//         * $..groupingObjectId[?(@ =~ /^\$.*$/)]  - Works, if you already know the element/field in the JSON where the value-regex is expected.
//         * $..*                                   - Works: "Give me every thing" works!
//         * $..*[?(@ =~ /^\$.*$/ )]                - Does NOT work: 'Give me every thing, where the field-value(s) follow the given Regex' DOES NOT WORK.
//         * 
//         */
//        String equivalentJsonPath = "$..groupingObjectId[0]";
//        documentContext.set(equivalentJsonPath, "$someVariable");
//        LOG.info("Resulting JSON: {}\n", new JSONObject(documentContext.jsonString()));
//        
//        /*
//         * Try the above Xpaths here.
//         */
//        equivalentJsonPath = "$..groupingObjectId[?(@ =~ /^\\$.*$/)]";
//        documentContext.set(equivalentJsonPath, "wow!-123");
//        LOG.info("Resulting JSON2: {}\n", new JSONObject(documentContext.jsonString()));
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

