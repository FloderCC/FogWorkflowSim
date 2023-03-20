package org.workflowsim.scheduling;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.workflowsim.*;
import org.workflowsim.utils.TSPEnvHelper;
import org.workflowsim.utils.TSPJobManager;
import org.workflowsim.utils.TSPSocketClient;

import java.util.Iterator;
import java.util.List;

/**
 * Strategy method for task scheduling
 *
 * @since TSP Extension 1.0
 * @author Julio Corona
 */
public class TSPSchedulingAlgorithm extends TSPBaseStrategyAlgorithm {


    public TSPSchedulingAlgorithm()
    {
        super();
    }

    /**
     * Scheduler regarding the constraints
     */
    @Override
    public void run() {

        System.out.println("Scheduling...");

        TSPJobManager.releaseFinishedTasks(CloudSim.clock);

        List cloudletList = getReadyForScheduleCloudletList();

        while (!cloudletList.isEmpty()){
            //parse the tasks and the devices' status to a vector
            Long[] state = TSPEnvHelper.parseStateWithTasksAndServers(cloudletList, getNotMobileVmList());

            int action = TSPSocketClient.askForDecision(null, state)[0];

            System.out.println("Action received from Python:" + action);

            if (action == -1) { // means there was no server with enough resources for any task
                break;
            }
            else {
                Cloudlet cloudlet = (Cloudlet) cloudletList.get(action);
                CondorVM vm = placerFCFS(cloudlet);

                TSPTask tsp_task = (TSPTask) ((Job) cloudlet).getTaskList().get(0);
                TSPJobManager.addTaskRunning(tsp_task);

                double reward = getReward(tsp_task, vm);

                System.out.println("Reward " + reward);
                TSPSocketClient.saveReward(tsp_task.getCloudletId(), reward);
                if (TSPJobManager.last_executed_cloudlet_id != -1){
                    //updating the placer information
                    TSPSocketClient.retrain(TSPJobManager.last_executed_cloudlet_id, state);
                }
                TSPJobManager.last_executed_cloudlet_id = tsp_task.getCloudletId();
            }
        }
    }

    /**
     * Placer in the next servers with enough resources
     */
    public CondorVM placerFCFS(Cloudlet cloudlet){
        for (Iterator itc = getNotMobileVmList().iterator(); itc.hasNext();) {
            CondorVM vm = (CondorVM) itc.next();
            if (vm.getState() == WorkflowSimTags.VM_STATUS_IDLE) {
                vm.setState(WorkflowSimTags.VM_STATUS_BUSY);
                cloudlet.setVmId(vm.getId());
                System.out.println("vm"+vm.getId()+".mips: "+vm.getMips()+"  host: "+vm.getHost().getId());
                getScheduledList().add(cloudlet);
                return vm;
            }
        }
        return null;
    }

}
