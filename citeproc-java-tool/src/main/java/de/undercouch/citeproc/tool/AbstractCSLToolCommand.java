// Copyright 2013 Michel Kraemer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package de.undercouch.citeproc.tool;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import de.undercouch.underline.Command;
import de.undercouch.underline.InputReader;
import de.undercouch.underline.OptionDesc;
import de.undercouch.underline.OptionGroup;
import de.undercouch.underline.OptionIntrospector;
import de.undercouch.underline.OptionIntrospector.ID;
import de.undercouch.underline.OptionParser;
import de.undercouch.underline.OptionParserException;

/**
 * A base class for commands from the {@link de.undercouch.citeproc.CSLTool}
 * @author Michel Kraemer
 */
public abstract class AbstractCSLToolCommand implements CSLToolCommand {
	private OptionGroup<ID> options;
	private boolean displayHelp;
	
	/**
	 * @return the command's options
	 */
	private OptionGroup<ID> getOptions() {
		return options;
	}
	
	/**
	 * Specifies if the command's help should be displayed
	 * @param display true if the help should be displayed
	 */
	@OptionDesc(longName = "help", shortName = "h",
			description = "display this help and exit", priority = 9000)
	public void setDisplayHelp(boolean display) {
		this.displayHelp = display;
	}
	
	/**
	 * Outputs an error message
	 * @param msg the message
	 */
	protected void error(String msg) {
		System.err.println(CSLToolContext.current().getToolName() + ": " + msg);
	}
	
	/**
	 * @return the classes to inspect for CLI options
	 */
	protected Class<?>[] getClassesToIntrospect() {
		return new Class<?>[] { getClass() };
	}
	
	/**
	 * @return the commands the parsed CLI values should be injected into
	 */
	protected Command[] getObjectsToEvaluate() {
		return new Command[] { this };
	}
	
	@Override
	public int run(String[] args, InputReader in, PrintWriter out)
			throws OptionParserException, IOException {
		if (options == null) {
			try {
				options = OptionIntrospector.introspect(getClassesToIntrospect());
			} catch (IntrospectionException e) {
				throw new RuntimeException("Could not inspect command", e);
			}
		}
		
		boolean unknownArgs = OptionIntrospector.hasUnknownArguments(
				getClassesToIntrospect());
		OptionParser.Result<ID> parsedOptions = OptionParser.parse(args,
				getOptions(), unknownArgs ? OptionIntrospector.DEFAULT_ID : null);
		try {
			OptionIntrospector.evaluate(parsedOptions.getValues(),
					(Object[])getObjectsToEvaluate());
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Could not evaluate options", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Could not evaluate options", e);
		} catch (InstantiationException e) {
			throw new RuntimeException("Could not evaluate options", e);
		}
		
		if (displayHelp) {
			usage();
			return 0;
		}
		
		if (!checkArguments()) {
			return 1;
		}
		
		return doRun(parsedOptions.getRemainingArgs(), in, out);
	}
	
	/**
	 * Prints out usage information
	 */
	protected void usage() {
		String footnotes = null;
		if (!getOptions().getCommands().isEmpty()) {
			footnotes = "Use `" + CSLToolContext.current().getToolName() +
					" help <command>' to read about a specific command.";
		}
		
		String name = CSLToolContext.current().getToolName();
		String usageName = getUsageName();
		if (usageName != null && !usageName.isEmpty()) {
			name += " " + usageName;
		}
		
		String unknownArguments = OptionIntrospector.getUnknownArgumentName(
				getClassesToIntrospect());
		
		OptionParser.usage(name, getUsageDescription(), getOptions(),
				unknownArguments, footnotes, new PrintWriter(System.out, true));
	}
	
	@Override
	public abstract String getUsageName();
	
	@Override
	public abstract String getUsageDescription();
	
	@Override
	public boolean checkArguments() {
		//nothing to check by default. subclasses may override
		return true;
	}
	
	@Override
	public abstract int doRun(String[] remainingArgs, InputReader in,
			PrintWriter out) throws OptionParserException, IOException;
}
