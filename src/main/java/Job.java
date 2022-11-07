import com.google.gson.annotations.SerializedName;

public class Job {
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

    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", duration=" + duration +
                ", releaseDate=" + releaseDate +
                ", dueDate=" + dueDate +
                ", earlinessPenalty=" + earlinessPenalty +
                ", rejectionPenalty=" + rejectionPenalty +
                "} \n";
    }
}
