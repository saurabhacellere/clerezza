/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.clerezza.rdf.simple.storage;

import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.commons.rdf.Graph;
import org.apache.clerezza.commons.rdf.Iri;
import org.apache.clerezza.commons.rdf.impl.utils.simple.SimpleGraph;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author developer
 */
public class AccessViaTcManager {
    
    @Test
    public void simple() {
        Graph g = TcManager.getInstance().createGraph(new Iri("http://example.org/foo"));
        Assert.assertTrue(g instanceof SimpleGraph);
    }
    
}
