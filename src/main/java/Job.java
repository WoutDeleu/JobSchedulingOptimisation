import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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

    public Job(int startDate, int finishDate, int id, int duration, int releaseDate, int dueDate, double earlinessPenalty, double rejectionPenalty) {
        super(startDate, finishDate);
        this.id = id;
        this.duration = duration;
        this.releaseDate = releaseDate;
        this.dueDate = dueDate;
        this.earlinessPenalty = earlinessPenalty;
        this.rejectionPenalty = rejectionPenalty;
    }

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

    public boolean isFeasibleDates() {
        return startDate>=releaseDate && finishDate<=dueDate;
    }
    public void setStartDate(int startDate) {
        assert startDate >= 0 : "Not a valid startDate";
        assert startDate >= releaseDate : "Cannot schedule before releaseDate";
        assert startDate <= dueDate : "Cannot schedule after dueDate";

        this.startDate = startDate;
    }
    public void setEarliestStartDate(int startDate) {
        this.startDate =  Math.max(startDate, releaseDate);
    }

    @Override
    public Job clone() {
        return new Job(this.startDate, this.finishDate, this.id, this.duration, this.releaseDate, this.dueDate, this.earlinessPenalty, this.rejectionPenalty);
    }

    public int getLatestStartDate() {
        return dueDate - duration;
    }

    public boolean makesDueDate(int startingTime) {
        return startingTime + duration <= dueDate;
    }

    public boolean makesReleaseDate(int startingTime) {
        return startingTime >= releaseDate;
    }

    public boolean makesHorizon(int startingTime, int horizon) {
        int finishDate = startingTime+duration-1;
        return finishDate <= horizon;
    }

    public double getCost() {
        if(startDate<0) return rejectionPenalty;
        else return earlinessPenalty*(dueDate - finishDate);
    }

    public double getScheduledCost() {
        return earlinessPenalty*(dueDate - finishDate);
    }

    public double getWaitingCost() {
        return rejectionPenalty;
    }

    public void setJobSkipped() {
        finishDate = -1;
        startDate = -1;
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
                ", start=" + startDate +
//                ", finishDate=" + finishDate +
                        "}";
    }

    public double getScheduledCostTemp(int startDate_temp) {
        int finishDate_temp = startDate_temp+getDuration()-1;
        return earlinessPenalty*(dueDate - finishDate_temp);
    }
}
