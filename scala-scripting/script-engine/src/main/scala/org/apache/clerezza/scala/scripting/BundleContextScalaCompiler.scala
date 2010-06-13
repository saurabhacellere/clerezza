/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.clerezza.scala.scripting

import org.osgi.framework.BundleContext
import org.osgi.framework.Bundle
import java.io.{File, PrintWriter}
import scala.tools.nsc._;
import scala.tools.nsc.interpreter._;
import scala.tools.nsc.io.{AbstractFile, PlainFile}
import scala.tools.nsc.util._
import java.io.PrintWriter
import java.net._
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.util.PathResolver

/*
 * unfortunately there seems to be no way to change the classpath, so this doesn't
 * listen to BundleEvents
 */
class BundleContextScalaCompiler(bundleContext : BundleContext,
		settings: Settings, reporter: Reporter)
		extends Global(settings, reporter) {


	override lazy val classPath: ClassPath[AbstractFile] = {

		val classPathOrig: ClassPath[AbstractFile]  = new PathResolver(settings).result
		var bundles: Array[Bundle] = bundleContext.getBundles
		val classPathAbstractFiles = for (bundle <- bundles;
										  val url = bundle.getResource("/");
										  if url != null) yield {
			if ("file".equals(url.getProtocol())) {
				new PlainFile(new File(url.toURI()))
			}
			else {
				BundleFS.create(bundle);
			}
		}
		val classPaths: List[ClassPath[AbstractFile]] = (for (abstractFile <- classPathAbstractFiles)
			yield {
					new DirectoryClassPath(abstractFile, classPathOrig.context)
				}) toList

		new MergedClassPath[AbstractFile](classPathOrig :: classPaths,
			   classPathOrig.context)

	}

	override def rootLoader: LazyType = {
		new loaders.JavaPackageLoader(classPath)
	}
}


