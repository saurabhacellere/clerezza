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
package org.apache.clerezza.shell;



import org.apache.felix.scr.annotations.Component;
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleListener
import org.osgi.service.component.ComponentContext;
import org.osgi.framework.Bundle
import java.io.{File, PrintWriter, Reader, StringWriter, BufferedReader, InputStreamReader, InputStream, Writer}
import java.lang.reflect.InvocationTargetException
import java.net._
import java.security.PrivilegedActionException
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.{ArrayList, Arrays};
import javax.script.ScriptContext
import javax.script.{ScriptEngineFactory => JavaxEngineFactory, Compilable,
					 CompiledScript, ScriptEngine, AbstractScriptEngine, Bindings,
					 SimpleBindings, ScriptException}
//import scala.collection.immutable.Map
import scala.actors.DaemonActor
import scala.tools.nsc._;
import scala.tools.nsc.interpreter._;
import scala.tools.nsc.io.{AbstractFile, PlainFile, VirtualDirectory}
import scala.tools.nsc.util._
import scala.tools.nsc.symtab.SymbolLoaders
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.reporters.Reporter
import scala.tools.util.PathResolver
import scala.tools.nsc.util.{ClassPath, JavaClassPath}
import scala.actors.Actor
import scala.actors.Actor._
import org.apache.clerezza.scala.scripting._
import java.io.File
import jline.{ ConsoleReader, ArgumentCompletor, History => JHistory }

class Shell(factory: InterpreterFactory, val inStream: InputStream, out: Writer)  {


	private var bundleContext: BundleContext = null

	private var bindings = Set[(String, String, Any)]()


	val interpreterLoop = new InterpreterLoop(new BufferedReader(new InputStreamReader(System.in)), new PrintWriter(System.out, true)) {
		override def createInterpreter() {
			interpreter = factory.createInterpreter(out)
			interpreter.interpret("import org.apache.clerezza._")
			interpreter.interpret("val a = 33")
			interpreter.interpret("println(\"enjoy!\")")
			for (binding <- bindings) {
				interpreter.bind(binding._1, binding._2, binding._3)
			}
		}

		override val prompt = "zz>"

		override def main(settings: Settings) {
			this.settings = settings
			createInterpreter()

			// sets in to some kind of reader depending on environmental cues
			in = new InteractiveReader() {

			  override lazy val history = Some(History(consoleReader))
			  override lazy val completion = Option(interpreter) map (x => new Completion(x))

			  val consoleReader = {
				val r = new jline.ConsoleReader(inStream, out)
				r setHistory (History().jhistory)
				r setBellEnabled false
				completion foreach { c =>
				  r addCompletor c.jline
				  r setAutoprintThreshhold 250
				}

				r
			  }

			  def readOneLine(prompt: String) = consoleReader readLine prompt
			  val interactive = true
			}

			loadFiles(settings)
			try {
				// it is broken on startup; go ahead and exit
				if (interpreter.reporter.hasErrors) return

				printWelcome()

				// this is about the illusion of snappiness.  We call initialize()
				// which spins off a separate thread, then print the prompt and try
				// our best to look ready.  Ideally the user will spend a
				// couple seconds saying "wow, it starts so fast!" and by the time
				// they type a command the compiler is ready to roll.
				interpreter.initialize()
				repl()
			}
			finally closeInterpreter()
		}
	}
	val console: Actor = actor {
		//cala.tools.nsc.MainGenericRunner.main(Array[String]());
		//scalaConsole();

		println("starting console")
		try {
			interpreterLoop.main(Array[String]())
		} finally {
			println("console terminated")
		}
	}

	def start() {
		console.start
	}

	def stop() {
		interpreterLoop.command(":q")
		interpreterLoop.closeInterpreter()
	}

	def bind(name: String, boundType: String, value: Any) {
		bindings += ((name, boundType, value))
	}



}