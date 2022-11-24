import java.sql.SQLOutput;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;


public class main {

    static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static List<Job> jobs = new LinkedList<>();
    private static SetupList setups;
    private static int horizon;
    private static int currentBestValue;
    private static double weight;

    public static void main(String[] args) throws Exception {

//        InputData inputData = InputData.readFile("datasets/eigen_dataset.json");
        InputData inputData = InputData.readFile("datasets/A-100-30.json");
        setups = inputData.generateSetupList();
        jobs = inputData.getJobsSortedReleaseDate();
        List<UnavailablePeriod> unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();
        weight = inputData.getWeightDuration();

        calculateInitialSolution(setups, jobs, unavailablePeriods);
        //inputData.printSetupMatrix();

        //illustrateBasicFunctions();

        double cost = calculateCost();

        // Write to JSON-file
        OutputData outputData = InputData.generateOutput("A-100-30", cost, scheduledTasks);
        InputData.writeFile("calculatedSolution/sol_A-100-30.json", outputData);


    }
    /*********************************** TESTING ***********************************/

    public static void illustrateBasicFunctions() throws Exception {
        //testDeleteJob();
        testDeleteSetup();
        testInsertJob();
        testSwapJobs();
    }

    public static void testDeleteJob() {
        System.out.println("**************************** Delete job ************************************");
        printScheduledTasks("Original data");
        operation_deleteJob(2);
        printScheduledTasks("Deleted job in the middle");
        operation_deleteJob(4);
        printScheduledTasks("Deleted last job");
        operation_deleteJob(0);
        printScheduledTasks("Deleted first job");
    }

    public static void testDeleteSetup() {
        System.out.println("*************************** Delete setup ***********************************");
        printScheduledTasks("Original data");
        operation_deleteSetup(3);
        printScheduledTasks("Deleted setup in the middle");
        operation_deleteSetup(3);
        printScheduledTasks("Deleted last setup");
        operation_deleteSetup(1);
        printScheduledTasks("Deleted first setup");
    }

    public static void testInsertJob() {
        System.out.println("****************************** Insert **************************************");
        printScheduledTasks("Original data");
        operation_insertJob(10, waitingJobs.get(0));
        printScheduledTasks("Inserted job at the end");
        operation_insertJob(0, waitingJobs.get(0));
        printScheduledTasks("Inserted job at the start");
        operation_insertJob(3, waitingJobs.get(0));
        printScheduledTasks("Inserted job in the middle");
    }

    public static void testSwapJobs() {
        System.out.println("******************************* Swap ***************************************");
        printScheduledTasks("Original data");
        operation_swapJobs(0, 6);
        printScheduledTasks("Swapped first and last");
        operation_swapJobs(2, 4);
        printScheduledTasks("Swapped second and third");
    }


    /*********************************** INITIAL SOLUTION ***********************************/

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
        waitingJobs.addLast(job);
    }



    /*********************************** BASIC OPERATIONS ***********************************/

    public static void operation_deleteJob(int index) {
        Task task = scheduledTasks.get(index);

        assert task.getClass()==Job.class : "Can't remove a setup with an operation: delete job";
        assert index>=0 : "No negative index allowed";

        waitingJobs.add((Job) task);

        if(index != 0) {
            // Remove job and setup necessary for that job
            scheduledTasks.remove(index);
            scheduledTasks.remove(index-1);

            // Adapt neighbouring setup
            if(index < scheduledTasks.size()) {
                Job j1 = (Job) scheduledTasks.get(index-2);
                Job j2 = (Job) scheduledTasks.get(index);
                scheduledTasks.set(index-1, setups.getSetup(j1.getId(), j2.getId()));
            }
        }
        else {
            scheduledTasks.remove(index);
            scheduledTasks.remove(index);
        }

    }

    // Assisting function for makeFeasible()
    public static void operation_deleteSetup(int index) {
        Task task = scheduledTasks.get(index);

        assert task.getClass()==Setup.class : "Can't remove a job with an operation: delete setup";
        assert index>0 : "No negative index or index zero allowed";

        // Remove setup and it's corresponding job
        scheduledTasks.remove(index);
        Job job = (Job) scheduledTasks.get(index);
        waitingJobs.add(job);
        scheduledTasks.remove(job);

        // Adapt neighbouring setup
        Job j2 = null;
        if (scheduledTasks.size() > index) j2 = (Job) scheduledTasks.get(index+1);
        if (j2 != null) { // j2 == null if the last setup was removed
            Job j1 = (Job) scheduledTasks.get(index-1);
            scheduledTasks.set(index, setups.getSetup(j1.getId(), j2.getId()));
        }

    }

    public static void operation_insertJob(int index, Job job) {
        assert index>=0 : "No negative index allowed";

        waitingJobs.remove(job);

        // Insert the job
        if (index <= scheduledTasks.size()){
            scheduledTasks.add(index, job);
        }
        else {
            scheduledTasks.addLast(job);
            index = scheduledTasks.indexOf(job);
        }

        // Adapt neighbouring setups
        Task previous = null;
        Task next = null;
        if (index != 0) previous = scheduledTasks.get(index-1);
        if (scheduledTasks.size() > index+1) next = scheduledTasks.get(index + 1);

        if(previous != null) { // If there was no previous job, no need to add setup before
            if (previous.getClass() == Setup.class) { // Job was inserted after a setup
                // Adapt previous setup to current job
                Job j1 = (Job) scheduledTasks.get(index-2);
                scheduledTasks.set(index-1, setups.getSetup(j1.getId(), job.getId()));
            }
            else { // Job was inserted after another job
                // Add a setup in between
                Job j1 = (Job) previous;
                scheduledTasks.add(index, setups.getSetup(j1.getId(), job.getId()));
                index = scheduledTasks.indexOf(job); // index is shifted by insert above
            }
        }

        if(next != null) {
            if (next.getClass() == Setup.class) { // Job was inserted before a setup
                // Adapt next setup to current job
                Job j2 = (Job) scheduledTasks.get(index+2);
                scheduledTasks.set(index+1, setups.getSetup(job.getId(), j2.getId()));
            }
            else { // Job was inserted before another job
                // Add a setup in between
                Job j2 = (Job) next;
                scheduledTasks.add(index+1, setups.getSetup(job.getId(), j2.getId()));
            }
        }
    }

    public static void operation_swapJobs(int i1, int i2) {
        assert i1!=i2 : "Indices cannot be equal for swap operation";
        assert i1%2==0 && i2%2==0 : "Indices must be even numbers to get Jobs and not Setups";

        int index1 = Math.min(i1, i2);
        int index2 = Math.max(i1, i2);

        Job job1 = (Job) scheduledTasks.get(index1);
        Job job2 = (Job) scheduledTasks.get(index2);

        operation_deleteJob(index2);
        operation_deleteJob(index1);

        operation_insertJob(index1, job2);
        operation_insertJob(index2, job1);
    }

    /*********************************** LOCAL SEARCH ***********************************/
    public static void localSearch(int x, List<UnavailablePeriod> unavailablePeriods) {
        // x is a parameter to tweak the amount of operations that will be executed before we calculate the starttimes
        // and validate if the new scheduling is acceptable / better than the original scheduling
        int i=0;
        LinkedList<Task> scheduledTasksCopy = new LinkedList<>(scheduledTasks);
        double lastCost = Double.MAX_VALUE;
        while(true) {
            if(i%x==0){
                calculateStartTimes();
                makeFeasibleUPs(0, unavailablePeriods);

                ///////// hier nog feasible inserts functie invoegen

                double tempCost = calculateCost();
                if(lastCost>tempCost) {
                    scheduledTasksCopy = new LinkedList<>(scheduledTasks);
                    lastCost=tempCost;
                }
                else{
                    scheduledTasks = new LinkedList<>(scheduledTasksCopy);
                }

            }
            executeRandomBasicOperation();
            i++;

        }
    }

    //function to execute one of the basic operations on the temporary solution
    public static void executeRandomBasicOperation(){
        Random operation = new Random();
        int jobIndex = 0;
        Random job = new Random();
        switch(operation.nextInt(3)){
            case 0:
                jobIndex = job.nextInt(scheduledTasks.size());
                if(jobIndex%2==1) jobIndex++;
                operation_deleteJob(jobIndex);
                break;
            case 1:
                jobIndex = job.nextInt(waitingJobs.size());
                operation_insertJob(jobIndex, waitingJobs.get(jobIndex));
                break;
            case 2:
                jobIndex = job.nextInt(scheduledTasks.size());
                if(jobIndex%2 == 1) jobIndex++;
                int job2 = job.nextInt(scheduledTasks.size());
                if (job2%2 == 1) job2++;
                if (job2 == jobIndex) job2 = job2+2;
                operation_swapJobs(jobIndex, job2);
                break;
        }
    }

    // Removes scheduled task which clash with the unavailability periods
    public static void makeFeasibleUPs(int start, List<UnavailablePeriod> unavailablePeriods) {

        for(int i=start; i<scheduledTasks.size(); i++) {
            Task task = scheduledTasks.get(i);

            if(!task.isFeasibleUPs(unavailablePeriods)) {
                if(task.getClass()==Job.class) operation_deleteJob(i);
                else operation_deleteSetup(i);
            }
        }
    }

    public static double calculateCost() {
        double cost = 0;
        for (Job job : jobs) cost += job.getCost();
        cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
    }

    public static void calculateStartTimes() {
        int timer=0;
        for (Task task: scheduledTasks) {
            task.setStartDate(timer); //todo overwrite en check of wel na releaseDate ligt
            if(task.isFeasibleDates()){
                task.calculateFinishDate();
                timer = task.getFinishDate()+1;
            }
            else {
                operation_deleteJob(scheduledTasks.indexOf(task));
            }
        }
    }

    public static boolean checkJobFeasible(Job job){
        return job.getReleaseDate() > job.getStartDate() + job.getDuration();
    }


    /*********************************** I/O ***********************************/

    public static void printScheduledTasks(String comment) {
        System.out.println(comment+":");
        printScheduledTasks();
    }

    public static void printScheduledTasks() {
        int i;
        for(i = 0; i < scheduledTasks.size()-1; i++) {
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println(i + ": " + scheduledTasks.get(i));
    }


}



