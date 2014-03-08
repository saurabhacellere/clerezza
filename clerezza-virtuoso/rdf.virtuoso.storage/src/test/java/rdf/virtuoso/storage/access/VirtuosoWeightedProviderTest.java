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
package rdf.virtuoso.storage.access;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.PlainLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.NoSuchEntityException;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rdf.virtuoso.storage.TestUtils;
import virtuoso.jdbc4.VirtuosoException;

/**
 * 
 * Tests the VirtuosoWeightedProvider
 * 
 * @author enridaga
 * 
 */
public class VirtuosoWeightedProviderTest {
	static final Logger log = LoggerFactory
			.getLogger(VirtuosoWeightedProviderTest.class);

	private static final String TEST_GRAPH_URI = "VirtuosoWeightedProviderTest";

	private static VirtuosoWeightedProvider wp = null;

	@BeforeClass
	public static void before() throws ClassNotFoundException, SQLException {
		log.info("Preparing VirtuosoWeightedProvider for test");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		wp = TestUtils.getProvider();
	}

	@Test
	public void weight() {
		log.info("Test setting the weight");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		int w = 200;
		wp.setWeight(w);
		assertTrue(wp.getWeight() == w);
	}

	@Test
	public void listGraphs() {
		log.info("Test listGraphs()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		Set<UriRef> gs = wp.listGraphs();
		Iterator<UriRef> it = gs.iterator();
		log.debug("Graphs:");
		while (it.hasNext()) {
			UriRef r = it.next();
			// Must not be null
			assertNotNull(r);
			// Must not be empty string
			assertFalse(r.getUnicodeString().equals(""));
			log.debug(" > {}", r.getUnicodeString());
		}
	}

	@Test
	public void listGraphsIsUnmodifiable() {
		log.info("Test listGraphsIsUnmodifiable()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		Set<UriRef> gs = wp.listGraphs();
		boolean exception = false;
		try {
			gs.add(new UriRef("example"));
		} catch (UnsupportedOperationException e) {
			log.debug(
					"Great, we had an {} exception while modifying an immutable set!",
					e.getClass());
			exception = true;
		}
		assertTrue(exception);
	}

	@Test
	public void listMGraphs() {
		log.info("Test listMGraphs()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		Set<UriRef> mg = wp.listMGraphs();
		log.debug("Graphs:");
		for (UriRef r : mg) {
			// Must not be null
			assertNotNull(r);
			// Must not be empty string
			assertFalse(r.getUnicodeString().equals(""));
			log.debug("MGraph iri: {}", r.getUnicodeString());
		}
	}

	final UriRef enridaga = new UriRef("enridaga");
	final UriRef alexdma = new UriRef("alexdma");
	final UriRef anuzzolese = new UriRef("anuzzolese");
	final UriRef predicate = new UriRef("http://property/name");
	final PlainLiteral object = new PlainLiteralImpl("Enrico Daga");
	final UriRef knows = new UriRef(TestUtils.FOAF_NS + "knows");

	@Test
	public void createMGraph() {
		log.info("createMGraph()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		try {
			MGraph mgraph = wp.createMGraph(new UriRef(TEST_GRAPH_URI));
			assertNotNull(mgraph);
			if (log.isDebugEnabled()) {
				log.debug("Created mgraph, adding a triple");
				log.debug("MGraph size is {}", mgraph.size());
			}
			mgraph.add(new Triple() {

				@Override
				public NonLiteral getSubject() {
					return enridaga;
				}

				@Override
				public UriRef getPredicate() {
					return knows;
				}

				@Override
				public Resource getObject() {
					return anuzzolese;
				}
			});
			log.debug("MGraph size is {}", mgraph.size());
			assertTrue(mgraph.size() == 1);
		} catch (RuntimeException re) {
			log.error("ERROR! ", re);
			assertFalse(true);
		}
	}

	@Test
	public void createGraph() throws VirtuosoException, ClassNotFoundException,
			SQLException {
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		MGraph smg = new SimpleMGraph();
		Triple t = new Triple() {

			@Override
			public NonLiteral getSubject() {
				return enridaga;
			}

			@Override
			public UriRef getPredicate() {
				return knows;
			}

			@Override
			public Resource getObject() {
				return anuzzolese;
			}
		};
		smg.add(t);
		UriRef name = new UriRef(TEST_GRAPH_URI);
		Graph g = wp.createGraph(name, smg);
		// Graph must contain the triple
		assertTrue(g.contains(t));
		// Graph size must be 1
		assertTrue(g.size() == 1);
		// Graph retrieved by id must contain the triple
		assertTrue(wp.getGraph(name).contains(t));
		// Graph retrieved by id must be equal to g
		assertTrue(wp.getGraph(name).equals(g));
	}

	@Test
	public void testEquals() {
		log.info("testEquals()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		UriRef name = new UriRef(TEST_GRAPH_URI);
		MGraph g = wp.createMGraph(name);
		// Equals
		log.debug("Should be equal: {}", g.equals(wp.getMGraph(name)));
		assertTrue(g.equals(wp.getMGraph(name)));
		log.debug("{}  <->  {}", g.getClass(), g.getGraph().getClass());
		log.debug("Should not be equal: {}", g.equals(g.getGraph()));
		// Not equals
		assertFalse(g.equals(g.getGraph()));
	}

	@Before
	@After
	public void clear() throws VirtuosoException, ClassNotFoundException,
			SQLException {
		log.info("clear()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		log.debug("Removing test graphs <{}>", TEST_GRAPH_URI);
		try {
			wp.deleteTripleCollection(new UriRef(TEST_GRAPH_URI));
		} catch (NoSuchEntityException nsee) {
			// Nothing to do
		}
		try {
			wp.deleteTripleCollection(new UriRef("urn:my-empty-graph"));
		} catch (NoSuchEntityException nsee) {
			// Nothing to do
		}
		try {
			wp.deleteTripleCollection(new UriRef("urn:my-non-empty-graph"));
		} catch (NoSuchEntityException nsee) {
			// Nothing to do
		}
	}
	
	@Test
	public void testCreateEmptyMGraph(){
		log.info("testCreateEmptyMGraph()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
//		try {
			UriRef ur = new UriRef("urn:my-empty-graph");
			Assert.assertFalse(wp.listGraphs().contains(ur));
			Assert.assertFalse(wp.listMGraphs().contains(ur));
			wp.createMGraph(ur);
			Assert.assertTrue(wp.canRead(ur));
			Assert.assertTrue(wp.canModify(ur));
			Assert.assertTrue(wp.listGraphs().contains(ur));
			Assert.assertTrue(wp.listMGraphs().contains(ur));
			wp.deleteTripleCollection(ur);
			Assert.assertFalse(wp.listGraphs().contains(ur));
			Assert.assertFalse(wp.listMGraphs().contains(ur));
//		} catch (NoSuchEntityException nsee) {
//			// Nothing to do
//		}
	}
	
	@Test
	public void testEmptyAndNonEmptyGraphs(){
		log.info("testEmptyAndNonEmptyGraphs()");
		if (TestUtils.SKIP) {
			log.warn("SKIPPED");
			return;
		}
		
		UriRef ur = new UriRef("urn:my-empty-graph");
		UriRef nur = new UriRef("urn:my-non-empty-graph");
		
		Assert.assertFalse(wp.listGraphs().contains(ur));
		Assert.assertFalse(wp.listMGraphs().contains(ur));
		wp.createMGraph(ur);
		Assert.assertTrue(wp.canRead(ur));
		Assert.assertTrue(wp.canModify(ur));
		wp.createMGraph(nur);
		Assert.assertTrue(wp.canRead(nur));
		Assert.assertTrue(wp.canModify(nur));
		Assert.assertTrue(wp.listGraphs().contains(nur));
		Assert.assertTrue(wp.listMGraphs().contains(nur));
		
		// Add a triple to the non-empty graph
		Triple t = new TripleImpl(new UriRef("urn:test:subject"), new UriRef("urn:test:predicate"), 
				new PlainLiteralImpl("A literal"));
		wp.getMGraph(nur).add(t);
		// Show inserted triple
		Iterator<Triple> ti = wp.getMGraph(nur).iterator();
		while(ti.hasNext()){
			log.info(" > {}", ti.next());
		}
		Assert.assertTrue(wp.getMGraph(nur).contains(t));
		Assert.assertTrue(wp.getMGraph(ur).isEmpty());
		// We delete the empty graph
		wp.deleteTripleCollection(ur);
		Assert.assertFalse(wp.listGraphs().contains(ur));
		Assert.assertFalse(wp.listMGraphs().contains(ur));
		// But the other is still there
		Assert.assertTrue(wp.listGraphs().contains(nur));
		Assert.assertTrue(wp.listMGraphs().contains(nur));
		// And it still contains its triple
		Assert.assertTrue(wp.getMGraph(nur).contains(t));
		// We delete the triple
		wp.getMGraph(nur).remove(t);
		Assert.assertFalse(wp.getMGraph(nur).contains(t));
		Assert.assertTrue(wp.getMGraph(nur).isEmpty());
		// We delete the non empty graph
		wp.deleteTripleCollection(nur);
		Assert.assertFalse(wp.listGraphs().contains(nur));
		Assert.assertFalse(wp.listMGraphs().contains(nur));

	}
}
