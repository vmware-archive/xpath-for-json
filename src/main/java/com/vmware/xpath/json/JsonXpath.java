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
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.MissingNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.ValueNode;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.xpath.TraversalStopException;
import com.vmware.xpath.XpathVisitorException;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import static javax.script.ScriptContext.ENGINE_SCOPE;
import static javax.script.ScriptContext.GLOBAL_SCOPE;

/**
 * Provides static method to use Xpath with JSON 
 *
 * @author banerjees
 * @see TestJsonUtils
 * @Copyright (c) 2018 VMware, Inc.  All rights reserved
 */
public class JsonXpath
{
    private static final class Pair<K, T> {
        final K k;

        final T t;

        public Pair( K k, T t ) {
            this.k = k;
            this.t = t;
        }
    }

    private static final Logger LOG = LoggerFactory
            .getLogger(JsonXpath.class);

    private static SimpleScriptContext SCR_CTX = new SimpleScriptContext();

    private static ScriptEngineManager SCR_FACTORY = new ScriptEngineManager();

    private static ScriptEngine SCR_ENGINE = SCR_FACTORY.getEngineByName( "JavaScript" );
    static {

        /*
         * - Regex Metacharacters -
         *
         * @see: https://www.w3schools.com/jsref/jsref_obj_regexp.asp
         */
        SCR_CTX.setAttribute( "d", "\\d", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "s", "\\s", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "S", "\\S", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "D", "\\D", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "b", "\\b", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "B", "\\B", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "w", "\\w", ENGINE_SCOPE );
        SCR_CTX.setAttribute( "W", "\\W", ENGINE_SCOPE );

        Function<?, String> unDoubleQuote = ( str ) -> {
            String ret = str.toString();

            if ( ret.startsWith( "\"" ) && ret.endsWith( "\"" ) ) {
                return ret.substring( 1, ret.length() - 1 );
            }
            return ret;
        };

        SCR_CTX.setAttribute( "unDoubleQuote", unDoubleQuote, ENGINE_SCOPE );
    }

    /**
     *
     */
    public JsonXpath() {
    }

    /**
     * @param tree
     * @param xpath
     * @return
     */
    public static boolean exists( JsonNode tree, String xpath ) {
        return !( find( tree, xpath ) instanceof MissingNode );
    }

    /**
     * Implements a best-fit Xpath evaluator on JSON object. Very basic implementation of XPath over JSON. only static
     * values and [] supported.
     * 
     * @param tree
     * @param xpath
     * @return The first occurrence of the JsonNode represented by the Xpath, in case there are more than one
     *         candidates.
     */
    public static JsonNode find( JsonNode tree, String xpath ) {
        if ( xpath == null || xpath.isEmpty() )
            throw new IllegalArgumentException( "Invalid Xpath: " + xpath );

        String[] paths = xpath.split( "/" );

        JsonNode res = tree;
        for ( String path : paths ) {
            if ( path.contains( "[" ) ) {
                String realPath = path.substring( 0, path.indexOf( "[" ) );
                String index = path.substring( path.indexOf( "[" ) + 1, path.indexOf( "]" ) );

                if ( res == null || res instanceof MissingNode )
                    return MissingNode.getInstance();
                res = res.findPath( realPath );

                if ( Character.isDigit( index.charAt( 0 ) ) ) {
                    if ( !res.has( Integer.valueOf( index ) ) )
                        return MissingNode.getInstance();
                    res = res.path( Integer.valueOf( index ) );
                }
            } else {
                if ( res == null || res instanceof MissingNode )
                    return MissingNode.getInstance();
                res = res.findPath( path );
            }
        }

        return res == null ? MissingNode.getInstance() : res;
    }

    /**
     * Traverses the nodes given by the xpath, and lets the visitor modify the source JSON tree.
     *
     * @param tree
     * @param xpath
     * @param visitor
     * @return
     */
    public static List<JsonNode> findAndUpdateMultiple( JsonNode tree, String xpath, JsonXpathVisitor visitor ) {
        LOG.debug( "XPath: {}", xpath );
        List<JsonNode> ret = new ArrayList<JsonNode>();

        if ( xpath == null || xpath.isEmpty() || xpath.equals( "/" ) || xpath.equals( "//" ) )
            throw new IllegalArgumentException( "Invalid JPath: " + xpath );

        boolean singular = true;

        /*
         * Trim all the leading '//' chars
         */
        if ( xpath.startsWith( "//" ) ) {
            singular = false;
            xpath = xpath.substring( 2 );
        } else if ( xpath.startsWith( "/" ) ) {
            singular = true;
            xpath = xpath.substring( 1 );
        }

        /*
         * unary path
         */
        if ( xpath.indexOf( '/' ) == -1 )
            return singular ? getSingularDepthValues( tree, xpath, visitor ) : getMultipleDepthValues( tree, xpath, visitor );

        /*
         * form: XPATH = <HEAD> / <CONS>
         * HEAD ==> singular depth
         * CONS ==> new (sub) XPATH
         */
        String head = xpath.substring( 0, xpath.indexOf( '/' ) );
        String cons = xpath.substring( xpath.indexOf( '/' ) );

        if ( head.isEmpty() )
            return findAndUpdateMultiple( tree, cons, visitor );

        List<JsonNode> breadthList =
            singular ? getSingularDepthValues( tree, head, new DebugJsonXpathVisitor( xpath, false ) ) : getMultipleDepthValues( tree, head, new DebugJsonXpathVisitor( xpath, false ) );

        for ( JsonNode currentNode : breadthList ) {
            LOG.debug( "BREADTH LIST: {}", currentNode );
            ret.addAll( findAndUpdateMultiple( currentNode, cons, visitor ) );
        }

        return ret;
    }

    /**
     * @param tree
     * @param xpath
     * @param visitor
     * @return
     */
    private static List<JsonNode> getMultipleDepthValues( JsonNode tree, String xpath, JsonXpathVisitor visitor ) {
        List<JsonNode> filteredNodes = new ArrayList<>();
        List<Pair<JsonNode, JsonNode>> traverseMap = new ArrayList<>();
        String filterExprStr = "";

        if ( xpath.contains( "[" ) ) {
            if ( !xpath.endsWith( "]" ) ) {
                throw new IllegalArgumentException( "Incorrect XPath with filter: " + xpath );
            }

            filterExprStr = xpath.substring( xpath.indexOf( '[' ) + 1, xpath.lastIndexOf( ']' ) );
            xpath = xpath.substring( 0, xpath.indexOf( '[' ) );
        }

        List<JsonNode> foundParents = tree.findParents( xpath );
        List<JsonNode> foundValues = tree.findValues( xpath );

        for ( int i = 0; i < foundValues.size(); i++ ) {
            try {
                LOG.debug( "\n\n\nFilter Pattern: {}", filterExprStr );

                JsonNode fieldValue = foundValues.get( i );
                JsonNode parentValue = foundParents.get( i );

                if ( fieldValue instanceof ArrayNode ) {

                    ArrayNode fieldArr = (ArrayNode) fieldValue;

                    for ( Iterator<JsonNode> itr = fieldArr.iterator(); itr.hasNext(); ) {
                        JsonNode jthFieldValue = itr.next();

                        if ( !filterExprStr.isEmpty() ) {
                            SCR_CTX.setAttribute( "value", jthFieldValue, ENGINE_SCOPE );
                            if ( LOG.isDebugEnabled() ) {
                                SCR_ENGINE.eval( "print('value-type: ', typeof value)", SCR_CTX );
                                SCR_ENGINE.eval( "print('value: ', value)", SCR_CTX );
                                SCR_ENGINE.eval( "print('MATCH: ', " + filterExprStr + ")", SCR_CTX );
                            }

                            Object filterResult = SCR_ENGINE.eval( filterExprStr, SCR_CTX );
                            if ( filterResult instanceof Boolean ) {
                                if ( Boolean.TRUE.equals( filterResult ) ) {
                                    filteredNodes.add( jthFieldValue );
                                    traverseMap.add( new Pair( fieldArr, jthFieldValue ) );
                                } else {
                                    LOG.debug( "SKIPPING: {}", filterExprStr );
                                }
                            }
                        } else {
                            filteredNodes.add( jthFieldValue );
                            traverseMap.add( new Pair( fieldArr, jthFieldValue ) );
                        }
                    }
                } else {

                    if ( !filterExprStr.isEmpty() ) {
                        SCR_CTX.setAttribute( "value", fieldValue, ENGINE_SCOPE );
                        if ( LOG.isDebugEnabled() ) {
                            SCR_ENGINE.eval( "print('value-type: ', typeof value)", SCR_CTX );
                            SCR_ENGINE.eval( "print('value: ', value)", SCR_CTX );
                            SCR_ENGINE.eval( "print('MATCH: ', " + filterExprStr + ")", SCR_CTX );
                        }
                        Object filterResult = SCR_ENGINE.eval( filterExprStr, SCR_CTX );
                        if ( filterResult instanceof Boolean ) {
                            if ( Boolean.FALSE.equals( filterResult ) ) {
                                LOG.debug( "SKIPPING: {}", filterExprStr );
                                continue;
                            }
                        }
                    }

                    filteredNodes.add( fieldValue );
                    traverseMap.add( new Pair( parentValue, fieldValue ) );
                }

            } catch ( ScriptException e ) {
                throw new IllegalArgumentException( "Illegal Filter Expression: " + filterExprStr, e );
            } catch ( Exception e ) {

            }
        }

        for ( Pair<JsonNode, JsonNode> pairs : traverseMap ) {
            try {
                visitor.visit( pairs.k, pairs.t );
            } catch ( XpathVisitorException e ) {
                continue;
            } catch ( TraversalStopException e ) {
                break;
            }
        }

        return filteredNodes;
    }

    /**
     * @param tree
     * @param xpath
     * @param visitor
     * @return
     */
    private static List<JsonNode> getSingularDepthValues( JsonNode tree, String xpath, JsonXpathVisitor visitor ) {
        List<JsonNode> ret = new ArrayList<JsonNode>();

        if ( tree instanceof ObjectNode )
            return findSingularDepthValuesOnObjectNode( tree, xpath, visitor );

        if ( tree instanceof ArrayNode ) {
            Iterator<JsonNode> itr = ( (ArrayNode) tree ).getElements();
            while ( itr.hasNext() ) {
                JsonNode elm = itr.next();
                ret.addAll( findSingularDepthValuesOnObjectNode( elm, xpath, visitor ) );
            }
        }

        return ret;
    }

    /**
     * @param tree
     * @param xpath
     * @param visitor
     * @return
     */
    private static List<JsonNode> findSingularDepthValuesOnObjectNode( JsonNode tree, String xpath, JsonXpathVisitor visitor ) {
        List<JsonNode> ret = new ArrayList<JsonNode>();

        if ( !( tree instanceof ObjectNode ) )
            return ret;

        ObjectNode on = (ObjectNode) tree;

        String filterExprStr = "";

        if ( xpath.contains( "[" ) ) {
            if ( !xpath.endsWith( "]" ) ) {
                throw new IllegalArgumentException( "Incorrect XPath with filter: " + xpath );
            }

            filterExprStr = xpath.substring( xpath.indexOf( '[' ) + 1, xpath.indexOf( ']' ) );
            xpath = xpath.substring( 0, xpath.indexOf( '[' ) );
        }

        Iterator<Entry<String, JsonNode>> itr = on.getFields();

        Map<JsonNode, List<JsonNode>> traverseMap = new HashMap<>();
        while ( itr.hasNext() ) {
            Entry<String, JsonNode> field = itr.next();

            if ( field.getKey().equals( xpath ) ) {
                JsonNode fieldValue = field.getValue();

                LOG.debug( "\n\n\nFilter Pattern Singular Depth: {}", filterExprStr );

                if ( fieldValue instanceof ArrayNode ) {

                    List<JsonNode> existingValues = traverseMap.get( fieldValue );
                    if ( existingValues == null ) {
                        existingValues = new ArrayList<JsonNode>();
                        traverseMap.put( fieldValue, existingValues );
                    }

                    ArrayNode arrayValues = (ArrayNode) fieldValue;
                    for ( Iterator<JsonNode> itr2 = arrayValues.iterator(); itr2.hasNext(); ) {
                        JsonNode ithFieldValue = itr2.next();

                        if ( !filterExprStr.isEmpty() ) {
                            try {
                                SCR_CTX.setAttribute( "value", ithFieldValue, ENGINE_SCOPE );
                                if ( LOG.isDebugEnabled() ) {
                                    SCR_ENGINE.eval( "print('(Singular) value-type: ', typeof value)", SCR_CTX );
                                    SCR_ENGINE.eval( "print('(Singular) value: ', value.get('id'))", SCR_CTX );
                                    SCR_ENGINE.eval( "print('(Singular) MATCH: ', " + filterExprStr + ")", SCR_CTX );
                                }
                                Object filterResult = SCR_ENGINE.eval( filterExprStr, SCR_CTX );
                                if ( filterResult instanceof Boolean ) {
                                    if ( Boolean.TRUE.equals( filterResult ) ) {
                                        ret.add( ithFieldValue );
                                        existingValues.add( ithFieldValue );

                                    } else {
                                        LOG.debug( "SKIPPING (Singular Depth): {}", filterExprStr );
                                    }
                                }
                            } catch ( ScriptException e ) {
                                throw new IllegalArgumentException( "Illegal Filter Expression: " + filterExprStr, e );
                            }
                        } else {
                            ret.add( ithFieldValue );
                            existingValues.add( ithFieldValue );
                        }
                    }
                } else {

                    List<JsonNode> existingValues = traverseMap.get( on );
                    if ( existingValues == null ) {
                        existingValues = new ArrayList<JsonNode>();
                        traverseMap.put( on, existingValues );
                    }

                    // Field value is likely a text-node
                    if ( !filterExprStr.isEmpty() ) {
                        try {
                            SCR_CTX.setAttribute( "value", fieldValue, ENGINE_SCOPE );
                            if ( LOG.isDebugEnabled() ) {
                                SCR_ENGINE.eval( "print('(Singular) value-type: ', typeof value)", SCR_CTX );
                                SCR_ENGINE.eval( "print('(Singular) value: ', value)", SCR_CTX );
                                SCR_ENGINE.eval( "print('(Singular) MATCH: ', " + filterExprStr + ")", SCR_CTX );
                            }
                            Object filterResult = SCR_ENGINE.eval( filterExprStr, SCR_CTX );
                            if ( filterResult instanceof Boolean ) {
                                if ( Boolean.TRUE.equals( filterResult ) ) {
                                    ret.add( fieldValue );
                                    existingValues.add( fieldValue );

                                } else {
                                    LOG.debug( "SKIPPING (Singular Depth): {}", filterExprStr );
                                }
                            }
                        } catch ( ScriptException e ) {
                            throw new IllegalArgumentException( "Illegal Filter Expression: " + filterExprStr, e );
                        }
                    } else {
                        ret.add( fieldValue );
                        existingValues.add( fieldValue );
                    }
                }
            }
        }

        for ( Entry<JsonNode, List<JsonNode>> entries : traverseMap.entrySet() )
            for ( JsonNode entry : entries.getValue() ) {
                try {
                    visitor.visit( entries.getKey(), entry );
                } catch ( XpathVisitorException e ) {
                    LOG.warn( "Skipping incorrect handler exception: {}", e.getMessage() );
                    LOG.debug( "Skipping incorrect handler exception", e );
                    continue;
                } catch ( TraversalStopException e ) {
                    break;
                }
            }

        return ret;
    }

    /**
     * Allows Readonly visiting of nodes. No source modification performed.
     *
     * @param json
     * @param xpath
     * @param visitor
     * @return
     * @throws JsonProcessingException
     * @throws IOException
     */
    public static List<JsonNode> findMultiple( JSONObject json, String xpath, JsonXpathVisitor visitor )
        throws JsonProcessingException, IOException {
        ObjectMapper om = new ObjectMapper();
        return findAndUpdateMultiple( om.readTree( json.toString() ), xpath, visitor );
    }

    /*
    *
    * This method accepts a JSON Object as an input and
    * returns a map which contains dotted representation of the JSON Object
    * , as Key and it's corresponding value.
    *
    * Sample input:
    *
    * {
    *   A:
    *       {
    *           B:
    *           {
    *               C:10
    *           }
    *       }
    * }
    *
    * Sample output:
    *
    * {A.B.C=10}
    *
    * @param json The JSON Object
    * @return Map
    *
    */

    public static Map<String, Object> flattenJSONObjectToMap( JSONObject json ) {
        Map<String, Object> keyValueMap = new HashMap<>();
        try {
            createDottedStringFromJson( "", new ObjectMapper().readTree( json.toString() ), keyValueMap );
        } catch ( IOException e ) {
            String errorMessage = "Unable to flatten the JSON object" + e.getMessage();
            LOG.error( errorMessage, e );
        }
        return keyValueMap;
    }

    private static void createDottedStringFromJson( String currentPath, JsonNode jsonNode, Map<String, Object> keyValueMap ) {
        if ( jsonNode.isObject() ) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            Iterator<Map.Entry<String, JsonNode>> iter = objectNode.getFields();
            String pathPrefix = currentPath.isEmpty() ? "" : currentPath + ".";
            while ( iter.hasNext() ) {
                Map.Entry<String, JsonNode> entry = iter.next();
                createDottedStringFromJson( pathPrefix + entry.getKey(), entry.getValue(), keyValueMap );
            }
        } else if ( jsonNode.isArray() ) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            for ( int i = 0; i < arrayNode.size(); i++ ) {
                createDottedStringFromJson( currentPath, arrayNode.get( i ), keyValueMap );
            }
        } else if ( jsonNode.isValueNode() ) {
            ValueNode valueNode = (ValueNode) jsonNode;
            keyValueMap.put( currentPath, valueNode.asText() );
        }
    }

    public static JSONArray append( JSONArray dst, JSONArray src )
        throws Exception {
        if ( dst == null )
            dst = new JSONArray();
        if ( src != null && src.length() > 0 ) {
            for ( int i = 0; i < src.length(); i++ ) {
                dst.put( src.get( i ) );
            }
        }
        return dst;
    }

    public static JSONArray append( JSONArray dst, Object src )
        throws Exception {
        if ( dst == null )
            dst = new JSONArray();
        dst.put( src );
        return dst;
    }

    public static void appendUniqueStrings( JSONArray destination, JSONArray source )
        throws Exception {
        if ( destination == null )
            destination = new JSONArray();
        if ( source != null && source.length() > 0 ) {
            for ( int i = 0; i < source.length(); i++ ) {
                String sourceElement = source.getString( i );
                boolean present = false;
                for ( int j = 0; j < destination.length(); j++ ) {
                    if ( destination.getString( j ).equals( sourceElement ) ) {
                        present = true;
                        break;
                    }
                }
                if ( !present ) {
                    destination.put( sourceElement );
                }
            }
        }
    }

    public static JSONObject pick( JSONObject src, String[] keys )
        throws Exception {
        JSONObject dst = new JSONObject();
        if ( src != null && keys != null && keys.length > 0 ) {
            for ( String k : keys ) {
                try {
                    dst.put( k, src.opt( k ) );
                } catch ( Exception ex ) {
                    // ???
                }
            }
        }
        return dst;
    }

    @SuppressWarnings("unchecked")
    public static void copyEntries(JSONObject dest, JSONObject src) {
        if (src == null) {
            return;
        }

        src.keys().forEachRemaining(new Consumer<String>() {
            @Override
            public void accept(String key) {
                try {
                    dest.put(key, src.get(key));
                } catch (JSONException e) {
                    LOG.warn("Error copying key-value from src to dest");
                }
            }
        });

    }
}
