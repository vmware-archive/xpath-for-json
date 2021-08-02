/*
 *
 * xpath-for-json
 *
 * Copyright (c) 2021 VMware, Inc.  All rights reserved
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
package com.vmware.xpath.context;

import java.util.HashSet;
import java.util.Set;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * Creates a context for the clients/visitor to associate info with the traversed XPath nodes.
 * @author banerjees
 *
 */
public final class Context {
	private static final Logger logger = LoggerFactory.getLogger(Context.class);
	private static final Set<String> nonOverwritable = new HashSet<>();
	
	static {
		nonOverwritable.add("_parent");
		nonOverwritable.add("_child");
		nonOverwritable.add("_root");
	}
	
	private final JSONObject ctx;
	private final String name;
	
	public static Context create() {
		return create("root");
	}
	
	public static Context create(String name) {
		return new Context(name);
	}
	
	private Context(String name) {
		this.name = name;
		this.ctx = new JSONObject();
		this.setInternal("_parent", this);
		this.setInternal("_root", this);
	}
	
	public final Context createSubContext(String name) {
		Context child = new Context(name);
		child.setInternal("_parent", this);
		child.setInternal("_root", this.root());
		return child;
	}
	
	public final Context parent() {
		return (Context) this.get("_parent");
	}
	
	public final String name() {
		return this.name;
	}
	
	public final boolean isRoot() {
		return (Context) this.get("_parent") == this;
	}
	
	public final Context root() {
		return isRoot() ? this : this.parent().root();
	}

	public Object get(String key) {
		try {
			return ctx.get(key);
		} catch (JSONException e) {
			logger.debug("Error fetching context value for the key: {}", key, e);
			return null;
		}
	}
	
	public void set(String key, Object value) {
		if(nonOverwritable.contains(key)) {
			throw new RuntimeException("Cannot overwrite value for the key: "+ key);
		}
		setInternal(key, value);
	}

	private void setInternal(String key, Object value) {
		try {
			this.ctx.put(key, value);
		} catch (JSONException e) {
			throw new RuntimeException("Could not set value for the key: "+ key, e);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Context curr = this;
		do {
			sb.append(" >> ");
			sb.append(this.name);
		} while(!(curr = curr.parent()).isRoot()) ;
		return sb.toString();
	}
	
	// Future: Search within parent contexts, Utils.
	
}
