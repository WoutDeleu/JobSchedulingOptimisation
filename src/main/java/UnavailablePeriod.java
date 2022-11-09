public class UnavailablePeriod {

    private int start;
    private int end;

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
