package services.util

import groovy.transform.Synchronized
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners.S3MRunner
import util.ProcessRunner

import java.nio.file.Path
import java.util.concurrent.TimeUnit

abstract class MergeToolRunner {

    public String mergeToolName
    public Project executedProject
    public MergeCommit executedMergeCommit

    protected static TIMEOUT_IN_HOURS = 1

    void collectResults(List<Path> filesQuadruplePaths) {
        filesQuadruplePaths.each { filesQuadruplePath ->
            collectResults(filesQuadruplePath)
        }
    }

    void collectResults(Path filesQuadruplePath) {
        Path leftFile = getContributionFile(filesQuadruplePath, 'left')
        Path baseFile = getContributionFile(filesQuadruplePath, 'base')
        Path rightFile = getContributionFile(filesQuadruplePath, 'right')

        createToolDirectory(filesQuadruplePath)

        long startTime = System.nanoTime()
        runTool(leftFile, baseFile, rightFile)
        long endTime = System.nanoTime()

        long executionTime = endTime - startTime
        String mergedFile = filesQuadruplePath.getFileName().toString()
        writeExecutionTime(this.executedProject, this.executedMergeCommit, mergedFile, this, executionTime)
    }

    protected Path getContributionFile(Path filesQuadruplePath, String contributionFileName) {
        return filesQuadruplePath.resolve("${contributionFileName}.java").toAbsolutePath()
    }

    protected void createToolDirectory(Path filesQuadruplePath) {
        filesQuadruplePath.resolve(mergeToolName).toFile().mkdir()
    }

    protected void runTool(Path leftFile, Path baseFile, Path rightFile) {
        ProcessBuilder processBuilder = buildProcess(leftFile, baseFile, rightFile)
        List<String> parameters = buildParameters(leftFile, baseFile, rightFile)
        processBuilder.command().addAll(parameters)

        Process process = ProcessRunner.startProcess(processBuilder)
        process.getInputStream().eachLine {}
        process.waitFor(TIMEOUT_IN_HOURS, TimeUnit.HOURS)
    }

    protected Path getOutputPath(Path filesQuadruplePath, String mergeFileName) {
        return filesQuadruplePath.resolve(mergeToolName).resolve("${mergeFileName}.java")
    }

    protected abstract ProcessBuilder buildProcess(Path leftFile, Path baseFile, Path rightFile)

    protected abstract List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile)

    private static synchronized void writeExecutionTime(Project p, MergeCommit m, String mergedFile, MergeToolRunner mergeTool, long executionTime) {
        File timeTable = new File("./Results/time-table.csv")
        if (!timeTable.exists()) {
            timeTable.createNewFile()
            timeTable << "name,mergecommit,mergedfile,mergetoolname,executiontime\n"
        }

        String mergeToolName = getMergeToolName(mergeTool)

        String line = p.name + "," + m.SHA + "," + mergedFile + "," + mergeToolName + "," + executionTime
        timeTable << "${line.replaceAll('\\\\', '/')}\n"
    }

    private static String getMergeToolName(MergeToolRunner mergeTool) {
        String mergeToolName = mergeTool.mergeToolName
        if (mergeTool instanceof S3MRunner) {
            mergeToolName += ((S3MRunner) mergeTool).getTextualStrategy().toString()
        }
        mergeToolName
    }
}