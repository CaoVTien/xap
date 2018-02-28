package org.gigaspaces.cli;

import com.gigaspaces.logger.Constants;
import com.gigaspaces.logger.GSLogConfigLoader;
import org.gigaspaces.cli.commands.XapVersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

@Command(
        versionProvider = XapVersionProvider.class,
        sortOptions = false,
        //headerHeading = "",
        //header = "<header goes here>",
        synopsisHeading = "Usage: ",
        descriptionHeading = "%nDescription: ",
        //description = "<description goes here>",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n")
public abstract class CliCommand implements Callable<Object> {

    protected static Logger LOGGER;

    @Option(names = {"--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

    @Option(names = {"--version"}, versionHelp = true, description = "display version information")
    boolean versionRequested;

    @Override
    public Object call() throws Exception {
        beforeExecute();
        execute();
        return null;
    }

    public CliCommand(){
        GSLogConfigLoader.getLoader("cli");
        LOGGER = Logger.getLogger(Constants.LOGGER_CLI);
    }

    protected void beforeExecute() {
    }

    protected abstract void execute() throws Exception;

    protected void underConstruction() {
        Command command = this.getClass().getAnnotation(Command.class);
        System.out.println("Command " + command.name() + " is under construction");
    }
}
