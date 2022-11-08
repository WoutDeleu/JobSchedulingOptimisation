public class UnavailablePeriod extends Task{

    //TODO hoe kunnen we met startDate en finishDate werken van Task object
    //  - Als we dat nu doen loopt inlezen van json mis door attributen die niet overeen komen

    private int start;
    private int end;

    public UnavailablePeriod(int start, int end) {
        super(start, end);
    }

    public int getStart() { return start; }
    public int getEnd() {
        return end;
    }

    public void setStart(int start) { this.start = start; }
    public void setEnd(int end) {
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
