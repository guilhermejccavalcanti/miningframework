package services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners

import services.util.MergeToolRunner
import util.ProcessRunner

import java.nio.file.Path
import java.nio.file.Paths

class SporkRunner extends MergeToolRunner {

    private static final Path SPORK_PATH = Paths.get('dependencies/spork.jar')
    private static final String SPORK_MERGE_FILE_NAME = 'merge'


    SporkRunner() {
        this.mergeToolName = 'Spork'
    }

    void collectResults(Path filesQuadruplePath) {
        super.collectResults(filesQuadruplePath)
        this.replaceSporkConflictMarkers(filesQuadruplePath)
    }

    protected ProcessBuilder buildProcess(Path leftFile, Path baseFile, Path rightFile) {
        String processDirectory = SPORK_PATH.getParent().toString()
        return ProcessRunner.buildProcess(processDirectory)
    }

    protected List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile) {
        String jarFileName = SPORK_PATH.getFileName().toString()
        List<String> parameters = ['java', '-jar', jarFileName]
        parameters.addAll(leftFile.toString(), baseFile.toString(), rightFile.toString())

        Path filesQuadruplePath = baseFile.getParent()
        Path outputPath = getOutputPath(filesQuadruplePath, SPORK_MERGE_FILE_NAME)
        parameters.addAll('-o', outputPath.toString())

        return parameters
    }

    private void replaceSporkConflictMarkers(Path filesQuadruplePath) {
        Path outputPath = getOutputPath(filesQuadruplePath, SPORK_MERGE_FILE_NAME)
        File mergedFile = new File(outputPath.toString())
        if (mergedFile.exists()) {
            def content = mergedFile.text

            content = content.replace('<<<<<<< LEFT', '<<<<<<< MINE')
            content = content.replace('>>>>>>> RIGHT', '>>>>>>> YOURS')

            mergedFile.write(content)
        }
    }
}