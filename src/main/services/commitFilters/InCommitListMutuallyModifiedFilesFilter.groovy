package services.commitFilters

import interfaces.CommitFilter
import project.MergeCommit
import project.Project

/**
 * In case the commits.csv file exists for a given project, the filter only returns the
 * commits in that file, otherwise it applies the MutuallyModifiedFilesCommitFilter to the commit.*/
class InCommitListMutuallyModifiedFilesFilter implements CommitFilter {
    private CommitFilter commitListFilter = IsInCommitListFilter.getInstance();
    private CommitFilter mutuallyModifiedFilter = new MutuallyModifiedFilesCommitFilter();

    @Override
    boolean applyFilter(Project project, MergeCommit mergeCommit) {
        File commitsFile = new File("./commits.csv")
        if (commitsFile.exists() && commitsFile.getText().contains(project.getName())) {
            return commitListFilter.applyFilter(project, mergeCommit)
        } else {
            return mutuallyModifiedFilter.applyFilter(project, mergeCommit)
        }
    }
}
