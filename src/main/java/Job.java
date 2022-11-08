import com.google.gson.annotations.SerializedName;

public class Job extends Schedulable{
    private int id;
    private int duration;
    @SerializedName("release_date")
    private int releaseDate;
    @SerializedName("due_date")
    private int dueDate;
    @SerializedName("earliness_penalty")
    private double earlinessPenalty;
    @SerializedName("rejectin_penalty")
    private double rejectionPenalty;

    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", duration=" + duration +
                ", release_date=" + releaseDate +
                ", due_date=" + dueDate +
                ", earliness_penalty=" + earlinessPenalty +
                ", rejection_penalty=" + rejectionPenalty +
                "} \n";
    }
}
