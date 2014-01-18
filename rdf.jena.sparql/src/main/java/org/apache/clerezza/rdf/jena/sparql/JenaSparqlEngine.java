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
package org.apache.clerezza.rdf.jena.sparql;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.sparql.QueryEngine;
import org.apache.clerezza.rdf.core.sparql.query.Query;
import org.apache.clerezza.rdf.jena.storage.JenaGraphAdaptor;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;
import com.hp.hpl.jena.query.QueryException;
import com.hp.hpl.jena.query.QueryExecException;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.sparql.core.DatasetDescription;
import com.hp.hpl.jena.sparql.core.DatasetGraph;
import com.hp.hpl.jena.sparql.core.DynamicDatasets;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.UpdateAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.LockableMGraph;
import org.apache.clerezza.rdf.core.sparql.ParseException;
import org.apache.clerezza.rdf.core.sparql.SparqlPreParser;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;


@Component
@Service(QueryEngine.class)
public class JenaSparqlEngine implements QueryEngine {

    // ------------------------------------------------------------------------
    // Implementing QueryEngine
    // ------------------------------------------------------------------------

    @Override
    public Object execute(TcManager tcManager, TripleCollection defaultGraph,
            final Query query) {
        return execute(tcManager, defaultGraph, query.toString());
    }

    @Override
    public Object execute(TcManager tcManager, TripleCollection defaultGraph,
            final String query) {
        final SparqlPreParser sparqlPreParser = new SparqlPreParser(tcManager);
        final UriRef defaultGraphName = new UriRef("http://fake-default.uri/879872");
        Set<UriRef> referencedGraphs;
        try {
            referencedGraphs = sparqlPreParser.getReferredGraphs(query, defaultGraphName);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
        Set<UriRef> graphsToLock = referencedGraphs != null ? referencedGraphs : tcManager.listTripleCollections();
        List<Lock> locks = new ArrayList<Lock>(graphsToLock.size());
        for (UriRef uriRef : graphsToLock) {
            TripleCollection tc;
            if (uriRef.equals(defaultGraphName)) {
                tc = defaultGraph;
            } else {
                tc = tcManager.getTriples(uriRef);
            }
            if (tc instanceof LockableMGraph) {
                locks.add(((LockableMGraph) tc).getLock().readLock());
            }
        }
        for (Lock lock : locks) {
            lock.lock();
        }
        try {
            final DatasetGraph datasetGraph = new TcDatasetGraph(tcManager, defaultGraph);
            final Dataset dataset = DatasetFactory.create(datasetGraph);

            // Missing permission (java.lang.RuntimePermission getClassLoader)
            // when calling QueryFactory.create causes ExceptionInInitializerError
            // to be thrown.
            // QueryExecutionFactory.create requires
            // (java.io.FilePermission [etc/]location-mapping.* read)
            // Thus, they are placed within doPrivileged
            QueryExecution qexec = AccessController
                    .doPrivileged(new PrivilegedAction<QueryExecution>() {

                        @Override
                        public QueryExecution run() {
                            try {
                                com.hp.hpl.jena.query.Query jenaQuery = QueryFactory
                                        .create(query);
                                if (jenaQuery.isUnknownType()) {
                                    return null;
                                }
                                DatasetDescription dd = DatasetDescription.create(jenaQuery);
                                Dataset dynaDataset = DynamicDatasets.dynamicDataset(dd, dataset, false);
                                return QueryExecutionFactory.create(jenaQuery, dynaDataset);
                            } catch (QueryException ex) {
                                return null;
                            }

                        }
                    });
            if (qexec == null) {
                return executeUpdate(dataset, query);
            }
            //TODO check with rather than trial and error: if (qexec.getQuery().isSelectType()) {
            try {
                try {
                    return new ResultSetWrapper(qexec.execSelect());
                } catch (QueryExecException e) {
                    try {
                        return Boolean.valueOf(qexec.execAsk());
                    } catch (QueryExecException e2) {
                        try {
                            return new JenaGraphAdaptor(qexec.execDescribe()
                                    .getGraph()).getGraph();
                        } catch (QueryExecException e3) {
                            return new JenaGraphAdaptor(qexec.execConstruct()
                                    .getGraph()).getGraph();
                        }
                    }
                }
            } finally {
                qexec.close();
            }
        } finally {
            for (Lock lock : locks) {
                lock.unlock();
            }
        }
    }

    private Object executeUpdate(Dataset dataset, String query) {
        GraphStore graphStore = GraphStoreFactory.create(dataset) ;
        UpdateAction.parseExecute(query, graphStore) ;
        return true;
    }
}
