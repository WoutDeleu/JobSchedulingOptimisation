import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Job extends Task{
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
        this.finishDate = this.startDate + this.duration;
    }



    @Override
    public String toString() {
        return
                "Job{" +
//                "{" +
                "id=" + id +
                ", duration=" + duration +
                ", releaseDate=" + releaseDate +
                ", dueDate=" + dueDate +
                ", earlinessPenalty=" + earlinessPenalty +
                ", rejectionPenalty=" + rejectionPenalty +
                ", start=" + startDate +
                ", finishDate=" + finishDate +
                "}, \n";
    }

    public boolean makesDueDate(int startingTime) {
        return startingTime + duration <= dueDate;
    }
}
