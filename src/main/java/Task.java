import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Task {
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

    public void setStartDate(int startDate) { this.startDate = startDate; }
    public void setFinishDate(int finishDate) {
        this.finishDate = finishDate;
    }
    // Returns true if the job doesn't overlap with any unavailable periods
    public boolean checkExecutable(List<UnavailablePeriod> unavailablePeriods) {
        assert startDate >= 0 : "startDate not set";
        assert finishDate >= 0 : "finishDate not set";
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


}
