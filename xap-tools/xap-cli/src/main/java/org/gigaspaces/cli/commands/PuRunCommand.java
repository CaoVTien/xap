package org.gigaspaces.cli.commands;

import org.gigaspaces.cli.commands.AbstractRunCommand;
import picocli.CommandLine.*;

import java.io.File;
import java.util.*;

@Command(name="run", header = "Runs a standalone processing unit")
public class PuRunCommand extends AbstractRunCommand {

    @Parameters(index = "0", description = "The relative/absolute path of a processing unit directory or jar")
    File path;
    @Option(names = {"--partitions" }, description = "Number of partitions in processing unit")
    int partitions;
    @Option(names = {"--ha" }, description = "Should the processing unit include backups for high availability")
    boolean ha;
    @Option(names = {"--instances" }, split = ",", description = "Which instances should be run (default is all instances)")
    String[] instances;
    @Option(names = {"--lus"}, description = "Should the lookup service be started")
    boolean lus;

    @Override
    protected void execute() throws Exception {

        validateOptions(partitions, ha, instances);

        final List<ProcessBuilder> processBuilders = new ArrayList<ProcessBuilder>();
        if (lus) {
            processBuilders.add(buildStartLookupServiceCommand());
        }
        if (partitions == 0) {
            processBuilders.add(buildSinglePuCommand());
        } else {
            for (int id = 1; id < partitions+1; id++) {
                if (instances == null) {
                    processBuilders.add(buildPartitionedPuCommand(id));
                    if (ha) {
                        processBuilders.add(buildPartitionedBackupPuCommand(id));
                    }
                } else {
                    if (containsInstance(instances, id + "_" + 1)) {
                        processBuilders.add(buildPartitionedPuCommand(id));
                    }
                    if (containsInstance(instances, id + "_" + 2)) {
                        processBuilders.add(buildPartitionedBackupPuCommand(id));
                    }
                }
            }
        }
        executeProcesses(processBuilders);
    }

    private ProcessBuilder buildSinglePuCommand(){

        final ProcessBuilder pb = createJavaProcessBuilder();
        final Collection<String> commands = new LinkedHashSet<String>();
        commands.add("-Dcom.gs.start-embedded-lus=false");

        String[] options = {"XAP_OPTIONS"};
        addOptions(commands, options);

        commands.add("-classpath");
        StringBuilder classpath = new StringBuilder();
        if (pb.environment().get("PRE_CLASSPATH") != null) {
            classpath.append(pb.environment().get("PRE_CLASSPATH")).append(File.pathSeparator);
        }
        classpath.append(pb.environment().get("GS_JARS")).append(File.pathSeparator).append(pb.environment().get("SPRING_JARS"));
        if (pb.environment().get("POST_CLASSPATH") != null) {
            classpath.append(File.pathSeparator).append(pb.environment().get("POST_CLASSPATH"));
        }

        commands.add(classpath.toString());
        commands.add("org.openspaces.pu.container.standalone.StandaloneProcessingUnitContainer");
        commands.add("-path");
        commands.add(path.getPath());

        pb.command().addAll(commands);
        showCommand("Starting Process Unit with line:", pb.command());
        return pb;
    }

    private ProcessBuilder buildPartitionedPuCommand(int id) {
        return buildPartitionedPuCommand(id, false);
    }

    private ProcessBuilder buildPartitionedBackupPuCommand(int id) {
        return buildPartitionedPuCommand(id, true);
    }

    private ProcessBuilder buildPartitionedPuCommand(int id, boolean backup) {

        final ProcessBuilder pb = createJavaProcessBuilder();
        final Collection<String> commands = new LinkedHashSet<String>();
        commands.add("-Dcom.gs.start-embedded-lus=false");

        String[] options = {"XAP_OPTIONS"};
        addOptions(commands, options);

        commands.add("-classpath");
        StringBuilder classpath = new StringBuilder();
        if (pb.environment().get("PRE_CLASSPATH") != null) {
            classpath.append(pb.environment().get("PRE_CLASSPATH")).append(File.pathSeparator);
        }
        classpath.append(pb.environment().get("GS_JARS")).append(File.pathSeparator).append(pb.environment().get("SPRING_JARS"));
        if (pb.environment().get("POST_CLASSPATH") != null) {
            classpath.append(File.pathSeparator).append(pb.environment().get("POST_CLASSPATH"));
        }

        commands.add(classpath.toString());
        commands.add("org.openspaces.pu.container.standalone.StandaloneProcessingUnitContainer");
        commands.add("-path");
        commands.add(path.getPath());

        commands.add("-cluster");
        commands.add("schema=partitioned");
        if(ha){
            commands.add("total_members=" + partitions + ",1");
        } else{
            commands.add("total_members=" + partitions + ",0");
        }
        commands.add("id=" + id);
        if(backup){
            commands.add("backup_id=1");
        }

        pb.command().addAll(commands);
        showCommand("Starting Process Unit with line:", pb.command());
        return pb;
    }
}
