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
package org.apache.clerezza.rdf.jena.storage;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.AbstractMGraph;
import org.apache.clerezza.rdf.jena.commons.Jena2TriaUtil;
import org.apache.clerezza.rdf.jena.commons.Tria2JenaUtil;
import org.wymiwyg.commons.util.collections.BidiMap;
import org.wymiwyg.commons.util.collections.BidiMapImpl;

/**
 * An adaptor to expose a Jena Graph as Clerezza MGraph.
 *
 * @author rbn
 */
public class JenaGraphAdaptor extends AbstractMGraph {

	private final Graph jenaGraph;
	final BidiMap<BNode, Node> tria2JenaBNodes = new BidiMapImpl<BNode, Node>();
	final Jena2TriaUtil jena2TriaUtil =
			new Jena2TriaUtil(tria2JenaBNodes.inverse());
	final Tria2JenaUtil tria2JenaUtil =
			new Tria2JenaUtil(tria2JenaBNodes);

	/**
	 * Constructs an MGraph based on the supplied jena Graph.
	 *
	 * @param jenaGraph
	 */
	public JenaGraphAdaptor(Graph jenaGraph) {
		this.jenaGraph = jenaGraph;
	}

	@Override
	public int size() {
		return jenaGraph.size();
	}

	@Override
	public Iterator<Triple> performFilter(NonLiteral subject, UriRef predicate, Resource object) {
		final ExtendedIterator jenaIter = jenaGraph.find(tria2JenaUtil.convert2JenaNode(subject),
				tria2JenaUtil.convert2JenaNode(predicate),
				tria2JenaUtil.convert2JenaNode(object));
		ArrayList<Triple> data = new ArrayList<Triple>();
		while (jenaIter.hasNext()) {
			data.add(jena2TriaUtil.convertTriple(
						(com.hp.hpl.jena.graph.Triple)jenaIter.next()));
		}
		final Iterator<Triple> base = data.iterator();
		return new Iterator<Triple>() {

			Triple lastReturned = null;

			@Override
			public boolean hasNext() {
				return base.hasNext();
			}

			@Override
			public Triple next() {
				lastReturned = base.next();
				return lastReturned;
			}

			@Override
			public void remove() {
				JenaGraphAdaptor.this.performRemove(lastReturned);
			}
		};
	}

	@Override
	public boolean performAdd(Triple triple) {
		if (contains(triple)) {
			return false;
		}
		jenaGraph.add(tria2JenaUtil.convertTriple(triple));
		return true;
	}

	@Override
	public boolean performRemove(Triple triple) {
		if (!contains(triple)) {
			return false;
		}
		jenaGraph.delete(tria2JenaUtil.convertTriple(triple));
		return true;
	}
}
