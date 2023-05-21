package org.cloudbus.cloudsim.container.core;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.container.app.model.DatacenterMetrics;
import org.cloudbus.cloudsim.container.app.model.Task;
import org.cloudbus.cloudsim.container.schedulers.UserRequestScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.List;
import java.util.stream.Collectors;

public class ContainerDatacenterBroker extends SimEntity {


    private UserRequestScheduler taskScheduler;
    public Integer datacenterId;
    public ContainerDatacenterCharacteristics datacenterCharacteristics;


    private final DatacenterMetrics dcMetrics = DatacenterMetrics.get();

    public ContainerDatacenterBroker(String name) {
        super(name);
    }

    @Override
    public void startEntity() {
        Log.printLine(getName(), " is starting...");
        schedule(getId(), 0, CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST);
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST -> processResourceCharacteristicsRequest();
            case CloudSimTags.RESOURCE_CHARACTERISTICS -> processResourceCharacteristics(ev);
            case ContainerCloudSimTags.TASK_SUBMIT -> processTaskSubmit(ev);
            case ContainerCloudSimTags.CONTAINER_CREATE_ACK -> processContainerAllocated(ev);
            case CloudSimTags.CLOUDLET_RETURN -> processCloudletReturn(ev);
            default -> processOtherEvent(ev);
        }
    }

    private void processContainerAllocated(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        int hostId = data[0];
        int containerId = data[1];
        if (data[2] == CloudSimTags.TRUE) {
            //container allocated
            if (hostId == -1) {
                Log.printLine("Error : Where is the HOST");
            } else {
                Log.printLine(getName(), ": The Container #", containerId, ", is created on host #", hostId);

                sendNow(datacenterId, ContainerCloudSimTags.CONTAINER_DC_LOG);
//                ---->process the cloudlet on created container
                Task processCloudletTask = dcMetrics.getTask(containerId);
                if (processCloudletTask != null) {
                    processCloudletTask.getCloudlet().setContainerId(containerId);
                    sendNow(datacenterId, CloudSimTags.CLOUDLET_SUBMIT, processCloudletTask);
                }

            }
        } else if (data[2] == CloudSimTags.FALSE) {
            Log.printLine(getName(), ": Container allocation failed for containerId #", containerId, " and hostId #", hostId);
            //allocation failed for task. return task to waiting queue untill resources are freed
            Task processCloudletTask = dcMetrics.getTask(containerId);
            sendNow(taskScheduler.getId(), ContainerCloudSimTags.TASK_WAIT, processCloudletTask);
            //return task with container to queue
        }
    }

    private void processTaskSubmit(SimEvent ev) {
        Task task = (Task) ev.getData();
        dcMetrics.startTask(task);
        sendNow(datacenterId, ContainerCloudSimTags.CONTAINER_SUBMIT, task);
    }

    private void processOtherEvent(SimEvent ev) {
        if (ev == null) {
            Log.printLine(getName(), ".processOtherEvent(): ", "Error - an event is null.");
            return;
        }
        Log.printLine(getName(), ".processOtherEvent(): Error - event unknown by this DatacenterBroker.");
    }


    private void processCloudletReturn(SimEvent ev) {

        ContainerCloudlet cloudlet = (ContainerCloudlet) ev.getData();
        List<String> finishedCloudlets = dcMetrics.getFinishedTasks().stream().map(t -> String.valueOf(t.getCloudlet().getCloudletId())).collect(Collectors.toList());
        finishedCloudlets.add(String.valueOf(cloudlet.getCloudletId()));

        Log.printLine(getName(), ": Cloudlet #", cloudlet.getCloudletId(),
                " returned. finished Cloudlets = ", String.join(", ", finishedCloudlets));
        Task task = dcMetrics.getTask(cloudlet);
        //deallocate the container used for cloudlet processing
        sendNow(datacenterId, ContainerCloudSimTags.CONTAINER_DESTROY, task.getContainer());
        sendNow(taskScheduler.getId(), ContainerCloudSimTags.TASK_RETURN, task);
        dcMetrics.finishTask(task);


        if (dcMetrics.allUserRequestTasksProcessed() && taskScheduler.hasMoreTasks()) {
            Log.printLine(getName(), ": All Cloudlets executed. Finishing...");
            clearDatacenters();
            finishExecution();
        } else { // some cloudlets haven't finished yet
            if (dcMetrics.hasTasksToProcess()) {
                // all the cloudlets sent finished. It means that some bount
                // cloudlet is waiting its container to be created
                //should never happen since the contains are allocated for specific cloudlet
                clearDatacenters();
            }

        }
    }

    protected void finishExecution() {
        sendNow(getId(), CloudSimTags.END_OF_SIMULATION);
    }


    protected void clearDatacenters() {
//TODO clear containers?hosts?

    }


    private void processResourceCharacteristics(SimEvent ev) {
        this.datacenterCharacteristics = (ContainerDatacenterCharacteristics) ev.getData();
    }

    private void processResourceCharacteristicsRequest() {
        this.datacenterId = CloudSim.getCloudResourceList() != null && CloudSim.getCloudResourceList().size() > 0
                ? CloudSim.getCloudResourceList().get(0)
                : null;
        if (datacenterId != null) {
            sendNow(datacenterId, CloudSimTags.RESOURCE_CHARACTERISTICS, getId());
        }

    }

    @Override
    public void shutdownEntity() {
        Log.printLine(getName(), " is shutting down...");
    }


    public void bind(UserRequestScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void printSystemMetrics() {
        dcMetrics.printMetrics();
    }
}
