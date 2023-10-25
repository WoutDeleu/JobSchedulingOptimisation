import java.util.Comparator;

public class JobComparator implements Comparator<Job> {
    public int compare(Job j1, Job j2) {
        // First sorts by releaseDate, if equal sorts by dueDate, if still equal finally sorts by earliness

        int compareReleaseDate = Integer.compare(j1.getReleaseDate(), j2.getReleaseDate());
        int compareDueDate = Integer.compare(j1.getDueDate(), j2.getDueDate());
        int compareEarliness = Double.compare(j1.getEarlinessPenalty(), j2.getEarlinessPenalty());

        if (compareReleaseDate != 0) return compareReleaseDate;
        if (compareDueDate != 0) return compareDueDate;
        else return compareEarliness;
    }
}

