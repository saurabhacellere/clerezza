/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.clerezza.platform.security.auth;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.wymiwyg.wrhapi.Handler;
import org.wymiwyg.wrhapi.HandlerException;
import org.wymiwyg.wrhapi.Request;
import org.wymiwyg.wrhapi.Response;
import org.wymiwyg.wrhapi.filter.Filter;

/**
 * 
 * @author reto
 * 
 * @scr.component 
 *                name="org.apache.clerezza.platform.security.auth.AuthenticatingFilter"
 * @scr.service interface="org.wymiwyg.wrhapi.filter.Filter"
 * @scr.reference name="weightedAuthenticationMethod"
 *     cardinality="1..n" policy="dynamic"
 *     interface="org.apache.clerezza.platform.security.auth.WeightedAuthenticationMethod"
 */
public class AuthenticatingFilter implements Filter {

	private final Logger logger = LoggerFactory.getLogger(AuthenticatingFilter.class);
	private SortedSet<WeightedAuthenticationMethod> methodList =
			new TreeSet<WeightedAuthenticationMethod>(new WeightedAuthMethodComparator());
	public static final Subject ANONYMOUS_SUBJECT = new Subject(true,
			Collections.singleton(new PrincipalImpl("anonymous")), Collections.EMPTY_SET,
			Collections.EMPTY_SET);

	@Override
	public void handle(final Request request, final Response response,
			final Handler wrapped) throws HandlerException {

		String userName = null;
		AuthenticationMethod authenticationMethod = null;
		try {
			for (Iterator<WeightedAuthenticationMethod> it = methodList.iterator(); it.hasNext();) {
				authenticationMethod = it.next();
				userName = authenticationMethod.authenticate(request);
				if (userName != null) {
					break;
				}
			}
		} catch (LoginException ex) {
			if (!authenticationMethod.writeLoginResponse(request, response, ex)) {
				writeLoginResponse(request, response, ex);
			}
			return;
		}

		Subject subject;
		if (userName == null) {
			subject = ANONYMOUS_SUBJECT;
		} else {
			subject = new Subject();
			subject.getPrincipals().add(new PrincipalImpl(userName));
		}
		try {
			Subject.doAsPrivileged(subject, new PrivilegedExceptionAction() {

				@Override
				public Object run() throws Exception {
					wrapped.handle(request, response);
					return null;
				}
			}, null);

		} catch (PrivilegedActionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof HandlerException) {
				throw (HandlerException) cause;
			}
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			logger.debug("SecurityException: {}", e);
			writeLoginResponse(request, response, e);
		}
	}

	/**
	 * Registers a <code>WeightedAuthenticationMethod</code>
	 *
	 * @param method the method to be registered
	 */
	protected void bindWeightedAuthenticationMethod(WeightedAuthenticationMethod method) {
		methodList.add(method);
	}

	/**
	 * Unregister a <code>WeightedAuthenticationMethod</code>
	 *
	 * @param method the method to be deregistered
	 */
	protected void unbindWeightedAuthenticationMethod(WeightedAuthenticationMethod method) {
		methodList.remove(method);
	}

	/**
	 * Compares the WeightedAuthenticationMethods, descending for weight and ascending by name
	 */
	static class WeightedAuthMethodComparator
			implements Comparator<WeightedAuthenticationMethod> {

		@Override
		public int compare(WeightedAuthenticationMethod o1,
				WeightedAuthenticationMethod o2) {
			int o1Weight = o1.getWeight();
			int o2Weight = o2.getWeight();
			if (o1Weight != o2Weight) {
				return o2Weight - o1Weight;
			}
			return o1.getClass().toString().compareTo(o2.getClass().toString());
		}
	}

	private void writeLoginResponse(final Request request, final Response response, Throwable e) throws HandlerException {
		for (AuthenticationMethod authMethod : methodList) {
			if (authMethod.writeLoginResponse(request, response, e)) {
				break;
			}
		}
	}
}
