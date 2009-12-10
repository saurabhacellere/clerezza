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
package org.apache.felix.framework;

import java.security.Permission;
import java.security.ProtectionDomain;

import org.apache.felix.framework.ext.SecurityProvider;
import org.apache.felix.framework.security.condpermadmin.ConditionalPermissionAdminImpl;
import org.apache.felix.framework.security.permissionadmin.PermissionAdminImpl;
import org.apache.felix.framework.security.util.TrustManager;
import org.apache.felix.framework.security.verifier.BundleDNParser;
import org.apache.felix.framework.security.verifier.SignerMatcher;
import org.apache.felix.framework.util.SecureAction;
import org.osgi.framework.Bundle;

/**
 * This class is the entry point to the security. It is used to determine whether
 * a given bundle is signed correctely and has permissions based on 
 * PermissionAdmin or ConditionalPermissionAdmin.
 */
public final class SecurityProviderImpl implements SecurityProvider
{
    private final BundleDNParser m_parser;
    private final PermissionAdminImpl m_pai;
    private final ConditionalPermissionAdminImpl m_cpai;
    private final SecureAction m_action;

    SecurityProviderImpl(String crlList, String typeList,
        String passwdList, String storeList, PermissionAdminImpl pai,
        ConditionalPermissionAdminImpl cpai, SecureAction action)
    {
        m_pai = pai;
        m_cpai = cpai;
        m_action = action;
        m_parser =
            new BundleDNParser(new TrustManager(crlList, typeList, passwdList,
                storeList, m_action));
    }

    BundleDNParser getParser()
    {
        return m_parser;
    }

    /**
     * If the given bundle is signed but can not be verified (e.g., missing files)
     * then throw an exception.
     */
    public void checkBundle(Bundle bundle) throws Exception
    {
        BundleInfo info = ((FelixBundle) bundle).getInfo();

        m_parser.checkDNChains(
            (Long.toString(bundle.getBundleId()) + "-" + info.getLastModified()), 
            info.getCurrentModule().getContentLoader());
    }

    /**
     * Get a signer matcher that can be used to match digital signed bundles.
     */
    public Object getSignerMatcher(final Bundle bundle)
    {
        return new SignerMatcher(Long.toString(bundle.getBundleId()), 
            ((FelixBundle) bundle).getInfo().getLastModified(),
            ((FelixBundle) bundle).getInfo().getCurrentModule().getContentLoader(), 
            m_parser);
    }

    /**
     * If we have a permissionadmin then ask that one first and have it
     * decide in case there is a location bound. If not then either use its 
     * default permission in case there is no conditional permission admin
     * or else ask that one. 
     */
    public boolean hasBundlePermission(ProtectionDomain bundleProtectionDomain,
        Permission permission, boolean direct)
    {
        BundleProtectionDomain pd =
            (BundleProtectionDomain) bundleProtectionDomain;
        FelixBundle bundle = pd.getBundle();
        BundleInfo info = bundle.getInfo();

        if (info.getBundleId() == 0)
        {
            return true;
        }

        // System.out.println(info.getBundleId() + " - " + permission);
        // TODO: using true, false, or null seems a bit awkward. Improve this.
        Boolean result = null;
        if (m_pai != null)
        {
            result =
                m_pai.hasPermission(info.getLocation(), pd.getBundle(),
                    permission, m_cpai, pd);
        }

        if (result != null)
        {
            return result.booleanValue();
        }

        if (m_cpai != null)
        {
            try
            {
                return m_cpai.hasPermission(bundle, 
                    info.getCurrentModule().getContentLoader(), 
                    bundle.getBundleId() + "-" + 
                    info.getLastModified(),null, pd,
                    permission, direct, m_pai);
            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return false;
    }
}