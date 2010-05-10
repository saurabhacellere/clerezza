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
package org.apache.clerezza.rdf.scala.utils

import rdf.utils._
import rdf.core._
import rdf.core.impl._
import rdf.ontologies._
import org.junit._
import Preamble._

class RichGraphNodeTest {

	private val johnUri = new UriRef("http://example.org/john")
	private val susanneUri = new UriRef("http://example.org/susanne")
	private val listUri = new UriRef("http://example.org/list")
	private val billBNode = new BNode()
	private var node : GraphNode = null;

	@Before
	def prepare() = {
		val mGraph = new SimpleMGraph()
		mGraph.add(new TripleImpl(johnUri, FOAF.name, new PlainLiteralImpl("John")));
		mGraph.add(new TripleImpl(johnUri, FOAF.nick, new PlainLiteralImpl("johny")));
		mGraph.add(new TripleImpl(johnUri, FOAF.name, new PlainLiteralImpl("Johnathan Guller")));
		mGraph.add(new TripleImpl(johnUri, FOAF.knows, billBNode))
		mGraph.add(new TripleImpl(billBNode, FOAF.nick, new PlainLiteralImpl("Bill")));
		mGraph.add(new TripleImpl(billBNode, FOAF.name, new PlainLiteralImpl("William")));
		mGraph.add(new TripleImpl(billBNode, RDF.`type`, FOAF.Person));
		mGraph.add(new TripleImpl(susanneUri, FOAF.knows, johnUri));
		mGraph.add(new TripleImpl(susanneUri, FOAF.name, new PlainLiteralImpl("Susanne")));
		val rdfList = new RdfList(listUri, mGraph);
		rdfList.add(johnUri)
		rdfList.add(new PlainLiteralImpl("foo"))
		rdfList.add(new PlainLiteralImpl("bar"))
		mGraph.add(new TripleImpl(johnUri, SKOS.related, listUri))
		node = new GraphNode(johnUri, mGraph)
	}

	@Test
	def testSlash = {
		val rNode = new RichGraphNode(node)
		Assert.assertEquals(new PlainLiteralImpl("johny"), (rNode/FOAF.nick)(0).getNode)
		Assert.assertEquals(2, (rNode/FOAF.name).length(20))
		val stringNames = (for(name <- (rNode/FOAF.name).elements) yield {
			name.toString
		}).toList
		Assert.assertTrue(stringNames.contains("\"Johnathan Guller\""))
		Assert.assertTrue(stringNames.contains("\"John\""))
	}

	@Test
	def testInverse = {
		val rNode = new RichGraphNode(node)
		Assert.assertEquals(1, (rNode/-FOAF.knows).length)
	}

	@Test
	def testMissingProperty = {
		val rNode = new RichGraphNode(node)
		Assert.assertEquals(0, (rNode/FOAF.thumbnail).length)
		Assert.assertEquals("", rNode/FOAF.thumbnail*)

	}

	@Test
	def testInverseImplicit = {
		Assert.assertEquals(1, (node/-FOAF.knows).length)
	}

	@Test
	def testPath = {
		Assert.assertEquals(1, (node/-FOAF.knows).length)
		Assert.assertEquals(new PlainLiteralImpl("Susanne"), node/-FOAF.knows%0/FOAF.name%0!)
		Assert.assertEquals(new PlainLiteralImpl("Susanne"), ((node/-FOAF.knows)(0)/FOAF.name)(0)!)
		Assert.assertEquals(new PlainLiteralImpl("Susanne"), node/-FOAF.knows/FOAF.name!)
		Assert.assertEquals(new PlainLiteralImpl("Bill"), node/FOAF.knows/FOAF.nick!)
		Assert.assertEquals("Bill", (node/FOAF.knows/FOAF.nick)(0)*)
		Assert.assertEquals("Bill", node/FOAF.knows/FOAF.nick*)
	}

	@Test
	def testLists = {
		Assert.assertEquals(new PlainLiteralImpl("foo"),(node/SKOS.related).asList().get(1))
		Assert.assertEquals(new PlainLiteralImpl("foo"), (node/SKOS.related%0!!)(1)!)
		Assert.assertEquals(new PlainLiteralImpl("foo"),
							(for (value <- node/SKOS.related%0!!) yield value!)(1))
		Assert.assertEquals(new PlainLiteralImpl("bar"),
							(for (value <- node/SKOS.related%0!!) yield value!)(2))
		Assert.assertEquals(new PlainLiteralImpl("foo"), node/SKOS.related%0%!!1!)
	}

	@Test
	def sortProperties = {
		Assert.assertEquals(new PlainLiteralImpl("bar"), (node/SKOS.related%0!!).sort((a,b) => ((a*) < (b*)))(0)!)
		Assert.assertEquals(johnUri, (node/SKOS.related%0!!).sort((a,b) => ((a*) > (b*)))(0)!)
	}

	@Test
	def literalAsObject = {
		val dateLiteral = new TypedLiteralImpl("2009-01-01T01:33:58Z",
					new UriRef("http://www.w3.org/2001/XMLSchema#dateTime"))
		val node = new GraphNode(dateLiteral, new SimpleMGraph())
		Assert.assertNotNull(node.as[java.util.Date])
	}

}
