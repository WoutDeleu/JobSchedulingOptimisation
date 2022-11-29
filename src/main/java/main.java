import java.util.*;

public class main {

    private static LinkedList<Job> waitingJobs = new LinkedList<>();
    private static LinkedList<Task> scheduledTasks = new LinkedList<>();
    private static LinkedList<Task> bestSchedule = new LinkedList<>();

    private static List<Job> jobs = new LinkedList<>();
    private static SetupList setups;
    private static List<UnavailablePeriod> unavailablePeriods;

    private static int horizon;
    private static double weight;
    private static double currentBestValue;
//    private static double currentValue;


    public static void main(String[] args) {

        InputData inputData = InputData.readFile("datasets/B-400-90.json");
        setups = inputData.generateSetupList();
        jobs = inputData.getJobsSortedReleaseDate();
        unavailablePeriods = inputData.getUnavailablePeriods();
        horizon = inputData.getHorizon();
        weight = inputData.getWeightDuration();

        calculateInitialSolution(setups, jobs);
        currentBestValue = calculateCost();

        localSearch(0);

        assert(isOrderCorrect()): "Fault in order (job - setup - job)";
        assert(noOverlapUP()): "Fault with overlap in unavailablePeriods";

        // Write to JSON-file
        OutputData outputData = InputData.generateOutput(inputData.getName(), currentBestValue, scheduledTasks);
        InputData.writeFile("calculatedSolution/sol_" + inputData.getName()+".json", outputData);
    }


    /*********************************** INITIAL SOLUTION ***********************************/
    public static void calculateInitialSolution(SetupList setups, List<Job> jobs) {
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
                    scheduleAndPlaceJob_EOL(job, setups);
                }
            }
            else {
                scheduleAndPlaceJob_EOL(job, setups);
            }
        }
        if(scheduledTasks.getLast().getFinishDate() > horizon) {
            Job j = (Job) scheduledTasks.getLast();
            // Remove last job + the linked setup
            scheduledTasks.removeLast();
            scheduledTasks.removeLast();
            queueJob(j);
        }
        currentBestValue = calculateCost();
    }
    // EOL = end of list
    private static void scheduleAndPlaceJob_EOL(Job job, SetupList setups) {
        // If scheduled is not empty - there needs to be a setup
        if(!scheduledTasks.isEmpty()) {
            Task previous = scheduledTasks.getLast();

            // Schedule setup
            int startingDateSetup = previous.finishDate+1;
            Setup setup = setups.getSetup((Job) previous, job);
            startingDateSetup = calculateFittingStartTime_UP(startingDateSetup, setup);

            // Schedule job
            int startingDateJob = Math.max(job.getReleaseDate(), startingDateSetup + setup.getDuration());
            startingDateJob = calculateFittingStartTime_UP(startingDateJob, job);

            planAndPlaceJobWithSetup(setup, startingDateSetup, job, startingDateJob);
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
            planAndPlaceJob(job, startingDate);
        }
    }
    private static void planAndPlaceJobWithSetup(Setup setup, int startingDateSetup, Job job, int startingDateJob) {
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
    public static void planAndPlaceJob(Job job, int startingDate) {
        if(job.makesDueDate(startingDate)) {
            job.setStartDate(startingDate);
            job.calculateFinishDate();
            scheduledTasks.addLast(job);
        }
        else {
            queueJob(job);
        }
    }
    private static int calculateFittingStartTime_UP(int startingDate, Task task) {
        int duration = task.getDuration();
        for(UnavailablePeriod up : unavailablePeriods) {
            if (startingDate + duration < up.getStartDate()) break;
            else startingDate = Math.max(up.getFinishDate() + 1, startingDate);
        }
        return startingDate;
    }
    private static void queueJob(Job job) {
        waitingJobs.add(job);
        job.setJobSkipped();
    }
    /*********************************** INITIAL SOLUTION ***********************************/



    /*********************************** BASIC OPERATIONS ***********************************/
    public static void operation_deleteJob(int index) {

        if(scheduledTasks.isEmpty()) return;

        Task task = scheduledTasks.get(index);
        System.out.println("DELETE "+task+" on index " + index);

        assert task.getClass()==Job.class : "Can't remove a setup with an operation: delete job";
        assert index>=0 : "No negative index allowed";

        queueJob((Job) task);

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
    public static void operation_deleteJobUsingObject(Job job){
        if (scheduledTasks.contains(job)) {
            operation_deleteJob(scheduledTasks.indexOf(job));
        }
        else {
            System.out.println(job+" was already deleted");
        }
    }
    // Assisting function for makeFeasible()
    public static void operation_deleteSetup(int index) {
        if(scheduledTasks.size()<2) return;
        Task task = scheduledTasks.get(index);
        System.out.println("DELETE "+task+" on index " + index);

        assert task.getClass()==Setup.class : "Can't remove a job with an operation: delete setup";
        assert index>0 : "No negative index or index zero allowed";

        // Remove setup and it's corresponding job
        scheduledTasks.remove(index);
        Job job = (Job) scheduledTasks.get(index);
        queueJob(job);
        scheduledTasks.remove(job);

        // Adapt neighbouring setup
        Job j2 = null;
        if (scheduledTasks.size() > index+1) j2 = (Job) scheduledTasks.get(index+1);
        if (j2 != null) { // j2 == null if the last setup was removed
            Job j1 = (Job) scheduledTasks.get(index-1);
            scheduledTasks.set(index, setups.getSetup(j1, j2));
        }
    }
    public static void operation_deleteSetupWithObject(Setup setup) {
        if (scheduledTasks.contains(setup)) {
            operation_deleteSetup(scheduledTasks.indexOf(setup));
        }
        else {
            System.out.println(setup+" was already deleted");
        }
    }
    public static void operation_insertJob(int index, Job job) {
/*        System.out.println("Insert Job "  + job.getId() + " on position " + index);
        assert !scheduledTasks.contains(job) : "The job was already scheduled";
        operation_insertJobNoWaitingList(index, job);
        waitingJobs.remove(job);
*/
        // todo : this is a work around... doesnt fix the issue
        if(!scheduledTasks.contains(job)) {
            System.out.println("Insert Job "  + job.getId() + " on position " + index);
            operation_insertJobNoWaitingList(index, job);
        }
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
        System.out.println("Swap jobs on " + i1 + ", " + i2);

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
    /*********************************** BASIC OPERATIONS ***********************************/



    /*********************************** FEASABLE WITH STARTTIMES ***********************************/
    // Calculates all the starting times and makes the solution feasible
    public static void finalizeSchedule_ascending() {
        boolean maxReached = false;
        List<Task> toRemove = new ArrayList<Task>();
        int previous_index = - 1;
        for(Task t : scheduledTasks) {
            int index = scheduledTasks.indexOf(t);
            if(previous_index >= 0) {
                Task previous = scheduledTasks.get(previous_index);
                if (previous.getFinishDate() == horizon || maxReached) {
                    if(previous instanceof Setup) {
                        toRemove.add(previous);
                    }
                    toRemove.add(t);
                }
                else if (previous.getFinishDate() > horizon) {
                    maxReached = true;
                    // Remove last task with everything connected to it
                    if(previous instanceof Job) {
                        // Remove the setup before the previous job
                        toRemove.add(scheduledTasks.get(previous_index-1));
                    }
                    toRemove.add(previous);
                    toRemove.add(t);
                }
                else {
                    previous_index = fitJob_EOL(t, previous_index);
                }
            }
            else {
                previous_index = fitJob_EOL(t, previous_index);
            }
        }
        if(previous_index == scheduledTasks.size() -2) previous_index++;
        Task lastScheduled = scheduledTasks.get(previous_index);
        // No need to schedule a setup whe there is no job following
        if(lastScheduled instanceof Setup) {
            toRemove.add(lastScheduled);
            previous_index--;
            lastScheduled = scheduledTasks.get(previous_index);
        }
        if(lastScheduled.getFinishDate() > horizon) {
            toRemove.add(lastScheduled);
            toRemove.add(scheduledTasks.get(previous_index-1));
        }
        cleanUpSchedule(toRemove);
    }
    public static void finalizeSchedule_descending() {

    }
    // EOL = end of list
    private static int fitJob_EOL(Task task, int previous_index) {
        int startingDate;
        if(previous_index >= 0) {
            Task previous = scheduledTasks.get(previous_index);
            startingDate = previous.getFinishDate() + 1;
        }
        else {
            startingDate = 0;
        }
        if(task instanceof Job) startingDate = Math.max(startingDate, ((Job) task).getReleaseDate());
        startingDate = calculateFittingStartTime_UP(startingDate, task);

        return scheduleTask(task, startingDate, previous_index);
    }
    private static int scheduleTask(Task t, int startingDate, int previous_index) {
        int index =  scheduledTasks.indexOf(t);
        if(t instanceof Setup) {
            t.setStartDate(startingDate);
            t.calculateFinishDate();
            return index;
        }
        else if(((Job) t).makesDueDate(startingDate)) {
            t.setStartDate(startingDate);
            t.calculateFinishDate();
            return index;
        }
        else {
            queueJob((Job) t);
            return previous_index;
        }
    }
    public static double calculateCost() {
        double cost = 0;
        for (Job job : jobs) cost += job.getCost();
        if (!scheduledTasks.isEmpty()) // add total schedule duration
            cost += (scheduledTasks.getLast().getFinishDate()-scheduledTasks.getFirst().getStartDate()+1)*weight;
        return (double) Math.round(cost * 100) / 100;
    }
    /*********************************** FEASABLE WITH STARTTIMES ***********************************/



    /*********************************** LOCAL SEARCH ***********************************/
    public static void localSearch(int x) {
        // x is a parameter to tweak the amount of operations that will be executed before we calculate the starttimes
        // and validate if the new scheduling is acceptable / better than the original scheduling
        int i=0;
        LinkedList<Job> jobsToRemove = new LinkedList<>();
         while(i<10000) {
             LinkedList<Task> old_Scheduling = deepClone(scheduledTasks);
             LinkedList<Job> old_Waiting = deepCloneJobs(waitingJobs);
             executeRandomBasicOperation();
             assert isOrderCorrect() : "Order is fucked up";
//            if(i%x==0){


               // TODO feasible inserts
//                System.out.println(waitingJobs.size());
//                for (Job j: waitingJobs) {
//                    feasibleInsert(j);
//                    if(scheduledTasks.contains(j)) jobsToRemove.add(j);
//                }
//                jobsToRemove.forEach(waitingJobs::remove);
//                System.out.println("feasible inserts took place");


             finalizeSchedule_ascending();
                double tempCost = calculateCost();
                if (currentBestValue>tempCost) currentBestValue=tempCost;
                else {
                    System.out.println("Reset old");
                    scheduledTasks = deepClone(old_Scheduling);
                    waitingJobs = deepCloneJobs(old_Waiting);
                }

//            }
            i++;
//            printScheduledTasks("local search, cost="+currentBestValue);

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
//                System.out.print("Swapping: ");
//                System.out.println("i1: "+i1+", i2 : "+i2);
                operation_swapJobs(i1, i2);
                break;
            case 2: // insert
                if(!waitingJobs.isEmpty()) {
                    int waitingIndex = rand.nextInt(waitingJobs.size());
                    int index = randomJobIndex();
//                    System.out.print("Insertion: ");
//                    System.out.println(index+"," + waitingIndex);
                    operation_insertJob(index, waitingJobs.get(waitingIndex));
                }
                break;
        }
//        printScheduledTasks();
    }
    public static int randomJobIndex() {
        Random rand = new Random();
        int jobIndex = rand.nextInt(scheduledTasks.size());
        if (!(scheduledTasks.get(jobIndex) instanceof Job)) {
            if(jobIndex==scheduledTasks.size()-1) jobIndex--;
            else jobIndex++;
        }
        assert jobIndex%2==0 : "Job index not even";
        return jobIndex;
    }
    // Removes scheduled task which clash with the unavailability periods
/*    public static void makeFeasibleUPs(int start) {
        LinkedList<Task> toRemove = new LinkedList<>();
        for(int i=start; i<scheduledTasks.size(); i++) {
            Task task = scheduledTasks.get(i);

            if(!task.isFeasibleUPs(unavailablePeriods)) {
                toRemove.add(task);
            }
        }
        for (Task t : toRemove) {
            if (t instanceof Job j) operation_deleteJobUsingObject(j);
            else if (t instanceof Setup s) operation_deleteSetupWithObject(s);
            else throw new IllegalStateException("Class klopt niet");
        }
    }
    // Removes tasks that are not date feasible
    public static void calculateStartTimes() {
        int timer = 0;
        LinkedList<Task> toRemove = new LinkedList<>();
        for (Task task: scheduledTasks) {
            task.setEarliestStartDate(timer);
            task.calculateFinishDate();
            if (task.isFeasibleDates()) timer = task.getFinishDate()+1;
            else toRemove.add(task);
        }
        for (Task t : toRemove) {
            if (t instanceof Job j) operation_deleteJobUsingObject(j);
            else if (t instanceof Setup s) operation_deleteSetupWithObject(s);
            else throw new IllegalStateException("Class is not right");
        }
//        System.out.println("End of calculate start times");
    }*/
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
    public static boolean checkIfInsertPossible(Job j1, Job j2, Job jInsert) {
        Setup s1 = setups.getSetup(j1, jInsert);
        Setup s2 = setups.getSetup(jInsert, j2);
        int oldDuration = j2.getStartDate()-j1.getFinishDate();
        int newDuration = s1.getDuration() + jInsert.getDuration() + s2.getDuration();
        return oldDuration >= newDuration;
    }
    /*********************************** LOCAL SEARCH ***********************************/



    /*********************************** PRINTS ***********************************/
    public static void printScheduledTasks(String comment) {
        System.out.print(comment+": \t");
        printScheduledTasks();
    }
    public static void printScheduledTasks() {
        if(scheduledTasks.isEmpty()) System.out.println("scheduledTasks is empty");
        int i;
        for(i = 0; i < scheduledTasks.size()-1; i++) {
            System.out.print(i + ": " + scheduledTasks.get(i) + " ->\t" );
        }
        System.out.println(i + ": " + scheduledTasks.get(i));
    }
    /*********************************** PRINTS ***********************************/



    /*********************************** TESTING ***********************************/
    public static void illustrateBasicFunctions()  {
        //testD eleteJob();
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
    // Function checks if the structure job - setup - job is preserved
    public static boolean isOrderCorrect() {
        assert scheduledTasks.get(0).getClass() == Job.class;
        boolean previousWasSetup = true;
        boolean previousWasJob = false;
        for(Task t : scheduledTasks) {
            if(previousWasSetup) {
                if(t.getClass() == Job.class) {
                    previousWasJob = true;
                    previousWasSetup = false;
                }
                else {
                    System.out.println("Fault Setup: "  + ((Setup) t).getJob1() + " -> " + ((Setup) t).getJob2());
                    return false;
                }
            }
            else if(previousWasJob) {
                if(t.getClass() == Setup.class) {
                    previousWasSetup = true;
                    previousWasJob = false;
                }
                else {
                    System.out.println("Fault Job: Id="  + ((Job) t).getId() + ", index=" + scheduledTasks.indexOf(t));
                    return false;
                }
            }
        }
        return true;
    }
    public static boolean noOverlapUP() {
        for(UnavailablePeriod up : unavailablePeriods) {
            for(Task t : scheduledTasks) {
                if(t.getStartDate() <= up.getStartDate() && t.getFinishDate() >= up.getStartDate()) {
                    if(t instanceof Job)System.out.println("Fault with job " + ((Job) t).getId());
                    else System.out.println("Fault with Setup " + ((Setup) t).getJob1() + " -> " + ((Setup) t).getJob2());
                    return false;
                }
                if(t.getStartDate() >= up.getStartDate() && t.getFinishDate() <= up.getFinishDate()) {
                    if(t instanceof Job)System.out.println("Fault with job " + ((Job) t).getId());
                    else System.out.println("Fault with Setup " + ((Setup) t).getJob1() + " -> " + ((Setup) t).getJob2());
                    return false;
                }
                if(t.getStartDate() <= up.getStartDate() && t.getFinishDate() >= up.getFinishDate()) {
                    if(t instanceof Job)System.out.println("Fault with job " + ((Job) t).getId());
                    else System.out.println("Fault with Setup " + ((Setup) t).getJob1() + " -> " + ((Setup) t).getJob2());
                    return false;
                }
            }
        }
         return true;
    }
    /*********************************** TESTING ***********************************/


    /*********************************** UITL ***********************************/
    public static LinkedList<Task> deepClone(LinkedList<Task> tasks) {
        LinkedList<Task> clone = new LinkedList<Task>();
        for(Task t : tasks) {
            clone.add(t.clone());
        }
        return clone;
    }
    public static LinkedList<Job> deepCloneJobs(LinkedList<Job> jobs) {
        LinkedList<Job> clone = new LinkedList<Job>();
        for(Job t : jobs) {
            clone.add(t.clone());
        }
        return clone;
    }
    private static void cleanUpSchedule(List<Task> toRemove) {
        for(Task t : toRemove) {
            scheduledTasks.remove(t);
            if(t instanceof Job) {
                queueJob((Job) t);
            }
        }
        assert isOrderCorrect() : "Order is fucked up";
    }
    /*********************************** UTIL ***********************************/
}


