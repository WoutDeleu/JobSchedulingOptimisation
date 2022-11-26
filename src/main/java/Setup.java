import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Setup extends Task{
    private int duration;
    @Expose
    @SerializedName("from")
    private int job1;
    @Expose
    @SerializedName("to")
    private int job2;

    public Setup(int duration, int job1, int job2) {
        this.duration = duration;
        this.job1 = job1;
        this.job2 = job2;
    }

    public int getDuration() {
        return duration;
    }
    public int getJob1() {
        return job1;
    }
    public int getJob2() {
        return job2;
    }


    public void setDuration(int duration) {
        this.duration = duration;
    }
    public void setJob1(int job1) {
        this.job1 = job1;
    }
    public void setJob2(int job2) {
        this.job2 = job2;
    }


    // Setup has no releaseDate and dueDate so is always feasible by its dates
    public boolean isFeasibleDates() {return true;}
    public void setStartDate(int startDate) { this.startDate = startDate; }
    public void setEarliestStartDate(int startDate) { this.startDate = startDate; }

    public void calculateStartAndFinish(Job job) {
        finishDate = job.startDate-1;
        startDate = finishDate-duration+1;
    }

    @Override
    public String toString() {
        return "Setup{" +
                "duration=" + duration +
                ", from=" + job1 +
                ", to=" + job2 +
                ", start=" + startDate +
//                ", finishDate=" + finishDate +
                "}";
    }
}
