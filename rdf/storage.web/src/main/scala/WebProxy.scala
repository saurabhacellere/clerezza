/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.clerezza.rdf.storage.web


import org.apache.clerezza.commons.rdf.ImmutableGraph
import org.apache.clerezza.commons.rdf.IRI
import org.apache.clerezza.commons.rdf._
import org.apache.clerezza.commons.rdf.impl.utils.AbstractGraph
import org.osgi.service.component.ComponentContext
import java.io.IOException
import java.net.{HttpURLConnection, URL}
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat
import org.apache.clerezza.rdf.core.serializedform.Parser
import java.security.{PrivilegedExceptionAction, PrivilegedActionException, AccessController}

import org.slf4j.scala._
import org.apache.clerezza.rdf.core.access._
import org.apache.clerezza.rdf.core._
import java.sql.Time

/**
 * The Web Proxy Service enables applications to request remote (and local) graphs.
 * It keeps cached version of the remote graphs in store for faster delivery.
 *
 */
class WebProxy extends WeightedTcProvider with Logging {

  val networkTimeoutKey = "network-timeout"

  private var networkTimeout: Int = _

  private var tcProvider: TcProviderMultiplexer = new TcProviderMultiplexer

  /**
   * Register a provider
   *
   * @param provider
   *            the provider to be registered
   */
  protected def bindWeightedTcProvider(provider: WeightedTcProvider): Unit = {
    tcProvider.addWeightedTcProvider(provider)
  }

  /**
   * Deregister a provider
   *
   * @param provider
   *            the provider to be deregistered
   */
  protected def unbindWeightedTcProvider(provider: WeightedTcProvider): Unit = {
    tcProvider.removeWeightedTcProvider(provider)
  }

  /**OSGI method, called on activation */
  protected def activate(context: ComponentContext) = {
    networkTimeout = Integer.parseInt(context.getProperties.get(networkTimeoutKey).toString)
  }


  private var parser: Parser = null

  protected def bindParser(p: Parser) = {
    parser = p
  }

  protected def unbindParser(p: Parser) = {
    parser = null
  }

  def getWeight: Int = {
    return 0
  }

  /**
   * Any Graph is available as ImmutableGraph as well as immutable Graph
   *
   * @param name
   * @return
   * @throws NoSuchEntityException
   */
  def getMGraph(name: IRI): Graph = {
    val graph = getImmutableGraph(name)
    return new AbstractGraph() {
      protected def performFilter(subject: BlankNodeOrIRI, predicate: IRI, `object` : RDFTerm): java.util.Iterator[Triple] = {
        graph.filter(subject, predicate, `object`)
      }

      def performSize = graph.size
    }
  }

  def getImmutableGraph(name: IRI): ImmutableGraph = {
    try {
      getGraph(name, Cache.Fetch)
    } catch {
      case e: IOException => {
          logger.debug("could not get graph by dereferencing uri", e)
          throw new NoSuchEntityException(name)
      }

    }
  }

  def getGraph(name: IRI): Graph = {
    return getMGraph(name)
  }

  def createGraph(name: IRI): Graph = {
    throw new UnsupportedOperationException
  }

  def createImmutableGraph(name: IRI, triples: Graph): ImmutableGraph = {
    throw new UnsupportedOperationException
  }

  def deleteGraph(name: IRI): Unit = {
    throw new UnsupportedOperationException
  }

  def getNames(graph: ImmutableGraph): java.util.Set[IRI] = {
    var result: java.util.Set[IRI] = new java.util.HashSet[IRI]
    import collection.JavaConversions._
    for (name <- listGraphs) {
      if (getImmutableGraph(name).equals(graph)) {
        result.add(name)
      }
    }
    return result
  }

  def listGraphs: java.util.Set[IRI] = {
    var result: java.util.Set[IRI] = new java.util.HashSet[IRI]
    result.addAll(listMGraphs)
    result.addAll(listImmutableGraphs)
    return result
  }

  def listMGraphs: java.util.Set[IRI] = {
    //or should we list graphs for which we have a cached version?
    return java.util.Collections.emptySet[IRI]
  }

  def listImmutableGraphs: java.util.Set[IRI] = {
    return java.util.Collections.emptySet[IRI]
  }

  /**
   * The semantics of this resource
   * @param update if a remote URI, update information on the resource first
   */
  def getGraph(name: IRI, updatePolicy: Cache.Value): ImmutableGraph = {
    logger.debug("getting graph " + name)
    if (name.getUnicodeString.indexOf('#') != -1) {
      logger.debug("not dereferencing URI with hash sign. Please see CLEREZZA-533 for debate.")
      throw new NoSuchEntityException(name)
    }
    if (name.getUnicodeString.startsWith("urn")) {
      //these are not dereferenceable
      throw new NoSuchEntityException(name)
    }
    val cacheGraphName = new IRI("urn:x-localinstance:/cache/" + name.getUnicodeString)
    //todo: follow redirects and keep track of them
    //todo: keep track of headers especially date and etag. test for etag similarity
    //todo: for https connection allow user to specify his webid and send his key: ie allow web server to be an agent
    //todo: add GRDDL functionality, so that other return types can be processed too
    //todo: enable ftp and other formats (though content negotiation won't work there)
    def updateGraph() {
      val url = new URL(name.getUnicodeString)
      val connection = url.openConnection()
      connection match {
        case hc: HttpURLConnection => {
          hc.addRequestProperty("Accept", acceptHeader)
          hc.setReadTimeout(networkTimeout)
          hc.setConnectTimeout(networkTimeout)
        };
      }
      connection.connect()
      val in = connection.getInputStream()
      val mediaType = connection.getContentType()
      val remoteTriples = parser.parse(in, mediaType, name)
      tcProvider.synchronized {
        try {
          tcProvider.deleteGraph(cacheGraphName)
        } catch {
          case e: NoSuchEntityException =>;
        }
        tcProvider.createImmutableGraph(cacheGraphName, remoteTriples)
      }
    }
    try {
      //the logic here is not quite right, as we don't look at time of previous fetch.
      updatePolicy match {
        case Cache.Fetch => try {
          tcProvider.getImmutableGraph(cacheGraphName)
        } catch {
          case e: NoSuchEntityException => updateGraph(); tcProvider.getImmutableGraph(cacheGraphName)
        }
        case Cache.ForceUpdate => updateGraph(); tcProvider.getImmutableGraph(cacheGraphName)
        case Cache.CacheOnly => tcProvider.getImmutableGraph(cacheGraphName)
      }
    } catch {
      case ex: PrivilegedActionException => {
        var cause: Throwable = ex.getCause
        if (cause.isInstanceOf[UnsupportedOperationException]) {
          throw cause.asInstanceOf[UnsupportedOperationException]
        }
        if (cause.isInstanceOf[EntityAlreadyExistsException]) {
          throw cause.asInstanceOf[EntityAlreadyExistsException]
        }
        if (cause.isInstanceOf[RuntimeException]) {
          throw cause.asInstanceOf[RuntimeException]
        }
        throw new RuntimeException(cause)
      }
    }
  }


  private lazy val acceptHeader = {

    import scala.collection.JavaConversions._

    (for (f <- parser.getSupportedFormats) yield {
      val qualityOfFormat = {
        f match {
          //the default, well established format
          case SupportedFormat.RDF_XML => "1.0";
          //n3 is a bit less well defined and/or many parsers supports only subsets
          case SupportedFormat.N3 => "0.6";
          //we prefer most dedicated formats to (X)HTML, not because those are "better",
          //but just because it is quite likely that the pure RDF format will be
          //lighter (contain less presentation markup), and it is also possible that HTML does not
          //contain any RDFa, but just points to another format.
          case SupportedFormat.XHTML => "0.5";
          //we prefer XHTML over html, because parsing (should) be easier
          case SupportedFormat.HTML => "0.4";
          //all other formats known currently are structured formats
          case _ => "0.8"
        }
      }
      f + "; q=" + qualityOfFormat + ","
    }).mkString + " *; q=.1" //is that for GRDDL?
  }
}

object Cache extends Enumeration {
  /**fetch if not in cache, if version in cache is out of date, or return cache */
  val Fetch = Value
  /**fetch from source whatever is in cache */
  val ForceUpdate = Value
  /**only get cached version. If none exists return empty graph */
  val CacheOnly = Value
}
