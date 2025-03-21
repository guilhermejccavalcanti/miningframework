package services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners

import services.util.MergeToolRunner
import util.ProcessRunner
import util.TextualMergeStrategy

import java.nio.file.Path
import java.nio.file.Paths

class SepmergeRunner extends MergeToolRunner {

    private static final Path SEPMERGE_PATH = Paths.get('dependencies/sepmerge.jar')
    private static final String SEPMERGE_MERGE_FILE_NAME = 'merge'


    SepmergeRunner() {
        this.mergeToolName = 'Sepmerge'
    }

    protected ProcessBuilder buildProcess(Path leftFile, Path baseFile, Path rightFile) {
        String processDirectory = SEPMERGE_PATH.getParent().toString()
        return ProcessRunner.buildProcess(processDirectory)
    }

    protected List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile) {
        String jarFileName = SEPMERGE_PATH.getFileName().toString()
        List<String> parameters = ['java', '-jar', jarFileName]
        parameters.addAll(leftFile.toString(), baseFile.toString(), rightFile.toString())

        Path filesQuadruplePath = baseFile.getParent()
        Path outputPath = getOutputPath(filesQuadruplePath, SEPMERGE_MERGE_FILE_NAME)
        parameters.addAll('-o', outputPath.toString())

        return parameters
    }
}