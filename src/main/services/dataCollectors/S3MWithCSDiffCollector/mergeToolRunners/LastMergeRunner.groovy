package services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners

import services.util.MergeToolRunner
import util.ProcessRunner
import util.TextualMergeStrategy

import java.nio.file.Path
import java.nio.file.Paths

class LastMergeRunner extends MergeToolRunner {

    private static final Path LAST_MERGE_PATH = Paths.get('dependencies/lastmerge.jar') //lastmerge is actually
                                                                                           // wrapped into S3M
    private static final String LAST_MERGE_FILE_NAME = 'merge'

    LastMergeRunner() {
        this.mergeToolName = 'LastMerge'

    }

    protected ProcessBuilder buildProcess(Path leftFile, Path baseFile, Path rightFile) {
        String processDirectory = LAST_MERGE_PATH.getParent().toString()
        return ProcessRunner.buildProcess(processDirectory)
    }

    protected List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile) {
        String jarFileName = LAST_MERGE_PATH.getFileName().toString()
        List<String> parameters = ['java', '-jar', jarFileName]
        parameters.addAll(leftFile.toString(), baseFile.toString(), rightFile.toString())

        Path filesQuadruplePath = baseFile.getParent()
        Path outputPath = getOutputPath(filesQuadruplePath, LAST_MERGE_FILE_NAME)
        parameters.addAll('-o', outputPath.toString())
        parameters.addAll('-c', 'false', '-l', 'false')
        parameters.addAll('-s', 'true')

        return parameters
    }
}