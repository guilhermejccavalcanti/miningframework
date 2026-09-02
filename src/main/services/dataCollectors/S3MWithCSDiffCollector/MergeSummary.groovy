package services.dataCollectors.S3MWithCSDiffCollector

import services.util.MergeConflict
import util.JavaEquivalenceChecker
import util.TextualMergeStrategy

import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MergeSummary {

    private static final String MERGE_FILE_NAME = "merge.java"

    // Path with the base, left, right and merge files involved in the merge
    Path filesQuadruplePath

    Map<String, Integer> numberOfConflictsPerApproach
    Map<String, Map<String, Boolean>> approachesHaveSameOutputs
    Map<String, Map<String, Boolean>> approachesHaveSameConflicts

    // Compares base.java, left.java and right.java (revisions) against the "Actual" merge output
    Map<String, Boolean> revisionsHaveSameOutputAsActual

    MergeSummary(Path filesQuadruplePath) {
        this.filesQuadruplePath = filesQuadruplePath
        compareMergeApproaches()
    }

    private void compareMergeApproaches() {
        try {
            Map<String, Path> mergeOutputPaths = getMergeOutputPaths()
            Map<String, String> mergeOutputs = getMergeOutputs(mergeOutputPaths)
            Map<String, Set<MergeConflict>> mergeConflicts = getMergeConflicts(mergeOutputPaths)

            this.numberOfConflictsPerApproach = [:]
            mergeConflicts.each { approach, conflicts ->
                this.numberOfConflictsPerApproach[approach] = conflicts.size()
            }

            this.approachesHaveSameOutputs = [:]
            this.approachesHaveSameConflicts = [:]

            for (int i = 0; i < MergesCollector.mergeApproaches.size(); i++) {
                String approach1 = MergesCollector.mergeApproaches[i]
                this.approachesHaveSameOutputs[approach1] = [:]
                this.approachesHaveSameConflicts[approach1] = [:]

                for (int j = i + 1; j < MergesCollector.mergeApproaches.size(); j++) {
                    String approach2 = MergesCollector.mergeApproaches[j]

                    // Merge outputs are compared
                    compareMergeOutputs(approach1, approach2, mergeOutputPaths, mergeOutputs, mergeConflicts)

                    Set<MergeConflict> conflicts1 = mergeConflicts[approach1]
                    Set<MergeConflict> conflicts2 = mergeConflicts[approach2]
                    this.approachesHaveSameConflicts[approach1][approach2] = conflicts1 == conflicts2
                }
            }
            compareRevisionsToActual(mergeOutputs)
        } catch (Exception e){
            println 'Ignoring ' + filesQuadruplePath + e.getMessage()
        }
    }

    private void compareRevisionsToActual(Map<String, String> mergeOutputs) {
        this.revisionsHaveSameOutputAsActual = [:]

        String actualOutput = mergeOutputs["Actual"]

        for (String revision : ["base", "left", "right"]) {
            Path revisionPath = getRevisionPath(revision)
            boolean isEqual = false

            if (revisionPath.toFile().exists()) {
                String revisionOutput = getMergeOutput(revisionPath)
                isEqual = JavaEquivalenceChecker.areJavaFilesEquivalent(revisionOutput, actualOutput)
            }

            this.revisionsHaveSameOutputAsActual[revision] = isEqual
        }
    }

    private Path getRevisionPath(String revisionName) {
        return this.filesQuadruplePath.resolve("${revisionName}.java")
    }

    private void compareMergeOutputs(String approach1, String approach2, Map<String, Path> mergeOutputPaths,
                                     Map<String, String> mergeOutputs, Map<String, Set<MergeConflict>> mergeConflicts) {
        String approach1Output = mergeOutputs[approach1]
        String approach2Output = mergeOutputs[approach2]
        if (isComparingSporkToMergeCommit(approach1, approach2)) {
            if(mergeConflicts["Spork"].size() == 0) { //only to compute Sporks' FN
                try {
                    approach1Output = normalizeToSporkFormat(mergeOutputPaths[approach1])
                    approach2Output = normalizeToSporkFormat(mergeOutputPaths[approach2])
                } catch (Exception e) {
                    approach1Output = mergeOutputs[approach1]
                    approach2Output = mergeOutputs[approach2]
                }
            }
        }
        this.approachesHaveSameOutputs[approach1][approach2] = JavaEquivalenceChecker.
                areJavaFilesEquivalent(approach1Output, approach2Output)
    }

    private Map<String, Path> getMergeOutputPaths() {
        Map<String, Path> mergeOutputPaths = [:]
        mergeOutputPaths["CSDiff"] = getCSDiffMergeOutputPath()
        mergeOutputPaths["Diff3"] = getDiff3MergeOutputPath()
        mergeOutputPaths["Sepmerge"] = getSepMergeOutputPath()
        mergeOutputPaths["Autosepmerge"] = getAutotuningSepmergeOutputPath()
        mergeOutputPaths["Spork"] = getSporkOutputPath()
        mergeOutputPaths["LastMerge"] = getLastMergeOutputPath()
        mergeOutputPaths["JMergeGen"] = getJMergeGenOutputPath()
        mergeOutputPaths["GitMergeFile"] = getGitMergeFileOutputPath()
        mergeOutputPaths["Actual"] = getActualMergeOutputPath()

        for (TextualMergeStrategy strategy : MergesCollector.strategies) {
            String key = "S3M${strategy.name()}"
            mergeOutputPaths[key] = getMergeStrategyOutputPath(strategy)
        }

        return mergeOutputPaths
    }

    private Path getCSDiffMergeOutputPath() {
        return this.filesQuadruplePath.resolve("CSDiff").resolve(MERGE_FILE_NAME)
    }

    private Path getDiff3MergeOutputPath() {
        return this.filesQuadruplePath.resolve("Diff3").resolve(MERGE_FILE_NAME)
    }

    private Path getSepMergeOutputPath() {
        return this.filesQuadruplePath.resolve("Sepmerge").resolve(MERGE_FILE_NAME)
    }

    private Path getAutotuningSepmergeOutputPath() {
        return this.filesQuadruplePath.resolve("Autosepmerge").resolve(MERGE_FILE_NAME)
    }

    private Path getSporkOutputPath() {
        return this.filesQuadruplePath.resolve("Spork").resolve(MERGE_FILE_NAME)
    }

    private Path getLastMergeOutputPath() {
        return this.filesQuadruplePath.resolve("LastMerge").resolve(MERGE_FILE_NAME)
    }

    private Path getJMergeGenOutputPath() {
        return this.filesQuadruplePath.resolve("JMergeGen").resolve(MERGE_FILE_NAME)
    }

    private Path getGitMergeFileOutputPath() {
        return this.filesQuadruplePath.resolve("GitMergeFile").resolve(MERGE_FILE_NAME)
    }

    private Path getActualMergeOutputPath() {
        return this.filesQuadruplePath.resolve(MERGE_FILE_NAME)
    }

    private Path getMergeStrategyOutputPath(TextualMergeStrategy strategy) {
        String mergeFileName = getMergeStrategyOutputFileName(strategy)
        return this.filesQuadruplePath.resolve("S3M").resolve(mergeFileName)
    }

    private String getMergeStrategyOutputFileName(TextualMergeStrategy strategy) {
        return "${strategy.name()}.java"
    }

    private Map<String, String> getMergeOutputs(Map<String, Path> mergeOutputPaths) {
        Map<String, String> outputs = [:]
        MergesCollector.mergeApproaches.each { approach ->
            Path mergeOutputPath = mergeOutputPaths[approach]
            String output = getMergeOutput(mergeOutputPath)
            outputs[approach] = output
        }

        return outputs
    }

    private String getMergeOutput(Path mergeOutputPath) {
        return mergeOutputPath.getText()
    }

    private Map<String, Set<MergeConflict>> getMergeConflicts(Map<String, Path> mergeOutputPaths) {
        Map<String, Set<MergeConflict>> conflicts = [:]
        MergesCollector.mergeApproaches.each { approach ->
            Path mergeOutputPath = mergeOutputPaths[approach]
            Set<MergeConflict> currentConflicts = getMergeConflicts(mergeOutputPath)
            conflicts[approach] = currentConflicts
        }

        return conflicts
    }

    private Set<MergeConflict> getMergeConflicts(Path mergeOutputPath) {
        return MergeConflict.extractMergeConflicts(mergeOutputPath)
    }

    @Override
    String toString() {
        List<String> values = [this.filesQuadruplePath.getFileName()]
        for (String approach : MergesCollector.mergeApproaches) {
            values.add(Integer.toString(this.numberOfConflictsPerApproach[approach]))
        }

        for (int i = 0; i < MergesCollector.mergeApproaches.size(); i++) {
            String approach1 = MergesCollector.mergeApproaches[i]
            for (int j = i + 1; j < MergesCollector.mergeApproaches.size(); j++) {
                String approach2 = MergesCollector.mergeApproaches[j]

                boolean sameOutput = this.approachesHaveSameOutputs[approach1][approach2]
                values.add(Boolean.toString(sameOutput))
            }
        }

        for (int i = 0; i < MergesCollector.mergeApproaches.size(); i++) {
            String approach1 = MergesCollector.mergeApproaches[i]
            for (int j = i + 1; j < MergesCollector.mergeApproaches.size(); j++) {
                String approach2 = MergesCollector.mergeApproaches[j]

                boolean sameConflict = this.approachesHaveSameConflicts[approach1][approach2]
                values.add(Boolean.toString(sameConflict))
            }
        }

        for (String revision : ["base", "left", "right"]) {
            boolean sameAsActual = this.revisionsHaveSameOutputAsActual[revision]
            values.add(Boolean.toString(sameAsActual))
        }

        return values.join(',')
    }

    private boolean isComparingSporkToMergeCommit(String mergeToolA, String mergeToolB) {
        return (mergeToolA == "Spork" && mergeToolB == "Actual")
                || (mergeToolA == "Actual" && mergeToolB == "Spork");
    }

    private String normalizeToSporkFormat(Path fileToBeNormalized) throws Exception {
        // To normalize to the spork format, it is necessary to merge the file with itself.
        ProcessBuilder processBuilder = getSporkProcessBuilder(fileToBeNormalized)
        StringBuilder output = new StringBuilder()

        try {
            Process process = processBuilder.start()
            process.inputStream.withReader { reader ->
                reader.eachLine { line ->
                    output.append(line).append(System.lineSeparator())
                }
            }
            process.waitFor(1, TimeUnit.HOURS)
        } catch (IOException | InterruptedException e) {
            throw e
        }

        String commandOutput = output.toString()
        return commandOutput
    }

    private ProcessBuilder getSporkProcessBuilder(Path fileToBeNormalized) {
        String[] command = [
                "java",
                "-jar",
                "dependencies/spork.jar",
                fileToBeNormalized.toString(),
                fileToBeNormalized.toString(),
                fileToBeNormalized.toString()
        ] as String[]

        // Create a ProcessBuilder
        ProcessBuilder processBuilder = new ProcessBuilder(command)
        processBuilder.redirectErrorStream(true) // Redirect error stream to output stream
        return processBuilder
    }
}