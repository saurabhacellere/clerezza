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
package org.apache.clerezza.rdf.core.access;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;


import org.apache.commons.rdf.Graph;
import org.apache.commons.rdf.MGraph;
import org.apache.commons.rdf.TripleCollection;
import org.apache.commons.rdf.Iri;
import org.apache.clerezza.rdf.core.sparql.QueryEngine;

/**
 * This makes a set of WeightedTcProvider appear as one TcProvider. It delegates
 * requests to the WeightedTcProvider with the highest Weight
 *
 * @author reto
 */
public class TcProviderMultiplexer implements TcProvider {

    protected SortedSet<WeightedTcProvider> providerList = new TreeSet<WeightedTcProvider>(
            new WeightedProviderComparator());
    /**
     * Mapping to LockableMGraph's and ServiceRegistration using their URI's as key.
     * Makes sure that per URI only one instance of the LockableMGraph is used,
     * otherwise the locks in the <code>LockableMGraph</code>s would have no effect
     * between different instances and concurrency issues could occur.
     */
    private Map<Iri, MGraphHolder> mGraphCache = Collections.synchronizedMap(new HashMap<Iri, MGraphHolder>());

	/**
	 * Flag to indicate whether mgraphs should be cached for faster access. By
	 * default caching is enabled for backward compatibility.
	 */
	private boolean isCachingEnabled = true;

    /**
     * Registers a provider
     *
     * @param provider
     *            the provider to be registered
     */
    public void addWeightedTcProvider(WeightedTcProvider provider) {
        providerList.add(provider);
        updateLockableMGraphCache(provider, true);
    }

    /**
     * Unregister a provider
     *
     * @param provider
     *            the provider to be deregistered
     */
    public void removeWeightedTcProvider(
            WeightedTcProvider provider) {
        providerList.remove(provider);
        updateLockableMGraphCache(provider, false);
    }

    /**
     * subclasses overwrite this method to be notified when a new
     * Graph is available (either because it has been created or being
     * provided by a newly added WeightedTcProvider). The default implementation
     * does nothing.
     *
     * @param name
     */
    protected void graphAppears(Iri name) {
    }

    /**
     * subclasses overwrite this method to be notified when a new
     * MGraph is available (either because it has been created or being
     * provided by a newly added WeightedTcProvider). The default implementation
     * does nothing.
     *
     * @param name
     */
    protected void mGraphAppears(Iri name) {
    }

    /**
     * subclasses overwrite this method to be notified whenTripleCollection is 
     * no longer available (either because it has been deleted or bacause its
     * WeightedTcProvider was removed). The default implementation does nothing.
     *
     * for implementational reasons even for name of TripleCollection not
     * previously registered.
     *
     * @param name
     */
    protected void tcDisappears(Iri name) {
    }

    /**
     * Updates the lockableMGraphCache AFTER a new <code>provider</code> was
     * bound or unbound.
     * This method also takes care of registering and unregistering
     * provided triple collections as services based on the weight of
     * all affected providers.
     *
     * @param provider
     *            the provider that was added or removed
     * @param providerAdded
     *            <code>boolean</code> that should be set as <code>true</code>
     *            if <code>provider</code> was added to
     *            <code>org.apache.clerezza.rdf.core.TcManager.providerList</code>
     *            otherwise <code>false</code>
     */
    private void updateLockableMGraphCache(WeightedTcProvider provider,
            boolean providerAdded) {
        Set<Iri> uriSet = provider.listTripleCollections();
        if (!(uriSet == null || uriSet.isEmpty())) {
            if (providerAdded) {
                weightedProviderAdded(provider, uriSet);
            } else {
                weightedProviderRemoved(provider, uriSet);
            }
        }
    }

    private void weightedProviderAdded(WeightedTcProvider newProvider,
            Set<Iri> newProvidedUris) {
        Set<WeightedTcProvider> lowerWeightedProviderList = getLowerWeightedProvider(newProvider);
    	if (isCachingEnabled()) {
	        for (Iri name : newProvidedUris) {
	            final MGraphHolder holder = mGraphCache.get(name);
	            if ((holder != null) && (holder.getWeightedTcProvider() != null)) {
	                if (lowerWeightedProviderList.contains(holder.getWeightedTcProvider())) {
	                    tcDisappears(name);
	                    mGraphCache.remove(name);
	                } else {
	                    continue;
	                }
	            }
	            TripleCollection triples = newProvider.getTriples(name);
	            if (triples instanceof MGraph) {
	           		mGraphCache.put(name, new MGraphHolder(newProvider, ensureLockable((MGraph)triples)));
	                mGraphAppears(name);
	            } else {
	                graphAppears(name);
	            }
	    	}
        }
    }

    

    private Set<WeightedTcProvider> getLowerWeightedProvider(
            WeightedTcProvider newProvider) {
        boolean referenceProviderPassed = false;
        Set<WeightedTcProvider> lowerWeightedProviderList = new HashSet<WeightedTcProvider>();
        for (WeightedTcProvider weightedProvider : providerList) {
            if (referenceProviderPassed) {
                lowerWeightedProviderList.add(weightedProvider);
            } else if (newProvider.equals(weightedProvider)) {
                referenceProviderPassed = true;
            }
        }
        return lowerWeightedProviderList;
    }

    private void weightedProviderRemoved(WeightedTcProvider oldProvider,
            Set<Iri> oldProvidedUris) {
        for (Iri name : oldProvidedUris) {
            final MGraphHolder holder = mGraphCache.get(name);
            if ((holder != null) && (holder.getWeightedTcProvider() != null)
                    && holder.getWeightedTcProvider().equals(oldProvider)) {
                tcDisappears(name);
                mGraphCache.remove(name);

            	if (isCachingEnabled()) {
	                // check if another WeightedTcProvider has the TripleCollection.
	                // And if so register as service.
	                for (WeightedTcProvider provider : providerList) {
	                    try {
	                        TripleCollection triples = provider.getTriples(name);
	                        if (triples instanceof MGraph) {
	                       		mGraphCache.put(name, new MGraphHolder(provider, ensureLockable((MGraph)triples)));
	                            mGraphAppears(name);
	                        } else {
	                            graphAppears(name);
	                        }
	                        break;
	                    } catch (NoSuchEntityException e) {
	                        // continue;
	                    }
	                }
            	}
            }
        }
    }

    @Override
    public Graph getGraph(Iri name) throws NoSuchEntityException {
        for (TcProvider provider : providerList) {
            try {
                return provider.getGraph(name);
            } catch (NoSuchEntityException e) {
                //we do nothing and try our luck with the next provider
            } catch (IllegalArgumentException e) {
                //we do nothing and try our luck with the next provider
            }
        }
        throw new NoSuchEntityException(name);
    }

    @Override
    public LockableMGraph getMGraph(Iri name)
            throws NoSuchEntityException {
        LockableMGraph result = getMGraphFromCache(name);
        if (result == null) {
            synchronized (this) {
                result = getMGraphFromCache(name);
                if (result == null) {
                    result = getUnsecuredMGraphAndAddToCache(name);
                }
            }
        }
        return result;
    }

    private LockableMGraph getMGraphFromCache(Iri name) {
        MGraphHolder holder = mGraphCache.get(name);
        if (holder == null) {
            return null;
        }
        return holder.getMGraph();
    }

    private LockableMGraph getUnsecuredMGraphAndAddToCache(Iri name)
            throws NoSuchEntityException {
        for (WeightedTcProvider provider : providerList) {
            try {
                MGraph providedMGraph = provider.getMGraph(name);
                LockableMGraph result = ensureLockable(providedMGraph);

                if (isCachingEnabled()) {
	                mGraphCache.put(name, new MGraphHolder(
	                        provider, result));
                }
                return result;
            } catch (NoSuchEntityException e) {
                //we do nothing and try our luck with the next provider
            } catch (IllegalArgumentException e) {
                //we do nothing and try our luck with the next provider
            }
        }
        throw new NoSuchEntityException(name);
    }

    @Override
    public TripleCollection getTriples(Iri name)
            throws NoSuchEntityException {
        TripleCollection result;
        for (WeightedTcProvider provider : providerList) {
            try {
                result = provider.getTriples(name);
                if (!(result instanceof MGraph)) {
                    return result;
                } else {
                    // This is to ensure the MGraph gets added to the cache
                    return getMGraph(name);
                }
            } catch (NoSuchEntityException e) {
                //we do nothing and try our luck with the next provider
            } catch (IllegalArgumentException e) {
                //we do nothing and try our luck with the next provider
            }
        }
        throw new NoSuchEntityException(name);
    }

    @Override
    public LockableMGraph createMGraph(Iri name)
            throws UnsupportedOperationException {

        for (WeightedTcProvider provider : providerList) {
            try {
                MGraph providedMGraph = provider.createMGraph(name);
                LockableMGraph result;
                if (providedMGraph instanceof LockableMGraph) {
                    result = (LockableMGraph) providedMGraph;
                } else {
                    result = new LockableMGraphWrapper(providedMGraph);
                }

                // unregisters a possible Graph or MGraph service under this name
                // provided by a WeightedTcProvider with a lower weight.
                tcDisappears(name);
                if (isCachingEnabled()) {
                	mGraphCache.put(name, new MGraphHolder(provider, null));
                }
                mGraphAppears(name);
                return result;
            } catch (UnsupportedOperationException e) {
                //we do nothing and try our luck with the next provider
            } catch (IllegalArgumentException e) {
                //we do nothing and try our luck with the next provider
            }
        }
        throw new UnsupportedOperationException(
                "No provider could create MGraph.");
    }

    @Override
    public Graph createGraph(Iri name, TripleCollection triples) {
        for (WeightedTcProvider provider : providerList) {
            try {
                Graph result = provider.createGraph(name, triples);

                // unregisters a possible Graph or MGraph service under this name
                // provided by a WeightedTcProvider with a lower weight.
                tcDisappears(name);
                if (isCachingEnabled()) {
                	mGraphCache.put(name, new MGraphHolder(provider, null));
                }
                graphAppears(name);
                return result;
            } catch (UnsupportedOperationException e) {
                //we do nothing and try our luck with the next provider
            } catch (IllegalArgumentException e) {
                //we do nothing and try our luck with the next provider
            }
        }
        throw new UnsupportedOperationException(
                "No provider could create Graph.");
    }

    @Override
    public void deleteTripleCollection(Iri name) {
        for (TcProvider provider : providerList) {
            try {
                provider.deleteTripleCollection(name);
                final MGraphHolder holder = mGraphCache.get(name);
                if ((holder != null)
                        && (holder.getWeightedTcProvider() != null)
                        && holder.getWeightedTcProvider().equals(provider)) {
                    tcDisappears(name);
                    mGraphCache.remove(name);
                }
                return;
            } catch (UnsupportedOperationException e) {
                // we do nothing and try our luck with the next provider
            } catch (NoSuchEntityException e) {
                //we do nothing and try our luck with the next provider
            } catch (IllegalArgumentException e) {
                //we do nothing and try our luck with the next provider
            }
        }
        // this throws a NoSuchEntityException if the graph doesn't exist
        getTriples(name);
        // the entity exists but cannot be deleted
        throw new UnsupportedOperationException(
                "No provider could delete the entity.");
    }

    @Override
    public Set<Iri> getNames(Graph graph) {
        Set<Iri> result = new HashSet<Iri>();
        for (TcProvider provider : providerList) {
            result.addAll(provider.getNames(graph));
        }
        return result;
    }

    @Override
    public Set<Iri> listGraphs() {
        Set<Iri> result = new HashSet<Iri>();
        for (TcProvider provider : providerList) {
            result.addAll(provider.listGraphs());
        }
        return result;
    }

    @Override
    public Set<Iri> listMGraphs() {
        Set<Iri> result = new HashSet<Iri>();
        for (TcProvider provider : providerList) {
            result.addAll(provider.listMGraphs());
        }
        return result;
    }

    @Override
    public Set<Iri> listTripleCollections() {
        Set<Iri> result = new HashSet<Iri>();
        for (TcProvider provider : providerList) {
            result.addAll(provider.listTripleCollections());
        }
        return result;
    }

    private LockableMGraph ensureLockable(MGraph providedMGraph) {
        LockableMGraph result;
        if (providedMGraph instanceof LockableMGraph) {
            result = (LockableMGraph) providedMGraph;
        } else {
            result = new LockableMGraphWrapper(providedMGraph);
        }
        return result;
    }

    /**
     * Contains an unsecured LockableMGraph, a ServiceRegistration and
     * the WeightedTcProvider that generated the graph
     */
    private static class MGraphHolder {

        private WeightedTcProvider tcProvider;
        private WeakReference<LockableMGraph> mGraphReference;

        MGraphHolder(WeightedTcProvider tcProvider, LockableMGraph mGraph) {
            this.tcProvider = tcProvider;
            this.mGraphReference = new WeakReference<LockableMGraph>(mGraph);
        }

        LockableMGraph getMGraph() {
            return this.mGraphReference.get();
        }

        WeightedTcProvider getWeightedTcProvider() {
            return this.tcProvider;
        }
    }

    //methods for debuging / monitoring
    public SortedSet<WeightedTcProvider> getProviderList() {
        return providerList;
    }

    public boolean isCachingEnabled() {
		return isCachingEnabled;
	}
    
    public void setCachingEnabled(boolean isCachingEnabled) {
		this.isCachingEnabled = isCachingEnabled;
		
		if (!isCachingEnabled()) {
			mGraphCache.clear();
		}
	}
}
