package services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners

import services.util.MergeToolRunner
import util.ProcessRunner
import util.TextualMergeStrategy

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class LastMergeRunner extends MergeToolRunner {

    private static final Path LAST_MERGE_PATH = Paths.get('dependencies/dependencies/last-merge/release')
    //private static final Path LAST_MERGE_PATH = Paths.get('dependencies/lastmerge.jar')
    //lastmerge is actually wrapped into S3M
    private static final String LAST_MERGE_FILE_NAME = 'merge'

    LastMergeRunner() {
        this.mergeToolName = 'LastMerge'

    }

    protected ProcessBuilder buildProcess(Path leftFile, Path baseFile, Path rightFile) {
        //String processDirectory = LAST_MERGE_PATH.getParent().toString()
        String processDirectory = LAST_MERGE_PATH.toString()
        return ProcessRunner.buildProcess(processDirectory)
    }

//    protected List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile) {
//        String jarFileName = LAST_MERGE_PATH.getFileName().toString()
//        List<String> parameters = ['java', '-jar', jarFileName]
//        parameters.addAll(leftFile.toString(), baseFile.toString(), rightFile.toString())
//
//        Path filesQuadruplePath = baseFile.getParent()
//        Path outputPath = getOutputPath(filesQuadruplePath, LAST_MERGE_FILE_NAME)
//        parameters.addAll('-o', outputPath.toString())
//        parameters.addAll('-c', 'false', '-l', 'false')
//        parameters.addAll('-s', 'true')
//
//        return parameters
//    }

    protected List<String> buildParameters(Path leftFile, Path baseFile, Path rightFile) {
        List<String> parameters = ['./last-merge', 'merge']
        Path filesQuadruplePath = baseFile.getParent()
        Path outputPath = getOutputPath(filesQuadruplePath, LAST_MERGE_FILE_NAME)
        parameters.addAll('--base-path', baseFile.toString())
        parameters.addAll('--left-path', leftFile.toString())
        parameters.addAll('--right-path', rightFile.toString())
        parameters.addAll('--merge-path', outputPath.toString())
        parameters.addAll('--language=java')

        return parameters
    }

    protected void runTool(Path leftFile, Path baseFile, Path rightFile) {
        super.runTool(leftFile, baseFile, rightFile)

        fixConflictMarkers(baseFile)
    }

    private void fixConflictMarkers(Path baseFile) {
        Path filesQuadruplePath = baseFile.getParent()
        Path outputPath = getOutputPath(filesQuadruplePath, LAST_MERGE_FILE_NAME)
        if (Files.exists(outputPath)) {
            String content = new String(Files.readAllBytes(outputPath))
            if (content.contains("<<<<<<<")) {
                Files.write(outputPath, content.replace("<<<<<<<", "<<<<<<< MINE").getBytes())
                content = new String(Files.readAllBytes(outputPath))
                Files.write(outputPath, content.replace(">>>>>>>", ">>>>>>> YOURS").getBytes())
            }
        }
    }
}