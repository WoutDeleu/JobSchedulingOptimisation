public class UnavailablePeriod extends Schedulable {
    private int start;
    private int end;

    @Override
    public String toString() {
        return "UnavailablePeriod{" +
                "start=" + start +
                ", end=" + end +
                "} \n";
    }
}
