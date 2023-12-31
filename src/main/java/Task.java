import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public abstract class Task {
    @Expose
    @SerializedName("start")
    protected int startDate = -1;
    protected int finishDate = -1;

    public Task(int startDate, int finishDate) {
        this.startDate = startDate;
        this.finishDate = finishDate;
    }

    public Task () {}

    public int getStartDate() { return startDate; }
    public int getFinishDate() {
        return finishDate;
    }


    public void calculateFinishDate() {
        assert startDate >= 0 : "startDate not set";
        finishDate = startDate+getDuration()-1;
    }

    // Returns true if the job doesn't overlap with any unavailable periods
    public boolean isFeasibleUPs(List<UnavailablePeriod> unavailablePeriods) {
        assert startDate >= 0 : "startDate not set: "+this;
        assert finishDate >= 0 : "finishDate not set";

        // If task is scheduled after ALL the unavailable periods
        if (startDate>unavailablePeriods.get(unavailablePeriods.size()-1).getFinishDate()) {
            return true;
        }

        for (UnavailablePeriod up : unavailablePeriods) {
            int startUp = up.getStartDate();
            int endUp = up.getFinishDate();
            // Not executable if startUp or finish of a job lies in unavailable period:
            // startUp<startDate<finishDate or startDate<endUp<finishDate
            if(startUp <=startDate&&startDate<= endUp || startUp <=finishDate&&finishDate<= endUp) {
                return false;
            }
        }
        return true;
    }

    /** abstract functions **/
    public abstract int getDuration();
    public abstract boolean isFeasibleDates();
    public abstract void setStartDate(int startDate);
    public abstract void setEarliestStartDate(int startDate);

    @Override
    public abstract Task clone();


}
