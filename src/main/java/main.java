import javax.print.attribute.standard.JobName;
import java.sql.SQLOutput;
import java.util.*;


public class main {

    static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static List<Job> jobs = new LinkedList<>();
    private static SetupList setups;
    private static int horizon;
    private static double currentBestValue;
    private static double weight;

    public static void main(String[] args) throws Exception {

//        InputData inputData = InputData.readFile("datasets/eigen_dataset.json");
        InputData inputData = InputData.readFile("datasets/A-100-30.json");
        setups = inputData.generateSetupList();
        jobs = inputData.getJobsSortedReleaseDate();
        List<UnavailablePeriod> unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();
        weight = inputData.getWeightDuration();

        System.out.println(unavailablePeriods);

        calculateInitialSolution(setups, jobs, unavailablePeriods);
        //inputData.printSetupMatrix();

        //illustrateBasicFunctions();
        testLocalSearch(unavailablePeriods);



        double cost = calculateCost();


        // Write to JSON-file
        //OutputData outputData = InputData.generateOutput("A-100-30", cost, scheduledTasks);
        //InputData.writeFile("calculatedSolution/sol_A-100-30.json", outputData);


    }
    /*********************************** TESTING ***********************************/

    public static void illustrateBasicFunctions()  {
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

    public static void testLocalSearch(List<UnavailablePeriod> unavailablePeriods) {
        System.out.println("**************************** Local Search ************************************");
//        localSearchDelete(1, unavailablePeriods);
//        localSearchInsert(1, unavailablePeriods);
//        localSearchSwap(1, unavailablePeriods);


        printScheduledTasks("Original data");
        localSearch(20, unavailablePeriods);
        printScheduledTasks("first local search attempt");
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
            Setup setup = setups.getSetup((Job) previous, job);
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
        System.out.println("start delete job");
        if(scheduledTasks.isEmpty()) return;

        Task task = scheduledTasks.get(index);

        assert task.getClass()==Job.class : "Can't remove a setup with an operation: delete job";
        assert index>=0 : "No negative index allowed";

        waitingJobs.add((Job) task);

        if(index != 0) {
            // Remove job and setup necessary for that job
            scheduledTasks.remove(index);
            scheduledTasks.remove(index-1);

            // Adapt neighbouring setup
            if(index < scheduledTasks.size() && index > 1) {
                Job j1 = (Job) scheduledTasks.get(index-2);
                Job j2 = (Job) scheduledTasks.get(index);
                scheduledTasks.set(index-1, setups.getSetup(j1, j2));
            }
        }
        else {
            // If 2 items or more left, execute remove 2 times, else only one time
            if (scheduledTasks.size()>=2) scheduledTasks.remove(index);
            scheduledTasks.remove(index);
        }

    }

    // Assisting function for makeFeasible()
    public static void operation_deleteSetup(int index) {
        System.out.println("start delete setup");
        if(scheduledTasks.size()<2) return;
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
        if (scheduledTasks.size() > index+1) j2 = (Job) scheduledTasks.get(index+1);
        if (j2 != null) { // j2 == null if the last setup was removed
            Job j1 = (Job) scheduledTasks.get(index-1);
            scheduledTasks.set(index, setups.getSetup(j1, j2));
        }

    }

    public static void operation_insertJob(int index, Job job) {
        System.out.println("start insert job");
        operation_insertJobNoWaitingList(index, job);
        waitingJobs.remove(job);
    }

    public static void operation_insertJobNoWaitingList(int index, Job job) {
        assert index>=0 : "No negative index allowed";

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
                scheduledTasks.set(index-1, setups.getSetup(j1, job));
            }
            else { // Job was inserted after another job
                // Add a setup in between
                Job j1 = (Job) previous;
                scheduledTasks.add(index, setups.getSetup(j1, job));
                index = scheduledTasks.indexOf(job); // index is shifted by insert above
            }
        }

        if(next != null) {
            if (next.getClass() == Setup.class) { // Job was inserted before a setup
                // Adapt next setup to current job
                System.out.println(scheduledTasks.get(index+2).getClass());
                Job j2 = (Job) scheduledTasks.get(index+2);
                scheduledTasks.set(index+1, setups.getSetup(job, j2));
            }
            else { // Job was inserted before another job
                // Add a setup in between
                Job j2 = (Job) next;
                scheduledTasks.add(index+1, setups.getSetup(job, j2));
            }
        }
    }

    public static void operation_swapJobs(int i1, int i2) {
        System.out.println("start swap jobs");

        // Correct for 2 matching indexes
        if (i1 == i2) {
            if (i1==0) {
                if (scheduledTasks.size()>1) i2 += 2;
                else return; // No swap possible of only one job in scheduledTasks
            }
            else i1 -= 2;
        }

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
        currentBestValue = Double.MAX_VALUE;
        LinkedList<Job> jobsToRemove = new LinkedList<>();
        while(i<100) {
//            System.out.println("while loop iteration: "+i);
//            System.out.println("grootte van de wachtlijst "+waitingJobs.size());
//            System.out.println("grootte van de scheduled list "+scheduledTasks.size());
            executeRandomBasicOperation();
//            if(i%x==0){
                LinkedList<Task> oldScheduling = new LinkedList<>(scheduledTasks);
                calculateStartTimes();
                makeFeasibleUPs(0, unavailablePeriods);

                ///////// hier nog feasible inserts functie invoegen
//                System.out.println(waitingJobs.size());
//                for (Job j: waitingJobs) {
//                    feasibleInsert(j);
//                    if(scheduledTasks.contains(j)) jobsToRemove.add(j);
//                }
//                jobsToRemove.forEach(waitingJobs::remove);
//                System.out.println("feasible inserts took place");

                double tempCost = calculateCost();
                if (currentBestValue>tempCost) currentBestValue=tempCost;
                else scheduledTasks = oldScheduling;

//            }
            i++;

        }
    }

    //function to execute one of the basic operations on the temporary solution
    public static void executeRandomBasicOperation(){
        Random rand = new Random();
        switch(rand.nextInt(3)){
            case 0:
                if(scheduledTasks.size() > waitingJobs.size()){
                    operation_deleteJob(randomJobIndex()); break;
                }
            case 1:
                int i1 = randomJobIndex();
                int i2 = randomJobIndex();
                System.out.println("i1: "+i1+", i2 : "+i2);
                operation_swapJobs(i1, i2);
                break;
            case 2: // insert
                if(!waitingJobs.isEmpty()) {
                    int waitingIndex = rand.nextInt(waitingJobs.size());
                    int index = randomJobIndex();
                    System.out.println(index+","+waitingIndex);
                    operation_insertJob(index, waitingJobs.get(waitingIndex));
                }
                break;
        }
        printScheduledTasks();
    }

    public static int randomJobIndex() {
        Random rand = new Random();
        int jobIndex = rand.nextInt(scheduledTasks.size());
        if (!(scheduledTasks.get(jobIndex) instanceof Job)) {
            if(jobIndex==scheduledTasks.size()-1) jobIndex--;
            else jobIndex++;
        }
        return jobIndex;
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
        if (!scheduledTasks.isEmpty()) // add total schedule duration
            cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
    }

    public static void calculateStartTimes() {
        System.out.println("start calculate start times");
        int timer=0;
        LinkedList<Job> jobsToRemove = new LinkedList<>();
        LinkedList<Setup> setupsToRemove = new LinkedList<>();
        for (Task task: scheduledTasks) {
            task.setEarliestStartDate(timer);
            task.calculateFinishDate();
            if (task.isFeasibleDates()) {
                timer = task.getFinishDate()+1;
            }
            else {
                if (task instanceof Job j) {
                    jobsToRemove.add(j);
                }
                else if(task instanceof Setup s) {
                    setupsToRemove.add(s);
                }
                else {
                    throw new IllegalStateException("Klasse klopt niet"+ task.getClass());
                }

            }
        }

        System.out.println("Jobs to remove: "+ jobsToRemove.size()+", Setups to remove: "+ setupsToRemove.size());
        System.out.println("Remove Jobs");
        for (Job job: jobsToRemove) {
            operation_deleteJob(scheduledTasks.indexOf(job));
        }
        System.out.println("Remove Setups");
        for (Setup setup: setupsToRemove) {
            operation_deleteSetup(scheduledTasks.indexOf(setup));
        }
        System.out.println("End of calculate start times");
    }

    public static boolean checkJobFeasible(Job job){
        return job.getReleaseDate() > job.getStartDate() + job.getDuration();
    }

    public static void feasibleInsert(Job job) {
        //alle jobs overlopen behalve de laatste
        for (int i = 0; i < scheduledTasks.size()-2; i+=2) {
            Job j1 = (Job) scheduledTasks.get(i);
            Job j2 = (Job) scheduledTasks.get(i+2);

            if(checkIfInsertPossible(j1, j2, job)){
                int index = scheduledTasks.indexOf(j2);
                operation_insertJobNoWaitingList(index, job);
                if(scheduledTasks.get(index)==job) {
                    System.out.println("job feasibly inserted");
                    //calculate start- & finishTime for new setups around inserted job
                    //and for the job itself
                    Setup s1 = (Setup) scheduledTasks.get(index-1);
                    s1.setStartDate(scheduledTasks.get(index-2).getFinishDate()+1);
                    s1.calculateFinishDate();

                    job.setStartDate(s1.getFinishDate()+1);
                    job.calculateFinishDate();

                    Setup s2 = (Setup) scheduledTasks.get(index+1);
                    s2.setStartDate(scheduledTasks.get(index-2).getFinishDate()+1);
                    s2.calculateFinishDate();
                }
                break;
            }
        }
    }

    public static boolean checkIfInsertPossible(Job j1, Job j2, Job jInsert){
        Setup s1 = setups.getSetup(j1, jInsert);
        Setup s2 = setups.getSetup(jInsert, j2);
        int oldDuration = j2.getStartDate()-j1.getFinishDate();
        int newDuration = s1.getDuration() + jInsert.getDuration() + s2.getDuration();
        return oldDuration >= newDuration;
    }


    /*********************************** I/O ***********************************/

    public static void printScheduledTasks(String comment) {
        System.out.println(comment+":");
        printScheduledTasks();
    }

    public static void printScheduledTasks() {
        if(scheduledTasks.isEmpty()) System.out.println("scheduledTasks is empty");
        int i;
        for(i = 0; i < scheduledTasks.size()-1; i++) {
            System.out.print(i + ": " + scheduledTasks.get(i) + " -> " );
        }
        System.out.println(i + ": " + scheduledTasks.get(i));
    }


}



