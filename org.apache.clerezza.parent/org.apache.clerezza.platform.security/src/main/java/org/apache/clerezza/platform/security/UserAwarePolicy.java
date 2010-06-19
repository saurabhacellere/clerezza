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
package org.apache.clerezza.platform.security;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.clerezza.platform.config.SystemConfig;

import org.osgi.service.permissionadmin.PermissionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.ontologies.PERMISSION;
import org.apache.clerezza.rdf.ontologies.PLATFORM;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.SIOC;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;

/**
 * 
 * @author mir
 */
@Component
@Service(UserAwarePolicy.class)
public class UserAwarePolicy extends Policy {

	final Logger logger = LoggerFactory.getLogger(UserAwarePolicy.class);
	
	@Reference(target=SystemConfig.SYSTEM_GRAPH_FILTER)
	private MGraph systemGraph;
	
	/**
	 * Stores the mapping between a String describing the permission and the
	 * described <code>Permission</code> object.
	 */
	private Map<String, Permission> permissionMap = new HashMap<String, Permission>();

	/**
	 * Cache for user permissions
	 */
	private UserPermissionsCache cache = new UserPermissionsCache();

	private Policy originalPolicy;

	public UserAwarePolicy() {
		this.originalPolicy = Policy.getPolicy();
	}
	
	@Override
	public PermissionCollection getPermissions(final ProtectionDomain domain) {

		PermissionCollection result;

		Principal[] principals = domain.getPrincipals();
		if (principals.length > 0) {
			final Principal user = domain.getPrincipals()[0];

			result = cache.getCachedUserPermissions(user);
			if (result != null) {
				return result;
			} else {
				result = getUserPermissionsFromSystemGraph(user);
				cache.cacheUserPermissions(user, result);
			}
		} else {
			result = originalPolicy.getPermissions(domain);
		}
		return result;
	}

	@Override
	public void refresh() {
		cache.clear();
	}

	/**
	 * Returns the permissions of the specified user according to the entries in
	 * the sytemGraph.
	 * 
	 * @param user
	 * @return
	 * @throws java.lang.IllegalArgumentException
	 * @throws java.lang.SecurityException
	 */
	private PermissionCollection getUserPermissionsFromSystemGraph(
			final Principal user) throws IllegalArgumentException,
			SecurityException, UserUnregisteredException {
		final PermissionCollection result = new Permissions();
		AccessController.doPrivileged(new PrivilegedAction<Object>() {

			@Override
			public Object run() {
				logger.debug("Get permissions for user " + user.getName());

				List<String> permissions = getAllPermissionsOfAUserByName(user
						.getName());
				for (String permissionStr : permissions) {
					logger.debug("Add permission {}", permissionStr);
					Permission perm = permissionMap.get(permissionStr);

					// make new permission, if the required
					// <code>Permission</code> object is not in the map.
					if (perm == null) {
						PermissionInfo permissionInfo;
						try {
							permissionInfo = new PermissionInfo(
									permissionStr);
						} catch (IllegalArgumentException e) {
							logger.error("parsing "+permissionStr,e);
							continue;
						}

						try {
							Class clazz = Class.forName(permissionInfo
									.getType());
							Constructor<?> constructor = clazz.getConstructor(
									String.class, String.class);
							perm = (Permission) constructor.newInstance(
									permissionInfo.getName(), permissionInfo
											.getActions());
							permissionMap.put(permissionStr, perm);
						} catch (InstantiationException ie) {
							logger.warn("{}", ie);
							continue;
						} catch (ClassNotFoundException cnfe) {
							logger.warn("{}", cnfe);
							continue;
						} catch (NoSuchMethodException nsme) {
							logger.warn("{}", nsme);
							continue;
						} catch (InvocationTargetException ite) {
							logger.warn("{}", ite);
							continue;
						} catch (IllegalAccessException iae) {
							logger.warn("{}", iae);
							continue;
						}
					}

					result.add(perm);
				}
				return null;
			}
		});
		return result;
	}

	/**
	 * Returns the string representations of all permissions of a user. Those
	 * are his/her own permissions and the permissions of his roles
	 * 
	 */
	private List<String> getAllPermissionsOfAUserByName(String userName)
			throws UserUnregisteredException {

		NonLiteral user = getUserByName(userName);
		List<String> result = getPermissionEntriesOfARole(user, userName);

		Iterator<Triple> roleTriples = systemGraph.filter(user,
				SIOC.has_function, null);

		while (roleTriples.hasNext()) {
			NonLiteral anotherRole = (NonLiteral) roleTriples.next()
					.getObject();
			result.addAll(getPermissionEntriesOfARole(anotherRole, userName));
		}
		Iterator<NonLiteral> baseRoles = getResourcesOfType(PERMISSION.BaseRole);
		while(baseRoles.hasNext()) {
			result.addAll(getPermissionEntriesOfARole(baseRoles.next(), userName));
		}
		return result;
	}

	private NonLiteral getUserByName(String userName)
			throws UserUnregisteredException {
		Iterator<Triple> triples = systemGraph.filter(null, PLATFORM.userName,
				new PlainLiteralImpl(userName));

		if (triples.hasNext()) {
			return triples.next().getSubject();
		}
		throw new UserUnregisteredException(userName);
	}

	private List<String> getPermissionEntriesOfARole(NonLiteral role, String userName) {
		List<String> result = new ArrayList<String>();
		Iterator<Triple> permsForRole = systemGraph.filter(role,
				PERMISSION.hasPermission, null);

		while (permsForRole.hasNext()) {
			Iterator<Triple> javaPermForRole = systemGraph.filter(
					(BNode) permsForRole.next().getObject(),
					PERMISSION.javaPermissionEntry, null);
			if (javaPermForRole.hasNext()) {
				PlainLiteralImpl permissionEntry = (PlainLiteralImpl) javaPermForRole
						.next().getObject();
				String permission = permissionEntry.getLexicalForm();
				if(permission.contains("{username}")) {
					permission = permission.replace("{username}",userName);
				}
				result.add(permission);
			}
		}
		return result;
	}
	
	private Iterator<NonLiteral> getResourcesOfType(UriRef type) {
		final Iterator<Triple> triples =
				systemGraph.filter(null, RDF.type, type);
		return new Iterator<NonLiteral>() {

			@Override
			public boolean hasNext() {
				return triples.hasNext();
			}

			@Override
			public NonLiteral next() {
				return triples.next().getSubject();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("Not supported yet.");
			}
		};
	}

}
