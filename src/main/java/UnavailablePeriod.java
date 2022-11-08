public class UnavailablePeriod extends Task {

    //TODO hoe kunnen we met startDate en finishDate werken van Task object
    //  - Als we dat nu doen loopt inlezen van json mis door attributen die niet overeen komen

    private int start;
    private int end;

    public UnavailablePeriod(int start, int end) {
        super(start, end);
    }

    public int getStartDate() { return start; }
    public int getFinishDate() {
        return end;
    }

    public void setStartDate(int start) { this.start = start; }
    public void setFinishDate(int end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "UnavailablePeriod{" +
                "start=" + start +
                ", end=" + end +
                "} \n";
    }
}
