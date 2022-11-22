import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Job extends Task{
    @Expose
    private int id;
    private int duration;
    @SerializedName("release_date")
    private int releaseDate;
    @SerializedName("due_date")
    private int dueDate;
    @SerializedName("earliness_penalty")
    private double earlinessPenalty;
    @SerializedName("rejection_penalty")
    private double rejectionPenalty;


    public Job(int id, int duration, int releaseDate, int dueDate, double earlinessPenalty, double rejectionPenalty) {
        this.id = id;
        this.duration = duration;
        this.releaseDate = releaseDate;
        this.dueDate = dueDate;
        this.earlinessPenalty = earlinessPenalty;
        this.rejectionPenalty = rejectionPenalty;
    }

    public int getId() { return id; }
    public int getDuration() {
        return duration;
    }
    public int getReleaseDate() {
        return releaseDate;
    }
    public int getDueDate() {
        return dueDate;
    }
    public double getEarlinessPenalty() {
        return earlinessPenalty;
    }
    public double getRejectionPenalty() {
        return rejectionPenalty;
    }

    public void setId(int id) { this.id = id; }
    public void setDuration(int duration) {
        this.duration = duration;
    }
    public void setReleaseDate(int releaseDate) {
        this.releaseDate = releaseDate;
    }
    public void setDueDate(int dueDate) {
        this.dueDate = dueDate;
    }
    public void setEarlinessPenalty(double earlinessPenalty) {
        this.earlinessPenalty = earlinessPenalty;
    }
    public void setRejectionPenalty(double rejectionPenalty) {
        this.rejectionPenalty = rejectionPenalty;
    }

    public void calculateFinishDate() {
        this.finishDate = this.startDate + this.duration - 1;
    }



    @Override
    public String toString() {
        return
                "Job{" +
                "id=" + id +
//                ", duration=" + duration +
//                ", releaseDate=" + releaseDate +
//                ", dueDate=" + dueDate +
//                ", earlinessPenalty=" + earlinessPenalty +
//                ", rejectionPenalty=" + rejectionPenalty +
//                ", start=" + startDate +
//                ", finishDate=" + finishDate +
                "}";
    }

    public boolean makesDueDate(int startingTime) {
        return startingTime + duration <= dueDate;
    }

    public double getCost() {
        double cost;
        if(startDate<0) { // Job not scheduled
            cost = rejectionPenalty;
        }
        else {
            cost = earlinessPenalty*(dueDate - finishDate);
        }
        return cost;
    }

    public void setJobSkipped() {
        finishDate = -1;
        startDate = -1;
    }
}
