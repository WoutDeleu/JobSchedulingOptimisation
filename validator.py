import sys, os, argparse;
from operator import itemgetter;
import json;
import math;
from enum import Enum;

MAX_INT = 1000000

## Prepare the arguments the program shall receive
def __prepareargs__():
    parser = argparse.ArgumentParser(description='Validate solution files for OMP Scheduling problems.')
    parser.add_argument('-i', nargs=1, type=str, help='Instance file.', required=True)

    ## add mutual exclusion so that ONE of two args are required
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-s', nargs=1, type=str, help='Solution file (JSON).')
    group.add_argument('-t', nargs=1, type=str, help='Solution string in JSON format.')

    return parser

## Parse the input arguments and returns a dictionary with them
def __getargs__(parser):
	args = vars(parser.parse_args())
	return args


## =======================================================================
## Below there are basic class definitions used throughout the validator.
## This includes Period, Job, Solution and Instance.
## =======================================================================

class Period:
    def __init__(self, s=0, e=0):
        self.start = s
        self.end = e

class IJob:
    def __init__(self):
        self.id = 0
        self.dur = 0
        self.period = Period()
        self.ep = 0
        self.rp = 0

class Instance:
    def __init__(self):
        self.name = ""
        self.jobs = list()
        self.setup = list()
        self.horizon = 0
        self.mw = 0
        self.unav = list()

    def load_from_json(self, js):
        self.name = js["name"]
        self.horizon = int(js["horizon"])
        self.dw = float(js["weight_duration"])

        for ju in js["unavailability"]:
            s = int(ju["start"])
            e = int(ju["end"])
            self.unav.append(Period(s,e))

        for jj in js["jobs"]:
            job = self.load_job_json(jj)
            self.jobs.append(job)

        for row in js["setups"]:
            self.setup.append(list())
            for sij in row:
                self.setup[-1].append(int(sij))

    def load_job_json(self, jj):
        job = IJob()

        job.id = int(jj["id"])
        job.dur = int(jj["duration"])
        job.ep = float(jj["earliness_penalty"])
        job.rp = float(jj["rejection_penalty"])
        job.period = Period(int(jj["release_date"]), int(jj["due_date"]))

        return job

class SJob:
    def __init__(self, id, start):
        self.id = id
        self.start = start

class Solution:
    def __init__(self):
        self.inst_name = ""
        self.jobs = list()
        self.setups = dict()
        self.pen_rejec = 0.0
        self.pen_earl = 0.0
        self.pen_dur = 0.0

        self.of_read = 0.0


    def load_from_json(self, js):
        self.inst_name = js["name"]
        self.of_read = float(js["value"])

        for jj in js["jobs"]:
            jid = int(jj["id"])
            jst = int(jj["start"])
            j = SJob(jid, jst)
            self.jobs.append(j)

        for sij in js["setups"]:
            i = int(sij["from"])
            j = int(sij["to"])
            s = int(sij["start"])
            self.setups[ tuple([i,j]) ] = s

    def size(self):
        return len(self.jobs)

    def cost(self):
        return self.pen_dur + self.pen_earl + self.pen_rejec



## =======================================================================
## Now the main class containing the validation methods begins.
## =======================================================================

class Validator:
    def __init__(self):
        self.errors = 0
        self.msg = ""

    def add_error(self, msg):
        self.errors += 1
        self.msg += msg + "\n"

    def has_error(self):
        return (self.errors > 0)

    def get_message(self):
        return self.msg

    def validate_job_time(self, instance, i, t):
        ri = instance.jobs[i].period.start
        di = instance.jobs[i].period.end

        if ri > t:
            self.add_error("Job %d is scheduled to start at time %d which is before its release time %d." % (i, t, ri))
            return False

        e = t + instance.jobs[i].dur-1
        if e > di:
            self.add_error("Job %d starts at %d, has duration %d and is completed at time %d which is after its due time %d." % (i, t, instance.jobs[i].dur, e, di))
            return False

        for u in instance.unav:
            if (u.start <= t and t <= u.end) or (u.start <= e and e <= u.end) or (t <= u.start and u.end <= e):
                self.add_error("Job %d starts at %d, ends at %d which overlaps unavailability period [%d,%d]." % (i, t, e, u.start, u.end))
                return False

        return True

    def validate_setup_time(self, instance, i, j, t):
        dij = instance.setup[i][j]
        e = t + dij-1

        for u in instance.unav:
            if (u.start <= t and t <= u.end) or (u.start <= e and e <= u.end) or (t <= u.start and u.end <= e):
                self.add_error("Setup (%d -> %d) starts at %d, ends at %d which overlaps unavailability period [%d,%d]." % (i, j, t, e, u.start, u.end))
                return False

        return True


    def step_time(self, instance, i, j, sij, t):
        k = 0
        while k < len(instance.unav):
            if t-1 < instance.unav[k].start:
                t += sij
                if t-1 < instance.unav[k].start:
                    return t-1
                else:
                    t = instance.unav[k].end+1
            k += 1

        t += sij
        return t-1

    def validate_time(self, instance, t):
        k = 0
        while k < len(instance.unav):
            if t < instance.unav[k].start:
                return -1
            elif instance.unav[k].start <= t and t <= instance.unav[k].end:
                return k

            k += 1

        return -1

    def validate_sequence(self, instance, solution):
        sched = set()

        time = solution.jobs[0].start
        k = self.validate_time(instance, time) ## checks if the start time of the first job is not within an unavailability period
        if k >= 0:
            self.add_error("Start time of job %d is %d which lies within unavailability period [%d,%d]" % (solution.jobs[0].id, time, instance.unav[k].start, instance.unav[k].end))
            valid = False

        sched.add(solution.jobs[0].id)

        solution.pen_earl = 0.0

        valid = self.validate_job_time(instance, solution.jobs[0].id, time)
        time += instance.jobs[solution.jobs[0].id].dur ## time now points to the slot immediately after the end of the first job
        k = self.validate_time(instance, time-1) ## checks if the time job i finishes is within an unavailability period
        if k >= 0:
            self.add_error("Completion time of job %d is %d which lies within unavailability period [%d,%d]" % (solution.jobs[0].id, time-1, instance.unav[k].start, instance.unav[k].end))
            valid = False

        solution.pen_earl += instance.jobs[solution.jobs[0].id].ep*(instance.jobs[solution.jobs[0].id].period.end - time + 1)

        x = 1
        while x < solution.size():
            i = solution.jobs[x-1].id
            j = solution.jobs[x].id
            dij = instance.setup[i][j]

            if not tuple([i,j]) in solution.setups.keys():
                self.add_error("There is no setup scheduled between jobs %d and %d. A setup must always be scheduled!" % (i, j))
                valid = False

            sij = solution.setups[tuple([i,j])] ## the start time of the setup i -> j
            if sij < time:
                self.add_error("Setup (%d -> %d) has been scheduled to start at %d which is before the earliest available time %d." % (i, j, sij, time))
                valid = False

            tj = solution.jobs[x].start
            if tj <= sij:
                self.add_error("Setup (%d -> %d) has been scheduled to start at %d which is before the scheduled time for the following job %d which starts at %d." % (i, j, sij, j, tj))
                valid = False

            if tj - sij < dij: ## test with time informed in the file
                self.add_error("There is not enough time between jobs %d and %d for a setup which takes %d. There is only %d time left." % (i, j, dij, tj - sij))
                valid = False

            if tj - time < dij: ## test with time computed by validator
                self.add_error("There is not enough time between jobs %d and %d for a setup which takes %d. There is only %d time left." % (i, j, dij, tj - time))
                valid = False

            valid = self.validate_setup_time(instance, i, j, sij)

            time = self.step_time(instance, i, j, dij, time)
            if tj < time:
                self.add_error("Solution estimates start time of job %d at %d but the earliest feasible start time is at %d." % (j, tj, time))
                valid = False

            time = max(time, tj)
            k = self.validate_time(instance, time) ## checks if the time job j starts is not within an unavailability period
            if k >= 0:
                self.add_error("Start time of job %d is %d which lies within unavailability period [%d,%d]" % (j, time, instance.unav[k].start, instance.unav[k].end))
                valid = False

            valid = self.validate_job_time(instance, j, time)
            time += instance.jobs[j].dur
            k = self.validate_time(instance, time-1)
            if k >= 0:
                self.add_error("Completion time of job %d is %d which lies within unavailability period [%d,%d]" % (j, time-1, instance.unav[k].start, instance.unav[k].end))
                valid = False

            solution.pen_earl += instance.jobs[j].ep*(instance.jobs[j].period.end - time + 1)

            ##print("Earliness up to %d : %.2f" % (j, solution.pen_earl))

            if j in sched:
                self.add_error("Job %d is scheduled more than once in solution. Jobs must be scheduled at most once!" % (j))
                valid = False

            sched.add(j)
            x += 1

        duration = time - solution.jobs[0].start
        solution.pen_dur = instance.dw*(duration)
        pen_rej = 0.0
        for job in instance.jobs:
            if not job.id in sched:
                pen_rej += job.rp

        solution.pen_rejec = pen_rej

        return valid

    def validate_setup_sequence(self, solution):
        valid = True
        checked = set()
        i = solution.jobs[0].id
        for x in range(1, len(solution.jobs)):
            j = solution.jobs[x].id
            if not tuple([i,j]) in solution.setups:
                self.add_error("Setup (%d -> %d) should exist but I could not find it in the solution file." % (i, j))
                valid = False
            checked.add(tuple([i,j]))
            i = j

        for sij in solution.setups.keys():
            if not sij in checked:
                self.add_error("Setup (%d -> %d) has been scheduled but it is never used in the solution." % (sij[0], sij[1]))
                valid = False

        return valid

    def validate_solution(self, instance, solution):
        if instance.name != solution.inst_name:
            self.add_error("Instance name '%s' and solution '%s' differ. Are you trying to validate a solution of another instance?" % (instance.name, solution.inst_name))
            return False

        valid = self.validate_setup_sequence(solution)
        if not valid:
            self.add_error("Error in setup sequence (see messages above). I stopped validation early. There could be additional errors which I have not processed.")
            return valid

        valid = self.validate_sequence(instance, solution)

        if abs(solution.cost() - solution.of_read) > 0.01:
            self.add_error("Solution cost reported is %.2f but I computed %.2f (rejection: %.2f + earliness: %.2f + duration: %.2f)" % (solution.of_read, solution.cost(), solution.pen_rejec, solution.pen_earl, solution.pen_dur))
            valid = False

        return valid




## =======================================================================
## Additional utility functions follow below.
## =======================================================================

def load_instance(ipath):
    instance = Instance()

    with open(ipath) as ifile:
        try:
            js = json.load(ifile)
            instance.load_from_json(js)
        except KeyError as e:
            print("Log")
            print("Errors: 1")
            print("Message(s):\nMissing field %s in instance file '%s'. Is it properly formatted?" % (e, ipath))
            print("Result: INPUT ERROR")
            exit(-1)
        except Exception as e:
            print("Log")
            print("Errors: 1")
            print("Message(s):\nI could not read the instance file '%s'. Is it a properly formatted JSON file?" % (ipath))
            print("Result: INPUT ERROR")
            print(e)
            exit(-1)

    return instance


def load_solution_file(spath):
    solution = Solution()
    with open(spath) as sfile:
        try:
            js = json.load(sfile)
            solution.load_from_json(js)
        except KeyError as e:
            print("Log")
            print("Errors: 1")
            print("Message(s):\nMissing field %s in solution file '%s'. Is it properly formatted?" % (e, spath))
            print("Result: INPUT ERROR")
            exit(-1)
        # except:
        #     print("Log")
        #     print("Errors: 1")
        #     print("Message(s):\nI could not read the solution file '%s'. Is it a properly formatted JSON file?" % (spath))
        #     print("Result: INPUT ERROR")
        #     exit(-1)

    return solution


def load_solution_str(sstr):
    solution = Solution()
    try:
        js = json.loads(sstr)
        solution.load_from_json(js)
    except KeyError as e:
        print("Log")
        print("Errors: 1")
        print("Message(s):\nMissing field %s in solution string. Is it properly formatted?" % (e))
        print("Result: INPUT ERROR")
        exit(-1)
    except:
        print("Log")
        print("Errors: 1")
        print("Message(s):\nI could not read the solution string. Is it a properly formatted JSON string?")
        print("Result: INPUT ERROR")
        exit(-1)

    return solution


## =======================================================================
## And finally the 'main' method of this script
## =======================================================================

if __name__ == '__main__':
    ## receive and prepare the arguments
    parser = __prepareargs__()
    args = __getargs__(parser)

    ## read parameters
    ipath = args['i'][0] ## path to instance file

    instance = load_instance(ipath)

    solution = None
    if args['s'] != None:
        solution = load_solution_file(args['s'][0])
    else:
        solution = load_solution_str(args['t'][0])

    validator = Validator()
    validator.validate_solution(instance, solution)

    print("Log %s" % (instance.name))
    print("Value: %.2f" % (solution.cost()))
    print("Errors: %d" % (validator.errors))
    if validator.has_error():
        print("Message(s):\n%s" % (validator.get_message()), end='')
        print("Result: INVALID")
    else:
        print("Message(s):\nOK")
        print("Result: VALID")
