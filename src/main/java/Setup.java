public class Setup extends Task{

    private int duration;
    private int job1;
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

    public void calculateFinishDate() {
        finishDate = startDate + duration;
    }

    @Override
    public String toString() {
        return "Setup{" +
                "duration=" + duration +
                ", job1=" + job1 +
                ", job2=" + job2 +
                "} \n";
    }
}
