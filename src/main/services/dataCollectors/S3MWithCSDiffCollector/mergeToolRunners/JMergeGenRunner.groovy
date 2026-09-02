package services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners

import services.util.MergeToolRunner
import util.ProcessRunner

import java.nio.file.Path
import java.nio.file.Paths

class JMergeGenRunner extends MergeToolRunner {

    private static final Path JMERGEGEN_PATH = Paths.get('dependencies/JMergeGen.jar')
    private static final String JMERGEGEN_MERGE_FILE_NAME = 'merge'


    JMergeGenRunner() {
        this.mergeToolName = 'JMergeGen'
    }

    protected ProcessBuilder buildProcess(Path leftFile, Path baseFile, Path rightFile) {
        String processDirectory = JMERGEGEN_PATH.getParent().toString()
        return ProcessRunner.buildProcess(processDirectory)
    }

    protected List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile) {
        String jarFileName = JMERGEGEN_PATH.getFileName().toString()
        List<String> parameters = ['java', '-jar', jarFileName]
        parameters.addAll(leftFile.toString(), baseFile.toString(), rightFile.toString())

        Path filesQuadruplePath = baseFile.getParent()
        Path outputPath = getOutputPath(filesQuadruplePath, JMERGEGEN_MERGE_FILE_NAME)
        parameters.addAll(outputPath.toString())

        String mergeIdentifier = this.executedProject.name + "," + this.executedMergeCommit.SHA + "," +
                filesQuadruplePath.getFileName().toString()
        parameters.addAll(mergeIdentifier)

        return parameters
    }
}