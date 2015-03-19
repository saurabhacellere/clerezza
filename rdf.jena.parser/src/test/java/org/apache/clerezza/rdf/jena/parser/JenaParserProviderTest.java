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
package org.apache.clerezza.rdf.jena.parser;

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

import org.apache.clerezza.rdf.core.serializedform.ParsingProvider;
import org.apache.commons.rdf.Graph;
import org.apache.commons.rdf.ImmutableGraph;
import org.apache.commons.rdf.Iri;
import org.apache.commons.rdf.impl.utils.simple.SimpleGraph;



/**
 * test taken from http://www.w3.org/2001/sw/DataAccess/df1/tests/
 * @author reto
 */
public class JenaParserProviderTest {

    /*
     * comparing result from nt and turtle parsing,
     */
    @Test
    public void testTurtleParser() {
        ParsingProvider provider = new JenaParserProvider();
        InputStream nTriplesIn = getClass().getResourceAsStream("test-04.nt");
        InputStream turtleIn = getClass().getResourceAsStream("test-04.ttl");
        ImmutableGraph graphFromNTriples = parse(provider, nTriplesIn, "application/n-triples", null);
        ImmutableGraph graphFromTurtle = parse(provider, turtleIn, "text/turtle", null);
        Assert.assertEquals(graphFromNTriples, graphFromTurtle);
    }

    /*
     * comparing result from nt and rdf/xml parsing,
     */
    @Test
    public void testRdfXmlParser() {
        ParsingProvider provider = new JenaParserProvider();
        InputStream nTriplesIn = getClass().getResourceAsStream("test-04.nt");
        InputStream rdfIn = getClass().getResourceAsStream("test-04.rdf");
        ImmutableGraph graphFromNTriples = parse(provider, nTriplesIn, "application/n-triples", null);
        ImmutableGraph graphFromTurtle = parse(provider, rdfIn, "application/rdf+xml", null);
        Assert.assertEquals(graphFromNTriples, graphFromTurtle);
    }
    
    @Test
    public void testTurtleParserWithArgument() {
        ParsingProvider provider = new JenaParserProvider();
        InputStream nTriplesIn = getClass().getResourceAsStream("test-04.nt");
        InputStream turtleIn = getClass().getResourceAsStream("test-04.ttl");
        ImmutableGraph graphFromNTriples = parse(provider, nTriplesIn, "application/n-triples", null);
        ImmutableGraph graphFromTurtle = parse(provider, turtleIn, "text/turtle;charset=UTF-", null);
        Assert.assertEquals(graphFromNTriples, graphFromTurtle);
    }

    private ImmutableGraph parse(ParsingProvider parsingProvider, InputStream in, String type, Iri base) {
        Graph simpleMGraph = new SimpleGraph();
        parsingProvider.parse(simpleMGraph, in, type, base);
        return simpleMGraph.getImmutableGraph();
    }

}
