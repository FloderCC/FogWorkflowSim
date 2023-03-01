/**
 * Copyright 2012-2013 University Of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.workflowsim;

import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
import java.util.regex.Pattern;

/**
 This class allows dealing with the task execution restrictions specified on the jobs
 *
 * @author Julio Corona
 * @since WorkflowSim Toolkit 1.0 's TSP extension
 */
public class TSPJobManager {

    /**
     * Dictionary to store the execution status of each job
     */
    private static Map<Integer, TSPJob> jobs = new HashMap<>();

    /**
     * Create a new job
     * @param job_id the job id
     * @param max_parallel_executable_tasks the maximum number of tasks can be executed simultaneously in this job
     * @param tasks_which_can_run_in_parallel the task list can be executed simultaneously in this job
     */

    public static void createTSPJob(Integer job_id, String max_parallel_executable_tasks,  String tasks_which_can_run_in_parallel){
        String[] task_id_list = tasks_which_can_run_in_parallel.substring(2, tasks_which_can_run_in_parallel.length() - 2).split(Pattern.quote("],["));

        ArrayList<ArrayList<Integer>> tasks_which_can_run_in_parallel_parsed = new ArrayList<ArrayList<Integer>>(task_id_list.length);
        for (int i=0; i<task_id_list.length; i++){
            String[] task = task_id_list[i].split(",");
            ArrayList<Integer> tasks_parsed = new ArrayList<Integer>(task.length);

            for (int j=0; j<task.length; j++){
                tasks_parsed.add(Integer.parseInt(task[j]));
            }
            tasks_which_can_run_in_parallel_parsed.add(tasks_parsed);
        }

        //creating the job
        TSPJob tsp_job = new TSPJob(Integer.parseInt(max_parallel_executable_tasks), tasks_which_can_run_in_parallel_parsed);

        jobs.put(job_id, tsp_job);
    }

    /**
     * Check if a task can be executed
     * @param job_id the task's job id
     * @param task_id the task id
     * @return true if it can be executed, false otherwise
     */
    public static boolean canRunTask(Integer job_id, Integer task_id){
        return jobs.get(job_id).canRunTask(task_id);
    }


    /**
     * List of all running tasks
     */
    private static ArrayList<TSPTask> executing_task = new ArrayList<>();

    /**
     * Add a task to the list of running tasks
     * @param task the task to be added
     */
    public static void addTaskRunning(TSPTask task){
        //restringing the execution for considering scheduling restrictions
        jobs.get(task.getJobId()).addTasksRunning(task);

        //restringing the execution for the end of the scheduling restrictions
        task.setTimeStartProcessing(CloudSim.clock);
        executing_task.add(task);
    }

    /**
     * Review the list of tasks that have already finished and delete them from the list of tasks in progress
     * @param time the simulation time
     */
    public static void releaseFinishedTasks(double time){
        Stack<TSPTask> finished_tasks = new Stack<TSPTask>();

        for (TSPTask task: executing_task) {
            if (task.getTaskFinishTime() != -1 && task.getTaskFinishTime() >= time){
                jobs.get(task.getJobId()).removeTasksRunning(task);
                finished_tasks.push(task);
            }
        }

        while (!finished_tasks.isEmpty()){
            executing_task.remove(finished_tasks.pop());
        }
    }

    /**
     * Returns the next time when one of the tasks that are running will end
     */
    private static double getNextFinishTime(){
        double next_finish_time = Double.MAX_VALUE;
        if (executing_task.size() > 0){
            for (int i=0; i < executing_task.size(); i++){
                if (executing_task.get(i).getTaskFinishTime() != -1 &&  executing_task.get(i).getTaskFinishTime() < next_finish_time){
                    next_finish_time = executing_task.get(i).getTaskFinishTime();
                }
            }
            return next_finish_time;
        }
        return next_finish_time;
    }

    /**
     * Returns the next list of jobs that will be ready to be executed at a given clock time
     * @param cloudletList The list of jobs pending to be executed
     * @param clock the clock time
     * @return the list of jobs ready to be executed
     */
    private static ArrayList<Job> getAvailableJobs(List cloudletList, double clock){
        ArrayList<Job> jobs = new ArrayList<>();

        for (int i=0; i < cloudletList.size(); i++){
            Job job = (Job)cloudletList.get(i);
            TSPTask task_info = (TSPTask)job.getTaskList().get(0);
            if (task_info.getTimeSubmission() <= clock && canRunTask(task_info.getJobId(), task_info.getTaskId())){
                jobs.add((Job)cloudletList.get(i));
            }
        }

        return jobs;
    }

    /**
     * Returns the next time when one of the pending jobs will arrive
     * @param cloudletList the list of pending jobs
     */
    private static double getFutureJobsTime(List cloudletList){
        double time = Double.MAX_VALUE;

        for (int i=0; i < cloudletList.size(); i++){
            Job job = (Job)cloudletList.get(i);
            TSPTask task_info = (TSPTask)job.getTaskList().get(0);
            if (task_info.getTimeSubmission() <= time){
                time = task_info.getTimeSubmission();
            }
        }
        return time;
    }

    /**
     * Returns the next jobs that will be able to be executed either due to their submission time or due to simulation
     * restrictions
     * @param cloudletList the list of pending jobs
     * @param clock the clock time
     * @return the time of the next availability and the list of available jobs
     */
    public static Object[] getNextAvailableJobs(List cloudletList, double clock){

        ArrayList<Job> next_available_jobs_to_income = getAvailableJobs(cloudletList, clock);

        if (!next_available_jobs_to_income.isEmpty()){
            return new Object[]{clock, next_available_jobs_to_income};
        }

        double next_finish_time =  getNextFinishTime();
        double future_jobs_time = getFutureJobsTime(cloudletList);

        double new_time = Math.min(next_finish_time, future_jobs_time);

        releaseFinishedTasks(new_time);

        return getNextAvailableJobs(cloudletList, new_time);
    }
}
