/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This class was taken from Sling Scala module at
 * http://code.google.com/p/sling-scala/
 */
import scala.tools.nsc.io.AbstractFile
import java.io.{File, InputStream, OutputStream, IOException}
import java.net.URL
import org.osgi.framework.Bundle
import org.apache.clerezza.scala.console.Utils.{valueOrElse, nullOrElse}

package org.apache.clerezza.scala.console {



	/**
	 * Implementation of {@link AbstractFile} on top of a {@link org.osgi.framework.Bundle}
	 */
	object BundleFS {

		/**
		 * Create a new {@link AbstractFile} instance representing an
		 * {@link org.osgi.framework.Bundle}
		 * @param bundle
		 */
		def create(bundle: Bundle): AbstractFile = {
			require(bundle != null, "bundle must not be null")

			/**
			 * The superclass of FileEntry and DirEntry
			 */
			abstract class BundleEntry(url: URL, parent: DirEntry) extends AbstractFile {
				require(url != null, "url must not be null")
				lazy val (path: String, name: String) = getPathAndName(url)
				lazy val fullName: String = (path::name::Nil).filter(!_.isEmpty).mkString("/")

				/**
				 * @return null
				 */
				def file: File = null

				//what if this is not a relative file?
				def absolute = null

				/**
				 * @return last modification time or 0 if not known
				 */
				def lastModified: Long =
					try { url.openConnection.getLastModified }
				catch { case _ => 0 }

				@throws(classOf[IOException])
				def container: AbstractFile =
					valueOrElse(parent) {
						throw new IOException("No container")
					}

				@throws(classOf[IOException])
				def input: InputStream = url.openStream()

				/**
				 * Not supported. Always throws an IOException.
				 * @throws IOException
				 */
				@throws(classOf[IOException])
				def output = throw new IOException("not supported: output")

				def delete = throw new IOException("not supported: delete")
				def create = throw new IOException("not supported: create")

				private def getPathAndName(url: URL): (String, String) = {
					val u = url.getPath
					var k = u.length
					while( (k > 0) && (u(k - 1) == '/') )
						k = k - 1

					var j = k
					while( (j > 0) && (u(j - 1) != '/') )
						j = j - 1

					(u.substring(if (j > 0) 1 else 0, if (j > 1) j - 1 else j), u.substring(j, k))
				}

				override def toString = fullName
			}

			class DirEntry(url: URL, parent: DirEntry) extends BundleEntry(url, parent) {

				/**
				 * @return true
				 */
				def isDirectory: Boolean = true

				override def iterator: Iterator[AbstractFile] = {
					new Iterator[AbstractFile]() {
						val dirs = bundle.getEntryPaths(fullName)
						def hasNext = if (dirs != null) { dirs.hasMoreElements} else {false}
						def next = {
							val entry = dirs.nextElement.asInstanceOf[String]
							var entryUrl = bundle.getResource("/" + entry)

							// Bundle.getResource seems to be inconsistent with respect to requiring
							// a trailing slash
							if (entryUrl == null)
								entryUrl = bundle.getResource("/" + removeTralingSlash(entry))
            
							if (entry.endsWith(".class"))
								new FileEntry(entryUrl, DirEntry.this)
							else
								new DirEntry(entryUrl, DirEntry.this)
						}
          
						private def removeTralingSlash(s: String): String = {
							if (s == null || s.length == 0) {
								s
							}else if (s.last == '/') {
								removeTralingSlash(s.substring(0, s.length - 1))
							} else {
								s
							}
						}
					}
				}

				def lookupName(name: String, directory: Boolean): AbstractFile = {
					val entry = bundle.getEntry(fullName + "/" + name)
					nullOrElse(entry) { entry =>
						if (directory)
							new DirEntry(entry, DirEntry.this)
						else
							new FileEntry(entry, DirEntry.this)
					}
				}

				def lookupNameUnchecked(name: String, directory: Boolean): AbstractFile = {
					val entry = bundle.getEntry(fullName + "/" + name)
					if (entry == null) {
						throw new RuntimeException("Not yet implemented: inexistendt file")
					} else {
						if (directory)
							new DirEntry(entry, DirEntry.this)
						else
							new FileEntry(entry, DirEntry.this)
					}
				}

			}

			class FileEntry(url: URL, parent: DirEntry) extends BundleEntry(url, parent) {

				/**
				 * @return false
				 */
				def isDirectory: Boolean = false
				override def sizeOption: Option[Int] = Some(bundle.getEntry(fullName).openConnection().getContentLength())
				override def iterator: Iterator[AbstractFile] = Iterator.empty
				def lookupName(name: String, directory: Boolean): AbstractFile = null
				def lookupNameUnchecked(name: String, directory: Boolean): AbstractFile = null
			}

			new DirEntry(bundle.getResource("/"), null)
		}

	}

}