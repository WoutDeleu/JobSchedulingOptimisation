import java.util.LinkedList;
import java.util.List;


public class main {

    private static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static List<Job> jobs = new LinkedList<>();
    private static SetupList setups;
    private static int horizon;
    private static int currentBestValue;
    private static double weight;

    public static void main(String[] args) throws Exception {

        InputData inputData = InputData.readFile("datasets/eigen_dataset.json");
        setups = inputData.generateSetupList();
        jobs = inputData.getJobsSortedReleaseDate();
        List<UnavailablePeriod> unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();
        weight = inputData.getWeightDuration();

        calculateInitialSolution(setups, jobs, unavailablePeriods);

        double cost = calculateCost();
        System.out.println("Cost: " + cost);

/**
        // Write to JSON-file
        OutputData outputData = InputData.generateOutput("TOY-20-10", cost, scheduledTasks);
        InputData.writeFile("calculatedSolution/sol_TOY-20-10.json", outputData);
 **/

        illustrateBasicFunctions();
    }

    /**Funciton to illustrate the 3 basic operations**/
    public static void illustrateBasicFunctions() throws Exception {
        System.out.println("****************************Delete job************************************");
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();
        operation_deleteJob(2);
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();

        System.out.println("****************************Delete Setup************************************");
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();
        operation_deleteSetup(1);
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();

        System.out.println("****************************Insert************************************");
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();
        operation_insertJob(1, jobs.get(1));
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();

        System.out.println("****************************SWAPt************************************");
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();
        operation_swap2(0, 2);
        for(int i = 0; i < scheduledTasks.size(); i++){
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println();
    }
    public static void calculateInitialSolution(SetupList setups, List<Job> jobs, List<UnavailablePeriod> unavailablePeriods) {
        boolean maxReached = false;
        for(Job job : jobs) {
            if(!scheduledTasks.isEmpty()) { // In case of first job, we can't reach previous job
                // Consistently check if horizon is reached
                Job previous = (Job) scheduledTasks.getLast();
                if (previous.getFinishDate() == horizon || maxReached) {
                    queueJob(job);
                }
                else if (previous.getFinishDate() > horizon) {
                    maxReached = true;
                    // Remove last job + the linked setup
                    scheduledTasks.removeLast();
                    scheduledTasks.removeLast();
                    queueJob(previous);
                }
                else {
                    scheduleJob_EndOfLine(job, setups, unavailablePeriods);
                }
            }
            else {
                scheduleJob_EndOfLine(job, setups, unavailablePeriods);
            }
        }
        if(scheduledTasks.getLast().getFinishDate() > horizon) {
            Job j = (Job) scheduledTasks.getLast();
            // Remove last job + the linked setup
            scheduledTasks.removeLast();
            scheduledTasks.removeLast();
            queueJob(j);
        }

        // todo : fill up holes
    }

    private static void scheduleJob_EndOfLine(Job job, SetupList setups, List<UnavailablePeriod> unavailablePeriods) {
        // If scheduled is not empty - there needs to be a setup
        if(!scheduledTasks.isEmpty()) {
            Task previous = scheduledTasks.getLast();

            // Schedule setup
            int startingDateSetup = previous.finishDate+1;
            Setup setup = setups.getSetup(((Job) previous).getId(), job.getId());
            int durationSetup = setup.getDuration();
            for (UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the setup
                // Does setup lie before up?
                if(startingDateSetup + durationSetup-1 < up.getStartDate()) {
                    break; // can be scheduled starting from this startDate
                }
                else {
                    startingDateSetup = Math.max(up.getFinishDate() + 1, startingDateSetup);
                }
            }

            // Schedule job
            // Point from which job can be scheduled
            int startingDateJob = Math.max(job.getReleaseDate(), startingDateSetup + durationSetup);
            int durationJob = job.getDuration();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the job
                if (startingDateJob < up.getStartDate() && startingDateJob + durationJob < up.getStartDate()) break;
                else {
                    startingDateJob = Math.max(up.getFinishDate() + 1, startingDateJob);
                }
            }
            planJobSetup(setup, startingDateSetup, job, startingDateJob);
        }
        // If the list is empty, no need to take setup in account
        else {
            // List is empty
            // Make sure job is scheduled in first possible slot (no unavailable periods)
            int duration = job.getDuration();
            int startingDate = job.getReleaseDate();
            for(UnavailablePeriod up : unavailablePeriods) {
                // Break: when slot is found to plan the job
                if(startingDate < up.getStartDate() && startingDate + duration-1 < up.getStartDate()) break;
                else {
                    startingDate = up.getFinishDate() + 1;
                }
            }
            planJob(job, startingDate);
        }
    }

    private static void planJobSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
        if(job.makesDueDate(startingDateJob)) {
            setup.setStartDate(startingDateSetup);
            setup.calculateFinishDate();
            scheduledTasks.addLast(setup);

            job.setStartDate(startingDateJob);
            job.calculateFinishDate();
            scheduledTasks.addLast(job);
        }
        else {
            queueJob(job);
        }
    }

    // Plan job to waitingJobs or ScheduledTasks based on whether it makes the deadline
    public static void planJob(Job job, int startingDate) {
        if(job.makesDueDate(startingDate)) {
            job.setStartDate(startingDate);
            job.calculateFinishDate();
            scheduledTasks.addLast(job);
        }
        else {
            queueJob(job);
        }
    }

    private static void queueJob(Job job) {
//        job.setJobSkipped();
        waitingJobs.addLast(job);
    }

    public static double calculateCost() {
        double cost = 0;
        for (Job job : jobs) {
            cost += job.getCost();
        }
        cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
    }

    // Runs over schedule
    // Removes scheduled task which clash with the unavailability periods
    public static void makeFeasible(int index, List<UnavailablePeriod> unavailablePeriods) throws Exception {
        for(int i=index; i<scheduledTasks.size(); i++) {
            Task t = scheduledTasks.get(i);
            // If task is scheduled after ALL the unavailable periods
            if(t.getStartDate() > unavailablePeriods.get(unavailablePeriods.size()-1).getFinishDate()){
                break;
            }
            /* if(t.getClass()==Job.class){
                Job job = (Job) t;
                if(job.getStartDate()<job.getReleaseDate()){
                    scheduledTasks.remove(job);
                }
            }*/

            for(UnavailablePeriod u : unavailablePeriods) {
                // If task of task is executed during unavailability period
                // It needs to be taken out (T1 -> S12 -> T2 -> S23 -> T3 ->) => (T1 -> S13 -> T3)
                // Remark: links must be restored (no dangling links)
                if((t.getStartDate() >= u.getStartDate() && t.getStartDate() <= u.getFinishDate()) || (u.getStartDate()<=t.getFinishDate() && t.getFinishDate()<=u.getFinishDate())) {
                    if(t.getClass()==Job.class){
                        operation_deleteJob(i);
                    }
                    if(t.getClass()==Setup.class) {
                        operation_deleteSetup(i);
                    }
                }
            }
        }
    }
    /**Deze functie is enkel en alleen om de makefeasable goe te krijgen...**/
    public static void operation_deleteSetup(int index) throws Exception {
        Task task = scheduledTasks.get(index);
        if(task.getClass()==Job.class) {
            throw new Exception("Can't remove a job with an operation: delete setup");
        }
        if(task.getClass()==Setup.class) {
            //remove setup in unavailable period and job after setup in unavailable period
            // + replace setup remaining after job after setup in unavailable period
            scheduledTasks.remove(index);
            scheduledTasks.remove(index);

            // reconnect links
            Job j = (Job) scheduledTasks.get(index-1);
            // Setup s = (Setup) scheduledTasks.get(i-2);
            ((Setup) scheduledTasks.get(index)).setJob1(j.getId());
        }
    }

    public static void operation_deleteJob(int index) throws Exception {
        Task task = scheduledTasks.get(index);
        if(task.getClass()==Job.class) {
            // remove job and setup necessary for that job
            if(index > 0) {
                scheduledTasks.remove(index);
                scheduledTasks.remove(index-1);

                // reconnect links
                if(index  < scheduledTasks.size()) {
                    Job j = (Job) scheduledTasks.get(index - 2);
                    ((Setup) scheduledTasks.get(index - 1)).setJob1(j.getId());
                }
            }
            else {
                scheduledTasks.remove(index);
                scheduledTasks.remove(index);
            }
        }
        else if(task.getClass()==Setup.class) {
            throw new Exception("Can't remove a setup with an operation: delete job");
        }
    }

    public static void operation_insertJob(int index, Job job) throws Exception {
        if (index <= scheduledTasks.size()) scheduledTasks.add(index, job);
        else {
            scheduledTasks.addLast(job);
            index = scheduledTasks.indexOf(job);
        }
        Task pre = null, nex = null;
        if (index > 0) pre = scheduledTasks.get(index - 1);
        if (scheduledTasks.size() > index+1) nex = scheduledTasks.get(index + 1);

        // Determine wether job is scheduled before or after setup
        // If there was no previous job, no need to add setup before
        if(pre != null) {
            if (pre.getClass() == Setup.class) {
                // scheduled right after setup
                // adjust previous setup to current job
                ((Setup) pre).setJob2(job.getId());
            }
            else scheduledTasks.add(index, setups.getSetup((((Job) pre).getId()), job.getId()));

        }
        if(nex != null) {
            if (nex.getClass() == Setup.class) {
                ((Setup) nex).setJob1(job.getId());
            }
            else scheduledTasks.add(index + 1, setups.getSetup(job.getId(), ((Job) nex).getId()));
        }
    }
    public static void operation_swap2(int i1, int i2) throws Exception {
        int index1 = Math.min(i1, i2);
        int index2 = Math.max(i1, i2);

        Job job1 = (Job) scheduledTasks.get(index1);
        Job job2 = (Job) scheduledTasks.get(index2);

        operation_deleteJob(index2);
        operation_deleteJob(index1);

        operation_insertJob(index1, job2);
        operation_insertJob(index2, job1);

    }
}



